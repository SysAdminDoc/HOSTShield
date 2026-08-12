package com.hostshield.ui.screens.lists

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hostshield.data.database.AppDnsRuleDao
import com.hostshield.data.model.AppDnsRule
import com.hostshield.data.model.RuleType
import com.hostshield.data.model.UserRule
import com.hostshield.data.repository.HostShieldRepository
import com.hostshield.service.AppDnsRuleEngine
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class RulesViewModel @Inject constructor(
    private val repository: HostShieldRepository,
    private val appDnsRuleDao: AppDnsRuleDao,
    private val appDnsRuleEngine: AppDnsRuleEngine,
) : ViewModel() {
    val rules: StateFlow<List<UserRule>> = repository.getAllRules()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val appRules: StateFlow<List<AppDnsRule>> = appDnsRuleDao.getAllRules()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun addRule(
        hostname: String,
        type: RuleType,
        redirectIp: String = "",
        comment: String = "",
        isRegex: Boolean = false,
        expiresAt: Long = 0L,
    ) {
        // Trim BEFORE the wildcard check: " *.example.com" must classify as a
        // wildcard, not be stored as an exact rule for a literal "*." string
        // that can never match a DNS hostname.
        val trimmed = hostname.trim()
        val isWild = !isRegex && trimmed.startsWith("*.")
        viewModelScope.launch {
            repository.addRule(UserRule(
                hostname = trimmed.let { if (isRegex) it else it.lowercase() },
                type = type, redirectIp = redirectIp,
                comment = comment.trim().take(500),
                expiresAt = expiresAt.coerceAtLeast(0L),
                isWildcard = isWild,
                isRegex = isRegex,
            ))
        }
    }

    fun toggleRule(id: Long, enabled: Boolean) {
        viewModelScope.launch { repository.toggleRule(id, enabled) }
    }

    fun deleteRule(rule: UserRule) {
        viewModelScope.launch { repository.deleteRule(rule) }
    }

    fun toggleAppRule(rule: AppDnsRule, enabled: Boolean) {
        viewModelScope.launch {
            appDnsRuleDao.update(rule.copy(enabled = enabled))
            appDnsRuleEngine.reloadForApp(rule.packageName)
        }
    }

    fun deleteAppRule(rule: AppDnsRule) {
        viewModelScope.launch {
            appDnsRuleDao.delete(rule)
            appDnsRuleEngine.reloadForApp(rule.packageName)
        }
    }
}
