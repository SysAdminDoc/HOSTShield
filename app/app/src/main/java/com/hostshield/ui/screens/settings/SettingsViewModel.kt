package com.hostshield.ui.screens.settings

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.hostshield.data.model.RuleType
import com.hostshield.data.preferences.AppPreferences
import com.hostshield.data.repository.HostShieldRepository
import com.hostshield.service.HostsUpdateWorker
import com.hostshield.service.LocalDnsServer
import com.hostshield.service.LocalDnsServerService
import com.hostshield.service.SourceHealthWorker
import com.hostshield.service.ThreatIntelWorker
import com.hostshield.service.parseSupportedLocalDnsPort
import com.hostshield.util.BackupRestoreUtil
import com.hostshield.util.BatteryOptimizationUtil
import com.hostshield.util.DiagnosticEventStore
import com.hostshield.util.DiagnosticEventType
import com.hostshield.util.EvidenceJsonlDataset
import com.hostshield.util.EvidenceJsonlExportOptions
import com.hostshield.util.EvidenceJsonlExporter
import com.hostshield.util.ImportExportUtil
import com.hostshield.util.PcapExporter
import com.hostshield.util.RootUtil
import com.hostshield.util.UpdateChecker
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.File
import java.io.OutputStream
import javax.inject.Inject

// ══════════════════════════════════════════════════════════════
// Settings view model
// ══════════════════════════════════════════════════════════════

enum class ExportArtifactKind {
    RULES_JSON,
    FIREWALL_RULES,
    SHAREABLE_BLOCKLIST,
    STATS_CSV,
    DIAGNOSTIC_ZIP,
    PCAP,
    EVIDENCE_JSONL,
    BACKUP
}

data class ExportArtifact(
    val kind: ExportArtifactKind,
    val fileName: String,
    val mimeType: String,
    val privacyNotice: String,
    val shareSubject: String,
    val chooserTitle: String,
    val sizeBytes: Long,
    val content: String? = null,
    val filePath: String? = null,
    val requestId: Long = System.nanoTime()
)

internal fun buildContentExportArtifact(
    kind: ExportArtifactKind,
    fileName: String,
    mimeType: String,
    privacyNotice: String,
    shareSubject: String,
    chooserTitle: String,
    content: String
): ExportArtifact =
    ExportArtifact(
        kind = kind,
        fileName = fileName,
        mimeType = mimeType,
        privacyNotice = privacyNotice,
        shareSubject = shareSubject,
        chooserTitle = chooserTitle,
        sizeBytes = content.toByteArray(Charsets.UTF_8).size.toLong(),
        content = content
    )

internal fun buildFileExportArtifact(
    kind: ExportArtifactKind,
    file: File,
    mimeType: String,
    privacyNotice: String,
    shareSubject: String,
    chooserTitle: String
): ExportArtifact =
    ExportArtifact(
        kind = kind,
        fileName = file.name,
        mimeType = mimeType,
        privacyNotice = privacyNotice,
        shareSubject = shareSubject,
        chooserTitle = chooserTitle,
        sizeBytes = file.length(),
        filePath = file.absolutePath
    )

internal fun ExportArtifact.writeExportBytesTo(output: OutputStream) {
    when {
        content != null -> output.write(content.toByteArray(Charsets.UTF_8))
        filePath != null -> File(filePath).inputStream().use { it.copyTo(output) }
        else -> error("Export artifact has no content")
    }
}

sealed interface PcapExportState {
    data object Idle : PcapExportState
    data object Exporting : PcapExportState
    data class Ready(
        val artifact: ExportArtifact,
        val mode: String
    ) : PcapExportState {
        val filePath: String get() = artifact.filePath.orEmpty()
        val fileName: String get() = artifact.fileName
        val sizeBytes: Long get() = artifact.sizeBytes
    }
    data object Empty : PcapExportState
    data class Failed(val error: String) : PcapExportState
}

sealed interface EvidenceJsonlExportState {
    data object Idle : EvidenceJsonlExportState
    data object Exporting : EvidenceJsonlExportState
    data class Ready(
        val artifact: ExportArtifact,
        val mode: String,
        val rowCount: Int,
        val chunkCount: Int,
        val truncated: Boolean
    ) : EvidenceJsonlExportState {
        val fileName: String get() = artifact.fileName
        val sizeBytes: Long get() = artifact.sizeBytes
    }
    data object Empty : EvidenceJsonlExportState
    data class Failed(val error: String) : EvidenceJsonlExportState
}

sealed interface DiagnosticExportState {
    data object Idle : DiagnosticExportState
    data object Generating : DiagnosticExportState
    data class Ready(val artifact: ExportArtifact) : DiagnosticExportState {
        val filePath: String get() = artifact.filePath.orEmpty()
        val fileName: String get() = artifact.fileName
        val sizeBytes: Long get() = artifact.sizeBytes
        val privacyNotice: String get() = artifact.privacyNotice
    }
    data class Failed(val error: String) : DiagnosticExportState
}

sealed interface BackupDialogState {
    data object None : BackupDialogState
    data object ExportChoice : BackupDialogState
    data class ExportPassphrase(val uri: Uri) : BackupDialogState
    data class ImportPassphrase(
        val uri: Uri,
        val error: String? = null
    ) : BackupDialogState
}

