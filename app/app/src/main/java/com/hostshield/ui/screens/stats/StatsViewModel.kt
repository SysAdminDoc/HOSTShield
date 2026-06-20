package com.hostshield.ui.screens.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hostshield.data.database.HourlyStat
import com.hostshield.data.database.ThreatIntelDailyImpact
import com.hostshield.data.database.ThreatIntelFeedImpact
import com.hostshield.data.database.ThreatIntelTopApp
import com.hostshield.data.database.ThreatIntelTopDomain
import com.hostshield.data.database.TopApp
import com.hostshield.data.database.TopHostname
import com.hostshield.data.model.BlockStats
import com.hostshield.data.repository.HostShieldRepository
import com.hostshield.service.ThreatIntelManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class StatsUiState(
    val totalBlocked: Int = 0,
    val totalQueries: Int = 0,
    val blockedToday: Int = 0,
    val queriesToday: Int = 0,
    val blockRate: Float = 0f,
    val hourlyBlocked: List<HourlyStat> = emptyList(),
    val dailyStats: List<BlockStats> = emptyList(),
    val topDomains: List<TopHostname> = emptyList(),
    val topApps: List<TopApp> = emptyList(),
    val mostQueried: List<TopHostname> = emptyList(),
    val dailyTrend: List<com.hostshield.data.database.DailyBreakdown> = emptyList(),
    val hourlyLatency: List<com.hostshield.data.database.HourlyLatency> = emptyList(),
    val queryTypeDistribution: List<com.hostshield.data.database.QueryTypeStat> = emptyList(),
    // DNS Cache stats
    val cacheSize: Int = 0,
    val cacheHitRate: Float = 0f,
    val cacheHits: Long = 0,
    val cacheMisses: Long = 0,
    val cacheEvictions: Long = 0,
    // v5.0: New cache stats
    val cacheStaleHits: Long = 0,
    val cachePrefetchTriggers: Long = 0,
    // VPN Health
    val vpnUptimeHours: Float = 0f,
    val vpnRebuilds: Int = 0,
    val vpnFdErrors: Int = 0,
    val vpnDroppedQueries: Int = 0,
    val vpnTotalQueries: Int = 0,
    val threatIntelFeeds: List<ThreatIntelFeedHealthUi> = emptyList(),
    val threatIntelDomainCount: Int = 0,
    val threatIntelIpCidrCount: Int = 0,
    val threatIntelLastUpdated: Long = 0L,
    val isRefreshingThreatIntel: Boolean = false,
    val threatIntelRefreshMessage: String? = null,
    val threatIntelFeedImpact: List<ThreatIntelFeedImpact> = emptyList(),
    val threatIntelTopDomains: List<ThreatIntelTopDomain> = emptyList(),
    val threatIntelTopApps: List<ThreatIntelTopApp> = emptyList(),
    val threatIntelDailyImpact: List<ThreatIntelDailyImpact> = emptyList(),
    val latencyP50: Float = 0f,
    val latencyP95: Float = 0f,
    val latencyP99: Float = 0f
) {
    val avgLatencyMs: Double
        get() = if (hourlyLatency.isNotEmpty()) hourlyLatency.map { it.avgMs }.average() else 0.0

    val peakLatencyMs: Int
        get() = hourlyLatency.maxOfOrNull { it.maxMs } ?: 0

    val queryTypeTotal: Int
        get() = queryTypeDistribution.sumOf { it.cnt }.coerceAtLeast(1)

    enum class VpnHealth(val label: String) {
        HEALTHY("Healthy"),
        MINOR_DROPS("Minor drops"),
        UNSTABLE("Unstable"),
        DEGRADED("Degraded"),
        NO_DATA("No data")
    }

    val vpnHealth: VpnHealth
        get() = when {
            vpnFdErrors > 5 -> VpnHealth.DEGRADED
            vpnRebuilds > 10 -> VpnHealth.UNSTABLE
            vpnDroppedQueries > 0 -> VpnHealth.MINOR_DROPS
            vpnUptimeHours > 0 -> VpnHealth.HEALTHY
            else -> VpnHealth.NO_DATA
        }
}

enum class ThreatIntelFeedStatus {
    HEALTHY,
    STALE,
    DEGRADED,
    FAILED,
    NEVER_REFRESHED
}

