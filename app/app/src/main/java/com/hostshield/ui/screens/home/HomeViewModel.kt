package com.hostshield.ui.screens.home

import android.app.Application
import android.content.Intent
import android.net.VpnService
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.hostshield.data.database.DnsLogDao
import com.hostshield.data.database.ConnectionLogDao
import com.hostshield.data.model.BlockMethod
import com.hostshield.data.model.DnsLogEntry
import com.hostshield.data.model.RuleType
import com.hostshield.data.preferences.AppPreferences
import com.hostshield.data.repository.HostShieldRepository
import com.hostshield.data.source.SourceDownloader
import com.hostshield.domain.BlocklistHolder
import com.hostshield.domain.parser.HostsParser
import com.hostshield.service.DnsVpnService
import com.hostshield.service.HostShieldWidgetProvider
import com.hostshield.service.HostsUpdateWorker
import com.hostshield.service.IptablesManager
import com.hostshield.service.NflogReader
import com.hostshield.service.RootDnsService
import com.hostshield.util.PrivateDnsDetector
import com.hostshield.util.PrivateSpaceDetector
import com.hostshield.util.BatteryOptimizationUtil
import com.hostshield.util.RootUtil
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

// HostShield v1.6.0

data class HomeUiState(
    val isEnabled: Boolean = false,
    /** What the user has selected in the mode chips. */
    val blockMethod: BlockMethod = BlockMethod.ROOT_HOSTS,
    /** What is actually running right now. Null = nothing running. */
    val activeMethod: BlockMethod? = null,
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
    /** Private DNS warning (null = no warning). */
    val privateDnsWarning: String? = null,
    /** DNS trap enabled (catches hardcoded DNS servers). */
    val dnsTrapEnabled: Boolean = true,
    /** DoH enabled. */
    val dohEnabled: Boolean = false,
    /** Firewalled app count. */
    val firewalledApps: Int = 0,
    /** Battery optimization warning. */
    val batteryWarning: String? = null,
    /** Android Private Space / work profile VPN bypass warning. */
    val privateSpaceWarning: String? = null,
    /** Network firewall (iptables) active. */
    val networkFirewallActive: Boolean = false,
    /** Network firewall blocked rule count. */
    val networkFirewallRules: Int = 0,
    /** Firewall blocked connection count. */
    val firewallBlockedConnections: Int = 0,
    /** DNS logging enabled. */
    val dnsLoggingEnabled: Boolean = true
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    application: Application,
    private val repository: HostShieldRepository,
    private val rootUtil: RootUtil,
    private val prefs: AppPreferences,
    private val downloader: SourceDownloader,
    private val blocklistHolder: BlocklistHolder,
    private val privateDnsDetector: PrivateDnsDetector,
    private val batteryUtil: BatteryOptimizationUtil,
    private val iptablesManager: IptablesManager,
    private val nflogReader: NflogReader,
    private val dnsLogDao: DnsLogDao,
    private val connectionLogDao: ConnectionLogDao
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    /** Live DNS log feed for the dashboard — last 50 entries from database, auto-updates. */
    val liveLogs: StateFlow<List<DnsLogEntry>> = dnsLogDao.getRecentLogs(50)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /**
     * Real-time DNS query stream — directly from VPN packet processing.
     * Unlike liveLogs (database-backed, 2s batch delay), this emits instantly
     * as each query is processed. Used for the live tail view.
     */
    val liveQueryStream: StateFlow<List<DnsLogEntry>> = DnsVpnService.liveQueries
        .runningFold(emptyList<DnsLogEntry>()) { acc, entry ->
            (listOf(entry) + acc).take(200) // keep last 200, newest first
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        checkRoot()
        observePrefs()
        observeStats()
        observeNetworkFirewall()
        seedDefaults()
        scheduleAutoUpdate()
        checkPrivateDns()
        checkBattery()
        checkPrivateSpace()
        resumeBlockingIfNeeded()
    }

    private fun checkRoot() {
        viewModelScope.launch(Dispatchers.IO) {
            val available = rootUtil.isRootAvailable()
            _uiState.update { it.copy(isRootAvailable = available) }
        }
    }

    private fun checkPrivateDns() {
        viewModelScope.launch(Dispatchers.IO) {
            val status = privateDnsDetector.detect()
            val warning = when {
                status.mode == PrivateDnsDetector.PrivateDnsMode.STRICT ->
                    "Private DNS is set to \"${status.hostname}\". This bypasses HostShield's DNS filtering. " +
                    "Go to Settings > Network > Private DNS and set it to \"Off\" for full protection."
                status.mode == PrivateDnsDetector.PrivateDnsMode.AUTOMATIC ->
                    "Private DNS is set to \"Automatic\". Some queries may bypass HostShield. " +
                    "For full protection, set Private DNS to \"Off\" in system settings."
                else -> null
            }
            _uiState.update { it.copy(privateDnsWarning = warning) }
        }
    }

    private fun checkBattery() {
        viewModelScope.launch(Dispatchers.IO) {
            val status = batteryUtil.check()
            if (status.needsUserAction) {
                _uiState.update { it.copy(batteryWarning = status.message) }
            }
        }
    }

    fun dismissBatteryWarning() { _uiState.update { it.copy(batteryWarning = null) } }

    private fun checkPrivateSpace() {
        viewModelScope.launch(Dispatchers.IO) {
            if (PrivateSpaceDetector.hasPrivateSpace(getApplication())) {
                val isVpnMode = _uiState.value.blockMethod == BlockMethod.VPN
                val warning = PrivateSpaceDetector.getWarningMessage(isVpnMode)
                _uiState.update { it.copy(privateSpaceWarning = warning) }
            }
        }
    }

    fun dismissPrivateSpaceWarning() { _uiState.update { it.copy(privateSpaceWarning = null) } }

    /**
     * Request battery optimization exemption with automatic fallback.
     * Tries direct dialog -> battery settings list -> general settings.
     */
    fun requestBatteryExemption(activityContext: android.content.Context): Boolean {
        return batteryUtil.requestExemption(activityContext)
    }

    /** Returns intent to open Private DNS settings (Network & Internet on Android 9+). */
    fun getPrivateDnsSettingsIntent(): android.content.Intent {
        return try {
            android.content.Intent(android.provider.Settings.ACTION_WIRELESS_SETTINGS)
        } catch (_: Exception) {
            android.content.Intent(android.provider.Settings.ACTION_SETTINGS)
        }
    }

    /**
     * Re-check battery + Private DNS status. Call when the user returns
     * from system settings so banners auto-dismiss if the issue was fixed.
     */
    fun recheckWarnings() {
        viewModelScope.launch(Dispatchers.IO) {
            // Battery
            val batteryStatus = batteryUtil.check()
            _uiState.update {
                it.copy(batteryWarning = if (batteryStatus.needsUserAction) batteryStatus.message else null)
            }
            // Private DNS
            val dnsStatus = privateDnsDetector.detect()
            val dnsWarning = when {
                dnsStatus.mode == PrivateDnsDetector.PrivateDnsMode.STRICT ->
                    "Private DNS is set to \"${dnsStatus.hostname}\". This bypasses HostShield's DNS filtering."
                dnsStatus.mode == PrivateDnsDetector.PrivateDnsMode.AUTOMATIC ->
                    "Private DNS is set to \"Automatic\". Some queries may bypass HostShield."
                else -> null
            }
            _uiState.update { it.copy(privateDnsWarning = dnsWarning) }
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
        viewModelScope.launch {
            prefs.dnsTrapEnabled.collect { v ->
                _uiState.update { it.copy(dnsTrapEnabled = v) }
            }
        }
        viewModelScope.launch {
            prefs.dohEnabled.collect { v ->
                _uiState.update { it.copy(dohEnabled = v) }
            }
        }
        viewModelScope.launch {
            prefs.blockedApps.collect { apps ->
                _uiState.update { it.copy(firewalledApps = apps.size) }
            }
        }
        viewModelScope.launch {
            prefs.dnsLogging.collect { enabled ->
                _uiState.update { it.copy(dnsLoggingEnabled = enabled) }
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

    private fun observeNetworkFirewall() {
        viewModelScope.launch {
            iptablesManager.isActive.collect { active ->
                _uiState.update { it.copy(networkFirewallActive = active) }
            }
        }
        viewModelScope.launch {
            iptablesManager.lastApplyCount.collect { count ->
                _uiState.update { it.copy(networkFirewallRules = count) }
            }
        }
        viewModelScope.launch {
            connectionLogDao.getTotalBlockedCount().collect { count ->
                _uiState.update { it.copy(firewallBlockedConnections = count) }
            }
        }
        // Auto-apply iptables + start NFLOG reader on boot if enabled
        viewModelScope.launch(Dispatchers.IO) {
            val autoApply = prefs.autoApplyFirewall.first()
            val netFwEnabled = prefs.networkFirewallEnabled.first()
            // Check root directly — _uiState.value.isRootAvailable may not be set yet
            // because checkRoot() runs async and this coroutine may execute first.
            if (autoApply && netFwEnabled && rootUtil.isRootAvailable()) {
                iptablesManager.applyRules()
                nflogReader.start()
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

    // =================================================================
    // CORE LIFECYCLE: stopCurrent -> startNew
    // Every "apply" path MUST call stopCurrentBlocking() first.
    // =================================================================

    /**
     * Stop whatever blocking method is currently active.
     * Safe to call even if nothing is running (no-op).
     * Does NOT touch prefs.isEnabled.
     */
    private suspend fun stopCurrentBlocking() {
        val active = _uiState.value.activeMethod ?: return
        when (active) {
            BlockMethod.ROOT_HOSTS -> {
                repository.disableBlocking()
                RootDnsService.stop(getApplication())
                blocklistHolder.clear()
                // Also stop network firewall if running
                if (iptablesManager.isActive.value) {
                    iptablesManager.clearRules()
                    nflogReader.stop()
                }
            }
            BlockMethod.VPN -> {
                val intent = Intent(getApplication(), DnsVpnService::class.java).apply {
                    action = DnsVpnService.ACTION_STOP
                }
                getApplication<Application>().startService(intent)
                blocklistHolder.clear()
            }
            BlockMethod.DISABLED -> { }
        }
        _uiState.update { it.copy(activeMethod = null) }
    }

    // -- Public API ---------------------------------------------------

    /** Apply root blocking. Stops any running VPN/root first. */
    fun applyRootMode() {
        if (_uiState.value.isApplying) return
        viewModelScope.launch {
            _uiState.update { it.copy(isApplying = true, errorMessage = null) }
            stopCurrentBlocking()
            prefs.setBlockMethod(BlockMethod.ROOT_HOSTS)
            applyRootBlocking()
        }
    }

    /**
     * Called by the UI after the Activity resolves VPN permission.
     * Stops any running Root/VPN first, then starts VPN.
     */
    fun onVpnPermissionResult(granted: Boolean) {
        if (granted) {
            viewModelScope.launch {
                _uiState.update { it.copy(isApplying = true, errorMessage = null) }
                stopCurrentBlocking()
                prefs.setBlockMethod(BlockMethod.VPN)
                applyVpnBlocking()
            }
        } else {
            _uiState.update { it.copy(
                errorMessage = "VPN permission denied. HostShield needs VPN access to filter DNS queries.",
                isApplying = false
            ) }
        }
    }

    /**
     * Disable all blocking. Stops whatever is actually running
     * (uses activeMethod, NOT the chip selection).
     */
    fun disableBlocking() {
        viewModelScope.launch {
            _uiState.update { it.copy(isApplying = true) }
            val wasActive = _uiState.value.activeMethod
            stopCurrentBlocking()
            prefs.setEnabled(false)
            HostShieldWidgetProvider.updateWidget(getApplication(), false, 0)
            val label = when (wasActive) {
                BlockMethod.VPN -> "VPN stopped"
                BlockMethod.ROOT_HOSTS -> "Root blocking disabled"
                else -> "Blocking disabled"
            }
            showSnackbar(label)
            _uiState.update { it.copy(isEnabled = false, isApplying = false) }
        }
    }

    /** Only changes the UI chip + preference. Does NOT start/stop anything. */
    fun setBlockMethod(method: BlockMethod) {
        _uiState.update { it.copy(blockMethod = method) }
        viewModelScope.launch { prefs.setBlockMethod(method) }
    }

    // -- Root apply ---------------------------------------------------
    //
    // Architecture: The DNS proxy (RootDnsLogger) is the sole blocker.
    //
    //   1. Download sources → build in-memory blocklist
    //   2. Write MINIMAL hosts file (just localhost) so the OS doesn't
    //      resolve blocked domains from hosts before they reach the network
    //   3. Start RootDnsService → iptables redirects ALL DNS (UDP 53) to
    //      the local proxy on 127.0.0.1:5454
    //   4. Proxy checks blocklist → NXDOMAIN for blocked, forwards allowed
    //   5. Proxy logs EVERY query to Room → Live DNS feed works
    //
    // The old approach wrote a full blocking hosts file, but domains resolved
    // from /etc/hosts never generate DNS packets, so the proxy never saw them
    // and the live feed showed no blocked queries.

    private suspend fun applyRootBlocking() {
        try {
            _uiState.update { it.copy(progressMessage = "Building blocklist...") }

            val sources = repository.getEnabledSourcesList()
            val allDomains = mutableSetOf<String>()

            for ((index, source) in sources.withIndex()) {
                _uiState.update {
                    it.copy(progressMessage = "Downloading ${source.label} (${index + 1}/${sources.size})...")
                }
                downloader.download(source, forceDownload = true).onSuccess { dl ->
                    val parsed = HostsParser.parse(dl.content)
                    parsed.forEach { allDomains.add(it.hostname) }
                    // Update source meta for health tracking
                    try {
                        repository.updateSource(source.copy(
                            entryCount = parsed.size,
                            lastUpdated = System.currentTimeMillis(),
                            health = com.hostshield.data.model.SourceHealth.OK,
                            consecutiveFailures = 0
                        ))
                    } catch (_: Exception) { }
                }
            }

            // Merge user rules
            val blockRules = repository.getEnabledRulesByType(RuleType.BLOCK)
            blockRules.filter { !it.isWildcard }.forEach { allDomains.add(it.hostname.lowercase()) }
            val allowRules = repository.getEnabledRulesByType(RuleType.ALLOW)
            allowRules.filter { !it.isWildcard }.forEach { allDomains.remove(it.hostname.lowercase()) }
            val wildcards = repository.getEnabledWildcards()
            blocklistHolder.update(allDomains, wildcards)

            val count = allDomains.size
            if (count == 0 && sources.isNotEmpty()) {
                _uiState.update {
                    it.copy(isApplying = false,
                        errorMessage = "No domains downloaded. Check your internet connection.",
                        progressMessage = "")
                }
                return
            }

            // Write minimal hosts file — proxy handles all blocking.
            // This prevents the OS from resolving blocked domains from /etc/hosts
            // before they reach the network (which would bypass logging).
            _uiState.update { it.copy(progressMessage = "Configuring DNS proxy ($count domains)...") }
            rootUtil.writeHostsFile(
                "# HostShield root mode — blocking via DNS proxy\n" +
                "# Do not edit: this file is managed by HostShield\n" +
                "127.0.0.1 localhost\n::1 localhost\n"
            )

            // Start DNS proxy service
            RootDnsService.start(getApplication())

            prefs.setEnabled(true)
            prefs.setLastApplyTime(System.currentTimeMillis())
            prefs.setLastApplyCount(count)
            HostShieldWidgetProvider.updateWidget(getApplication(), true, count)
            showSnackbar("$count domains blocked via root DNS proxy")
            _uiState.update {
                it.copy(
                    isEnabled = true, isApplying = false,
                    activeMethod = BlockMethod.ROOT_HOSTS,
                    totalDomainsBlocked = count, progressMessage = ""
                )
            }
        } catch (e: Exception) {
            _uiState.update {
                it.copy(isApplying = false, errorMessage = e.message, progressMessage = "")
            }
        }
    }

    // -- VPN apply ----------------------------------------------------

    private suspend fun applyVpnBlocking() {
        _uiState.update { it.copy(progressMessage = "Building blocklist...") }

        try {
            val sources = repository.getEnabledSourcesList()
            val allDomains = mutableSetOf<String>()
            val totalSources = sources.size

            for ((index, source) in sources.withIndex()) {
                _uiState.update {
                    it.copy(progressMessage = "Downloading ${source.label} (${index + 1}/$totalSources)...")
                }
                val result = downloader.download(source, forceDownload = true)
                result.onSuccess { dl ->
                    val parsed = HostsParser.parse(dl.content)
                    parsed.forEach { allDomains.add(it.hostname) }
                }
            }

            val blockRules = repository.getEnabledRulesByType(RuleType.BLOCK)
            blockRules.filter { !it.isWildcard }.forEach { allDomains.add(it.hostname.lowercase()) }
            val allowRules = repository.getEnabledRulesByType(RuleType.ALLOW)
            allowRules.filter { !it.isWildcard }.forEach { allDomains.remove(it.hostname.lowercase()) }
            val wildcards = repository.getEnabledWildcards()
            blocklistHolder.update(allDomains, wildcards)

            _uiState.update {
                it.copy(progressMessage = "Starting VPN (${allDomains.size} domains)...")
            }

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
                it.copy(
                    isEnabled = true, isApplying = false,
                    activeMethod = BlockMethod.VPN,
                    totalDomainsBlocked = allDomains.size, progressMessage = ""
                )
            }
        } catch (e: Exception) {
            _uiState.update {
                it.copy(isApplying = false, errorMessage = e.message, progressMessage = "")
            }
        }
    }

    // -- Resume on process restart ------------------------------------
    //
    // FIX: Shows spinner so the orb says "Applying..." (not "Protection
    //      Active") while we rebuild the blocklist. This prevents user
    //      interaction during the download window and avoids the race
    //      where stopCurrentBlocking() sees activeMethod=null.
    //
    // FIX: If VPN permission was revoked, sets isEnabled=false and
    //      shows an error instead of silently doing nothing.

    private fun resumeBlockingIfNeeded() {
        viewModelScope.launch(Dispatchers.IO) {
            val isEnabled = prefs.isEnabled.first()
            val method = prefs.blockMethod.first()
            if (!isEnabled) return@launch

            // Show loading spinner so the user knows we're not active yet
            _uiState.update {
                it.copy(isApplying = true, progressMessage = "Resuming protection...")
            }

            when (method) {
                BlockMethod.ROOT_HOSTS -> {
                    // Full apply: re-downloads sources and writes /etc/hosts.
                    // Covers both post-onboarding (hosts not yet written) and
                    // process-death recovery (re-verifies hosts file is correct).
                    applyRootBlocking()
                }
                BlockMethod.VPN -> {
                    val needsPermission = VpnService.prepare(getApplication()) != null
                    if (needsPermission) {
                        // Permission was revoked -- don't pretend we're active
                        prefs.setEnabled(false)
                        _uiState.update {
                            it.copy(
                                isEnabled = false, isApplying = false,
                                progressMessage = "",
                                errorMessage = "VPN permission was revoked. Tap the shield to re-enable."
                            )
                        }
                        return@launch
                    }

                    _uiState.update { it.copy(progressMessage = "Rebuilding blocklist...") }
                    buildBlocklistHolder()

                    _uiState.update { it.copy(progressMessage = "Starting VPN...") }
                    val intent = Intent(getApplication(), DnsVpnService::class.java).apply {
                        action = DnsVpnService.ACTION_START
                    }
                    getApplication<Application>().startForegroundService(intent)

                    _uiState.update {
                        it.copy(
                            activeMethod = BlockMethod.VPN,
                            isApplying = false, progressMessage = ""
                        )
                    }
                }
                BlockMethod.DISABLED -> {
                    _uiState.update { it.copy(isApplying = false, progressMessage = "") }
                }
            }
        }
    }

    // -- Helpers -------------------------------------------------------

    fun dismissError() { _uiState.update { it.copy(errorMessage = null) } }
    fun dismissSnackbar() { _uiState.update { it.copy(snackbarMessage = null) } }
    fun dismissPrivateDnsWarning() { _uiState.update { it.copy(privateDnsWarning = null) } }

    private fun showSnackbar(msg: String) {
        _uiState.update { it.copy(snackbarMessage = msg) }
    }

    /** Build the in-memory blocklist from sources + user rules. */
    private suspend fun buildBlocklistHolder() {
        try {
            val sources = repository.getEnabledSourcesList()
            val allDomains = mutableSetOf<String>()

            for (source in sources) {
                val result = downloader.download(source, forceDownload = true)
                result.onSuccess { dl ->
                    val parsed = HostsParser.parse(dl.content)
                    parsed.forEach { allDomains.add(it.hostname) }
                }
            }

            val blockRules = repository.getEnabledRulesByType(RuleType.BLOCK)
            blockRules.filter { !it.isWildcard }.forEach { allDomains.add(it.hostname.lowercase()) }
            val allowRules = repository.getEnabledRulesByType(RuleType.ALLOW)
            allowRules.filter { !it.isWildcard }.forEach { allDomains.remove(it.hostname.lowercase()) }
            val wildcards = repository.getEnabledWildcards()
            blocklistHolder.update(allDomains, wildcards)

            if (allDomains.isEmpty() && sources.isNotEmpty()) {
                android.util.Log.w("HomeViewModel", "Blocklist build produced 0 domains from ${sources.size} sources")
                _uiState.update { it.copy(errorMessage = "Warning: blocklist is empty. Check your internet connection.") }
            }
        } catch (e: Exception) {
            android.util.Log.e("HomeViewModel", "Blocklist build failed: ${e.message}", e)
            _uiState.update { it.copy(errorMessage = "Failed to build blocklist: ${e.message}") }
        }
    }
}
