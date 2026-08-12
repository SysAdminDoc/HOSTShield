package com.hostshield.ui.screens.apps

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hostshield.data.database.AppDomainStat
import com.hostshield.data.database.AppQueryStat
import com.hostshield.data.database.AppDnsRuleDao
import com.hostshield.data.database.DnsLogDao
import com.hostshield.data.model.BlockMethod
import com.hostshield.data.model.AppDnsRule
import com.hostshield.data.model.DnsLogEntry
import com.hostshield.data.model.RuleType
import com.hostshield.data.model.UserRule
import com.hostshield.data.preferences.AppPreferences
import com.hostshield.data.preferences.SavedDenseListFilter
import com.hostshield.data.repository.HostShieldRepository
import com.hostshield.domain.BlocklistHolder
import com.hostshield.ui.accessibility.accessibilityAction
import com.hostshield.util.RootUtil
import com.hostshield.service.AppDnsRuleEngine
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import org.json.JSONObject
import javax.inject.Inject

private const val APPS_SAVED_FILTER_SCREEN = "apps"

enum class AppsActivityFilter { ALL, BLOCKED, UNBLOCKED }

data class AppBreakageDomain(
    val hostname: String,
    val hitCount: Int,
    val sources: List<String>,
    val matchedValues: List<String>,
    val reasons: List<String>,
)

data class AppDiagnosisState(
    val packageName: String = "",
    val isLoading: Boolean = false,
    val blockedDomains: List<AppBreakageDomain> = emptyList(),
    val allowedDomains: Set<String> = emptySet(),
)

internal fun buildAppBreakageDomains(logs: List<DnsLogEntry>): List<AppBreakageDomain> =
    logs.asSequence()
        .filter { it.blocked && it.hostname.isNotBlank() }
        .groupBy { it.hostname.trim().lowercase() }
        .map { (hostname, entries) ->
            AppBreakageDomain(
                hostname = hostname,
                hitCount = entries.size,
                sources = entries.asSequence()
                    .map { it.decisionSource.ifBlank { it.decisionReason } }
                    .filter { it.isNotBlank() }
                    .distinct()
                    .sorted()
                    .toList()
                    .ifEmpty { listOf("Unattributed block decision") },
                matchedValues = entries.asSequence()
                    .map { it.matchedValue }
                    .filter { it.isNotBlank() }
                    .distinct()
                    .sorted()
                    .toList(),
                reasons = entries.asSequence()
                    .map { it.decisionReason }
                    .filter { it.isNotBlank() }
                    .distinct()
                    .sorted()
                    .toList(),
            )
        }
        .sortedWith(compareByDescending<AppBreakageDomain> { it.hitCount }.thenBy { it.hostname })
        .toList()

private fun describeFilter(query: String, filter: AppsActivityFilter): String = buildList {
    if (query.isNotBlank()) add("\"${query.take(18)}\"")
    when (filter) {
        AppsActivityFilter.ALL -> Unit
        AppsActivityFilter.BLOCKED -> add("Blocked")
        AppsActivityFilter.UNBLOCKED -> add("No blocks")
    }
}.joinToString(" + ").ifBlank { "App filter" }

