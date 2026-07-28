package com.hostshield.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hostshield.domain.BlocklistHolder
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val RULE_TEST_SCREEN_TAG = "RuleTestScreen"

@HiltViewModel
class RuleTestViewModel @Inject constructor(
    private val blocklist: BlocklistHolder
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

    private fun testSingleDomain(domain: String): RuleTestResult {
        // Evaluate with a concrete query type so `$dnstype` rules (which bail out
        // when the query type is null) produce the same verdict the live VPN/proxy
        // paths do. Test A (1) and AAAA (28); the domain is "blocked" if either
        // record type is blocked. Attribution comes from the engine decision so
        // the tester never drifts from the real decision path.
        val decisionA = blocklist.decide(domain, queryType = 1)
        val decisionAaaa = blocklist.decide(domain, queryType = 28)
        val decision = if (decisionA.blocked) decisionA else decisionAaaa
        val isBlocked = decisionA.blocked || decisionAaaa.blocked

        val matchedBy = if (isBlocked) {
            val src = decision.source.ifBlank { "source blocklist" }
            if (decision.matchedValue.isNotBlank() && decision.matchedValue != domain) {
                "$src (${decision.matchedValue})"
            } else {
                src
            }
        } else {
            val src = decisionA.source.ifBlank { decisionAaaa.source }
            if (src.isNotBlank()) src else "not blocked"
        }

        return RuleTestResult(domain, isBlocked, matchedBy)
    }
}