data class ThreatIntelFeedHealthUi(
    val name: String,
    val status: ThreatIntelFeedStatus,
    val lastSuccess: Long,
    val lastFailure: Long,
    val httpStatus: Int,
    val entryCount: Int,
    val bytesDownloaded: Long,
    val sha256Short: String,
    val consecutiveFailures: Int,
    val lastError: String
) {
    companion object {
        private const val STALE_AFTER_MS = 48 * 60 * 60 * 1000L

        fun fromHealth(
            health: ThreatIntelManager.FeedHealth,
            nowMs: Long = System.currentTimeMillis()
        ): ThreatIntelFeedHealthUi {
            val status = when {
                health.lastSuccess == 0L && health.lastFailure == 0L -> ThreatIntelFeedStatus.NEVER_REFRESHED
                health.lastSuccess == 0L -> ThreatIntelFeedStatus.FAILED
                health.consecutiveFailures > 0 && health.lastFailure >= health.lastSuccess -> ThreatIntelFeedStatus.DEGRADED
                nowMs - health.lastSuccess > STALE_AFTER_MS -> ThreatIntelFeedStatus.STALE
                else -> ThreatIntelFeedStatus.HEALTHY
            }
            return ThreatIntelFeedHealthUi(
                name = health.name,
                status = status,
                lastSuccess = health.lastSuccess,
                lastFailure = health.lastFailure,
                httpStatus = health.httpStatus,
                entryCount = health.entryCount,
                bytesDownloaded = health.bytesDownloaded,
                sha256Short = health.sha256.take(12),
                consecutiveFailures = health.consecutiveFailures,
                lastError = health.lastError
            )
        }
    }
}