@HiltViewModel
class AppsViewModel @Inject constructor(
    private val dnsLogDao: DnsLogDao,
    private val appDnsRuleDao: AppDnsRuleDao,
    private val appDnsRuleEngine: AppDnsRuleEngine,
    private val repository: HostShieldRepository,
    private val blocklist: BlocklistHolder,
    private val prefs: AppPreferences,
    private val rootUtil: RootUtil,
    savedStateHandle: androidx.lifecycle.SavedStateHandle
) : ViewModel() {
    val apps: StateFlow<List<AppQueryStat>> = dnsLogDao.getAllAppsWithCounts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _selectedApp = MutableStateFlow<String?>(null)
    val selectedApp = _selectedApp.asStateFlow()

    private val _appDomains = MutableStateFlow<List<AppDomainStat>>(emptyList())
    val appDomains = _appDomains.asStateFlow()

    private val _diagnosis = MutableStateFlow(AppDiagnosisState())
    val diagnosis = _diagnosis.asStateFlow()

    // Seed search from the Home search suggestion nav arg ("apps?query=…").
    private val _searchQuery = MutableStateFlow(savedStateHandle.get<String>("query").orEmpty())
    val searchQuery = _searchQuery.asStateFlow()
    private val _filter = MutableStateFlow(AppsActivityFilter.ALL)
    val filter = _filter.asStateFlow()
    val savedFilters: StateFlow<List<SavedDenseListFilter>> = prefs.ui
        .savedDenseListFilters(APPS_SAVED_FILTER_SCREEN)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Track domains the user blocked during this session so the UI
    // updates immediately (the DB logs still show historic blocked=false)
    private val _locallyBlocked = MutableStateFlow<Set<String>>(emptySet())
    val locallyBlocked = _locallyBlocked.asStateFlow()

    // Cancel the previous domain-collection coroutine when switching apps
    // so two collectors don't race to update _appDomains ("flipping" bug)
    private var domainCollectionJob: kotlinx.coroutines.Job? = null
    private var diagnosisCollectionJob: kotlinx.coroutines.Job? = null

    fun setSearch(q: String) { _searchQuery.value = q }
    fun setFilter(filter: AppsActivityFilter) { _filter.value = filter }
    fun clearFilters() {
        _searchQuery.value = ""
        _filter.value = AppsActivityFilter.ALL
    }

    fun saveCurrentFilter() {
        val query = _searchQuery.value.trim()
        val filter = _filter.value
        if (query.isBlank() && filter == AppsActivityFilter.ALL) return
        viewModelScope.launch {
            prefs.ui.saveDenseListFilter(
                APPS_SAVED_FILTER_SCREEN,
                describeFilter(query, filter),
                JSONObject()
                    .put("query", query)
                    .put("filter", filter.name)
                    .toString()
            )
        }
    }

    fun applySavedFilter(saved: SavedDenseListFilter) {
        runCatching {
            val json = JSONObject(saved.payload)
            _searchQuery.value = json.optString("query")
            _filter.value = runCatching {
                AppsActivityFilter.valueOf(json.optString("filter"))
            }.getOrDefault(AppsActivityFilter.ALL)
        }
    }

    fun clearSavedFilters() {
        viewModelScope.launch {
            prefs.ui.clearDenseListFilters(APPS_SAVED_FILTER_SCREEN)
        }
    }

    fun selectApp(pkg: String?) {
        _selectedApp.value = pkg
        // Cancel any running domain collection first
        domainCollectionJob?.cancel()
        domainCollectionJob = null
        diagnosisCollectionJob?.cancel()
        diagnosisCollectionJob = null

        if (pkg != null) {
            _diagnosis.value = AppDiagnosisState(packageName = pkg, isLoading = true)
            domainCollectionJob = viewModelScope.launch {
                dnsLogDao.getDomainsForApp(pkg).collect { _appDomains.value = it }
            }
            diagnosisCollectionJob = viewModelScope.launch {
                combine(
                    dnsLogDao.getLogsForApp(pkg, 500),
                    appDnsRuleDao.getRulesForApp(pkg),
                ) { logs, rules ->
                    AppDiagnosisState(
                        packageName = pkg,
                        blockedDomains = buildAppBreakageDomains(logs),
                        allowedDomains = rules
                            .filter { it.action.equals("allow", ignoreCase = true) }
                            .map { it.domain.trim().lowercase().removePrefix("*.") }
                            .toSet(),
                    )
                }.collect { _diagnosis.value = it }
            }
        } else {
            _appDomains.value = emptyList()
            _diagnosis.value = AppDiagnosisState()
        }
    }

    /** Open the app diagnosis view from an app row. */
    fun diagnoseApp(pkg: String) = selectApp(pkg)

    fun allowDomainForApp(hostname: String) {
        val pkg = _selectedApp.value?.trim().orEmpty()
        val host = hostname.trim().lowercase().removePrefix("*.")
        if (pkg.isBlank() || host.isBlank()) return

        _diagnosis.update { it.copy(allowedDomains = it.allowedDomains + host) }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                appDnsRuleDao.insert(
                    AppDnsRule(packageName = pkg, domain = host, action = "allow")
                )
                appDnsRuleEngine.reloadForApp(pkg)
            } catch (e: Exception) {
                _diagnosis.update { it.copy(allowedDomains = it.allowedDomains - host) }
                android.util.Log.w("AppsViewModel", "Failed to allow $host for $pkg", e)
            }
        }
    }

    fun blockDomain(hostname: String) {
        val host = hostname.lowercase()
        // Immediately mark as blocked in the UI
        _locallyBlocked.update { it + host }
        viewModelScope.launch(Dispatchers.IO) {
            repository.addRule(UserRule(hostname = host, type = RuleType.BLOCK))
            blocklist.addDomain(host)
            // Root mode: also write directly to /etc/hosts
            val method = prefs.blockMethod.first()
            if (method == BlockMethod.ROOT_HOSTS) {
                rootUtil.appendHostEntry(host)
            }
        }
    }
}
