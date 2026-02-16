package com.hostshield.ui.screens.home

import android.app.Application
import android.content.Intent
import android.net.VpnService
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.hostshield.data.model.BlockMethod
import com.hostshield.data.model.RuleType
import com.hostshield.data.preferences.AppPreferences
import com.hostshield.data.repository.HostShieldRepository
import com.hostshield.data.source.SourceDownloader
import com.hostshield.domain.BlocklistHolder
import com.hostshield.domain.parser.HostsParser
import com.hostshield.service.DnsVpnService
import com.hostshield.service.HostShieldWidgetProvider
import com.hostshield.service.HostsUpdateWorker
import com.hostshield.service.RootDnsLogger
import com.hostshield.util.RootUtil
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

// HostShield v1.0.0

data class HomeUiState(
    val isEnabled: Boolean = false,
    val blockMethod: BlockMethod = BlockMethod.ROOT_HOSTS,
    val isApplying: Boolean = false,
    val progressMessage: String = "",
    val totalDomainsBlocked: Int = 0,
    val blockedToday: Int = 0,
    val totalQueriesToday: Int = 0,
    val enabledSources: Int = 0,
    val isRootAvailable: Boolean = false,
    val lastApplyTime: Long = 0L,
    val errorMessage: String? = null,
    val snackbarMessage: String? = null,
    val needsVpnPermission: Boolean = false,
    val vpnPermissionPending: Boolean = false
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    application: Application,
    private val repository: HostShieldRepository,
    private val rootUtil: RootUtil,
    private val prefs: AppPreferences,
    private val downloader: SourceDownloader,
    private val blocklistHolder: BlocklistHolder,
    private val rootDnsLogger: RootDnsLogger
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        checkRoot()
        observePrefs()
        observeStats()
        seedDefaults()
        scheduleAutoUpdate()
        resumeBlockingIfNeeded()
    }

    private fun checkRoot() {
        viewModelScope.launch(Dispatchers.IO) {
            val available = rootUtil.isRootAvailable()
            _uiState.update { it.copy(isRootAvailable = available) }
        }
    }

    private fun seedDefaults() {
        viewModelScope.launch { repository.seedDefaultSources() }
    }

    private fun observePrefs() {
        viewModelScope.launch {
            prefs.isEnabled.collect { enabled ->
                _uiState.update { it.copy(isEnabled = enabled) }
            }
        }
        viewModelScope.launch {
            prefs.blockMethod.collect { method ->
                _uiState.update { it.copy(blockMethod = method) }
            }
        }
        viewModelScope.launch {
            prefs.lastApplyTime.collect { time ->
                _uiState.update { it.copy(lastApplyTime = time) }
            }
        }
        viewModelScope.launch {
            prefs.lastApplyCount.collect { count ->
                _uiState.update { it.copy(totalDomainsBlocked = count) }
            }
        }
    }

    private fun observeStats() {
        val todayStart = java.time.LocalDate.now()
            .atStartOfDay(java.time.ZoneId.systemDefault())
            .toInstant().toEpochMilli()

        viewModelScope.launch {
            repository.getTotalEnabledEntries().collect { count ->
                val current = _uiState.value
                val displayCount = maxOf(count ?: 0, current.totalDomainsBlocked)
                _uiState.update { it.copy(totalDomainsBlocked = displayCount) }
            }
        }
        viewModelScope.launch {
            repository.getBlockedCountSince(todayStart).collect { count ->
                _uiState.update { it.copy(blockedToday = count) }
            }
        }
        viewModelScope.launch {
            repository.getTotalCountSince(todayStart).collect { count ->
                _uiState.update { it.copy(totalQueriesToday = count) }
            }
        }
        viewModelScope.launch {
            repository.getAllSources().collect { sources ->
                _uiState.update { it.copy(enabledSources = sources.count { s -> s.enabled }) }
            }
        }
    }

    private fun scheduleAutoUpdate() {
        viewModelScope.launch {
            val autoUpdate = prefs.autoUpdate.first()
            if (autoUpdate) {
                val interval = prefs.updateIntervalHours.first()
                val wifiOnly = prefs.wifiOnly.first()
                HostsUpdateWorker.schedule(getApplication(), interval, wifiOnly)
            }
        }
    }

    // ── Apply / Toggle ─────────────────────────────────────

    fun applyBlocking() {
        if (_uiState.value.isApplying) return

        val state = _uiState.value
        val method = if (state.blockMethod == BlockMethod.ROOT_HOSTS && state.isRootAvailable)
            BlockMethod.ROOT_HOSTS else BlockMethod.VPN

        viewModelScope.launch {
            _uiState.update { it.copy(isApplying = true, errorMessage = null) }

            when (method) {
                BlockMethod.ROOT_HOSTS -> applyRootBlocking()
                BlockMethod.VPN -> requestVpnAndApply()
                BlockMethod.DISABLED -> disableBlocking()
            }
        }
    }

    /** VPN mode: check permission first, then apply. */
    private fun requestVpnAndApply() {
        // Check if VPN permission is already granted
        val vpnIntent = VpnService.prepare(getApplication())
        if (vpnIntent != null) {
            // Need user consent — signal the UI layer to launch the system dialog
            _uiState.update { it.copy(isApplying = false, needsVpnPermission = true) }
        } else {
            // Already granted — proceed directly
            viewModelScope.launch { applyVpnBlocking() }
        }
    }

    /**
     * Called by the UI after the VPN consent dialog returns.
     * If granted, proceed with VPN setup. If denied, show error.
     */
    fun onVpnPermissionResult(granted: Boolean) {
        _uiState.update { it.copy(needsVpnPermission = false, vpnPermissionPending = false) }
        if (granted) {
            viewModelScope.launch {
                _uiState.update { it.copy(isApplying = true) }
                applyVpnBlocking()
            }
        } else {
            _uiState.update { it.copy(
                errorMessage = "VPN permission denied. HostShield needs VPN access to filter DNS queries.",
                isApplying = false
            ) }
        }
    }

    fun toggleBlocking() {
        if (_uiState.value.isEnabled) disableBlocking() else applyBlocking()
    }

    fun disableBlocking() {
        viewModelScope.launch {
            _uiState.update { it.copy(isApplying = true) }

            when (_uiState.value.blockMethod) {
                BlockMethod.ROOT_HOSTS -> {
                    repository.disableBlocking()
                    rootDnsLogger.stop()
                    showSnackbar("Root blocking disabled")
                }
                BlockMethod.VPN -> {
                    val intent = Intent(getApplication(), DnsVpnService::class.java).apply {
                        action = DnsVpnService.ACTION_STOP
                    }
                    getApplication<Application>().startService(intent)
                    blocklistHolder.clear()
                    showSnackbar("VPN stopped")
                }
                BlockMethod.DISABLED -> { }
            }

            prefs.setEnabled(false)
            HostShieldWidgetProvider.updateWidget(getApplication(), false, 0)
            _uiState.update { it.copy(isEnabled = false, isApplying = false) }
        }
    }

    // ── Root apply ──────────────────────────────────────────

    private suspend fun applyRootBlocking() {
        val ipv4 = prefs.ipv4Redirect.first()
        val ipv6 = prefs.ipv6Redirect.first()
        val includeV6 = prefs.includeIpv6.first()

        val result = repository.applyBlocking(
            redirectIp4 = ipv4, redirectIp6 = ipv6, includeIpv6 = includeV6
        ) { msg -> _uiState.update { it.copy(progressMessage = msg) } }

        result.onSuccess { count ->
            prefs.setEnabled(true)
            prefs.setLastApplyTime(System.currentTimeMillis())
            prefs.setLastApplyCount(count)
            HostShieldWidgetProvider.updateWidget(getApplication(), true, count)
            buildBlocklistHolder()
            rootDnsLogger.start()
            showSnackbar("$count domains blocked via hosts file")

            _uiState.update {
                it.copy(isEnabled = true, isApplying = false, totalDomainsBlocked = count, progressMessage = "")
            }
        }.onFailure { err ->
            _uiState.update { it.copy(isApplying = false, errorMessage = err.message, progressMessage = "") }
        }
    }

    // ── VPN apply ───────────────────────────────────────────

    private suspend fun applyVpnBlocking() {
        _uiState.update { it.copy(progressMessage = "Building blocklist...") }

        try {
            val sources = repository.getEnabledSourcesList()
            val allDomains = mutableSetOf<String>()
            val totalSources = sources.size

            for ((index, source) in sources.withIndex()) {
                _uiState.update { it.copy(progressMessage = "Downloading ${source.label} (${index + 1}/$totalSources)...") }
                val result = downloader.download(source)
                result.onSuccess { dl ->
                    val parsed = HostsParser.parse(if (dl.notModified) "" else dl.content)
                    parsed.forEach { allDomains.add(it.hostname) }
                }
            }

            val blockRules = repository.getEnabledRulesByType(RuleType.BLOCK)
            blockRules.filter { !it.isWildcard }.forEach { allDomains.add(it.hostname.lowercase()) }
            val allowRules = repository.getEnabledRulesByType(RuleType.ALLOW)
            allowRules.filter { !it.isWildcard }.forEach { allDomains.remove(it.hostname.lowercase()) }
            val wildcards = repository.getEnabledWildcards()
            blocklistHolder.update(allDomains, wildcards)

            _uiState.update { it.copy(progressMessage = "Starting VPN (${allDomains.size} domains)...") }

            val intent = Intent(getApplication(), DnsVpnService::class.java).apply {
                action = DnsVpnService.ACTION_START
            }
            getApplication<Application>().startForegroundService(intent)

            prefs.setEnabled(true)
            prefs.setBlockMethod(BlockMethod.VPN)
            prefs.setLastApplyTime(System.currentTimeMillis())
            prefs.setLastApplyCount(allDomains.size)
            HostShieldWidgetProvider.updateWidget(getApplication(), true, allDomains.size)
            showSnackbar("VPN active \u2014 ${allDomains.size} domains blocked")

            _uiState.update {
                it.copy(isEnabled = true, isApplying = false, totalDomainsBlocked = allDomains.size, progressMessage = "")
            }
        } catch (e: Exception) {
            _uiState.update { it.copy(isApplying = false, errorMessage = e.message, progressMessage = "") }
        }
    }

    // ── Resume on process restart ───────────────────────────

    /**
     * If HostShield was enabled before the app process was killed/restarted,
     * resume the appropriate blocking mode automatically.
     */
    private fun resumeBlockingIfNeeded() {
        viewModelScope.launch(Dispatchers.IO) {
            val isEnabled = prefs.isEnabled.first()
            val method = prefs.blockMethod.first()
            if (!isEnabled) return@launch

            when (method) {
                BlockMethod.ROOT_HOSTS -> {
                    // Root DNS logger: rebuild blocklist + resume tcpdump
                    if (!rootDnsLogger.isRunning.value) {
                        buildBlocklistHolder()
                        rootDnsLogger.start()
                    }
                }
                BlockMethod.VPN -> {
                    // Rebuild in-memory blocklist so VPN service can reference it,
                    // then re-start the VPN service if it's not running
                    buildBlocklistHolder()
                    // VpnService.prepare returns null if permission still granted
                    val needsPermission = VpnService.prepare(getApplication()) != null
                    if (!needsPermission) {
                        val intent = Intent(getApplication(), DnsVpnService::class.java).apply {
                            action = DnsVpnService.ACTION_START
                        }
                        getApplication<Application>().startForegroundService(intent)
                    }
                }
                BlockMethod.DISABLED -> { }
            }
        }
    }

    // ── Helpers ──────────────────────────────────────────────

    fun setBlockMethod(method: BlockMethod) {
        viewModelScope.launch {
            prefs.setBlockMethod(method)
            _uiState.update { it.copy(blockMethod = method) }
        }
    }

    fun dismissError() { _uiState.update { it.copy(errorMessage = null) } }
    fun dismissSnackbar() { _uiState.update { it.copy(snackbarMessage = null) } }

    private fun showSnackbar(msg: String) {
        _uiState.update { it.copy(snackbarMessage = msg) }
    }

    /** Build the in-memory blocklist from sources + user rules. */
    private suspend fun buildBlocklistHolder() {
        try {
            val sources = repository.getEnabledSourcesList()
            val allDomains = mutableSetOf<String>()

            for (source in sources) {
                val result = downloader.download(source)
                result.onSuccess { dl ->
                    if (!dl.notModified) {
                        val parsed = HostsParser.parse(dl.content)
                        parsed.forEach { allDomains.add(it.hostname) }
                    }
                }
            }

            val blockRules = repository.getEnabledRulesByType(RuleType.BLOCK)
            blockRules.filter { !it.isWildcard }.forEach { allDomains.add(it.hostname.lowercase()) }
            val allowRules = repository.getEnabledRulesByType(RuleType.ALLOW)
            allowRules.filter { !it.isWildcard }.forEach { allDomains.remove(it.hostname.lowercase()) }
            val wildcards = repository.getEnabledWildcards()
            blocklistHolder.update(allDomains, wildcards)
        } catch (_: Exception) { }
    }
}