@HiltViewModel
class StatsViewModel @Inject constructor(
    private val repository: HostShieldRepository,
    private val vpnStabilityDao: com.hostshield.data.database.VpnStabilityDao,
    private val threatIntelManager: ThreatIntelManager
) : ViewModel() {
    private val _uiState = MutableStateFlow(StatsUiState())
    val uiState: StateFlow<StatsUiState> = _uiState.asStateFlow()

    private val todayStart = java.time.LocalDate.now()
        .atStartOfDay(java.time.ZoneId.systemDefault())
        .toInstant().toEpochMilli()

    private val weekStart = todayStart - (7 * 24 * 60 * 60 * 1000L)
    private val last24hStart = System.currentTimeMillis() - (24 * 60 * 60 * 1000L)

    init {
        viewModelScope.launch { repository.getTotalBlocked().collect { t -> _uiState.update { it.copy(totalBlocked = t ?: 0) } } }
        viewModelScope.launch { repository.getBlockedCountSince(todayStart).collect { c -> _uiState.update { it.copy(blockedToday = c) } } }
        viewModelScope.launch {
            repository.getTotalCountSince(todayStart).collect { c ->
                _uiState.update {
                    val rate = if (c > 0) it.blockedToday.toFloat() / c else 0f
                    it.copy(queriesToday = c, blockRate = rate)
                }
            }
        }
        viewModelScope.launch { repository.getRecentStats(14).collect { s -> _uiState.update { it.copy(dailyStats = s) } } }
        viewModelScope.launch { repository.getTopBlocked(15).collect { t -> _uiState.update { it.copy(topDomains = t) } } }
        viewModelScope.launch { repository.getTopBlockedApps(10).collect { a -> _uiState.update { it.copy(topApps = a) } } }
        viewModelScope.launch { repository.getHourlyBlocked(todayStart).collect { h -> _uiState.update { it.copy(hourlyBlocked = h) } } }
        viewModelScope.launch { repository.getMostQueriedDomains(weekStart, 15).collect { m -> _uiState.update { it.copy(mostQueried = m) } } }
        viewModelScope.launch { repository.getDailyBreakdown(weekStart).collect { d -> _uiState.update { it.copy(dailyTrend = d) } } }
        viewModelScope.launch { repository.getHourlyLatency(todayStart).collect { l -> _uiState.update { it.copy(hourlyLatency = l) } } }
        viewModelScope.launch {
            repository.getLatencyValues(weekStart).collect { rows ->
                if (rows.isNotEmpty()) {
                    val values = rows.map { it.value }
                    _uiState.update {
                        it.copy(
                            latencyP50 = percentile(values, 50),
                            latencyP95 = percentile(values, 95),
                            latencyP99 = percentile(values, 99)
                        )
                    }
                }
            }
        }
        viewModelScope.launch { repository.getQueryTypeDistribution(weekStart).collect { d -> _uiState.update { it.copy(queryTypeDistribution = d) } } }
        viewModelScope.launch { repository.getThreatIntelFeedImpact(last24hStart, weekStart).collect { impact -> _uiState.update { it.copy(threatIntelFeedImpact = impact) } } }
        viewModelScope.launch { repository.getThreatIntelTopDomains(weekStart).collect { domains -> _uiState.update { it.copy(threatIntelTopDomains = domains) } } }
        viewModelScope.launch { repository.getThreatIntelTopApps(weekStart).collect { apps -> _uiState.update { it.copy(threatIntelTopApps = apps) } } }
        viewModelScope.launch { repository.getThreatIntelDailyImpact(weekStart).collect { trend -> _uiState.update { it.copy(threatIntelDailyImpact = trend) } } }
        pollCacheStats()
        loadVpnStability()
        loadThreatIntelHealth()
    }

    /** Poll DNS cache stats from DnsVpnService every 5 seconds. */
    private fun pollCacheStats() {
        viewModelScope.launch {
            while (currentCoroutineContext()[Job]?.isActive != false) {
                val stats = com.hostshield.service.DnsVpnService.currentCacheStats
                if (stats != null) {
                    _uiState.update { it.copy(
                        cacheSize = stats.size + stats.negativeSize + stats.failureSize,
                        cacheHitRate = stats.hitRate,
                        cacheHits = stats.hits,
                        cacheMisses = stats.misses,
                        cacheEvictions = stats.evictions,
                        cacheStaleHits = stats.staleHits,
                        cachePrefetchTriggers = stats.prefetchTriggers
                    ) }
                }
                val dropped = com.hostshield.service.DnsVpnService.currentDroppedQueries
                if (dropped > 0) {
                    _uiState.update { it.copy(vpnDroppedQueries = dropped) }
                }
                delay(5_000)
            }
        }
    }

    /** Load aggregated VPN stability from Room (last 7 days). */
    private fun loadVpnStability() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val sevenDaysAgo = java.time.LocalDate.now().minusDays(7)
                    .format(java.time.format.DateTimeFormatter.ISO_LOCAL_DATE)
                val agg = vpnStabilityDao.getAggregated(sevenDaysAgo)
                _uiState.update { it.copy(
                    vpnUptimeHours = agg.totalUptime / 3_600_000f,
                    vpnRebuilds = agg.totalRebuilds,
                    vpnFdErrors = agg.totalErrors,
                    vpnDroppedQueries = agg.totalDropped,
                    vpnTotalQueries = agg.totalQueries
                ) }
            } catch (_: Exception) { }
        }
    }

    private fun loadThreatIntelHealth() {
        viewModelScope.launch(Dispatchers.IO) {
            threatIntelManager.loadCached()
            syncThreatIntelHealth()
        }
    }

    fun refreshThreatIntelFeeds() {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update {
                it.copy(
                    isRefreshingThreatIntel = true,
                    threatIntelRefreshMessage = "Refreshing threat feeds..."
                )
            }
            val success = threatIntelManager.refreshFeedsAndPersist()
            syncThreatIntelHealth()
            _uiState.update {
                it.copy(
                    isRefreshingThreatIntel = false,
                    threatIntelRefreshMessage = if (success) {
                        "Threat feeds refreshed."
                    } else {
                        "Threat feed refresh degraded; check failed feeds."
                    }
                )
            }
        }
    }

    private fun percentile(sorted: List<Float>, p: Int): Float {
        val idx = (sorted.size * p / 100).coerceIn(0, sorted.size - 1)
        return sorted[idx]
    }

    private fun syncThreatIntelHealth() {
        val now = System.currentTimeMillis()
        _uiState.update {
            it.copy(
                threatIntelFeeds = threatIntelManager.getFeedHealthSnapshot()
                    .map { health -> ThreatIntelFeedHealthUi.fromHealth(health, now) },
                threatIntelDomainCount = threatIntelManager.domainCount,
                threatIntelIpCidrCount = threatIntelManager.ipCidrCount,
                threatIntelLastUpdated = threatIntelManager.lastUpdated
            )
        }
    }
}
