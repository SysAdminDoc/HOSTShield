package com.hostshield.ui.screens.logs

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hostshield.data.model.BlockMethod
import com.hostshield.data.model.AppDnsRule
import com.hostshield.data.model.BlockReasonFacet
import com.hostshield.data.model.DnsLogEntry
import com.hostshield.data.model.RuleType
import com.hostshield.data.model.UserRule
import com.hostshield.data.model.blockReasonFacet
import com.hostshield.data.preferences.AppPreferences
import com.hostshield.data.preferences.SavedDenseListFilter
import com.hostshield.data.repository.HostShieldRepository
import com.hostshield.data.database.AppDnsRuleDao
import com.hostshield.domain.BlocklistHolder
import com.hostshield.domain.OneShotAllowStore
import com.hostshield.util.GeoIpLookup
import com.hostshield.util.PrivacyLog
import com.hostshield.util.RootUtil
import com.hostshield.service.AppDnsRuleEngine
import com.hostshield.service.BlockNotificationActions
import com.hostshield.service.ParentalControlManager
import com.hostshield.service.TemporaryAllowWorker
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import org.json.JSONObject
import javax.inject.Inject

// DNS log screen

data class DedupedLogEntry(
    val hostname: String,
    val blocked: Boolean,
    val hitCount: Int,
    val latestTimestamp: Long,
    val appLabel: String,
    val appPackage: String,
    val queryType: String = "A",
    val responseTimeMs: Int = 0,
    val upstreamServer: String = "",
    val cnameChain: String = "",
    val resolvedIps: String = "",
    val decisionReason: String = "",
    val decisionSource: String = "",
    val matchedValue: String = "",
    val decisionPrecedence: String = "",
    val reasonFacets: Set<BlockReasonFacet> = emptySet(),
)

data class NotificationPinRequest(
    val action: String,
    val hostname: String,
    val error: String? = null,
    val lockoutMs: Long = 0L,
)

private data class LogFilters(
    val query: String,
    val blocked: Boolean?,
    val queryType: String?,
    val threatIntelOnly: Boolean,
    val reasonFacet: BlockReasonFacet?
)

private val THREAT_INTEL_DECISION_REASONS = setOf("threat_intel_domain", "threat_intel_ip")
private const val LOGS_SAVED_FILTER_SCREEN = "logs"

private fun LogFilters.isActive(): Boolean =
    query.isNotBlank() || blocked != null || queryType != null || threatIntelOnly || reasonFacet != null

private fun LogFilters.describe(): String = buildList {
    if (query.isNotBlank()) add("\"${query.trim().take(18)}\"")
    blocked?.let { add(if (it) "Blocked" else "Allowed") }
    queryType?.let { add(it.uppercase()) }
    if (threatIntelOnly) add("Threat review")
    reasonFacet?.let { add(it.label) }
}.joinToString(" + ").ifBlank { "DNS filter" }

private fun LogFilters.toPayload(): String = JSONObject()
    .put("query", query)
    .put("blocked", blocked)
    .put("queryType", queryType)
    .put("threatIntelOnly", threatIntelOnly)
    .put("reasonFacet", reasonFacet?.key ?: JSONObject.NULL)
    .toString()

private fun logFiltersFromPayload(payload: String): LogFilters? = runCatching {
    val json = JSONObject(payload)
    LogFilters(
        query = json.optString("query"),
        blocked = if (json.has("blocked") && !json.isNull("blocked")) json.getBoolean("blocked") else null,
        queryType = json.optString("queryType").takeIf { it.isNotBlank() },
        threatIntelOnly = json.optBoolean("threatIntelOnly", false),
        reasonFacet = if (json.isNull("reasonFacet")) null else BlockReasonFacet.fromKey(json.optString("reasonFacet"))
    )
}.getOrNull()

