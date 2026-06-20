package com.hostshield.ui.screens.settings

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hostshield.data.preferences.AppPreferences
import com.hostshield.ui.components.HostShieldBackHeader
import com.hostshield.ui.components.HostShieldEmptyState
import com.hostshield.ui.components.HostShieldLoadingState
import com.hostshield.ui.components.HostShieldStatusBanner
import com.hostshield.ui.screens.home.GlassCard
import com.hostshield.ui.theme.*
import com.hostshield.util.DnsBenchmark
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val DNS_BENCHMARK_SCREEN_TAG = "DnsBenchmarkScreen"

@HiltViewModel
class DnsBenchmarkViewModel @Inject constructor(
    private val benchmark: DnsBenchmark,
    private val prefs: AppPreferences,
) : ViewModel() {

    var isRunning by mutableStateOf(false)
        private set
    var progress by mutableFloatStateOf(0f)
        private set
    var results by mutableStateOf<List<DnsBenchmark.BenchmarkResult>>(emptyList())
        private set
    var statusMessage by mutableStateOf<String?>(null)
        private set
    var statusIsError by mutableStateOf(false)
        private set

    fun runBenchmark() {
        if (isRunning) return
        viewModelScope.launch {
            isRunning = true
            progress = 0f
            results = emptyList()
            statusMessage = null
            statusIsError = false
            try {
                val custom = prefs.customUpstreamDns.first()
                    .split(",")
                    .map { it.trim() }
                    .filter { it.isNotBlank() }
                val benchmarkResults = benchmark.runBenchmark(
                    includeCustom = custom,
                    onProgress = { done, total ->
                        progress = if (total > 0) done.toFloat() / total else 0f
                    }
                )
                results = benchmarkResults
                if (benchmarkResults.all { !it.isReachable }) {
                    statusMessage = "No resolver responded from this network. Check connectivity, VPN policy, or captive portal state."
                    statusIsError = true
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(DNS_BENCHMARK_SCREEN_TAG, "DNS benchmark failed", e)
                statusMessage = "Benchmark could not complete. Check the network connection and try again."
                statusIsError = true
            } finally {
                isRunning = false
            }
        }
    }

    fun clearStatus() {
        statusMessage = null
        statusIsError = false
    }
}

@Composable
fun DnsBenchmarkScreen(
    onBack: () -> Unit,
    viewModel: DnsBenchmarkViewModel = hiltViewModel(),
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Black)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        HostShieldBackHeader(
            title = "DNS benchmark",
            subtitle = "Compare resolver latency and reliability",
            onBack = onBack,
            horizontalPadding = 0.dp,
            verticalPadding = 0.dp,
        )

        // Run button
        Button(
            onClick = { viewModel.runBenchmark() },
            enabled = !viewModel.isRunning,
            shape = RoundedCornerShape(10.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Teal, contentColor = Color.Black),
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (viewModel.isRunning) {
                CircularProgressIndicator(Modifier.size(16.dp), color = Color.Black, strokeWidth = 2.dp)
                Spacer(Modifier.width(8.dp))
                Text("Testing... ${(viewModel.progress * 100).toInt()}%", fontWeight = FontWeight.SemiBold)
            } else {
                Icon(Icons.Filled.Speed, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Run benchmark", fontWeight = FontWeight.SemiBold)
            }
        }

        if (viewModel.isRunning) {
            HostShieldLoadingState(
                title = "Benchmark running",
                message = "Testing resolver response time. ${(viewModel.progress * 100).toInt()}% complete.",
                accent = Teal,
            )
            LinearProgressIndicator(
                progress = { viewModel.progress },
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(4.dp)),
                color = Teal,
                trackColor = Surface3,
            )
        }

        viewModel.statusMessage?.let { message ->
            HostShieldStatusBanner(
                icon = if (viewModel.statusIsError) Icons.Filled.Error else Icons.Filled.CheckCircle,
                title = if (viewModel.statusIsError) "Benchmark needs attention" else "Benchmark ready",
                message = message,
                accent = if (viewModel.statusIsError) Yellow else Teal,
                onDismiss = { viewModel.clearStatus() },
            )
        }

        // Results
        if (viewModel.results.isNotEmpty()) {
            val best = viewModel.results.first().avgLatencyMs

            viewModel.results.forEachIndexed { index, result ->
                val barColor = when {
                    !result.isReachable -> Red
                    result.avgLatencyMs <= 30 -> Green
                    result.avgLatencyMs <= 80 -> Teal
                    result.avgLatencyMs <= 150 -> Yellow
                    else -> Peach
                }

                GlassCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            // Rank badge
                            Box(
                                modifier = Modifier.size(26.dp)
                                    .background(
                                        if (index == 0) Teal.copy(alpha = 0.15f) else Surface2,
                                        RoundedCornerShape(6.dp),
                                    ),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    "#${index + 1}",
                                    color = if (index == 0) Teal else TextDim,
                                    fontSize = 10.sp, fontWeight = FontWeight.Bold,
                                )
                            }
                            Spacer(Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(result.serverName, color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                                Text(result.serverIp, color = TextDim, fontSize = 11.sp)
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                if (result.isReachable) {
                                    Text(
                                        "${result.avgLatencyMs}ms",
                                        color = barColor, fontSize = 18.sp, fontWeight = FontWeight.Bold,
                                    )
                                } else {
                                    Text("Unreachable", color = Red, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                                }
                            }
                        }
                        if (result.isReachable) {
                            Spacer(Modifier.height(8.dp))
                            // Latency bar
                            val maxLatency = viewModel.results.filter { it.isReachable }.maxOfOrNull { it.avgLatencyMs } ?: 1
                            val fraction = result.avgLatencyMs.toFloat() / maxLatency
                            Box(
                                modifier = Modifier.fillMaxWidth().height(4.dp)
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(Surface2),
                            ) {
                                Box(
                                    modifier = Modifier.fillMaxHeight()
                                        .fillMaxWidth(fraction.coerceIn(0.02f, 1f))
                                        .clip(RoundedCornerShape(2.dp))
                                        .background(barColor),
                                )
                            }
                            Spacer(Modifier.height(6.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text("Min: ${result.minLatencyMs}ms", color = TextDim, fontSize = 10.sp)
                                Text("Max: ${result.maxLatencyMs}ms", color = TextDim, fontSize = 10.sp)
                                Text("Success: ${(result.successRate * 100).toInt()}%", color = TextDim, fontSize = 10.sp)
                            }
                        }
                    }
                }
            }
        } else if (!viewModel.isRunning) {
            HostShieldEmptyState(
                icon = Icons.Filled.Speed,
                title = "No benchmark results yet",
                message = "Run a short latency test to see which resolver is fastest from this device and network.",
                accent = Teal,
                primaryActionLabel = "Run benchmark",
                onPrimaryAction = { viewModel.runBenchmark() },
            )
        }

        Spacer(Modifier.height(24.dp))
    }
}
