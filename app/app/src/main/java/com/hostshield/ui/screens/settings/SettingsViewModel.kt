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
import com.hostshield.util.ImportExportUtil
import com.hostshield.util.RootUtil
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

// ══════════════════════════════════════════════════════════════
// HostShield v0.2.0 — Settings ViewModel
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
    val showNotification: Boolean = true,
    val dohEnabled: Boolean = false,
    val dohProvider: String = "cloudflare",
    val isRootAvailable: Boolean = false,
    val systemInfo: Map<String, String> = emptyMap(),
    val exportResult: String? = null,
    val importMessage: String? = null,
    val backupMessage: String? = null
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    application: Application,
    private val prefs: AppPreferences,
    private val repository: HostShieldRepository,
    private val rootUtil: RootUtil,
    private val importExport: ImportExportUtil,
    private val backupRestore: BackupRestoreUtil
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        observePrefs()
        loadSystemInfo()
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

    fun exportRules() {
        viewModelScope.launch {
            val rules = repository.getAllRules().first()
            val sources = repository.getAllSources().first()
            val json = importExport.exportJson(rules, sources)
            _uiState.update { it.copy(exportResult = json) }
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
                    it.copy(backupMessage = "Restored ${result.sourcesCount} sources, ${result.rulesCount} rules, ${result.profilesCount} profiles")
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(backupMessage = "Restore failed: ${e.message}") }
            }
        }
    }
}
