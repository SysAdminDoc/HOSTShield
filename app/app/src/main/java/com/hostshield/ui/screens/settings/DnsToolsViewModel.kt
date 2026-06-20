package com.hostshield.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hostshield.data.preferences.AppPreferences
import com.hostshield.domain.BlocklistHolder
import com.hostshield.service.Doh3Resolver
import com.hostshield.service.DohResolver
import com.topjohnwu.superuser.Shell
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.InetAddress
import javax.inject.Inject

private val NETWORK_DIAGNOSTIC_HOSTNAME = Regex(
    """^(?=.{1,253}$)(?:[a-zA-Z0-9](?:[a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?\.)*[a-zA-Z0-9](?:[a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?$"""
)
private val NETWORK_DIAGNOSTIC_IPV4 = Regex("""^(?:\d{1,3}\.){3}\d{1,3}$""")
private val NETWORK_DIAGNOSTIC_IPV6 = Regex("""^[0-9a-fA-F:]{2,45}$""")

internal fun normalizeNetworkDiagnosticTarget(rawTarget: String): String? {
    val target = rawTarget.trim().trimEnd('.')
    if (target.isBlank() || target.length > 253) return null
    if (target.any { it.isWhitespace() }) return null
    if (target.any { it !in 'a'..'z' && it !in 'A'..'Z' && it !in '0'..'9' && it != '-' && it != '.' && it != ':' }) {
        return null
    }

    if (NETWORK_DIAGNOSTIC_IPV4.matches(target)) {
        val octets = target.split(".").map { it.toIntOrNull() ?: return null }
        return if (octets.size == 4 && octets.all { it in 0..255 }) target else null
    }

    if (target.contains(":") && NETWORK_DIAGNOSTIC_IPV6.matches(target)) {
        return target
    }

    return if (NETWORK_DIAGNOSTIC_HOSTNAME.matches(target)) target.lowercase() else null
}

data class DnsToolsState(
    val lookupDomain: String = "",
    val lookupResults: List<LookupResult> = emptyList(),
    val isLookingUp: Boolean = false,
    val currentDns: List<String> = emptyList(),
    val privateDnsMode: String = "unknown",
    val privateDnsProvider: String = "",
    val resolverStats: String = "",
    val blocklistSize: Int = 0,
    val cacheEntries: List<CacheEntry> = emptyList(),
    val isFlushing: Boolean = false,
    val dohProvider: String = "cloudflare",
    val dohEnabled: Boolean = false,
    val customUpstreamDns: String = "",
    val tab: DnsToolsTab = DnsToolsTab.LOOKUP,
    val pingResult: String = "",
    val isPinging: Boolean = false,
    val batchInput: String = "",
    val batchResults: List<LookupResult> = emptyList(),
    val isBatchRunning: Boolean = false,
    val batchProgress: Int = 0,
    val batchTotal: Int = 0,
    val ruleSyncUrls: String = "",
    val resolverHealth: List<ResolverHealthUi> = emptyList()
)

data class LookupResult(
    val domain: String,
    val addresses: List<String>,
    val isBlocked: Boolean,
    val latencyMs: Long,
    val error: String = ""
)

data class CacheEntry(
    val hostname: String,
    val addresses: String,
    val ttl: String
)

data class ResolverHealthUi(
    val provider: String,
    val selected: Boolean,
    val activeTransport: String,
    val latencyMs: Long?,
    val doh3LatencyMs: Long?,
    val attempts: Int,
    val successes: Int,
    val failures: Int,
    val successRatePercent: Int?,
    val failovers: Int,
    val pinFailures: Int,
    val edeCount: Int
)

enum class DnsToolsTab { LOOKUP, STATUS, CONFIG, DIAG }

