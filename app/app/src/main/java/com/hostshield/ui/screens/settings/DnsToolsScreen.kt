package com.hostshield.ui.screens.settings

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.hostshield.data.preferences.AppPreferences
import com.hostshield.domain.BlocklistHolder
import com.hostshield.ui.theme.*
import com.topjohnwu.superuser.Shell
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.InetAddress
import javax.inject.Inject

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
    val batchTotal: Int = 0
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

enum class DnsToolsTab { LOOKUP, STATUS, CONFIG, DIAG }

@HiltViewModel
class DnsToolsViewModel @Inject constructor(
    private val prefs: AppPreferences,
    private val blocklist: BlocklistHolder
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
                val result = LookupResult(domain, emptyList(), isBlocked, latency, e.message ?: "Failed")
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
                    cacheEntries = cache
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
                    results.add(LookupResult(domain, emptyList(), isBlocked, latency, e.message ?: "Failed"))
                }
            }
            _state.update { it.copy(isBatchRunning = false, batchResults = results) }
        }
    }

    /** Run ping to a domain/IP. */
    fun runPing(target: String) {
        if (target.isBlank()) return
        viewModelScope.launch(Dispatchers.IO) {
            _state.update { it.copy(isPinging = true, pingResult = "") }
            try {
                val result = withContext(Dispatchers.IO) {
                    val proc = Runtime.getRuntime().exec(arrayOf("ping", "-c", "4", "-W", "3", target.trim()))
                    val output = proc.inputStream.bufferedReader().readText()
                    if (!proc.waitFor(15, java.util.concurrent.TimeUnit.SECONDS)) {
                        proc.destroyForcibly()
                        output + "\n[Timed out after 15s]"
                    } else output
                }
                _state.update { it.copy(isPinging = false, pingResult = result) }
            } catch (e: Exception) {
                _state.update { it.copy(isPinging = false, pingResult = "Ping failed: ${e.message}") }
            }
        }
    }

    /** Run traceroute (uses tracepath, available without root). */
    fun runTraceroute(target: String) {
        if (target.isBlank()) return
        viewModelScope.launch(Dispatchers.IO) {
            _state.update { it.copy(isPinging = true, pingResult = "") }
            try {
                val result = withContext(Dispatchers.IO) {
                    val proc = try {
                        Runtime.getRuntime().exec(arrayOf("tracepath", "-m", "15", target.trim()))
                    } catch (_: Exception) {
                        Runtime.getRuntime().exec(arrayOf("su", "-c", "traceroute -m 15 -w 2 ${target.trim()}"))
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
                _state.update { it.copy(isPinging = false, pingResult = "Traceroute failed: ${e.message}") }
            }
        }
    }
}

@Composable
fun DnsToolsScreen(
    viewModel: DnsToolsViewModel = hiltViewModel(),
    onBack: () -> Unit
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Column(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Filled.ArrowBack, "Back", tint = TextPrimary)
            }
            Text("DNS Tools", style = MaterialTheme.typography.titleLarge, color = TextPrimary,
                modifier = Modifier.weight(1f))
            IconButton(onClick = { viewModel.refreshStatus() }) {
                Icon(Icons.Filled.Refresh, "Refresh", tint = Teal)
            }
        }

        // Tabs
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            TabPill("Lookup", state.tab == DnsToolsTab.LOOKUP, Teal) { viewModel.setTab(DnsToolsTab.LOOKUP) }
            TabPill("Status", state.tab == DnsToolsTab.STATUS, Blue) { viewModel.setTab(DnsToolsTab.STATUS) }
            TabPill("Config", state.tab == DnsToolsTab.CONFIG, Yellow) { viewModel.setTab(DnsToolsTab.CONFIG) }
            TabPill("Diag", state.tab == DnsToolsTab.DIAG, Peach) { viewModel.setTab(DnsToolsTab.DIAG) }
        }

        Spacer(Modifier.height(12.dp))

        when (state.tab) {
            DnsToolsTab.LOOKUP -> LookupTab(state, viewModel)
            DnsToolsTab.STATUS -> StatusTab(state, viewModel)
            DnsToolsTab.CONFIG -> ConfigTab(state, viewModel)
            DnsToolsTab.DIAG -> DiagTab(state, viewModel)
        }
    }
}

