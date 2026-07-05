package com.hostshield.ui.screens.apps

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hostshield.data.database.AppDomainStat
import com.hostshield.data.database.AppQueryStat
import com.hostshield.data.database.DnsLogDao
import com.hostshield.data.model.BlockMethod
import com.hostshield.data.model.RuleType
import com.hostshield.data.model.UserRule
import com.hostshield.data.preferences.AppPreferences
import com.hostshield.data.preferences.SavedDenseListFilter
import com.hostshield.data.repository.HostShieldRepository
import com.hostshield.domain.BlocklistHolder
import com.hostshield.ui.accessibility.accessibilityAction
import com.hostshield.util.RootUtil
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import org.json.JSONObject
import javax.inject.Inject

private const val APPS_SAVED_FILTER_SCREEN = "apps"

enum class AppsActivityFilter { ALL, BLOCKED, UNBLOCKED }

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
    private val repository: HostShieldRepository,
    private val blocklist: BlocklistHolder,
    private val prefs: AppPreferences,
    private val rootUtil: RootUtil
) : ViewModel() {
    val apps: StateFlow<List<AppQueryStat>> = dnsLogDao.getAllAppsWithCounts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _selectedApp = MutableStateFlow<String?>(null)
    val selectedApp = _selectedApp.asStateFlow()

    private val _appDomains = MutableStateFlow<List<AppDomainStat>>(emptyList())
    val appDomains = _appDomains.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
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

        if (pkg != null) {
            domainCollectionJob = viewModelScope.launch {
                dnsLogDao.getDomainsForApp(pkg).collect { _appDomains.value = it }
            }
        } else {
            _appDomains.value = emptyList()
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
