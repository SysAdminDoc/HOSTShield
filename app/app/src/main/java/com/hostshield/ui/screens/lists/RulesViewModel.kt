package com.hostshield.ui.screens.lists

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hostshield.data.model.RuleType
import com.hostshield.data.model.UserRule
import com.hostshield.data.repository.HostShieldRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class RulesViewModel @Inject constructor(
    private val repository: HostShieldRepository
) : ViewModel() {
    val rules: StateFlow<List<UserRule>> = repository.getAllRules()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun addRule(hostname: String, type: RuleType, redirectIp: String = "", comment: String = "", isRegex: Boolean = false) {
        val isWild = !isRegex && hostname.startsWith("*.")
        viewModelScope.launch {
            repository.addRule(UserRule(
                hostname = hostname.trim().let { if (isRegex) it else it.lowercase() },
                type = type, redirectIp = redirectIp,
                comment = comment, isWildcard = isWild, isRegex = isRegex
            ))
        }
    }

    fun toggleRule(id: Long, enabled: Boolean) {
        viewModelScope.launch { repository.toggleRule(id, enabled) }
    }

    fun deleteRule(rule: UserRule) {
        viewModelScope.launch { repository.deleteRule(rule) }
    }
}
