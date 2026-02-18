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
// HostShield v1.6.0 — Settings ViewModel
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
    val updateMessage: String? = null
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
    private val diagnosticExporter: com.hostshield.util.DiagnosticExporter
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        observePrefs()
        loadSystemInfo()
        checkBattery()
    }

    private fun observePrefs() {
        viewModelScope.launch { prefs.ipv4Redirect.collect { v -> _uiState.update { it.copy(ipv4Redirect = v) } } }
        viewModelScope.launch { prefs.ipv6Redirect.collect { v -> _uiState.update { it.copy(ipv6Redirect = v) } } }
        viewModelScope.launch { prefs.includeIpv6.collect { v -> _uiState.update { it.copy(includeIpv6 = v) } } }
        viewModelScope.launch { prefs.localWebserver.collect { v -> _uiState.update { it.copy(localWebserver = v) } } }
        viewModelScope.launch { prefs.autoUpdate.collect { v -> _uiState.update { it.copy(autoUpdate = v) } } }
        viewModelScope.launch { prefs.updateIntervalHours.collect { v -> _uiState.update { it.copy(updateIntervalHours = v) } } }
        viewModelScope.launch { prefs.wifiOnly.collect { v -> _uiState.update { it.copy(wifiOnly = v) } } }
        viewModelScope.launch { prefs.dnsLogging.collect { v -> _uiState.update { it.copy(dnsLogging = v) } } }
        viewModelScope.launch { prefs.logRetentionDays.collect { v -> _uiState.update { it.copy(logRetentionDays = v) } } }
        viewModelScope.launch { prefs.showNotification.collect { v -> _uiState.update { it.copy(showNotification = v) } } }
        viewModelScope.launch { prefs.dohEnabled.collect { v -> _uiState.update { it.copy(dohEnabled = v) } } }
        viewModelScope.launch { prefs.dohProvider.collect { v -> _uiState.update { it.copy(dohProvider = v) } } }
        viewModelScope.launch { prefs.dnsTrapEnabled.collect { v -> _uiState.update { it.copy(dnsTrapEnabled = v) } } }
        viewModelScope.launch { prefs.blockResponseType.collect { v -> _uiState.update { it.copy(blockResponseType = v) } } }
        viewModelScope.launch { prefs.blockedApps.collect { apps -> _uiState.update { it.copy(firewalledApps = apps.size) } } }
        viewModelScope.launch(Dispatchers.IO) {
            val available = rootUtil.isRootAvailable()
            _uiState.update { it.copy(isRootAvailable = available) }
        }
    }

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

    fun backupToUri(uri: Uri) {
        viewModelScope.launch {
            try {
                val json = backupRestore.createBackup()
                backupRestore.writeBackupToUri(getApplication(), uri, json)
                _uiState.update { it.copy(backupMessage = "Backup saved successfully") }
            } catch (e: Exception) {
                _uiState.update { it.copy(backupMessage = "Backup failed: ${e.message}") }
            }
        }
    }

    fun restoreFromUri(uri: Uri) {
        viewModelScope.launch {
            try {
                val json = backupRestore.readBackupFromUri(getApplication(), uri)
                val result = backupRestore.restoreBackup(json)
                _uiState.update {
                    it.copy(backupMessage = "Restored ${result.sourcesCount} sources, ${result.rulesCount} rules, " +
                        "${result.profilesCount} profiles, ${result.firewallRulesCount} firewall rules")
                }
            } catch (e: Exception) {
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
