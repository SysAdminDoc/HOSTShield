package com.hostshield.ui.screens.settings

import android.util.Log
import androidx.compose.runtime.*
import androidx.compose.runtime.mutableFloatStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hostshield.data.preferences.AppPreferences
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