data class SettingsUiState(
    val ipv4Redirect: String = "0.0.0.0",
    val ipv6Redirect: String = "::",
    val includeIpv6: Boolean = true,
    val localWebserver: Boolean = false,
    val autoUpdate: Boolean = true,
    val updateIntervalHours: Int = 24,
    val wifiOnly: Boolean = true,
    val dnsLogging: Boolean = true,
    val logRetentionDays: Int = 7,
    val connectionLogRetentionDays: Int = 3,
    val showNotification: Boolean = true,
    val dohEnabled: Boolean = false,
    val dohProvider: String = "cloudflare",
    val dnsTrapEnabled: Boolean = true,
    /** Block response type: "nxdomain", "zero_ip", "refused" */
    val blockResponseType: String = "nxdomain",
    val edeEnabled: Boolean = false,
    val isRootAvailable: Boolean = false,
    val systemInfo: Map<String, String> = emptyMap(),
    val pendingExportSave: ExportArtifact? = null,
    val importMessage: String? = null,
    val backupMessage: String? = null,
    val backupDialog: BackupDialogState = BackupDialogState.None,
    val diagnosticExport: DiagnosticExportState = DiagnosticExportState.Idle,
    val pcapExport: PcapExportState = PcapExportState.Idle,
    val evidenceJsonlExport: EvidenceJsonlExportState = EvidenceJsonlExportState.Idle,
    val batteryOptimized: Boolean = false,
    val batteryMessage: String = "",
    val oemBatteryKiller: String? = null,
    val firewalledApps: Int = 0,
    // App update checker
    val isCheckingUpdate: Boolean = false,
    val updateAvailable: Boolean = false,
    val latestVersion: String = "",
    val updateDownloadUrl: String = "",
    val updateReleaseNotes: String = "",
    val updatePublishedAt: String = "",
    val updateHtmlUrl: String = "",
    val updateMessage: String? = null,
    val accentColor: String = "teal",
    val highContrastAmoled: Boolean = false,
    val dynamicColor: Boolean = false,
    val themeMode: String = "dark",
    val scheduleEnabled: Boolean = false,
    val scheduleStart: String = "22:00",
    val scheduleEnd: String = "07:00",
    val scheduleMode: String = "block",
    val customUpstreamDns: String = "",
    val dotEnabled: Boolean = false,
    val dotProvider: String = "cloudflare",
    val doqEnabled: Boolean = false,
    val doqProvider: String = "adguard",
    val wireGuardEnabled: Boolean = false,
    val wireGuardEndpoint: String = "",
    val wireGuardDnsIp: String = "",
    val onlineGeoIpEnabled: Boolean = false,
    val lanDnsEnabled: Boolean = false,
    val lanDnsRunning: Boolean = false,
    val lanDnsPort: Int = LocalDnsServer.DEFAULT_PORT,
    val lanDnsAllowExternalClients: Boolean = false,
    val lanDnsQueriesHandled: Int = 0,
    val lanDnsQueriesBlocked: Int = 0,
    val lanDnsStatusMessage: String = "LAN DNS server stopped",
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    application: Application,
    private val prefs: AppPreferences,
    private val repository: HostShieldRepository,
    private val rootUtil: RootUtil,
    private val importExport: ImportExportUtil,
    private val backupRestore: BackupRestoreUtil,
    private val batteryUtil: BatteryOptimizationUtil,
    private val pcapExporter: PcapExporter,
    private val evidenceJsonlExporter: EvidenceJsonlExporter,
    private val updateChecker: UpdateChecker,
    private val diagnosticExporter: com.hostshield.util.DiagnosticExporter,
    private val diagnosticEvents: DiagnosticEventStore,
    private val firewallRuleDao: com.hostshield.data.database.FirewallRuleDao,
    private val localDnsServer: LocalDnsServer
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        observePrefs()
        loadSystemInfo()
        checkBattery()
        // Auto-check for updates when settings screen opens (silent, no error display)
        autoCheckForUpdate()
        observeLocalDnsStatus()
    }

    private fun observePrefs() {
        // ── IP / Redirect preferences ─────────────────────────
        viewModelScope.launch {
            combine(
                prefs.ipv4Redirect,
                prefs.ipv6Redirect,
                prefs.includeIpv6,
                prefs.localWebserver
            ) { ipv4, ipv6, inclV6, webserver ->
                IpRedirectPrefs(ipv4, ipv6, inclV6, webserver)
            }.collect { p ->
                _uiState.update {
                    it.copy(
                        ipv4Redirect = p.ipv4,
                        ipv6Redirect = p.ipv6,
                        includeIpv6 = p.includeIpv6,
                        localWebserver = p.localWebserver
                    )
                }
            }
        }

        // ── Auto-update preferences ───────────────────────────
        viewModelScope.launch {
            combine(
                prefs.autoUpdate,
                prefs.updateIntervalHours,
                prefs.wifiOnly
            ) { auto, interval, wifi ->
                UpdatePrefs(auto, interval, wifi)
            }.collect { p ->
                _uiState.update {
                    it.copy(
                        autoUpdate = p.autoUpdate,
                        updateIntervalHours = p.intervalHours,
                        wifiOnly = p.wifiOnly
                    )
                }
            }
        }

        // ── DNS / Logging preferences ─────────────────────────
        viewModelScope.launch {
            val dnsBasePrefs = combine(
                prefs.dnsLogging,
                prefs.logRetentionDays,
                prefs.dohEnabled,
                prefs.dohProvider
            ) { logging, retentionDays, dohEnabled, dohProvider ->
                DnsBasePrefs(logging, retentionDays, dohEnabled, dohProvider)
            }
            val dnsResponsePrefs = combine(
                prefs.dnsTrapEnabled,
                prefs.blockResponseType,
                prefs.customUpstreamDns,
                prefs.edeEnabled
            ) { dnsTrap, blockResponse, customUpstream, ede ->
                DnsResponsePrefs(dnsTrap, blockResponse, customUpstream, ede)
            }
            combine(dnsBasePrefs, dnsResponsePrefs) { base, response ->
                DnsPrefs(
                    logging = base.logging,
                    retentionDays = base.retentionDays,
                    dohEnabled = base.dohEnabled,
                    dohProvider = base.dohProvider,
                    dnsTrap = response.dnsTrap,
                    blockResponse = response.blockResponse,
                    customUpstream = response.customUpstream,
                    edeEnabled = response.edeEnabled
                )
            }.collect { p ->
                _uiState.update {
                    it.copy(
                        dnsLogging = p.logging,
                        logRetentionDays = p.retentionDays,
                        dohEnabled = p.dohEnabled,
                        dohProvider = p.dohProvider,
                        dnsTrapEnabled = p.dnsTrap,
                        blockResponseType = p.blockResponse,
                        customUpstreamDns = p.customUpstream,
                        edeEnabled = p.edeEnabled
                    )
                }
            }
        }

        // ── Online GeoIP fallback ─────────────────────────────
        viewModelScope.launch {
            prefs.onlineGeoIpEnabled.collect { enabled ->
                _uiState.update { it.copy(onlineGeoIpEnabled = enabled) }
            }
        }

        viewModelScope.launch {
            combine(
                prefs.lanDnsEnabled,
                prefs.lanDnsPort,
                prefs.lanDnsAllowExternalClients
            ) { enabled, port, allowExternal ->
                LanDnsPrefs(enabled, port, allowExternal)
            }.collect { p ->
                _uiState.update {
                    it.copy(
                        lanDnsEnabled = p.enabled,
                        lanDnsPort = p.port,
                        lanDnsAllowExternalClients = p.allowExternalClients
                    )
                }
                refreshLocalDnsStatus()
            }
        }

        // ── Notification / UI / Firewall count ────────────────
        viewModelScope.launch {
            combine(
                prefs.showNotification,
                prefs.accentColor,
                prefs.highContrastAmoled,
                prefs.dynamicColor,
                prefs.blockedApps
            ) { notification, accent, highContrast, dynamic, apps ->
                UiPrefs(notification, accent, highContrast, dynamic, "dark", apps.size)
            }.collect { p ->
                _uiState.update {
                    it.copy(
                        showNotification = p.showNotification,
                        accentColor = p.accentColor,
                        highContrastAmoled = p.highContrastAmoled,
                        dynamicColor = p.dynamicColor,
                        firewalledApps = p.firewalledApps
                    )
                }
            }
        }

        viewModelScope.launch {
            prefs.themeMode.collect { mode ->
                _uiState.update { it.copy(themeMode = mode) }
            }
        }

        // ── Schedule preferences ──────────────────────────────
        viewModelScope.launch {
            combine(
                prefs.scheduleEnabled,
                prefs.scheduleStart,
                prefs.scheduleEnd,
                prefs.scheduleMode
            ) { enabled, start, end, mode ->
                SchedulePrefs(enabled, start, end, mode)
            }.collect { p ->
                _uiState.update {
                    it.copy(
                        scheduleEnabled = p.enabled,
                        scheduleStart = p.start,
                        scheduleEnd = p.end,
                        scheduleMode = p.mode
                    )
                }
            }
        }

        // ── DoT / DoQ preferences ─────────────────────────────
        viewModelScope.launch {
            combine(
                prefs.dotEnabled,
                prefs.dotProvider,
                prefs.doqEnabled,
                prefs.doqProvider
            ) { dotOn, dotProv, doqOn, doqProv ->
                DotDoqPrefs(dotOn, dotProv, doqOn, doqProv)
            }.collect { p ->
                _uiState.update {
                    it.copy(
                        dotEnabled = p.dotEnabled,
                        dotProvider = p.dotProvider,
                        doqEnabled = p.doqEnabled,
                        doqProvider = p.doqProvider
                    )
                }
            }
        }

        // ── WireGuard preferences ─────────────────────────────
        viewModelScope.launch {
            combine(
                prefs.wireGuardEnabled,
                prefs.wireGuardEndpoint,
                prefs.wireGuardDnsIp
            ) { enabled, endpoint, dnsIp ->
                WireGuardPrefs(enabled, endpoint, dnsIp)
            }.collect { p ->
                _uiState.update {
                    it.copy(
                        wireGuardEnabled = p.enabled,
                        wireGuardEndpoint = p.endpoint,
                        wireGuardDnsIp = p.dnsIp
                    )
                }
            }
        }

        // ── Root availability (one-shot) ──────────────────────
        viewModelScope.launch(Dispatchers.IO) {
            val available = rootUtil.isRootAvailable()
            _uiState.update { it.copy(isRootAvailable = available) }
        }
    }

    // ── Combined preference data holders ──────────────────────
    private data class IpRedirectPrefs(val ipv4: String, val ipv6: String, val includeIpv6: Boolean, val localWebserver: Boolean)
    private data class UpdatePrefs(val autoUpdate: Boolean, val intervalHours: Int, val wifiOnly: Boolean)
    private data class DnsBasePrefs(val logging: Boolean, val retentionDays: Int, val dohEnabled: Boolean, val dohProvider: String)
    private data class DnsResponsePrefs(val dnsTrap: Boolean, val blockResponse: String, val customUpstream: String, val edeEnabled: Boolean)
    private data class DnsPrefs(val logging: Boolean, val retentionDays: Int, val dohEnabled: Boolean, val dohProvider: String, val dnsTrap: Boolean, val blockResponse: String, val customUpstream: String, val edeEnabled: Boolean)
    private data class UiPrefs(
        val showNotification: Boolean,
        val accentColor: String,
        val highContrastAmoled: Boolean,
        val dynamicColor: Boolean,
        val themeMode: String,
        val firewalledApps: Int
    )
    private data class SchedulePrefs(val enabled: Boolean, val start: String, val end: String, val mode: String)
    private data class DotDoqPrefs(val dotEnabled: Boolean, val dotProvider: String, val doqEnabled: Boolean, val doqProvider: String)
    private data class WireGuardPrefs(val enabled: Boolean, val endpoint: String, val dnsIp: String)
    private data class LanDnsPrefs(val enabled: Boolean, val port: Int, val allowExternalClients: Boolean)

    private fun loadSystemInfo() {
        viewModelScope.launch {
            val info = rootUtil.getSystemInfo()
            _uiState.update { it.copy(systemInfo = info) }
        }
    }

    private fun checkBattery() {
        viewModelScope.launch(Dispatchers.IO) {
            val status = batteryUtil.check()
            _uiState.update {
                it.copy(
                    batteryOptimized = status.isOptimized,
                    batteryMessage = status.message,
                    oemBatteryKiller = status.oemBatteryKiller
                )
            }
        }
    }

    fun requestBatteryExemption(activityContext: android.content.Context): Boolean {
        return batteryUtil.requestExemption(activityContext)
    }
    fun refreshBattery() { checkBattery() }

    private fun observeLocalDnsStatus() {
        viewModelScope.launch {
            while (true) {
                refreshLocalDnsStatus()
                delay(1_000L)
            }
        }
    }

    private fun refreshLocalDnsStatus(messageOverride: String? = null) {
        val status = localDnsServer.status()
        _uiState.update {
            it.copy(
                lanDnsRunning = status.isRunning,
                lanDnsQueriesHandled = status.queriesHandled,
                lanDnsQueriesBlocked = status.queriesBlocked,
                lanDnsStatusMessage = messageOverride ?: status.message
            )
        }
    }

    fun setLanDnsEnabled(enabled: Boolean) {
        viewModelScope.launch {
            if (enabled) {
                val state = _uiState.value
                prefs.setLanDnsEnabled(true)
                val started = LocalDnsServerService.start(
                    getApplication(),
                    state.lanDnsPort,
                    state.lanDnsAllowExternalClients,
                    "SettingsViewModel"
                )
                if (started) {
                    refreshLocalDnsStatus("Starting LAN DNS server...")
                } else {
                    prefs.setLanDnsEnabled(false)
                    refreshLocalDnsStatus("LAN DNS service start was denied by Android")
                }
            } else {
                prefs.setLanDnsEnabled(false)
                LocalDnsServerService.stop(getApplication())
                refreshLocalDnsStatus("LAN DNS server stopping...")
            }
        }
    }

    fun setLanDnsPort(value: String) {
        val parsedPort = parseSupportedLocalDnsPort(value)
        if (parsedPort == null) {
            refreshLocalDnsStatus("Port must be between 1024 and 65535")
            return
        }
        viewModelScope.launch {
            prefs.setLanDnsPort(parsedPort)
            if (_uiState.value.lanDnsEnabled) {
                restartLanDns(parsedPort, _uiState.value.lanDnsAllowExternalClients)
            }
        }
    }

    fun setLanDnsAllowExternalClients(enabled: Boolean) {
        viewModelScope.launch {
            prefs.setLanDnsAllowExternalClients(enabled)
            if (_uiState.value.lanDnsEnabled) {
                restartLanDns(_uiState.value.lanDnsPort, enabled)
            }
        }
    }

    private suspend fun restartLanDns(port: Int, allowExternalClients: Boolean) {
        LocalDnsServerService.stop(getApplication(), persistDisabled = false)
        val started = LocalDnsServerService.start(
            getApplication(),
            port,
            allowExternalClients,
            "SettingsViewModel.restartLanDns"
        )
        refreshLocalDnsStatus(
            if (started) "Restarting LAN DNS server..." else "LAN DNS service restart was denied by Android"
        )
    }

    fun setDnsTrapEnabled(v: Boolean) { viewModelScope.launch { prefs.setDnsTrapEnabled(v) } }

    fun setIncludeIpv6(v: Boolean) { viewModelScope.launch { prefs.setIncludeIpv6(v) } }
    fun setLocalWebserver(v: Boolean) { viewModelScope.launch { prefs.setLocalWebserver(v) } }

    fun setAutoUpdate(v: Boolean) {
        viewModelScope.launch {
            prefs.setAutoUpdate(v)
            if (v) {
                val interval = prefs.updateIntervalHours.first()
                val wifi = prefs.wifiOnly.first()
                HostsUpdateWorker.schedule(getApplication(), interval, wifi)
            } else {
                HostsUpdateWorker.cancel(getApplication())
            }
        }
    }

    fun setUpdateInterval(hours: Int) {
        viewModelScope.launch {
            prefs.setUpdateIntervalHours(hours)
            if (prefs.autoUpdate.first()) {
                val wifi = prefs.wifiOnly.first()
                HostsUpdateWorker.schedule(getApplication(), hours, wifi)
            }
        }
    }

    fun setWifiOnly(v: Boolean) {
        viewModelScope.launch {
            prefs.setWifiOnly(v)
            if (prefs.autoUpdate.first()) {
                val interval = prefs.updateIntervalHours.first()
                HostsUpdateWorker.schedule(getApplication(), interval, v)
            }
            SourceHealthWorker.schedule(getApplication(), v)
            ThreatIntelWorker.schedule(getApplication(), v)
        }
    }

    fun setDnsLogging(v: Boolean) { viewModelScope.launch { prefs.setDnsLogging(v) } }
    fun setShowNotification(v: Boolean) { viewModelScope.launch { prefs.setShowNotification(v) } }
    fun setDohEnabled(v: Boolean) { viewModelScope.launch { prefs.setDohEnabled(v) } }
    fun setOnlineGeoIpEnabled(v: Boolean) { viewModelScope.launch { prefs.setOnlineGeoIpEnabled(v) } }

    fun setDohProvider(provider: String) { viewModelScope.launch { prefs.setDohProvider(provider) } }

    fun setIpv4Redirect(ip: String) { viewModelScope.launch { prefs.setIpv4Redirect(ip) } }
    fun setIpv6Redirect(ip: String) { viewModelScope.launch { prefs.setIpv6Redirect(ip) } }
    fun setLogRetention(days: Int) { viewModelScope.launch { prefs.setLogRetentionDays(days) } }
    fun setBlockResponseType(type: String) { viewModelScope.launch { prefs.setBlockResponseType(type) } }
    fun setEdeEnabled(enabled: Boolean) { viewModelScope.launch { prefs.setEdeEnabled(enabled) } }
    fun setAccentColor(color: String) { viewModelScope.launch { prefs.setAccentColor(color) } }
    fun setHighContrastAmoled(enabled: Boolean) { viewModelScope.launch { prefs.setHighContrastAmoled(enabled) } }
    fun setDynamicColor(enabled: Boolean) { viewModelScope.launch { prefs.setDynamicColor(enabled) } }
    fun setThemeMode(mode: String) { viewModelScope.launch { prefs.setThemeMode(mode) } }

    fun setScheduleEnabled(v: Boolean) {
        viewModelScope.launch {
            prefs.setScheduleEnabled(v)
            if (v) com.hostshield.service.BlockingScheduleWorker.schedule(getApplication())
            else com.hostshield.service.BlockingScheduleWorker.cancel(getApplication())
        }
    }
    fun setScheduleMode(mode: String) { viewModelScope.launch { prefs.setScheduleMode(mode) } }
    fun setCustomUpstreamDns(dns: String) { viewModelScope.launch { prefs.setCustomUpstreamDns(dns.trim()) } }

    fun setDotEnabled(v: Boolean) { viewModelScope.launch { prefs.setDotEnabled(v) } }
    fun setDotProvider(provider: String) { viewModelScope.launch { prefs.setDotProvider(provider) } }
    fun setDoqEnabled(v: Boolean) { viewModelScope.launch { prefs.setDoqEnabled(v) } }
    fun setDoqProvider(provider: String) { viewModelScope.launch { prefs.setDoqProvider(provider) } }
    fun setWireGuardEnabled(v: Boolean) { viewModelScope.launch { prefs.setWireGuardEnabled(v) } }
    fun setWireGuardEndpoint(endpoint: String) { viewModelScope.launch { prefs.setWireGuardEndpoint(endpoint.trim()) } }
    fun setWireGuardDnsIp(ip: String) { viewModelScope.launch { prefs.setWireGuardDnsIp(ip.trim()) } }
    fun setWireGuardPrivateKey(key: String) { viewModelScope.launch { prefs.setWireGuardPrivateKey(key.trim()) } }
    fun setWireGuardPublicKey(key: String) { viewModelScope.launch { prefs.setWireGuardPublicKey(key.trim()) } }
    fun setWireGuardPresharedKey(key: String) { viewModelScope.launch { prefs.setWireGuardPresharedKey(key.trim()) } }

    fun clearDnsCache() {
        com.hostshield.service.DnsVpnService.clearCacheCallback?.invoke()
    }

    /** Export rules JSON directly to a SAF URI. */
    fun exportRulesToUri(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val rules = repository.getAllRules().first()
                val sources = repository.getAllSources().first()
                val artifact = buildContentExportArtifact(
                    kind = ExportArtifactKind.RULES_JSON,
                    fileName = "hostshield_rules.json",
                    mimeType = "application/json",
                    privacyNotice = "Contains user rules and source URLs.",
                    shareSubject = "HostShield Rules Export",
                    chooserTitle = "Share Rules Export",
                    content = importExport.exportJson(rules, sources)
                )
                writeExportArtifactToUri(artifact, uri)
                _uiState.update { it.copy(importMessage = "Exported ${rules.size} rules") }
            } catch (e: Exception) {
                android.util.Log.e("SettingsViewModel", "Rules export failed", e)
                _uiState.update { it.copy(importMessage = "Export failed. Choose another location and try again.") }
            }
        }
    }

    fun savePendingExportToUri(uri: Uri) {
        val artifact = _uiState.value.pendingExportSave ?: return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                writeExportArtifactToUri(artifact, uri)
                _uiState.update { it.copy(pendingExportSave = null) }
                when (artifact.kind) {
                    ExportArtifactKind.STATS_CSV -> _csvMessage.value = "Stats CSV saved"
                    else -> _uiState.update { it.copy(importMessage = "${artifact.fileName} saved") }
                }
            } catch (e: Exception) {
                android.util.Log.e("SettingsViewModel", "Export save failed", e)
                _uiState.update { it.copy(pendingExportSave = null) }
                when (artifact.kind) {
                    ExportArtifactKind.STATS_CSV -> _csvMessage.value = "Save failed. Choose another location."
                    else -> _uiState.update {
                        it.copy(importMessage = "Export failed. Choose another location and try again.")
                    }
                }
            }
        }
    }

    fun clearPendingExportSave() {
        _uiState.update { it.copy(pendingExportSave = null) }
    }

    fun importFromUri(uri: Uri) {
        viewModelScope.launch {
            try {
                val content = importExport.readUri(getApplication(), uri)
                val result = importExport.autoImport(content)
                val allRules = result.blocklist + result.allowlist + result.redirects
                if (allRules.isNotEmpty()) {
                    allRules.forEach { repository.addRule(it) }
                }
                result.sources.forEach { repository.addSource(it) }
                val count = allRules.size + result.sources.size
                _uiState.update { it.copy(importMessage = "Imported $count items (${result.format})") }
            } catch (e: Exception) {
                android.util.Log.e("SettingsViewModel", "Rules import failed", e)
                _uiState.update { it.copy(importMessage = "Import failed. Check the selected file and try again.") }
            }
        }
    }

    fun clearImportMessage() { _uiState.update { it.copy(importMessage = null) } }
    fun clearExportResult() { clearPendingExportSave() }
    fun clearBackupMessage() { _uiState.update { it.copy(backupMessage = null) } }

    fun showBackupExportChoice() {
        _uiState.update { it.copy(backupDialog = BackupDialogState.ExportChoice) }
    }

    fun dismissBackupDialog() {
        _uiState.update { it.copy(backupDialog = BackupDialogState.None) }
    }

    fun startEncryptedExport(uri: Uri) {
        _uiState.update { it.copy(backupDialog = BackupDialogState.ExportPassphrase(uri)) }
    }

    fun retryImportPassphrase(uri: Uri, error: String? = null) {
        _uiState.update { it.copy(backupDialog = BackupDialogState.ImportPassphrase(uri, error)) }
    }

    /** Export user rules as a shareable hosts file that other blockers can subscribe to. */
    fun exportShareableBlocklist() {
        viewModelScope.launch {
            try {
                val blockRules = repository.getEnabledRulesByType(RuleType.BLOCK)
                val allowRules = repository.getEnabledRulesByType(RuleType.ALLOW)
                val artifact = buildContentExportArtifact(
                    kind = ExportArtifactKind.SHAREABLE_BLOCKLIST,
                    fileName = "hostshield_blocklist.txt",
                    mimeType = "text/plain",
                    privacyNotice = "Contains enabled block and allow domains intended for sharing.",
                    shareSubject = "HostShield Shareable Blocklist",
                    chooserTitle = "Share Blocklist",
                    content = importExport.exportShareableHostsFile(blockRules, allowRules)
                )
                _uiState.update { it.copy(pendingExportSave = artifact, importMessage = null) }
            } catch (e: Exception) {
                android.util.Log.e("SettingsViewModel", "Shareable blocklist export failed", e)
                _uiState.update { it.copy(importMessage = "Export failed. Try again.") }
            }
        }
    }

    /** Export firewall rules as JSON to share/transfer. */
    fun exportFirewallRules() {
        viewModelScope.launch {
            try {
                val rules = firewallRuleDao.getAllRulesList()
                val artifact = buildContentExportArtifact(
                    kind = ExportArtifactKind.FIREWALL_RULES,
                    fileName = "hostshield_firewall_rules.json",
                    mimeType = "application/json",
                    privacyNotice = "Contains per-app firewall package names and network policy.",
                    shareSubject = "HostShield Firewall Rules Export",
                    chooserTitle = "Share Firewall Rules",
                    content = importExport.exportFirewallJson(rules)
                )
                _uiState.update { it.copy(pendingExportSave = artifact, importMessage = null) }
            } catch (e: Exception) {
                android.util.Log.e("SettingsViewModel", "Firewall export failed", e)
                _uiState.update { it.copy(importMessage = "Firewall export failed. Try again.") }
            }
        }
    }

    /**
     * Create a backup and write it to the given URI.
     * If [passphrase] is non-null and non-empty, the backup is AES-256-GCM encrypted.
     * Otherwise, plaintext JSON is written (backward-compatible).
     */
    fun backupToUri(uri: Uri, passphrase: String? = null) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val json = backupRestore.createBackup()
                if (passphrase.isNullOrEmpty()) {
                    val artifact = buildContentExportArtifact(
                        kind = ExportArtifactKind.BACKUP,
                        fileName = "hostshield_backup.json",
                        mimeType = "application/json",
                        privacyNotice = "Contains HostShield settings, rules, sources, profiles, and local preferences.",
                        shareSubject = "HostShield Backup",
                        chooserTitle = "Share Backup",
                        content = json
                    )
                    writeExportArtifactToUri(artifact, uri)
                } else {
                    backupRestore.writeBackupToUri(getApplication(), uri, json, passphrase)
                }
                val suffix = if (!passphrase.isNullOrEmpty()) " (encrypted)" else ""
                _uiState.update { it.copy(backupMessage = "Backup saved successfully$suffix") }
            } catch (e: Exception) {
                android.util.Log.e("SettingsViewModel", "Backup export failed", e)
                _uiState.update { it.copy(backupMessage = "Backup failed. Choose another location and try again.") }
            }
        }
    }

    fun restoreFromUri(uri: Uri, passphrase: String? = null) {
        viewModelScope.launch {
            try {
                val json = backupRestore.readBackupFromUri(getApplication(), uri, passphrase)
                val result = backupRestore.restoreBackup(json)
                _uiState.update {
                    it.copy(
                        backupDialog = BackupDialogState.None,
                        backupMessage = "Restored ${result.sourcesCount} sources, ${result.rulesCount} rules, " +
                            "${result.profilesCount} profiles, ${result.firewallRulesCount} firewall rules"
                    )
                }
            } catch (e: com.hostshield.util.EncryptedBackupException) {
                _uiState.update {
                    it.copy(backupDialog = BackupDialogState.ImportPassphrase(uri))
                }
            } catch (e: javax.crypto.AEADBadTagException) {
                diagnosticEvents.record(
                    DiagnosticEventType.BACKUP_IMPORT_FAILED,
                    "Encrypted backup restore failed",
                    mapOf("reason" to "auth_tag_or_passphrase")
                )
                _uiState.update {
                    it.copy(
                        backupDialog = BackupDialogState.ImportPassphrase(
                            uri, "Incorrect passphrase or corrupted backup"
                        )
                    )
                }
            } catch (e: Exception) {
                diagnosticEvents.record(
                    DiagnosticEventType.BACKUP_IMPORT_FAILED,
                    "Backup restore failed",
                    mapOf("error" to (e.message ?: e.javaClass.simpleName))
                )
                android.util.Log.e("SettingsViewModel", "Backup restore failed", e)
                _uiState.update { it.copy(backupMessage = "Restore failed. Check the selected backup and try again.") }
            }
        }
    }

    fun exportPcap(mode: String = "all", days: Int = 7) {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(pcapExport = PcapExportState.Exporting) }
            try {
                val file = when (mode) {
                    "dns" -> pcapExporter.exportDnsLogs(getApplication(), days)
                    "firewall" -> pcapExporter.exportConnectionLogs(getApplication(), days)
                    else -> pcapExporter.exportAll(getApplication(), days)
                }
                if (file != null) {
                    val artifact = buildFileExportArtifact(
                        kind = ExportArtifactKind.PCAP,
                        file = file,
                        mimeType = "application/vnd.tcpdump.pcap",
                        privacyNotice = "Contains DNS hostnames and connection destinations.",
                        shareSubject = "HostShield PCAP Export ($mode)",
                        chooserTitle = "Share PCAP"
                    )
                    _uiState.update {
                        it.copy(pcapExport = PcapExportState.Ready(artifact, mode))
                    }
                } else {
                    _uiState.update { it.copy(pcapExport = PcapExportState.Empty) }
                }
            } catch (e: Exception) {
                android.util.Log.e("SettingsViewModel", "PCAP export failed", e)
                _uiState.update {
                    it.copy(pcapExport = PcapExportState.Failed("PCAP export failed. Try again."))
                }
            }
        }
    }

    fun sharePcap() {
        val export = _uiState.value.pcapExport
        if (export !is PcapExportState.Ready) return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                shareExportArtifact(export.artifact)
            } catch (e: Exception) {
                android.util.Log.e("Settings", "PCAP share failed: ${e.message}", e)
                _uiState.update {
                    it.copy(pcapExport = PcapExportState.Failed("PCAP share failed. Choose another share target."))
                }
            }
        }
    }

    fun savePcapToUri(uri: Uri) {
        val export = _uiState.value.pcapExport
        if (export !is PcapExportState.Ready) return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                writeExportArtifactToUri(export.artifact, uri)
                deleteExportArtifact(export.artifact)
                _uiState.update { it.copy(pcapExport = PcapExportState.Idle) }
                _uiState.update { it.copy(backupMessage = "PCAP saved to chosen location") }
            } catch (e: Exception) {
                android.util.Log.e("SettingsViewModel", "PCAP save failed", e)
                _uiState.update {
                    it.copy(pcapExport = PcapExportState.Failed("PCAP save failed. Choose another location."))
                }
            }
        }
    }

    fun dismissPcapExport() {
        val export = _uiState.value.pcapExport
        if (export is PcapExportState.Ready) {
            deleteExportArtifact(export.artifact)
        }
        _uiState.update { it.copy(pcapExport = PcapExportState.Idle) }
    }

    // ── App Update Checker ──────────────────────────────────

    fun exportEvidenceJsonl(
        mode: String = "all",
        days: Int = 7,
        query: String = "",
        appFilter: String = "",
        redacted: Boolean = true
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(evidenceJsonlExport = EvidenceJsonlExportState.Exporting) }
            try {
                val modeKey = mode.lowercase()
                val dataset = when (modeKey) {
                    "dns" -> EvidenceJsonlDataset.DNS
                    "firewall", "connections" -> EvidenceJsonlDataset.CONNECTIONS
                    else -> EvidenceJsonlDataset.ALL
                }
                val boundedDays = days.coerceIn(1, 30)
                val sinceMs = System.currentTimeMillis() - boundedDays * 24L * 60L * 60L * 1000L
                val result = evidenceJsonlExporter.export(
                    context = getApplication(),
                    options = EvidenceJsonlExportOptions(
                        dataset = dataset,
                        sinceMs = sinceMs,
                        query = query.trim(),
                        appFilter = appFilter.trim(),
                        redactDomains = redacted,
                        redactApps = redacted,
                        redactIps = redacted
                    )
                )
                if (result != null) {
                    val privacyNotice = if (redacted) {
                        "Domains, app identifiers, and IPs are hashed; timestamps and decisions remain exact."
                    } else {
                        "Contains raw DNS hostnames, app identifiers, IPs, and connection destinations."
                    }
                    val artifact = buildFileExportArtifact(
                        kind = ExportArtifactKind.EVIDENCE_JSONL,
                        file = result.file,
                        mimeType = "application/x-ndjson",
                        privacyNotice = privacyNotice,
                        shareSubject = "HostShield Evidence JSONL ($modeKey)",
                        chooserTitle = "Share Evidence JSONL"
                    )
                    _uiState.update {
                        it.copy(
                            evidenceJsonlExport = EvidenceJsonlExportState.Ready(
                                artifact = artifact,
                                mode = modeKey,
                                rowCount = result.rowCount,
                                chunkCount = result.chunkCount,
                                truncated = result.truncated
                            )
                        )
                    }
                } else {
                    _uiState.update { it.copy(evidenceJsonlExport = EvidenceJsonlExportState.Empty) }
                }
            } catch (e: Exception) {
                android.util.Log.e("SettingsViewModel", "Evidence JSONL export failed", e)
                _uiState.update {
                    it.copy(
                        evidenceJsonlExport = EvidenceJsonlExportState.Failed(
                            "Evidence JSONL export failed. Try a shorter window or fewer filters."
                        )
                    )
                }
            }
        }
    }

    fun shareEvidenceJsonl() {
        val export = _uiState.value.evidenceJsonlExport
        if (export !is EvidenceJsonlExportState.Ready) return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                shareExportArtifact(export.artifact)
            } catch (e: Exception) {
                android.util.Log.e("SettingsViewModel", "Evidence JSONL share failed", e)
                _uiState.update {
                    it.copy(
                        evidenceJsonlExport = EvidenceJsonlExportState.Failed(
                            "Evidence JSONL share failed. Choose another share target."
                        )
                    )
                }
            }
        }
    }

    fun saveEvidenceJsonlToUri(uri: Uri) {
        val export = _uiState.value.evidenceJsonlExport
        if (export !is EvidenceJsonlExportState.Ready) return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                writeExportArtifactToUri(export.artifact, uri)
                deleteExportArtifact(export.artifact)
                _uiState.update {
                    it.copy(
                        evidenceJsonlExport = EvidenceJsonlExportState.Idle,
                        backupMessage = "Evidence JSONL saved to chosen location"
                    )
                }
            } catch (e: Exception) {
                android.util.Log.e("SettingsViewModel", "Evidence JSONL save failed", e)
                _uiState.update {
                    it.copy(
                        evidenceJsonlExport = EvidenceJsonlExportState.Failed(
                            "Evidence JSONL save failed. Choose another location."
                        )
                    )
                }
            }
        }
    }

    fun dismissEvidenceJsonlExport() {
        val export = _uiState.value.evidenceJsonlExport
        if (export is EvidenceJsonlExportState.Ready) {
            deleteExportArtifact(export.artifact)
        }
        _uiState.update { it.copy(evidenceJsonlExport = EvidenceJsonlExportState.Idle) }
    }

    fun checkForUpdate() {
        if (_uiState.value.isCheckingUpdate) return
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(isCheckingUpdate = true, updateMessage = null) }
            updateChecker.check().fold(
                onSuccess = { info ->
                    _uiState.update {
                        it.copy(
                            isCheckingUpdate = false,
                            updateAvailable = info.hasUpdate,
                            latestVersion = info.latestVersion,
                            updateDownloadUrl = info.downloadUrl,
                            updateReleaseNotes = info.releaseNotes,
                            updatePublishedAt = info.publishedAt,
                            updateHtmlUrl = info.htmlUrl,
                            updateMessage = if (info.hasUpdate)
                                "Update available: v${info.latestVersion}"
                            else
                                "You're on the latest version"
                        )
                    }
                },
                onFailure = { err ->
                    android.util.Log.w("SettingsViewModel", "Update check failed", err)
                    _uiState.update {
                        it.copy(
                            isCheckingUpdate = false,
                            updateMessage = "Update check failed. Try again later."
                        )
                    }
                }
            )
        }
    }

    fun dismissUpdateMessage() { _uiState.update { it.copy(updateMessage = null) } }

    /**
     * Silent auto-check: only shows result if an update is available.
     * Throttled to once per `AUTO_CHECK_INTERVAL_MS` per process so navigating
     * back into Settings doesn't hit GitHub on every open. Resets on process
     * restart, which is a reasonable acceptance window for a side-loaded app.
     */
    private fun autoCheckForUpdate() {
        val now = System.currentTimeMillis()
        val last = lastAutoCheckMs.get()
        if (now - last < AUTO_CHECK_INTERVAL_MS) return
        if (!lastAutoCheckMs.compareAndSet(last, now)) return
        viewModelScope.launch(Dispatchers.IO) {
            updateChecker.check().onSuccess { info ->
                if (info.hasUpdate) {
                    _uiState.update {
                        it.copy(
                            updateAvailable = true,
                            latestVersion = info.latestVersion,
                            updateDownloadUrl = info.downloadUrl,
                            updateReleaseNotes = info.releaseNotes,
                            updatePublishedAt = info.publishedAt,
                            updateHtmlUrl = info.htmlUrl,
                            updateMessage = "Update available: v${info.latestVersion}"
                        )
                    }
                }
            }.onFailure {
                // Roll back the throttle so a transient failure doesn't suppress
                // the next legitimate check.
                lastAutoCheckMs.compareAndSet(now, last)
            }
        }
    }

    companion object {
        private const val AUTO_CHECK_INTERVAL_MS = 24L * 60L * 60L * 1000L
        private val lastAutoCheckMs = java.util.concurrent.atomic.AtomicLong(0L)
    }

    // ── Stats CSV Export ──────────────────────────────────────

    private val _csvMessage = MutableStateFlow<String?>(null)
    val csvMessage: StateFlow<String?> = _csvMessage.asStateFlow()
    private val _isExportingCsv = MutableStateFlow(false)
    val isExportingCsv: StateFlow<Boolean> = _isExportingCsv.asStateFlow()

    fun exportStatsCsv() {
        viewModelScope.launch(Dispatchers.IO) {
            _isExportingCsv.value = true
            try {
                val stats = repository.getRecentStats(90).first()
                val topBlocked = repository.getTopBlocked(50).first()
                val topApps = repository.getTopBlockedApps(30).first()

                val sb = StringBuilder()
                sb.appendLine("HostShield Statistics Report")
                sb.appendLine("Generated,${java.time.Instant.now()}")
                sb.appendLine()

                // Daily stats
                sb.appendLine("=== Daily Statistics ===")
                sb.appendLine("Date,Blocked,Allowed,Total Queries")
                stats.forEach { day ->
                    sb.appendLine("${day.date},${day.blockedCount},${day.allowedCount},${day.totalQueries}")
                }
                sb.appendLine()

                // Top blocked domains
                sb.appendLine("=== Top Blocked Domains ===")
                sb.appendLine("Domain,Block Count")
                topBlocked.forEach { sb.appendLine("${it.hostname},${it.cnt}") }
                sb.appendLine()

                // Top apps
                sb.appendLine("=== Top Blocked Apps ===")
                sb.appendLine("Package,Label,Block Count")
                topApps.forEach { sb.appendLine("${it.appPackage},${it.appLabel},${it.cnt}") }

                val artifact = buildContentExportArtifact(
                    kind = ExportArtifactKind.STATS_CSV,
                    fileName = "hostshield_stats_${java.time.LocalDate.now()}.csv",
                    mimeType = "text/csv",
                    privacyNotice = "Contains local DNS statistics, blocked domains, and app package labels.",
                    shareSubject = "HostShield Statistics CSV",
                    chooserTitle = "Share Statistics CSV",
                    content = sb.toString()
                )
                _uiState.update { it.copy(pendingExportSave = artifact) }
                _csvMessage.value = "CSV ready (${stats.size} days, ${topBlocked.size} domains, ${topApps.size} apps)"
            } catch (e: Exception) {
                android.util.Log.e("SettingsViewModel", "CSV export failed", e)
                _csvMessage.value = "Export failed. Try again after reopening Settings."
            } finally {
                _isExportingCsv.value = false
            }
        }
    }

    fun writeCsvToUri(uri: Uri) {
        savePendingExportToUri(uri)
    }

    fun clearCsvMessage() { _csvMessage.value = null }

    fun generateDiagnosticReport() {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(diagnosticExport = DiagnosticExportState.Generating) }
            try {
                val file = diagnosticExporter.generateZip(getApplication())
                val artifact = buildFileExportArtifact(
                    kind = ExportArtifactKind.DIAGNOSTIC_ZIP,
                    file = file,
                    mimeType = "application/zip",
                    privacyNotice = "Contains device info, app configuration, local logs, and diagnostic events.",
                    shareSubject = "HostShield Diagnostic Package",
                    chooserTitle = "Share Diagnostic Package"
                )
                _uiState.update {
                    it.copy(diagnosticExport = DiagnosticExportState.Ready(artifact))
                }
            } catch (e: Exception) {
                android.util.Log.e("SettingsViewModel", "Diagnostic export failed", e)
                _uiState.update {
                    it.copy(diagnosticExport = DiagnosticExportState.Failed(
                        "Diagnostic package could not be created. Try again."
                    ))
                }
            }
        }
    }

    fun shareDiagnosticReport() {
        val export = _uiState.value.diagnosticExport
        if (export !is DiagnosticExportState.Ready) return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                shareExportArtifact(export.artifact)
            } catch (e: Exception) {
                android.util.Log.e("Settings", "Share failed: ${e.message}", e)
                _uiState.update {
                    it.copy(
                        diagnosticExport = DiagnosticExportState.Failed(
                            "Diagnostic package share failed. Choose another share target."
                        )
                    )
                }
            }
        }
    }

    fun saveDiagnosticReportToUri(uri: Uri) {
        val export = _uiState.value.diagnosticExport
        if (export !is DiagnosticExportState.Ready) return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                writeExportArtifactToUri(export.artifact, uri)
                deleteExportArtifact(export.artifact)
                _uiState.update {
                    it.copy(
                        diagnosticExport = DiagnosticExportState.Idle,
                        backupMessage = "Diagnostic package saved to chosen location"
                    )
                }
            } catch (e: Exception) {
                android.util.Log.e("SettingsViewModel", "Diagnostic save failed", e)
                _uiState.update {
                    it.copy(
                        diagnosticExport = DiagnosticExportState.Failed(
                            "Diagnostic package save failed. Choose another location."
                        )
                    )
                }
            }
        }
    }

    fun dismissDiagnosticExport() {
        val export = _uiState.value.diagnosticExport
        if (export is DiagnosticExportState.Ready) {
            deleteExportArtifact(export.artifact)
        }
        _uiState.update { it.copy(diagnosticExport = DiagnosticExportState.Idle) }
    }

    private fun writeExportArtifactToUri(artifact: ExportArtifact, uri: Uri) {
        val resolver = getApplication<android.app.Application>().contentResolver
        val output = resolver.openOutputStream(uri)
            ?: error("Unable to open export destination")
        output.use { artifact.writeExportBytesTo(it) }
    }

    private fun shareExportArtifact(artifact: ExportArtifact) {
        val file = artifact.shareableFile()
        val context = getApplication<android.app.Application>()
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = artifact.mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, artifact.shareSubject)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(
            Intent.createChooser(intent, artifact.chooserTitle)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    private fun deleteExportArtifact(artifact: ExportArtifact) {
        artifact.filePath?.let { path ->
            try {
                File(path).delete()
            } catch (_: Exception) {
            }
        }
    }

    private fun ExportArtifact.shareableFile(): File {
        filePath?.let { return File(it) }
        val dir = File(getApplication<android.app.Application>().cacheDir, "exports")
        dir.mkdirs()
        val file = File(dir, fileName)
        file.outputStream().use { writeExportBytesTo(it) }
        return file
    }
}
