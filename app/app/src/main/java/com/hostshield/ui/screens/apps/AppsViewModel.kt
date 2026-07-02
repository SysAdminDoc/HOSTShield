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
import com.hostshield.data.repository.HostShieldRepository
import com.hostshield.domain.BlocklistHolder
import com.hostshield.ui.accessibility.accessibilityAction
import com.hostshield.util.RootUtil
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

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

    // Track domains the user blocked during this session so the UI
    // updates immediately (the DB logs still show historic blocked=false)
    private val _locallyBlocked = MutableStateFlow<Set<String>>(emptySet())
    val locallyBlocked = _locallyBlocked.asStateFlow()

    // Cancel the previous domain-collection coroutine when switching apps
    // so two collectors don't race to update _appDomains ("flipping" bug)
    private var domainCollectionJob: kotlinx.coroutines.Job? = null

    fun setSearch(q: String) { _searchQuery.value = q }

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