@HiltViewModel
class DnsToolsViewModel @Inject constructor(
    private val prefs: AppPreferences,
    private val blocklist: BlocklistHolder,
    private val dohResolver: DohResolver,
    private val doh3Resolver: Doh3Resolver
) : ViewModel() {
    private val _state = MutableStateFlow(DnsToolsState())
    val state = _state.asStateFlow()

    init {
        refreshStatus()
        viewModelScope.launch {
            prefs.dohProvider.collect { p -> _state.update { it.copy(dohProvider = p) } }
        }
        viewModelScope.launch {
            prefs.dohEnabled.collect { e -> _state.update { it.copy(dohEnabled = e) } }
        }
        viewModelScope.launch {
            prefs.customUpstreamDns.collect { d -> _state.update { it.copy(customUpstreamDns = d) } }
        }
        viewModelScope.launch {
            prefs.ruleSyncUrls.collect { u -> _state.update { it.copy(ruleSyncUrls = u) } }
        }
    }

    fun setRuleSyncUrls(urls: String) {
        viewModelScope.launch { prefs.setRuleSyncUrls(urls) }
    }

    fun setTab(tab: DnsToolsTab) { _state.update { it.copy(tab = tab) } }
    fun setLookupDomain(d: String) { _state.update { it.copy(lookupDomain = d) } }
    fun setBatchInput(s: String) { _state.update { it.copy(batchInput = s) } }

    fun performLookup() {
        val domain = _state.value.lookupDomain.trim().lowercase()
        if (domain.isBlank()) return

        viewModelScope.launch(Dispatchers.IO) {
            _state.update { it.copy(isLookingUp = true) }
            val isBlocked = blocklist.isBlocked(domain)
            val start = System.nanoTime()
            try {
                val addrs = InetAddress.getAllByName(domain).map { it.hostAddress ?: "?" }
                val latency = (System.nanoTime() - start) / 1_000_000L
                val result = LookupResult(domain, addrs, isBlocked, latency)
                _state.update {
                    it.copy(
                        isLookingUp = false,
                        lookupResults = listOf(result) + it.lookupResults.take(19)
                    )
                }
            } catch (e: Exception) {
                val latency = (System.nanoTime() - start) / 1_000_000L
                android.util.Log.w("DnsTools", "DNS lookup failed for $domain", e)
                val result = LookupResult(domain, emptyList(), isBlocked, latency, lookupFailureMessage(e))
                _state.update {
                    it.copy(
                        isLookingUp = false,
                        lookupResults = listOf(result) + it.lookupResults.take(19)
                    )
                }
            }
        }
    }

    fun refreshStatus() {
        viewModelScope.launch(Dispatchers.IO) {
            val dns = mutableListOf<String>()
            try {
                for (prop in arrayOf("net.dns1", "net.dns2", "net.dns3", "net.dns4")) {
                    val r = Shell.cmd("getprop $prop 2>/dev/null").exec()
                    val ip = r.out.firstOrNull()?.trim()
                    if (!ip.isNullOrBlank() && ip != "0.0.0.0") dns.add(ip)
                }
            } catch (_: Exception) { }

            val privateDns = try {
                Shell.cmd("settings get global private_dns_mode").exec().out.firstOrNull()?.trim() ?: "unknown"
            } catch (_: Exception) { "unknown" }

            val privateDnsProvider = try {
                Shell.cmd("settings get global private_dns_specifier").exec().out.firstOrNull()?.trim() ?: ""
            } catch (_: Exception) { "" }

            val selectedProvider = DohResolver.Provider.fromId(prefs.dohProvider.first())
            val doh3Latency = doh3Resolver.getLatencyStats()
            val resolverHealth = dohResolver.getHealthSnapshot(selectedProvider, doh3Latency)
                .map { health ->
                    ResolverHealthUi(
                        provider = health.provider.name,
                        selected = health.selected,
                        activeTransport = health.activeTransport,
                        latencyMs = health.latencyMs,
                        doh3LatencyMs = health.doh3LatencyMs,
                        attempts = health.attempts,
                        successes = health.successes,
                        failures = health.failures,
                        successRatePercent = health.successRatePercent,
                        failovers = health.failovers,
                        pinFailures = health.pinFailures,
                        edeCount = health.edeCount
                    )
                }

            val resolverStats = try {
                val r = Shell.cmd("dumpsys dnsresolver 2>/dev/null | head -30").exec()
                r.out.joinToString("\n")
            } catch (_: Exception) { "" }

            // Parse DNS cache from dumpsys
            val cache = mutableListOf<CacheEntry>()
            try {
                val r = Shell.cmd("dumpsys dnsresolver 2>/dev/null").exec()
                var inCache = false
                for (line in r.out) {
                    if (line.contains("Cache entries") || line.contains("DnsQueryLog")) inCache = true
                    if (inCache) {
                        val parts = line.trim().split(Regex("\\s+"))
                        if (parts.size >= 3 && parts[0].matches(Regex("\\d+"))) {
                            // Format: uid hostname type ...
                            cache.add(CacheEntry(
                                hostname = parts.getOrElse(1) { "" },
                                addresses = parts.drop(2).take(3).joinToString(", "),
                                ttl = parts.lastOrNull() ?: ""
                            ))
                        }
                    }
                    if (cache.size >= 50) break
                }
            } catch (_: Exception) { }

            _state.update {
                it.copy(
                    currentDns = dns,
                    privateDnsMode = privateDns,
                    privateDnsProvider = privateDnsProvider,
                    resolverStats = resolverStats,
                    blocklistSize = blocklist.getBlockedCount(),
                    cacheEntries = cache,
                    resolverHealth = resolverHealth
                )
            }
        }
    }

    fun flushDnsCache() {
        viewModelScope.launch(Dispatchers.IO) {
            _state.update { it.copy(isFlushing = true) }
            try {
                // Multiple methods to flush DNS
                Shell.cmd(
                    "ndc resolver flushdefaultif 2>/dev/null || true",
                    "ndc resolver clearnetdns 0 2>/dev/null || true",
                    "service call dnsresolver 7 2>/dev/null || true"  // clearResolverConfiguration
                ).exec()
            } catch (_: Exception) { }
            _state.update { it.copy(isFlushing = false, cacheEntries = emptyList()) }
            refreshStatus()
        }
    }

    fun setDohProvider(provider: String) {
        viewModelScope.launch { prefs.setDohProvider(provider) }
    }

    fun toggleDoh(enabled: Boolean) {
        viewModelScope.launch { prefs.setDohEnabled(enabled) }
    }

    fun setCustomUpstreamDns(dns: String) {
        viewModelScope.launch { prefs.setCustomUpstreamDns(dns) }
    }

    /** Batch-test multiple domains (one per line). */
    fun runBatchTest() {
        val input = _state.value.batchInput.trim()
        if (input.isBlank()) return
        val domains = input.lines()
            .map { it.trim().lowercase() }
            .filter { it.isNotBlank() && it.contains('.') }
            .distinct()
            .take(100) // safety limit

        viewModelScope.launch(Dispatchers.IO) {
            _state.update { it.copy(isBatchRunning = true, batchTotal = domains.size, batchProgress = 0, batchResults = emptyList()) }
            val results = mutableListOf<LookupResult>()
            for ((idx, domain) in domains.withIndex()) {
                _state.update { it.copy(batchProgress = idx + 1) }
                val isBlocked = blocklist.isBlocked(domain)
                val start = System.nanoTime()
                try {
                    val addrs = InetAddress.getAllByName(domain).map { it.hostAddress ?: "?" }
                    val latency = (System.nanoTime() - start) / 1_000_000L
                    results.add(LookupResult(domain, addrs, isBlocked, latency))
                } catch (e: Exception) {
                    val latency = (System.nanoTime() - start) / 1_000_000L
                    android.util.Log.w("DnsTools", "Batch DNS lookup failed for $domain", e)
                    results.add(LookupResult(domain, emptyList(), isBlocked, latency, lookupFailureMessage(e)))
                }
            }
            _state.update { it.copy(isBatchRunning = false, batchResults = results) }
        }
    }

    /** Run ping to a domain/IP. */
    fun runPing(target: String) {
        val safeTarget = normalizeNetworkDiagnosticTarget(target)
        if (safeTarget == null) {
            _state.update { it.copy(pingResult = "Enter a valid hostname or IP address.") }
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            _state.update { it.copy(isPinging = true, pingResult = "") }
            try {
                val result = withContext(Dispatchers.IO) {
                    val proc = Runtime.getRuntime().exec(arrayOf("ping", "-c", "4", "-W", "3", safeTarget))
                    val output = proc.inputStream.bufferedReader().readText()
                    if (!proc.waitFor(15, java.util.concurrent.TimeUnit.SECONDS)) {
                        proc.destroyForcibly()
                        output + "\n[Timed out after 15s]"
                    } else output
                }
                _state.update { it.copy(isPinging = false, pingResult = result) }
            } catch (e: Exception) {
                android.util.Log.w("DnsTools", "Ping failed for $safeTarget", e)
                _state.update { it.copy(isPinging = false, pingResult = "Ping could not complete. Check the target and network connection.") }
            }
        }
    }

    /** Run traceroute (uses tracepath, available without root). */
    fun runTraceroute(target: String) {
        val safeTarget = normalizeNetworkDiagnosticTarget(target)
        if (safeTarget == null) {
            _state.update { it.copy(pingResult = "Enter a valid hostname or IP address.") }
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            _state.update { it.copy(isPinging = true, pingResult = "") }
            try {
                val result = withContext(Dispatchers.IO) {
                    val proc = try {
                        Runtime.getRuntime().exec(arrayOf("tracepath", "-m", "15", safeTarget))
                    } catch (_: Exception) {
                        Runtime.getRuntime().exec(arrayOf("su", "-c", "traceroute -m 15 -w 2 $safeTarget"))
                    }
                    val output = proc.inputStream.bufferedReader().readText()
                    if (!proc.waitFor(30, java.util.concurrent.TimeUnit.SECONDS)) {
                        proc.destroyForcibly()
                        output + "\n[Timed out after 30s]"
                    } else {
                        output.ifBlank { proc.errorStream.bufferedReader().readText() }
                    }
                }
                _state.update { it.copy(isPinging = false, pingResult = result) }
            } catch (e: Exception) {
                android.util.Log.w("DnsTools", "Traceroute failed for $safeTarget", e)
                _state.update { it.copy(isPinging = false, pingResult = "Traceroute could not complete. Check the target and network connection.") }
            }
        }
    }
}

private fun lookupFailureMessage(error: Exception): String =
    when (error) {
        is java.net.UnknownHostException -> "DNS lookup failed. Check the domain or network connection."
        is SecurityException -> "DNS lookup blocked by device policy."
        else -> "DNS lookup failed. Try again."
    }
