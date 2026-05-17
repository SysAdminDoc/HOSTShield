package com.hostshield.ui.screens.settings

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.hostshield.data.model.RuleType
import com.hostshield.data.preferences.AppPreferences
import com.hostshield.data.repository.HostShieldRepository
import com.hostshield.service.HostsUpdateWorker
import com.hostshield.util.BackupRestoreUtil
import com.hostshield.util.BatteryOptimizationUtil
import com.hostshield.util.DiagnosticEventStore
import com.hostshield.util.DiagnosticEventType
import com.hostshield.util.ImportExportUtil
import com.hostshield.util.PcapExporter
import com.hostshield.util.RootUtil
import com.hostshield.util.UpdateChecker
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

// ══════════════════════════════════════════════════════════════
// Settings view model
// ══════════════════════════════════════════════════════════════

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
    val isRootAvailable: Boolean = false,
    val systemInfo: Map<String, String> = emptyMap(),
    val exportResult: String? = null,
    val importMessage: String? = null,
    val backupMessage: String? = null,
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
    private val updateChecker: UpdateChecker,
    private val diagnosticExporter: com.hostshield.util.DiagnosticExporter,
    private val diagnosticEvents: DiagnosticEventStore,
    private val firewallRuleDao: com.hostshield.data.database.FirewallRuleDao
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        observePrefs()
        loadSystemInfo()
        checkBattery()
        // Auto-check for updates when settings screen opens (silent, no error display)
        autoCheckForUpdate()
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
                prefs.customUpstreamDns
            ) { dnsTrap, blockResponse, customUpstream ->
                DnsResponsePrefs(dnsTrap, blockResponse, customUpstream)
            }
            combine(dnsBasePrefs, dnsResponsePrefs) { base, response ->
                DnsPrefs(
                    logging = base.logging,
                    retentionDays = base.retentionDays,
                    dohEnabled = base.dohEnabled,
                    dohProvider = base.dohProvider,
                    dnsTrap = response.dnsTrap,
                    blockResponse = response.blockResponse,
                    customUpstream = response.customUpstream
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
                        customUpstreamDns = p.customUpstream
                    )
                }
            }
        }

        // ── Notification / UI / Firewall count ────────────────
        viewModelScope.launch {
            combine(
                prefs.showNotification,
                prefs.accentColor,
                prefs.highContrastAmoled,
                prefs.blockedApps
            ) { notification, accent, highContrast, apps ->
                UiPrefs(notification, accent, highContrast, apps.size)
            }.collect { p ->
                _uiState.update {
                    it.copy(
                        showNotification = p.showNotification,
                        accentColor = p.accentColor,
                        highContrastAmoled = p.highContrastAmoled,
                        firewalledApps = p.firewalledApps
                    )
                }
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
    private data class DnsResponsePrefs(val dnsTrap: Boolean, val blockResponse: String, val customUpstream: String)
    private data class DnsPrefs(val logging: Boolean, val retentionDays: Int, val dohEnabled: Boolean, val dohProvider: String, val dnsTrap: Boolean, val blockResponse: String, val customUpstream: String)
    private data class UiPrefs(
        val showNotification: Boolean,
        val accentColor: String,
        val highContrastAmoled: Boolean,
        val firewalledApps: Int
    )
    private data class SchedulePrefs(val enabled: Boolean, val start: String, val end: String, val mode: String)
    private data class DotDoqPrefs(val dotEnabled: Boolean, val dotProvider: String, val doqEnabled: Boolean, val doqProvider: String)
    private data class WireGuardPrefs(val enabled: Boolean, val endpoint: String, val dnsIp: String)

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
        }
    }

    fun setDnsLogging(v: Boolean) { viewModelScope.launch { prefs.setDnsLogging(v) } }
    fun setShowNotification(v: Boolean) { viewModelScope.launch { prefs.setShowNotification(v) } }
    fun setDohEnabled(v: Boolean) { viewModelScope.launch { prefs.setDohEnabled(v) } }

    fun setDohProvider(provider: String) { viewModelScope.launch { prefs.setDohProvider(provider) } }

    fun setIpv4Redirect(ip: String) { viewModelScope.launch { prefs.setIpv4Redirect(ip) } }
    fun setIpv6Redirect(ip: String) { viewModelScope.launch { prefs.setIpv6Redirect(ip) } }
    fun setLogRetention(days: Int) { viewModelScope.launch { prefs.setLogRetentionDays(days) } }
    fun setBlockResponseType(type: String) { viewModelScope.launch { prefs.setBlockResponseType(type) } }
    fun setAccentColor(color: String) { viewModelScope.launch { prefs.setAccentColor(color) } }
    fun setHighContrastAmoled(enabled: Boolean) { viewModelScope.launch { prefs.setHighContrastAmoled(enabled) } }

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
        viewModelScope.launch {
            try {
                val rules = repository.getAllRules().first()
                val sources = repository.getAllSources().first()
                val json = importExport.exportJson(rules, sources)
                getApplication<android.app.Application>().contentResolver.openOutputStream(uri)?.use {
                    it.write(json.toByteArray())
                }
                _uiState.update { it.copy(importMessage = "Exported ${rules.size} rules") }
            } catch (e: Exception) {
                _uiState.update { it.copy(importMessage = "Export failed: ${e.message}") }
            }
        }
    }

    /** Write the pending exportResult (shareable hosts file) to a SAF URI. */
    fun writeShareableToUri(uri: Uri) {
        viewModelScope.launch {
            try {
                val content = _uiState.value.exportResult
                if (content != null) {
                    getApplication<android.app.Application>().contentResolver.openOutputStream(uri)?.use {
                        it.write(content.toByteArray())
                    }
                    _uiState.update { it.copy(exportResult = null, importMessage = "Shareable blocklist saved") }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(exportResult = null, importMessage = "Export failed: ${e.message}") }
            }
        }
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
                _uiState.update { it.copy(importMessage = "Import failed: ${e.message}") }
            }
        }
    }

    fun clearImportMessage() { _uiState.update { it.copy(importMessage = null) } }
    fun clearExportResult() { _uiState.update { it.copy(exportResult = null) } }
    fun clearBackupMessage() { _uiState.update { it.copy(backupMessage = null) } }

    /** Export user rules as a shareable hosts file that other blockers can subscribe to. */
    fun exportShareableBlocklist() {
        viewModelScope.launch {
            val blockRules = repository.getEnabledRulesByType(RuleType.BLOCK)
            val allowRules = repository.getEnabledRulesByType(RuleType.ALLOW)
            val content = importExport.exportShareableHostsFile(blockRules, allowRules)
            _uiState.update { it.copy(exportResult = content) }
        }
    }

    /** Export firewall rules as JSON to share/transfer. */
    fun exportFirewallRules() {
        viewModelScope.launch {
            try {
                val rules = firewallRuleDao.getAllRulesList()
                val json = importExport.exportFirewallJson(rules)
                _uiState.update { it.copy(exportResult = json) }
            } catch (e: Exception) {
                _uiState.update { it.copy(importMessage = "Firewall export failed: ${e.message}") }
            }
        }
    }

    /**
     * Create a backup and write it to the given URI.
     * If [passphrase] is non-null and non-empty, the backup is AES-256-GCM encrypted.
     * Otherwise, plaintext JSON is written (backward-compatible).
     */
    fun backupToUri(uri: Uri, passphrase: String? = null) {
        viewModelScope.launch {
            try {
                val json = backupRestore.createBackup()
                backupRestore.writeBackupToUri(getApplication(), uri, json, passphrase)
                val suffix = if (!passphrase.isNullOrEmpty()) " (encrypted)" else ""
                _uiState.update { it.copy(backupMessage = "Backup saved successfully$suffix") }
            } catch (e: Exception) {
                _uiState.update { it.copy(backupMessage = "Backup failed: ${e.message}") }
            }
        }
    }

    /**
     * Restore from a backup file at the given URI.
     * If the file is encrypted and [passphrase] is null/empty, an
     * [EncryptedBackupException] is raised via backupMessage prefixed with "ENCRYPTED:"
     * so the UI can prompt the user for a passphrase.
     * Plaintext backups are restored directly regardless of passphrase.
     */
    fun restoreFromUri(uri: Uri, passphrase: String? = null) {
        viewModelScope.launch {
            try {
                val json = backupRestore.readBackupFromUri(getApplication(), uri, passphrase)
                val result = backupRestore.restoreBackup(json)
                _uiState.update {
                    it.copy(backupMessage = "Restored ${result.sourcesCount} sources, ${result.rulesCount} rules, " +
                        "${result.profilesCount} profiles, ${result.firewallRulesCount} firewall rules")
                }
            } catch (e: com.hostshield.util.EncryptedBackupException) {
                _uiState.update { it.copy(backupMessage = "ENCRYPTED:${e.message}") }
            } catch (e: javax.crypto.AEADBadTagException) {
                diagnosticEvents.record(
                    DiagnosticEventType.BACKUP_IMPORT_FAILED,
                    "Encrypted backup restore failed",
                    mapOf("reason" to "auth_tag_or_passphrase")
                )
                _uiState.update { it.copy(backupMessage = "Restore failed: incorrect passphrase or corrupted backup") }
            } catch (e: Exception) {
                diagnosticEvents.record(
                    DiagnosticEventType.BACKUP_IMPORT_FAILED,
                    "Backup restore failed",
                    mapOf("error" to (e.message ?: e.javaClass.simpleName))
                )
                _uiState.update { it.copy(backupMessage = "Restore failed: ${e.message}") }
            }
        }
    }

    private val _pcapMessage = MutableStateFlow("")
    val pcapMessage: StateFlow<String> = _pcapMessage.asStateFlow()
    private val _isExportingPcap = MutableStateFlow(false)
    val isExportingPcap: StateFlow<Boolean> = _isExportingPcap.asStateFlow()

    fun exportPcap(mode: String = "all", days: Int = 7) {
        viewModelScope.launch(Dispatchers.IO) {
            _isExportingPcap.value = true
            _pcapMessage.value = "Exporting..."
            try {
                val file = when (mode) {
                    "dns" -> pcapExporter.exportDnsLogs(getApplication(), days)
                    "firewall" -> pcapExporter.exportConnectionLogs(getApplication(), days)
                    else -> pcapExporter.exportAll(getApplication(), days)
                }
                if (file != null) {
                    _pcapMessage.value = "PCAP saved: ${file.name} (${file.length() / 1024}KB)"
                } else {
                    _pcapMessage.value = "No blocked entries to export"
                }
            } catch (e: Exception) {
                _pcapMessage.value = "Export failed: ${e.message}"
            } finally {
                _isExportingPcap.value = false
            }
        }
    }

    // ── App Update Checker ──────────────────────────────────

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
                    _uiState.update {
                        it.copy(
                            isCheckingUpdate = false,
                            updateMessage = "Update check failed: ${err.message}"
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
    private val _pendingCsv = MutableStateFlow<String?>(null)
    val pendingCsv: StateFlow<String?> = _pendingCsv.asStateFlow()

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

                _pendingCsv.value = sb.toString()
                _csvMessage.value = "CSV ready (${stats.size} days, ${topBlocked.size} domains, ${topApps.size} apps)"
            } catch (e: Exception) {
                _csvMessage.value = "Export failed: ${e.message}"
            } finally {
                _isExportingCsv.value = false
            }
        }
    }

    fun writeCsvToUri(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val content = _pendingCsv.value
                if (content != null) {
                    getApplication<android.app.Application>().contentResolver.openOutputStream(uri)?.use {
                        it.write(content.toByteArray())
                    }
                    _pendingCsv.value = null
                    _csvMessage.value = "Stats CSV saved"
                }
            } catch (e: Exception) {
                _csvMessage.value = "Save failed: ${e.message}"
            }
        }
    }

    fun clearCsvMessage() { _csvMessage.value = null }

    fun generateDiagnosticReport() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                diagnosticExporter.generateAndShare(getApplication())
            } catch (e: Exception) {
                android.util.Log.e("Settings", "Diagnostic export failed: ${e.message}", e)
            }
        }
    }
}
