package com.hostshield.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.hostshield.ui.screens.home.GlassCard
import com.hostshield.ui.theme.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.net.InetAddress
import java.util.UUID
import javax.inject.Inject

data class LeakTestResult(
    val testDomain: String,
    val resolvedTo: List<String>,
    val isLeaking: Boolean, // true = resolved outside HostShield (bad)
    val latencyMs: Long
)

data class LeakTestState(
    val isRunning: Boolean = false,
    val progress: String = "",
    val results: List<LeakTestResult> = emptyList(),
    val overallPass: Boolean? = null, // null = not run yet
    val blockedTestDomain: String? = null,
    val blockedCorrectly: Boolean? = null
)

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
        viewModelScope.launch(Dispatchers.IO) {
            _state.update { it.copy(isRunning = true, progress = "Testing DNS resolution...", results = emptyList(), overallPass = null) }

            val results = mutableListOf<LeakTestResult>()

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
                if (blocklist.isBlocked(domain)) {
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
            try {
                val addrs = InetAddress.getAllByName("connectivitycheck.gstatic.com").map { it.hostAddress ?: "?" }
                val latency = (System.nanoTime() - start) / 1_000_000L
                results.add(LeakTestResult("connectivitycheck.gstatic.com", addrs, isLeaking = false, latencyMs = latency))
            } catch (e: Exception) {
                val latency = (System.nanoTime() - start) / 1_000_000L
                android.util.Log.w("DnsLeakTest", "Connectivity DNS check failed", e)
                results.add(LeakTestResult("connectivitycheck.gstatic.com", listOf("Connectivity check failed"), isLeaking = true, latencyMs = latency))
            }

            val leaking = results.any { it.isLeaking }
            _state.update {
                it.copy(
                    isRunning = false,
                    progress = "",
                    results = results,
                    overallPass = !leaking,
                    blockedTestDomain = blockedTest,
                    blockedCorrectly = blockedCorrectly
                )
            }
        }
    }
}

@Composable
fun DnsLeakTestScreen(
    viewModel: DnsLeakTestViewModel = hiltViewModel(),
    onBack: () -> Unit
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Column(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = TextPrimary) }
            Column(modifier = Modifier.weight(1f)) {
                Text("DNS leak test", style = MaterialTheme.typography.titleLarge, color = TextPrimary)
                Text("Verify DNS queries go through HostShield", color = TextDim, fontSize = 11.sp)
            }
            Button(
                onClick = { viewModel.runTest() },
                enabled = !state.isRunning,
                colors = ButtonDefaults.buttonColors(containerColor = Teal),
                shape = RoundedCornerShape(10.dp)
            ) {
                if (state.isRunning) {
                    CircularProgressIndicator(Modifier.size(14.dp), color = Color.Black, strokeWidth = 2.dp)
                    Spacer(Modifier.width(6.dp))
                }
                Text(if (state.isRunning) "Testing" else "Run test", fontSize = 12.sp)
            }
        }

        if (state.progress.isNotEmpty()) {
            Text(state.progress, color = TextDim, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 20.dp))
        }

        LazyColumn(
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Overall result
            state.overallPass?.let { pass ->
                item {
                    GlassCard(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.padding(20.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier.size(48.dp).clip(CircleShape)
                                    .background(if (pass) Green.copy(alpha = 0.12f) else Red.copy(alpha = 0.12f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    if (pass) Icons.Filled.VerifiedUser else Icons.Filled.GppBad,
                                    null,
                                    tint = if (pass) Green else Red,
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                            Spacer(Modifier.width(16.dp))
                            Column {
                                Text(
                                    if (pass) "No DNS Leaks Detected" else "Potential DNS Leak",
                                    color = if (pass) Green else Red,
                                    fontWeight = FontWeight.Bold, fontSize = 16.sp
                                )
                                Text(
                                    if (pass) "Your DNS queries are routed through HostShield"
                                    else "Some queries may bypass HostShield filtering",
                                    color = TextSecondary, fontSize = 12.sp
                                )
                            }
                        }
                    }
                }

                // Blocked domain verification
                if (state.blockedCorrectly != null) {
                    item {
                        GlassCard(modifier = Modifier.fillMaxWidth()) {
                            Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    if (state.blockedCorrectly == true) Icons.Filled.CheckCircle else Icons.Filled.Warning,
                                    null,
                                    tint = if (state.blockedCorrectly == true) Green else Yellow,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(Modifier.width(10.dp))
                                Column {
                                    Text(
                                        if (state.blockedCorrectly == true) "Blocking verified"
                                        else "Blocking may not be active",
                                        color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Medium
                                    )
                                    Text(
                                        "${state.blockedTestDomain} ${if (state.blockedCorrectly == true) "correctly blocked" else "was not blocked"}",
                                        color = TextDim, fontSize = 11.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Individual results
            items(state.results) { result ->
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    color = if (result.isLeaking) Red.copy(alpha = 0.06f) else Surface2
                ) {
                    Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier.size(6.dp).clip(CircleShape)
                                .background(if (result.isLeaking) Red else Green)
                        )
                        Spacer(Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                result.testDomain, color = TextPrimary, fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace, maxLines = 1
                            )
                            if (result.resolvedTo.isNotEmpty()) {
                                result.resolvedTo.forEach { addr ->
                                    Text(addr, color = TextDim, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                                }
                            } else {
                                Text("NXDOMAIN (expected)", color = Green, fontSize = 10.sp)
                            }
                        }
                        Text("${result.latencyMs}ms", color = TextDim, fontSize = 9.sp, fontFamily = FontFamily.Monospace)
                    }
                }
            }

            if (state.results.isEmpty() && !state.isRunning) {
                item {
                    GlassCard(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier.padding(32.dp).fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(Icons.Filled.VerifiedUser, null, tint = TextDim, modifier = Modifier.size(40.dp))
                            Spacer(Modifier.height(12.dp))
                            Text("Tap Run test to check for DNS leaks", color = TextSecondary, fontSize = 13.sp)
                            Spacer(Modifier.height(4.dp))
                            Text("Tests random domains, blocked domains, and connectivity", color = TextDim, fontSize = 11.sp)
                        }
                    }
                }
            }

            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}
