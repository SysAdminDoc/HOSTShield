package com.hostshield.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
import com.hostshield.ui.screens.home.GlassCard
import com.hostshield.ui.theme.*
import com.hostshield.util.DnsBenchmark
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DnsBenchmarkViewModel @Inject constructor(
    private val benchmark: DnsBenchmark,
    private val prefs: AppPreferences,
) : ViewModel() {

    var isRunning by mutableStateOf(false)
        private set
    var progress by mutableStateOf(0f)
        private set
    var results by mutableStateOf<List<DnsBenchmark.BenchmarkResult>>(emptyList())
        private set

    fun runBenchmark() {
        if (isRunning) return
        viewModelScope.launch {
            isRunning = true
            progress = 0f
            results = emptyList()
            val custom = prefs.customUpstreamDns.first()
                .split(",")
                .map { it.trim() }
                .filter { it.isNotBlank() }
            results = benchmark.runBenchmark(
                includeCustom = custom,
                onProgress = { done, total -> progress = done.toFloat() / total }
            )
            isRunning = false
        }
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
            .background(Color.Black)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = TextPrimary)
            }
            Text("DNS Benchmark", style = MaterialTheme.typography.headlineMedium, color = TextPrimary)
        }

        Text(
            "Test latency to popular DNS resolvers. Results are sorted by average response time.",
            color = TextDim, fontSize = 12.sp, lineHeight = 16.sp,
            modifier = Modifier.padding(horizontal = 4.dp),
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
                Text("Run Benchmark", fontWeight = FontWeight.SemiBold)
            }
        }

        if (viewModel.isRunning) {
            LinearProgressIndicator(
                progress = { viewModel.progress },
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(4.dp)),
                color = Teal,
                trackColor = Surface3,
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
        }

        Spacer(Modifier.height(24.dp))
    }
}
