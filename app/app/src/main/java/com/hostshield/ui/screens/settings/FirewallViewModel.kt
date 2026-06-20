package com.hostshield.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hostshield.data.database.FirewallRuleDao
import com.hostshield.data.model.FirewallRule
import com.hostshield.data.preferences.AppPreferences
import com.hostshield.service.IptablesManager
import com.hostshield.service.NflogReader
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class FirewallViewModel @Inject constructor(
    private val prefs: AppPreferences,
    private val firewallRuleDao: FirewallRuleDao,
    private val iptablesManager: IptablesManager,
    private val nflogReader: NflogReader
) : ViewModel() {
    // DNS-level blocking (preferences-based, works in VPN + root)
    val blockedApps: StateFlow<Set<String>> = prefs.blockedApps
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())
    val excludedApps: StateFlow<Set<String>> = prefs.excludedApps
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    // Network-level firewall rules (Room, iptables)
    val firewallRules: StateFlow<List<FirewallRule>> = firewallRuleDao.getAllRules()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val blockedRuleCount: StateFlow<Int> = firewallRuleDao.getBlockedCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val iptablesActive: StateFlow<Boolean> = iptablesManager.isActive
    val iptablesError: StateFlow<String> = iptablesManager.lastError

    private val _isLoading = MutableStateFlow(true)
    val isLoading = _isLoading.asStateFlow()
    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()
    fun clearError() { _error.value = null }

    private val _searchQuery = MutableStateFlow("")
    val searchQuery = _searchQuery.asStateFlow()
    private val _showSystem = MutableStateFlow(false)
    val showSystem = _showSystem.asStateFlow()
    private val _filter = MutableStateFlow(FirewallFilter.ALL)
    val filter = _filter.asStateFlow()
    private val _tab = MutableStateFlow(FirewallTab.DNS)
    val tab = _tab.asStateFlow()
    private val _isSyncing = MutableStateFlow(false)
    val isSyncing = _isSyncing.asStateFlow()
    private val _isApplyingIptables = MutableStateFlow(false)
    val isApplyingIptables = _isApplyingIptables.asStateFlow()

    fun setSearchQuery(q: String) { _searchQuery.value = q }
    fun toggleShowSystem() { _showSystem.update { !it } }
    fun setFilter(f: FirewallFilter) { _filter.value = f }
    fun setTab(t: FirewallTab) { _tab.value = t }

    // ---- DNS Firewall -------------------------------------------

    fun toggleDnsBlock(packageName: String) {
        viewModelScope.launch {
            val current = blockedApps.value.toMutableSet()
            if (packageName in current) current.remove(packageName) else current.add(packageName)
            prefs.setBlockedApps(current)
        }
    }

    fun unblockAllDns() {
        viewModelScope.launch { prefs.setBlockedApps(emptySet()) }
    }

    // ---- Network Firewall (iptables) ----------------------------

    fun syncApps() {
        viewModelScope.launch(Dispatchers.IO) {
            _isSyncing.value = true
            _isLoading.value = true
            try {
                iptablesManager.syncInstalledApps()
            } catch (e: Exception) {
                android.util.Log.e("FirewallViewModel", "Failed to sync installed apps", e)
                _error.value = "Could not sync installed apps. Check app permissions and try again."
            } finally {
                _isSyncing.value = false
                _isLoading.value = false
            }
        }
    }

    fun toggleWifi(uid: Int, allowed: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            firewallRuleDao.setWifi(uid, allowed)
        }
    }

    fun toggleMobile(uid: Int, allowed: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            firewallRuleDao.setMobile(uid, allowed)
        }
    }

    fun toggleVpn(uid: Int, allowed: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            firewallRuleDao.setVpn(uid, allowed)
        }
    }

    fun blockAllNetwork(uid: Int) {
        viewModelScope.launch(Dispatchers.IO) { firewallRuleDao.blockAll(uid) }
    }

    fun allowAllNetwork(uid: Int) {
        viewModelScope.launch(Dispatchers.IO) { firewallRuleDao.allowAll(uid) }
    }

    fun resetAllNetwork() {
        viewModelScope.launch(Dispatchers.IO) { firewallRuleDao.resetAll() }
    }

    fun applyIptables() {
        viewModelScope.launch(Dispatchers.IO) {
            if (_isApplyingIptables.value) return@launch
            _isApplyingIptables.value = true
            _error.value = null
            try {
                val applied = iptablesManager.applyRules()
                if (applied) {
                    try {
                        nflogReader.start()
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        android.util.Log.w("FirewallViewModel", "Firewall logging failed to start", e)
                        _error.value = "Firewall applied, but block logging could not start."
                    }
                } else if (iptablesManager.lastError.value.isBlank()) {
                    _error.value = "Could not apply iptables rules. Confirm root access and try again."
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                android.util.Log.e("FirewallViewModel", "Failed to apply iptables rules", e)
                _error.value = "Could not apply iptables rules. Confirm root access and try again."
            } finally {
                _isApplyingIptables.value = false
            }
        }
    }

    fun clearIptables() {
        viewModelScope.launch(Dispatchers.IO) {
            if (_isApplyingIptables.value) return@launch
            _isApplyingIptables.value = true
            _error.value = null
            try {
                nflogReader.stop()
                iptablesManager.clearRules()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                android.util.Log.e("FirewallViewModel", "Failed to clear iptables rules", e)
                _error.value = "Could not clear iptables rules. Confirm root access and try again."
            } finally {
                _isApplyingIptables.value = false
            }
        }
    }

    // Diagnostic dump
    private val _diagnosticOutput = MutableStateFlow("")
    val diagnosticOutput: StateFlow<String> = _diagnosticOutput.asStateFlow()
    private val _isDiagnosing = MutableStateFlow(false)
    val isDiagnosing: StateFlow<Boolean> = _isDiagnosing.asStateFlow()

    fun runDiagnostic() {
        viewModelScope.launch(Dispatchers.IO) {
            _isDiagnosing.value = true
            _error.value = null
            try {
                _diagnosticOutput.value = iptablesManager.dumpFullDiagnostic()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                android.util.Log.e("FirewallViewModel", "Firewall diagnostic failed", e)
                _diagnosticOutput.value = ""
                _error.value = "Firewall diagnostic could not run. Confirm root access and try again."
            } finally {
                _isDiagnosing.value = false
            }
        }
    }

    fun exportScript(callback: (String) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val script = iptablesManager.exportAsScript()
                callback(script)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                android.util.Log.e("FirewallViewModel", "Firewall script export failed", e)
                _error.value = "Firewall script export failed. Try again after refreshing rules."
            }
        }
    }

    // Bulk operations
    fun blockAllWifi() {
        viewModelScope.launch(Dispatchers.IO) {
            val rules = firewallRuleDao.getAllRulesList()
            rules.filter { !it.isSystem }.forEach {
                firewallRuleDao.setWifi(it.uid, false)
            }
        }
    }

    fun blockAllMobile() {
        viewModelScope.launch(Dispatchers.IO) {
            val rules = firewallRuleDao.getAllRulesList()
            rules.filter { !it.isSystem }.forEach {
                firewallRuleDao.setMobile(it.uid, false)
            }
        }
    }

    // ---- Context-Aware Firewall ------------------------------------

    fun toggleBlockScreenOff(uid: Int, block: Boolean) {
        viewModelScope.launch(Dispatchers.IO) { firewallRuleDao.setBlockScreenOff(uid, block) }
    }

    fun toggleBlockBackground(uid: Int, block: Boolean) {
        viewModelScope.launch(Dispatchers.IO) { firewallRuleDao.setBlockBackground(uid, block) }
    }

    fun toggleBlockMetered(uid: Int, block: Boolean) {
        viewModelScope.launch(Dispatchers.IO) { firewallRuleDao.setBlockMetered(uid, block) }
    }

    init { syncApps() }
}

enum class FirewallFilter { ALL, BLOCKED, UNBLOCKED }
enum class FirewallTab { DNS, NETWORK, CONTEXT }
