package com.hostshield.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hostshield.data.model.RuleType
import com.hostshield.data.repository.HostShieldRepository
import com.hostshield.domain.BlocklistHolder
import com.hostshield.domain.parser.HostsParser
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val RULE_TEST_SCREEN_TAG = "RuleTestScreen"

@HiltViewModel
class RuleTestViewModel @Inject constructor(
    private val blocklist: BlocklistHolder,
    private val repository: HostShieldRepository
) : ViewModel() {
    private val _state = MutableStateFlow(RuleTestState())
    val state = _state.asStateFlow()

    fun setTestDomain(d: String) { _state.update { it.copy(testDomain = d, message = null) } }
    fun setBatchInput(s: String) { _state.update { it.copy(batchInput = s, message = null) } }
    fun clearMessage() { _state.update { it.copy(message = null, messageIsError = false) } }

    fun testDomain() {
        if (_state.value.isTesting) return
        val domain = normalizeRuleTestDomain(_state.value.testDomain)
        if (domain == null) {
            _state.update {
                it.copy(
                    message = "Enter a valid domain such as ads.example.com.",
                    messageIsError = true
                )
            }
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            _state.update { it.copy(isTesting = true, message = null, messageIsError = false) }
            try {
                val result = testSingleDomain(domain)
                _state.update {
                    it.copy(
                        isTesting = false,
                        results = listOf(result) + it.results.take(29)
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                android.util.Log.w(RULE_TEST_SCREEN_TAG, "Single rule test failed for $domain", e)
                _state.update {
                    it.copy(
                        isTesting = false,
                        message = "Rule test could not complete. Refresh sources and try again.",
                        messageIsError = true
                    )
                }
            }
        }
    }

    fun testBatch() {
        if (_state.value.isTesting) return
        val domains = normalizeRuleTestDomains(_state.value.batchInput)
        if (domains.isEmpty()) {
            _state.update {
                it.copy(
                    message = "Paste at least one valid domain, one per line.",
                    messageIsError = true
                )
            }
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            _state.update { it.copy(isTesting = true, message = null, messageIsError = false) }
            try {
                val results = domains.map { testSingleDomain(it) }
                _state.update { it.copy(isTesting = false, batchResults = results) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                android.util.Log.w(RULE_TEST_SCREEN_TAG, "Batch rule test failed", e)
                _state.update {
                    it.copy(
                        isTesting = false,
                        message = "Batch test could not complete. Try fewer domains or refresh the blocklists.",
                        messageIsError = true
                    )
                }
            }
        }
    }

    private suspend fun testSingleDomain(domain: String): RuleTestResult {
        val isBlocked = blocklist.isBlocked(domain)

        // Determine what matched
        val matchedBy = if (isBlocked) {
            // Check user rules first
            val blockRules = repository.getEnabledRulesByType(RuleType.BLOCK)
            val exactMatch = blockRules.find { !it.isWildcard && !it.isRegex && it.hostname.lowercase() == domain }
            if (exactMatch != null) return RuleTestResult(domain, true, "exact rule: ${exactMatch.hostname}")

            val wildcardMatch = blockRules.filter { it.isWildcard }.find {
                HostsParser.matchesWildcard(domain, it.hostname)
            }
            if (wildcardMatch != null) return RuleTestResult(domain, true, "wildcard: ${wildcardMatch.hostname}")

            val regexMatch = blockRules.filter { it.isRegex }.find {
                try { Regex(it.hostname, RegexOption.IGNORE_CASE).containsMatchIn(domain) }
                catch (_: Exception) { false }
            }
            if (regexMatch != null) return RuleTestResult(domain, true, "regex: ${regexMatch.hostname}")

            "source blocklist"
        } else {
            // Check if allow rule matches
            val allowRules = repository.getEnabledRulesByType(RuleType.ALLOW)
            val allowMatch = allowRules.find { it.hostname.lowercase() == domain }
            if (allowMatch != null) "allowed by rule: ${allowMatch.hostname}"
            else "not blocked"
        }

        return RuleTestResult(domain, isBlocked, matchedBy)
    }
}