@HiltViewModel
class LogsViewModel @Inject constructor(
    @param:ApplicationContext private val appContext: Context,
    private val repository: HostShieldRepository,
    private val blocklist: BlocklistHolder,
    private val rootUtil: RootUtil,
    private val prefs: AppPreferences,
    private val geoIpLookup: GeoIpLookup,
    private val appDnsRuleDao: AppDnsRuleDao,
    private val appDnsRuleEngine: AppDnsRuleEngine,
    private val parentalControlManager: ParentalControlManager,
    savedStateHandle: androidx.lifecycle.SavedStateHandle
) : ViewModel() {
    val logs: StateFlow<List<DnsLogEntry>> = repository.getRecentLogs(2000)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Seed search from the Home search suggestion nav arg ("logs?query=…").
    private val _searchQuery = MutableStateFlow(savedStateHandle.get<String>("query").orEmpty())
    val searchQuery = _searchQuery.asStateFlow()
    private val notificationAction = savedStateHandle.get<String>("notificationAction").orEmpty()
    private val notificationSource = savedStateHandle.get<String>("notificationSource").orEmpty()
    private val notificationReason = savedStateHandle.get<String>("notificationReason").orEmpty()
    val notificationTarget: String = savedStateHandle.get<String>("notificationHost")
        ?.trim()
        ?.lowercase()
        ?.removeSuffix(".")
        .orEmpty()
    private val _notificationPinRequest = MutableStateFlow<NotificationPinRequest?>(null)
    val notificationPinRequest: StateFlow<NotificationPinRequest?> = _notificationPinRequest.asStateFlow()
    private val _searchHistory = MutableStateFlow<List<String>>(emptyList())
    val searchHistory = _searchHistory.asStateFlow()
    val savedFilters: StateFlow<List<SavedDenseListFilter>> = prefs.ui
        .savedDenseListFilters(LOGS_SAVED_FILTER_SCREEN)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    private val _showBlocked = MutableStateFlow<Boolean?>(null)
    val showBlocked = _showBlocked.asStateFlow()

    private val _queryTypeFilter = MutableStateFlow<String?>(null)
    val queryTypeFilter = _queryTypeFilter.asStateFlow()

    private val _threatIntelOnly = MutableStateFlow(false)
    val threatIntelOnly = _threatIntelOnly.asStateFlow()

    private val _reasonFacet = MutableStateFlow<BlockReasonFacet?>(null)
    val reasonFacet = _reasonFacet.asStateFlow()

    // Authoritative set of blocked hostnames — loaded from DB + blocklist on init,
    // updated instantly on block/allow actions. Persists across sessions via DB.
    private val _blockedHostnames = MutableStateFlow<Set<String>>(emptySet())
    val blockedHostnames = _blockedHostnames.asStateFlow()

    // Hostnames the user has allowed (allow rule or active temporary allow).
    // Overrides historical "blocked" log rows so an Allow action gives instant,
    // persistent feedback even for source-list-blocked domains whose past log
    // entries are immutably marked blocked.
    private val _allowedHostnames = MutableStateFlow<Set<String>>(emptySet())

    private val _isLoading = MutableStateFlow(true)
    val isLoading = _isLoading.asStateFlow()

    private val filters: Flow<LogFilters> =
        combine(_searchQuery, _showBlocked, _queryTypeFilter, _threatIntelOnly, _reasonFacet) {
                query, blocked, queryType, threatIntelOnly, reasonFacet ->
            LogFilters(query, blocked, queryType, threatIntelOnly, reasonFacet)
        }

    val reasonFacetCounts: StateFlow<Map<BlockReasonFacet, Int>> = logs
        .map { logList ->
            logList.asSequence()
                .filter { it.blocked && it.decisionReason.isNotBlank() }
                .map { blockReasonFacet(it.decisionReason, it.decisionSource) }
                .groupingBy { it }
                .eachCount()
        }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    val deduped: StateFlow<List<DedupedLogEntry>> =
        combine(logs, _blockedHostnames, _allowedHostnames, filters) { logList, blockedSet, allowedSet, filterState ->
            logList
                .groupBy { it.hostname.lowercase() }
                .map { (hostname, entries) ->
                    val isBlocked = hostname !in allowedSet &&
                        (hostname in blockedSet || entries.any { it.blocked })
                    val notificationLatest = if (
                        notificationAction.isNotBlank() && hostname == notificationTarget
                    ) {
                        entries.asSequence()
                            .filter {
                                (notificationSource.isBlank() || it.decisionSource == notificationSource) &&
                                    (notificationReason.isBlank() || it.decisionReason == notificationReason)
                            }
                            .maxByOrNull { it.timestamp }
                    } else {
                        null
                    }
                    val latest = notificationLatest ?: entries.maxByOrNull { it.timestamp }
                    DedupedLogEntry(
                        hostname = hostname,
                        blocked = isBlocked,
                        hitCount = entries.size,
                        latestTimestamp = entries.maxOf { it.timestamp },
                        appLabel = entries.firstOrNull { it.appLabel.isNotEmpty() }?.appLabel ?: "",
                        appPackage = entries.firstOrNull { it.appPackage.isNotEmpty() }?.appPackage ?: "",
                        queryType = latest?.queryType ?: "A",
                        responseTimeMs = latest?.responseTimeMs ?: 0,
                        upstreamServer = latest?.upstreamServer ?: "",
                        cnameChain = latest?.cnameChain ?: "",
                        resolvedIps = latest?.resolvedIps ?: "",
                        decisionReason = latest?.decisionReason ?: "",
                        decisionSource = latest?.decisionSource ?: "",
                        matchedValue = latest?.matchedValue ?: "",
                        decisionPrecedence = latest?.decisionPrecedence ?: "",
                        reasonFacets = entries.asSequence()
                            .filter { it.blocked && it.decisionReason.isNotBlank() }
                            .map { blockReasonFacet(it.decisionReason, it.decisionSource) }
                            .toSet(),
                    )
                }
                .filter { entry ->
                    (filterState.query.isBlank() || entry.hostname.contains(filterState.query, ignoreCase = true) || entry.appPackage.contains(filterState.query, ignoreCase = true)) &&
                    (filterState.blocked == null || entry.blocked == filterState.blocked) &&
                    (filterState.queryType == null || entry.queryType.equals(filterState.queryType, ignoreCase = true)) &&
                    (!filterState.threatIntelOnly || entry.isThreatIntelBlock()) &&
                    (filterState.reasonFacet == null ||
                        (entry.blocked && filterState.reasonFacet in entry.reasonFacets))
                }
                .sortedByDescending { it.latestTimestamp }
        }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val totalDomains: StateFlow<Int> = logs
        .map { logList -> logList.map { it.hostname.lowercase() }.distinct().size }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val blockedCount: StateFlow<Int> = deduped
        .map { list -> list.count { it.blocked } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val threatReviewCount: StateFlow<Int> = logs
        .map { logList ->
            logList.asSequence()
                .filter { it.isThreatIntelBlock() }
                .map { it.hostname.lowercase() }
                .distinct()
                .count()
        }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()
    fun clearError() { _error.value = null }

    init {
        loadBlockedState()
        prepareNotificationAction(savedStateHandle)
    }

    private fun prepareNotificationAction(savedStateHandle: androidx.lifecycle.SavedStateHandle) {
        if (!BlockNotificationActions.isAllow(notificationAction) || notificationTarget.isBlank()) return
        if (savedStateHandle.get<Boolean>("notificationActionHandled") == true) return
        // A route may be recreated after rotation. Mark it before doing any
        // work so an Always/Once action cannot be replayed by a new ViewModel.
        savedStateHandle["notificationActionHandled"] = true
        viewModelScope.launch(Dispatchers.IO) {
            if (prefs.parentalEnabled.first()) {
                val pinSet = parentalControlManager.isPinSet()
                _notificationPinRequest.value = NotificationPinRequest(
                    action = notificationAction,
                    hostname = notificationTarget,
                    error = if (pinSet) null else appContext.getString(
                        com.hostshield.R.string.notification_pin_no_pin,
                    ),
                )
            } else {
                executeNotificationAction(notificationAction, notificationTarget)
            }
        }
    }

    fun submitNotificationPin(pin: String) {
        val request = _notificationPinRequest.value ?: return
        if (request.lockoutMs > 0L || pin.length != 4) return
        viewModelScope.launch(Dispatchers.IO) {
            when (val result = parentalControlManager.verifyPinDetailed(pin)) {
                ParentalControlManager.PinResult.Success -> {
                    _notificationPinRequest.value = null
                    executeNotificationAction(request.action, request.hostname)
                }
                ParentalControlManager.PinResult.Wrong -> {
                    _notificationPinRequest.value = request.copy(
                        error = appContext.getString(com.hostshield.R.string.notification_pin_wrong),
                        lockoutMs = 0L,
                    )
                }
                is ParentalControlManager.PinResult.LockedOut -> {
                    val seconds = (result.retryAfterMs / 1000L).coerceAtLeast(1L).toInt()
                    _notificationPinRequest.value = request.copy(
                        error = appContext.resources.getQuantityString(
                            com.hostshield.R.plurals.notification_pin_locked,
                            seconds,
                            seconds,
                        ),
                        lockoutMs = result.retryAfterMs,
                    )
                }
                ParentalControlManager.PinResult.NoPin -> {
                    _notificationPinRequest.value = request.copy(
                        error = appContext.getString(com.hostshield.R.string.notification_pin_no_pin),
                    )
                }
            }
        }
    }

    fun dismissNotificationPin() {
        _notificationPinRequest.value = null
    }

    private fun executeNotificationAction(action: String, hostname: String) {
        when (action) {
            BlockNotificationActions.ALLOW_ONCE -> OneShotAllowStore.grant(hostname)
            BlockNotificationActions.ALLOW_10_MINUTES -> temporaryAllow(hostname, 10)
            BlockNotificationActions.ALLOW_ALWAYS -> allowDomain(hostname)
        }
    }

    /**
     * Load all blocked hostnames from:
     * 1. User BLOCK rules in the database (persists across sessions)
     * 2. The active in-memory blocklist (source lists + user rules)
     * Subtract any ALLOW rules. This ensures every launch shows correct state.
     */
    private fun loadBlockedState() {
        viewModelScope.launch(Dispatchers.IO) {
            _isLoading.value = true
            try {
                val blockRules = repository.getEnabledRulesByType(RuleType.BLOCK)
                val allowRules = repository.getEnabledRulesByType(RuleType.ALLOW)
                val allowed = allowRules.map { it.hostname.lowercase() }.toSet()

                val all = mutableSetOf<String>()
                // From user block rules (these are always known)
                all.addAll(blockRules.map { it.hostname.lowercase() })
                // Remove explicit allows
                all.removeAll(allowed)

                _blockedHostnames.value = all
                _allowedHostnames.value = allowed
            } catch (e: Exception) {
                Log.e("LogsViewModel", "Failed to load blocked state", e)
                _error.value = "Could not load DNS log state. Try again."
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun setSearch(q: String) {
        _searchQuery.value = q
        if (q.length >= 3 && q !in _searchHistory.value) {
            _searchHistory.update { (listOf(q) + it).distinct().take(10) }
        }
    }
    fun clearSearchHistory() { _searchHistory.value = emptyList() }
    fun setFilter(blocked: Boolean?) { _showBlocked.value = blocked }
    fun setQueryTypeFilter(type: String?) { _queryTypeFilter.value = type }
    fun setThreatIntelOnly(enabled: Boolean) { _threatIntelOnly.value = enabled }
    fun setReasonFacet(facet: BlockReasonFacet?) { _reasonFacet.value = facet }
    fun clearFilters() {
        _searchQuery.value = ""
        _showBlocked.value = null
        _queryTypeFilter.value = null
        _threatIntelOnly.value = false
        _reasonFacet.value = null
    }

    fun saveCurrentFilter() {
        val current = LogFilters(
            query = _searchQuery.value,
            blocked = _showBlocked.value,
            queryType = _queryTypeFilter.value,
            threatIntelOnly = _threatIntelOnly.value,
            reasonFacet = _reasonFacet.value,
        )
        if (!current.isActive()) return
        viewModelScope.launch {
            prefs.ui.saveDenseListFilter(
                LOGS_SAVED_FILTER_SCREEN,
                current.describe(),
                current.toPayload()
            )
        }
    }

    fun applySavedFilter(filter: SavedDenseListFilter) {
        logFiltersFromPayload(filter.payload)?.let { saved ->
            _searchQuery.value = saved.query
            _showBlocked.value = saved.blocked
            _queryTypeFilter.value = saved.queryType
            _threatIntelOnly.value = saved.threatIntelOnly
            _reasonFacet.value = saved.reasonFacet
        }
    }

    fun clearSavedFilters() {
        viewModelScope.launch {
            prefs.ui.clearDenseListFilters(LOGS_SAVED_FILTER_SCREEN)
        }
    }

    fun blockDomain(hostname: String) {
        val host = hostname.lowercase()
        val wasAllowed = host in _allowedHostnames.value
        _blockedHostnames.update { it + host }
        _allowedHostnames.update { it - host }

        viewModelScope.launch(Dispatchers.IO) {
            try {
                repository.addRule(UserRule(hostname = host, type = RuleType.BLOCK))
                blocklist.addDomain(host)
                val method = prefs.blockMethod.first()
                if (method == BlockMethod.ROOT_HOSTS) {
                    rootUtil.appendHostEntry(host)
                }
            } catch (e: Exception) {
                // Roll the optimistic UI state back (mirrors temporaryAllow) —
                // otherwise the row keeps rendering BLOCKED with no rule behind it.
                _blockedHostnames.update { it - host }
                if (wasAllowed) _allowedHostnames.update { it + host }
                PrivacyLog.e("LogsViewModel", "Failed to block domain: $host", e)
                _error.value = "Could not block $host. Check permissions and try again."
            }
        }
    }

    fun allowDomain(hostname: String) {
        val host = hostname.lowercase()
        val wasBlocked = host in _blockedHostnames.value
        _blockedHostnames.update { it - host }
        _allowedHostnames.update { it + host }

        viewModelScope.launch(Dispatchers.IO) {
            try {
                repository.addRule(UserRule(hostname = host, type = RuleType.ALLOW))
                blocklist.allowDomain(host)
                val method = prefs.blockMethod.first()
                if (method == BlockMethod.ROOT_HOSTS) {
                    rootUtil.removeHostEntry(host)
                }
            } catch (e: Exception) {
                _allowedHostnames.update { it - host }
                if (wasBlocked) _blockedHostnames.update { it + host }
                PrivacyLog.e("LogsViewModel", "Failed to allow domain: $host", e)
                _error.value = "Could not allow $host. Check permissions and try again."
            }
        }
    }

    fun allowThreatIntelForApp(hostname: String, packageName: String) {
        val host = hostname.lowercase()
        val pkg = packageName.trim()
        if (pkg.isBlank()) {
            allowDomain(host)
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            try {
                appDnsRuleDao.insert(
                    AppDnsRule(
                        packageName = pkg,
                        domain = host,
                        action = "allow"
                    )
                )
                appDnsRuleEngine.reloadForApp(pkg)
            } catch (e: Exception) {
                PrivacyLog.e("LogsViewModel", "Failed to allow threat-intel domain for app: $host / $pkg", e)
                _error.value = "Could not add an app-scoped allow rule. Try again."
            }
        }
    }

    fun clearLogs() {
        viewModelScope.launch {
            try {
                repository.clearAllLogs()
            } catch (e: Exception) {
                Log.e("LogsViewModel", "Failed to clear logs", e)
                _error.value = "Could not clear DNS logs. Try again."
            }
        }
    }

    fun blockDomains(hostnames: Set<String>) {
        val hosts = hostnames.map { it.lowercase() }
        val priorAllowed = _allowedHostnames.value.intersect(hosts.toSet())
        _blockedHostnames.update { it + hosts }
        _allowedHostnames.update { it - hosts.toSet() }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                hosts.forEach { host ->
                    repository.addRule(UserRule(hostname = host, type = RuleType.BLOCK))
                    blocklist.addDomain(host)
                }
                val method = prefs.blockMethod.first()
                if (method == BlockMethod.ROOT_HOSTS) {
                    hosts.forEach { rootUtil.appendHostEntry(it) }
                }
            } catch (e: Exception) {
                _blockedHostnames.update { it - hosts.toSet() }
                _allowedHostnames.update { it + priorAllowed }
                Log.e("LogsViewModel", "Failed to block domains", e)
                _error.value = "Could not block the selected domains. Try again."
            }
        }
    }

    val pinnedDomains: StateFlow<Set<String>> = prefs.pinnedDomains
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    /** Temporarily allow a domain for N minutes, then re-block. */
    fun temporaryAllow(hostname: String, minutes: Int) {
        val host = hostname.lowercase()
        // Optimistic UI update; the holder mutation runs off the main thread
        // because it rebuilds the structural Bloom over the full rule set.
        _blockedHostnames.update { it - host }
        _allowedHostnames.update { it + host }

        viewModelScope.launch(Dispatchers.IO) {
            try {
                // A temporary allow must override wildcard/regex/dnstype source
                // blocks too, so use the user-exact-allow set (which wins in the
                // decision path) instead of only removing an exact block.
                blocklist.allowDomain(host)
                val method = prefs.blockMethod.first()
                if (method == BlockMethod.ROOT_HOSTS) rootUtil.removeHostEntry(host)
                TemporaryAllowWorker.schedule(appContext, host, minutes)
            } catch (e: Exception) {
                _blockedHostnames.update { it + host }
                _allowedHostnames.update { it - host }
                blocklist.clearTemporaryAllow(host)
                runCatching {
                    if (prefs.blockMethod.first() == BlockMethod.ROOT_HOSTS) {
                        rootUtil.appendHostEntry(host)
                    }
                }
                PrivacyLog.e("LogsViewModel", "Failed to temporarily allow domain: $host", e)
                _error.value = "Could not temporarily allow $host. Try again."
            }
        }
    }

    fun togglePin(domain: String) {
        viewModelScope.launch {
            try {
                val current = prefs.pinnedDomains.first()
                if (domain.lowercase() in current) prefs.unpinDomain(domain)
                else prefs.pinDomain(domain)
            } catch (e: Exception) {
                PrivacyLog.e("LogsViewModel", "Failed to toggle pin for: $domain", e)
                _error.value = "Could not update the pinned domain. Try again."
            }
        }
    }

    fun allowDomains(hostnames: Set<String>) {
        val hosts = hostnames.map { it.lowercase() }
        val priorBlocked = _blockedHostnames.value.intersect(hosts.toSet())
        _blockedHostnames.update { it - hosts.toSet() }
        _allowedHostnames.update { it + hosts.toSet() }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                hosts.forEach { host ->
                    repository.addRule(UserRule(hostname = host, type = RuleType.ALLOW))
                    blocklist.allowDomain(host)
                }
                val method = prefs.blockMethod.first()
                if (method == BlockMethod.ROOT_HOSTS) {
                    hosts.forEach { rootUtil.removeHostEntry(it) }
                }
            } catch (e: Exception) {
                _allowedHostnames.update { it - hosts.toSet() }
                _blockedHostnames.update { it + priorBlocked }
                Log.e("LogsViewModel", "Failed to allow domains", e)
                _error.value = "Could not allow the selected domains. Try again."
            }
        }
    }

    suspend fun lookupAllGeo(ips: List<String>): List<GeoIpLookup.GeoInfo> {
        val useOnline = prefs.onlineGeoIpEnabled.first()
        if (!useOnline) return emptyList()
        return geoIpLookup.lookupAll(ips.map { it.trim() })
    }
}

fun DedupedLogEntry.isThreatIntelBlock(): Boolean =
    blocked && decisionReason in THREAT_INTEL_DECISION_REASONS

private fun DnsLogEntry.isThreatIntelBlock(): Boolean =
    blocked && decisionReason in THREAT_INTEL_DECISION_REASONS
