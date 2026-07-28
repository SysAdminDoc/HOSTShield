package com.hostshield.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.net.InetAddress
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class DnsLeakTestViewModel @Inject constructor(
    private val blocklist: com.hostshield.domain.BlocklistHolder
) : ViewModel() {
    private val _state = MutableStateFlow(LeakTestState())
    val state = _state.asStateFlow()

    // Known test domains — if these resolve to their actual IPs, DNS is NOT going through HostShield
    private val leakTestDomains = listOf(
        "dns-test-1.${UUID.randomUUID().toString().take(8)}.example.invalid",
        "dns-test-2.${UUID.randomUUID().toString().take(8)}.example.invalid",
        "dns-test-3.${UUID.randomUUID().toString().take(8)}.example.invalid",
    )

    // Known blocked domains — should resolve to 0.0.0.0 or fail if HostShield is working
    private val blockedTestDomains = listOf(
        "graph.facebook.com",
        "analytics.google.com",
        "ads.doubleclick.net",
        "pagead2.googlesyndication.com",
        "data.microsoft.com"
    )

    fun runTest() {
        if (_state.value.isRunning) return
        viewModelScope.launch(Dispatchers.IO) {
            _state.update {
                it.copy(
                    isRunning = true,
                    progress = "Testing DNS resolution...",
                    results = emptyList(),
                    overallPass = null,
                    blockedTestDomain = null,
                    blockedCorrectly = null,
                    message = null,
                    messageIsError = false
                )
            }

            val results = mutableListOf<LeakTestResult>()
            try {

                // Test 1: Non-existent domains should NXDOMAIN
                _state.update { it.copy(progress = "Testing random domains (should fail)...") }
                for (domain in leakTestDomains) {
                    val start = System.nanoTime()
                    try {
                        val addrs = InetAddress.getAllByName(domain).map { it.hostAddress ?: "?" }
                        val latency = (System.nanoTime() - start) / 1_000_000L
                        // If random domains resolve, something is intercepting (captive portal, ISP redirect)
                        results.add(LeakTestResult(domain, addrs, isLeaking = true, latencyMs = latency))
                    } catch (_: Exception) {
                        val latency = (System.nanoTime() - start) / 1_000_000L
                        results.add(LeakTestResult(domain, emptyList(), isLeaking = false, latencyMs = latency))
                    }
                }

                // Test 2: Known blocked domains should be blocked by HostShield
                _state.update { it.copy(progress = "Testing blocked domains...") }
                var blockedTest: String? = null
                var blockedCorrectly: Boolean? = null

                for (domain in blockedTestDomains) {
                    if (blocklist.isBlocked(domain, queryType = 1)) {
                        blockedTest = domain
                        val start = System.nanoTime()
                        try {
                            val addrs = InetAddress.getAllByName(domain).map { it.hostAddress ?: "?" }
                            val latency = (System.nanoTime() - start) / 1_000_000L
                            // If it resolved to 0.0.0.0 or ::, HostShield is blocking correctly
                            val isBlocked = addrs.any { it == "0.0.0.0" || it == "::" || it == "::1" } ||
                                addrs.isEmpty()
                            blockedCorrectly = isBlocked
                            results.add(LeakTestResult(domain, addrs, isLeaking = !isBlocked, latencyMs = latency))
                        } catch (_: Exception) {
                            val latency = (System.nanoTime() - start) / 1_000_000L
                            // NXDOMAIN = also correctly blocked
                            blockedCorrectly = true
                            results.add(LeakTestResult(domain, emptyList(), isLeaking = false, latencyMs = latency))
                        }
                        break
                    }
                }

                // Test 3: Resolve a known-good domain to verify DNS works at all
                _state.update { it.copy(progress = "Testing connectivity...") }
                val start = System.nanoTime()
                var connectivityFailed = false
                try {
                    val addrs = InetAddress.getAllByName("connectivitycheck.gstatic.com").map { it.hostAddress ?: "?" }
                    val latency = (System.nanoTime() - start) / 1_000_000L
                    results.add(LeakTestResult("connectivitycheck.gstatic.com", addrs, isLeaking = false, latencyMs = latency))
                } catch (e: Exception) {
                    val latency = (System.nanoTime() - start) / 1_000_000L
                    android.util.Log.w("DnsLeakTest", "Connectivity DNS check failed", e)
                    // Being offline is not a leak. Record it as a non-leaking
                    // failure and mark the whole run inconclusive rather than
                    // showing the alarming "Potential DNS leak" verdict.
                    connectivityFailed = true
                    results.add(LeakTestResult("connectivitycheck.gstatic.com", listOf("No response — network may be offline"), isLeaking = false, latencyMs = latency))
                }

                val leaking = results.any { it.isLeaking }
                _state.update {
                    it.copy(
                        isRunning = false,
                        progress = "",
                        results = results,
                        overallPass = if (connectivityFailed) null else !leaking,
                        blockedTestDomain = blockedTest,
                        blockedCorrectly = blockedCorrectly,
                        message = when {
                            connectivityFailed ->
                                "Network unreachable — the DNS leak test is inconclusive. Reconnect and try again."
                            blockedTest == null ->
                                "No loaded blocked-domain sample matched. Update blocklists before relying on this test alone."
                            else -> null
                        },
                        messageIsError = false
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                android.util.Log.w("DnsLeakTest", "DNS leak test failed", e)
                _state.update {
                    it.copy(
                        isRunning = false,
                        progress = "",
                        results = results,
                        overallPass = null,
                        message = "Leak test could not complete. Check network access and try again.",
                        messageIsError = true
                    )
                }
            }
        }
    }

    fun clearMessage() {
        _state.update { it.copy(message = null, messageIsError = false) }
    }
}