@Composable
private fun LookupTab(state: DnsToolsState, viewModel: DnsToolsViewModel) {
    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
        // Domain input
        OutlinedTextField(
            value = state.lookupDomain,
            onValueChange = { viewModel.setLookupDomain(it) },
            placeholder = { Text("example.com", color = TextDim) },
            leadingIcon = { Icon(Icons.Filled.Dns, null, tint = TextDim) },
            trailingIcon = {
                if (state.isLookingUp) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Teal, strokeWidth = 2.dp)
                } else {
                    IconButton(onClick = { viewModel.performLookup() }) {
                        Icon(Icons.Filled.Send, "Lookup", tint = Teal)
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true, shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Teal, unfocusedBorderColor = Surface3,
                cursorColor = Teal, focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary
            )
        )

        Spacer(Modifier.height(12.dp))

        // Results
        LazyColumn {
            items(state.lookupResults) { result ->
                Surface(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                    shape = RoundedCornerShape(10.dp),
                    color = if (result.isBlocked) Red.copy(alpha = 0.06f) else Surface2
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(modifier = Modifier.size(6.dp).clip(CircleShape)
                                .background(if (result.isBlocked) Red else Green))
                            Spacer(Modifier.width(8.dp))
                            Text(
                                result.domain,
                                color = TextPrimary, fontWeight = FontWeight.Medium,
                                fontFamily = FontFamily.Monospace, fontSize = 13.sp
                            )
                            Spacer(Modifier.weight(1f))
                            if (result.isBlocked) {
                                Surface(shape = RoundedCornerShape(4.dp), color = Red.copy(alpha = 0.15f)) {
                                    Text("BLOCKED", modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
                                        color = Red, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                            Spacer(Modifier.width(8.dp))
                            Text("${result.latencyMs}ms", color = TextDim, fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace)
                        }
                        if (result.error.isNotEmpty()) {
                            Text(result.error, color = Red, fontSize = 11.sp)
                        }
                        for (addr in result.addresses) {
                            Text(addr, color = Teal, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusTab(state: DnsToolsState, viewModel: DnsToolsViewModel) {
    LazyColumn(
        modifier = Modifier.padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Current DNS servers
        item {
            GlassInfoCard("Active DNS Servers") {
                if (state.currentDns.isEmpty()) {
                    Text("No DNS servers detected", color = TextDim, fontSize = 12.sp)
                } else {
                    for (dns in state.currentDns) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(Green))
                            Spacer(Modifier.width(8.dp))
                            Text(dns, color = Teal, fontFamily = FontFamily.Monospace, fontSize = 13.sp)
                        }
                        Spacer(Modifier.height(4.dp))
                    }
                }
            }
        }

        // Private DNS status
        item {
            GlassInfoCard("Private DNS") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Mode: ", color = TextDim, fontSize = 12.sp)
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = when (state.privateDnsMode) {
                            "off" -> Green.copy(alpha = 0.12f)
                            "opportunistic" -> Yellow.copy(alpha = 0.12f)
                            "hostname" -> Blue.copy(alpha = 0.12f)
                            else -> Surface3
                        }
                    ) {
                        Text(
                            state.privateDnsMode,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            color = when (state.privateDnsMode) {
                                "off" -> Green; "opportunistic" -> Yellow
                                "hostname" -> Blue; else -> TextDim
                            },
                            fontSize = 11.sp, fontWeight = FontWeight.SemiBold, fontFamily = FontFamily.Monospace
                        )
                    }
                }
                if (state.privateDnsProvider.isNotBlank() && state.privateDnsProvider != "null") {
                    Spacer(Modifier.height(4.dp))
                    Text("Provider: ${state.privateDnsProvider}", color = TextSecondary, fontSize = 11.sp)
                }
            }
        }

        // Blocklist stats
        item {
            GlassInfoCard("Blocklist") {
                Text("${state.blocklistSize} domains loaded", color = Teal, fontSize = 13.sp,
                    fontWeight = FontWeight.Medium)
            }
        }

        // DNS cache
        item {
            GlassInfoCard("DNS Cache (${state.cacheEntries.size} entries)") {
                Row {
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = { viewModel.flushDnsCache() }, enabled = !state.isFlushing) {
                        if (state.isFlushing) {
                            CircularProgressIndicator(Modifier.size(14.dp), color = Red, strokeWidth = 2.dp)
                        } else {
                            Text("Flush Cache", color = Red, fontSize = 11.sp)
                        }
                    }
                }
                for (entry in state.cacheEntries.take(20)) {
                    Text(entry.hostname, color = TextPrimary, fontSize = 11.sp, fontFamily = FontFamily.Monospace,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(entry.addresses, color = TextDim, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                    Spacer(Modifier.height(4.dp))
                }
                if (state.cacheEntries.isEmpty()) {
                    Text("No cached entries", color = TextDim, fontSize = 11.sp)
                }
            }
        }
    }
}

@Composable
private fun ConfigTab(state: DnsToolsState, viewModel: DnsToolsViewModel) {
    LazyColumn(
        modifier = Modifier.padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // DoH toggle
        item {
            GlassInfoCard("DNS-over-HTTPS") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Encrypted DNS", color = TextPrimary, fontWeight = FontWeight.Medium)
                        Text("Route DNS queries over HTTPS for privacy", color = TextDim, fontSize = 11.sp)
                    }
                    Switch(
                        checked = state.dohEnabled,
                        onCheckedChange = { viewModel.toggleDoh(it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Teal, checkedTrackColor = Teal.copy(alpha = 0.25f),
                            uncheckedThumbColor = TextDim, uncheckedTrackColor = Surface3
                        )
                    )
                }
            }
        }

        // DoH provider selection
        item {
            GlassInfoCard("DoH Provider") {
                val providers = listOf(
                    "cloudflare" to "Cloudflare (1.1.1.1)",
                    "google" to "Google (8.8.8.8)",
                    "quad9" to "Quad9 (9.9.9.9)",
                    "adguard" to "AdGuard DNS",
                    "mullvad" to "Mullvad DNS"
                )
                for ((key, label) in providers) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = state.dohProvider == key,
                            onClick = { viewModel.setDohProvider(key) },
                            colors = RadioButtonDefaults.colors(selectedColor = Teal, unselectedColor = TextDim)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(label, color = if (state.dohProvider == key) TextPrimary else TextSecondary,
                            fontSize = 13.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun DiagTab(state: DnsToolsState, viewModel: DnsToolsViewModel) {
    var pingTarget by remember { mutableStateOf("") }

    LazyColumn(
        modifier = Modifier.padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Batch domain test
        item {
            GlassInfoCard("Batch Domain Test") {
                Text("Paste domains (one per line) to test blocklist + resolution:", color = TextDim, fontSize = 11.sp)
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(
                    value = state.batchInput,
                    onValueChange = { viewModel.setBatchInput(it) },
                    placeholder = { Text("example.com\nad.doubleclick.net\ngoogle.com", color = TextDim) },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 80.dp, max = 140.dp),
                    maxLines = 10,
                    shape = RoundedCornerShape(8.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Teal, unfocusedBorderColor = Surface3,
                        cursorColor = Teal, focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary
                    )
                )
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Button(
                        onClick = { viewModel.runBatchTest() },
                        enabled = !state.isBatchRunning && state.batchInput.isNotBlank(),
                        colors = ButtonDefaults.buttonColors(containerColor = Teal),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        if (state.isBatchRunning) {
                            CircularProgressIndicator(Modifier.size(14.dp), color = Color.White, strokeWidth = 2.dp)
                            Spacer(Modifier.width(6.dp))
                            Text("${state.batchProgress}/${state.batchTotal}", fontSize = 12.sp)
                        } else {
                            Text("Test All", fontSize = 12.sp)
                        }
                    }
                    if (state.batchResults.isNotEmpty()) {
                        Spacer(Modifier.width(12.dp))
                        val blocked = state.batchResults.count { it.isBlocked }
                        val failed = state.batchResults.count { it.error.isNotEmpty() }
                        Text("$blocked blocked, $failed failed, ${state.batchResults.size - blocked - failed} allowed",
                            color = TextDim, fontSize = 10.sp)
                    }
                }
            }
        }

        // Batch results
        if (state.batchResults.isNotEmpty()) {
            items(state.batchResults) { result ->
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    color = if (result.isBlocked) Red.copy(alpha = 0.06f) else Surface2.copy(alpha = 0.5f)
                ) {
                    Row(
                        modifier = Modifier.padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(modifier = Modifier.size(6.dp).clip(CircleShape)
                            .background(if (result.isBlocked) Red else if (result.error.isNotEmpty()) Yellow else Green))
                        Spacer(Modifier.width(8.dp))
                        Text(result.domain, color = TextPrimary, fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace, modifier = Modifier.weight(1f),
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("${result.latencyMs}ms", color = TextDim, fontSize = 9.sp, fontFamily = FontFamily.Monospace)
                    }
                }
            }
        }

        // Ping
        item {
            GlassInfoCard("Ping / Traceroute") {
                OutlinedTextField(
                    value = pingTarget,
                    onValueChange = { pingTarget = it },
                    placeholder = { Text("8.8.8.8 or google.com", color = TextDim) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true, shape = RoundedCornerShape(8.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Peach, unfocusedBorderColor = Surface3,
                        cursorColor = Peach, focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary
                    )
                )
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { viewModel.runPing(pingTarget) },
                        enabled = !state.isPinging && pingTarget.isNotBlank(),
                        colors = ButtonDefaults.buttonColors(containerColor = Peach),
                        shape = RoundedCornerShape(8.dp)
                    ) { Text("Ping", fontSize = 12.sp) }
                    OutlinedButton(
                        onClick = { viewModel.runTraceroute(pingTarget) },
                        enabled = !state.isPinging && pingTarget.isNotBlank(),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Blue)
                    ) { Text("Traceroute", fontSize = 12.sp) }
                    if (state.isPinging) {
                        CircularProgressIndicator(Modifier.size(18.dp).align(Alignment.CenterVertically),
                            color = Peach, strokeWidth = 2.dp)
                    }
                }
                if (state.pingResult.isNotBlank()) {
                    Spacer(Modifier.height(8.dp))
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(6.dp), color = Surface1
                    ) {
                        Text(
                            state.pingResult,
                            modifier = Modifier.padding(8.dp),
                            color = Teal, fontSize = 10.sp, fontFamily = FontFamily.Monospace,
                            lineHeight = 14.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun GlassInfoCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = Surface2
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(title, color = TextDim, fontSize = 10.sp, fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp)
            Spacer(Modifier.height(8.dp))
            content()
        }
    }
}

@Composable
private fun TabPill(label: String, selected: Boolean, accent: Color, onClick: () -> Unit) {
    Surface(
        onClick = onClick, shape = RoundedCornerShape(10.dp),
        color = if (selected) accent.copy(alpha = 0.15f) else Surface2
    ) {
        Text(label, modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
            color = if (selected) accent else TextDim, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    }
}
