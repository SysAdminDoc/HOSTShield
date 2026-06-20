package com.hostshield.ui.screens.stats

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hostshield.data.database.HourlyStat
import com.hostshield.data.database.ThreatIntelDailyImpact
import com.hostshield.data.database.ThreatIntelFeedImpact
import com.hostshield.data.database.ThreatIntelTopApp
import com.hostshield.data.database.ThreatIntelTopDomain
import com.hostshield.ui.components.HostShieldCompactState
import com.hostshield.ui.screens.home.GlassCard
import com.hostshield.ui.theme.*
import java.text.NumberFormat
import java.util.Locale

@Composable
fun StatsScreen(viewModel: StatsViewModel = hiltViewModel(), onNavigateToLogs: () -> Unit = {}) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val nf = NumberFormat.getNumberInstance()

    LazyColumn(
        modifier = Modifier.fillMaxSize().background(Color.Black),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Statistics", style = MaterialTheme.typography.headlineMedium, color = TextPrimary)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Protection trends, DNS cache health, and VPN reliability.",
                        color = TextSecondary,
                        style = MaterialTheme.typography.bodySmall,
                        lineHeight = 16.sp
                    )
                }
                TextButton(onClick = onNavigateToLogs) {
                    Text("View logs", color = Teal, fontSize = 13.sp)
                    Spacer(Modifier.width(2.dp))
                    Icon(Icons.Filled.ChevronRight, null, tint = Teal, modifier = Modifier.size(16.dp))
                }
            }
        }

        // Summary cards
        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                MiniStat(Modifier.weight(1f), "Blocked", nf.format(state.blockedToday), Red, Icons.Filled.Block)
                MiniStat(Modifier.weight(1f), "Queries", nf.format(state.queriesToday), Blue, Icons.Filled.Dns)
                MiniStat(Modifier.weight(1f), "Rate", "${(state.blockRate * 100).toInt()}%", Teal, Icons.Filled.Speed)
            }
        }

        // Hourly chart
        item {
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(modifier = Modifier.size(28.dp).clip(RoundedCornerShape(8.dp)).background(Teal.copy(alpha = 0.1f)), contentAlignment = Alignment.Center) {
                            Icon(Icons.Filled.Timeline, null, tint = Teal, modifier = Modifier.size(14.dp))
                        }
                        Spacer(Modifier.width(10.dp))
                        Text("Today's Activity", color = TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    }
                    Spacer(Modifier.height(14.dp))
                    if (state.hourlyBlocked.isNotEmpty()) {
                        HourlyBarChart(data = state.hourlyBlocked, modifier = Modifier.fillMaxWidth().height(140.dp))
                    } else {
                        HostShieldCompactState(
                            icon = Icons.Filled.Timeline,
                            title = "No hourly activity yet",
                            message = "VPN mode logging will chart blocked traffic by hour.",
                            accent = Teal,
                        )
                    }
                }
            }
        }

        // DNS Latency Chart
        item {
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(modifier = Modifier.size(28.dp).clip(RoundedCornerShape(8.dp)).background(Peach.copy(alpha = 0.1f)), contentAlignment = Alignment.Center) {
                            Icon(Icons.Filled.Speed, null, tint = Peach, modifier = Modifier.size(14.dp))
                        }
                        Spacer(Modifier.width(10.dp))
                        Text("DNS Latency", color = TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    }
                    Spacer(Modifier.height(14.dp))
                    if (state.hourlyLatency.isNotEmpty()) {
                        LatencyBarChart(data = state.hourlyLatency, modifier = Modifier.fillMaxWidth().height(120.dp))
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            Text("Avg: ${"%.0f".format(state.avgLatencyMs)}ms", color = Peach, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                            Text("Peak: ${state.peakLatencyMs}ms", color = Red, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                        }
                        if (state.latencyP50 > 0f) {
                            Spacer(Modifier.height(4.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                Text("p50: ${"%.0f".format(state.latencyP50)}ms", color = TextDim, fontSize = 10.sp)
                                Text("p95: ${"%.0f".format(state.latencyP95)}ms", color = Yellow, fontSize = 10.sp)
                                Text("p99: ${"%.0f".format(state.latencyP99)}ms", color = Red.copy(alpha = 0.7f), fontSize = 10.sp)
                            }
                        }
                    } else {
                        HostShieldCompactState(
                            icon = Icons.Filled.Speed,
                            title = "No latency samples yet",
                            message = "Resolver timing appears after DNS queries pass through VPN mode.",
                            accent = Peach,
                        )
                    }
                }
            }
        }

        // DNS Cache Performance
        item {
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(modifier = Modifier.size(28.dp).clip(RoundedCornerShape(8.dp)).background(Green.copy(alpha = 0.1f)), contentAlignment = Alignment.Center) {
                            Icon(Icons.Filled.Cached, null, tint = Green, modifier = Modifier.size(14.dp))
                        }
                        Spacer(Modifier.width(10.dp))
                        Text("DNS Cache", color = TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column {
                            Text("Hit Rate", color = TextDim, fontSize = 11.sp)
                            Text("${(state.cacheHitRate * 100).toInt()}%", color = if (state.cacheHitRate > 0.5f) Green else Peach, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text("Entries", color = TextDim, fontSize = 11.sp)
                            Text(nf.format(state.cacheSize), color = TextPrimary, fontWeight = FontWeight.Medium, fontSize = 14.sp)
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Hits: ${nf.format(state.cacheHits)}", color = Green, fontSize = 11.sp)
                        Text("Misses: ${nf.format(state.cacheMisses)}", color = TextDim, fontSize = 11.sp)
                        Text("Evictions: ${nf.format(state.cacheEvictions)}", color = Peach, fontSize = 11.sp)
                    }
                    if (state.cacheStaleHits > 0 || state.cachePrefetchTriggers > 0) {
                        Spacer(Modifier.height(4.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Stale served: ${nf.format(state.cacheStaleHits)}", color = TextDim, fontSize = 11.sp)
                            Text("Prefetches: ${nf.format(state.cachePrefetchTriggers)}", color = TextDim, fontSize = 11.sp)
                        }
                    }
                }
            }
        }

        // Query Type Distribution
        if (state.queryTypeDistribution.isNotEmpty()) {
            item {
                GlassCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(modifier = Modifier.size(28.dp).clip(RoundedCornerShape(8.dp)).background(Mauve.copy(alpha = 0.1f)), contentAlignment = Alignment.Center) {
                                Icon(Icons.Filled.Category, null, tint = Mauve, modifier = Modifier.size(14.dp))
                            }
                            Spacer(Modifier.width(10.dp))
                            Text("Query Types (7d)", color = TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        }
                        Spacer(Modifier.height(12.dp))
                        val total = state.queryTypeTotal
                        val typeColors = mapOf(
                            "A" to Teal, "AAAA" to Blue, "CNAME" to Peach,
                            "MX" to Flamingo, "TXT" to Yellow, "SRV" to Green,
                            "SOA" to Red, "NS" to Mauve, "PTR" to Sky
                        )
                        state.queryTypeDistribution.forEach { stat ->
                            val pct = stat.cnt.toFloat() / total
                            val color = typeColors[stat.queryType] ?: TextDim
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(stat.queryType, color = color, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                                    modifier = Modifier.width(48.dp))
                                Box(
                                    modifier = Modifier.weight(1f).height(14.dp)
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(Surface3)
                                ) {
                                    Box(
                                        modifier = Modifier.fillMaxHeight()
                                            .fillMaxWidth(pct.coerceIn(0.01f, 1f))
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(color.copy(alpha = 0.6f))
                                    )
                                }
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    "${(pct * 100).toInt()}%",
                                    color = TextDim, fontSize = 10.sp,
                                    modifier = Modifier.width(32.dp)
                                )
                                Text(
                                    nf.format(stat.cnt),
                                    color = TextDim, fontSize = 10.sp
                                )
                            }
                        }
                    }
                }
            }
        }

        // VPN Health
        item {
            val health = state.vpnHealth
            val healthColor = when (health) {
                StatsUiState.VpnHealth.DEGRADED -> Red
                StatsUiState.VpnHealth.UNSTABLE, StatsUiState.VpnHealth.MINOR_DROPS -> Peach
                else -> Green
            }
            val healthLabel = health.label
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(modifier = Modifier.size(28.dp).clip(RoundedCornerShape(8.dp)).background(healthColor.copy(alpha = 0.1f)), contentAlignment = Alignment.Center) {
                            Icon(Icons.Filled.HealthAndSafety, null, tint = healthColor, modifier = Modifier.size(14.dp))
                        }
                        Spacer(Modifier.width(10.dp))
                        Text("VPN Health (7d)", color = TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        Spacer(Modifier.weight(1f))
                        Text(healthLabel, color = healthColor, fontWeight = FontWeight.Medium, fontSize = 12.sp)
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column {
                            Text("Uptime", color = TextDim, fontSize = 11.sp)
                            Text("${"%.1f".format(state.vpnUptimeHours)}h", color = TextPrimary, fontWeight = FontWeight.Medium, fontSize = 14.sp)
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Rebuilds", color = TextDim, fontSize = 11.sp)
                            Text("${state.vpnRebuilds}", color = if (state.vpnRebuilds > 5) Peach else TextPrimary, fontWeight = FontWeight.Medium, fontSize = 14.sp)
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Errors", color = TextDim, fontSize = 11.sp)
                            Text("${state.vpnFdErrors}", color = if (state.vpnFdErrors > 0) Red else TextPrimary, fontWeight = FontWeight.Medium, fontSize = 14.sp)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text("Dropped", color = TextDim, fontSize = 11.sp)
                            Text("${state.vpnDroppedQueries}", color = if (state.vpnDroppedQueries > 0) Red else TextPrimary, fontWeight = FontWeight.Medium, fontSize = 14.sp)
                        }
                    }
                    if (state.vpnTotalQueries > 0) {
                        Spacer(Modifier.height(6.dp))
                        val dropRate = state.vpnDroppedQueries.toFloat() / state.vpnTotalQueries * 100
                        Text("${nf.format(state.vpnTotalQueries)} total queries, ${String.format(Locale.US, "%.2f", dropRate)}% drop rate",
                            color = TextDim, fontSize = 11.sp)
                    }
                }
            }
        }

        item {
            ThreatIntelFeedHealthCard(
                state = state,
                numberFormat = nf,
                onRefresh = viewModel::refreshThreatIntelFeeds
            )
        }

        item {
            ThreatIntelImpactCard(state = state, numberFormat = nf)
        }

        // 7-Day Trend Line Chart
        item {
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(modifier = Modifier.size(28.dp).clip(RoundedCornerShape(8.dp)).background(Blue.copy(alpha = 0.1f)), contentAlignment = Alignment.Center) {
                            Icon(Icons.AutoMirrored.Filled.TrendingUp, null, tint = Blue, modifier = Modifier.size(14.dp))
                        }
                        Spacer(Modifier.width(10.dp))
                        Text("7-Day Trend", color = TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    }
                    Spacer(Modifier.height(14.dp))
                    if (state.dailyTrend.size >= 2) {
                        TrendLineChart(data = state.dailyTrend, modifier = Modifier.fillMaxWidth().height(140.dp))
                    } else {
                        HostShieldCompactState(
                            icon = Icons.AutoMirrored.Filled.TrendingUp,
                            title = "Trend warming up",
                            message = "Two or more days of DNS history are needed for a reliable trend.",
                            accent = Blue,
                        )
                    }
                }
            }
        }

        // Top blocked domains
        item {
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(modifier = Modifier.size(28.dp).clip(RoundedCornerShape(8.dp)).background(Red.copy(alpha = 0.1f)), contentAlignment = Alignment.Center) {
                            Icon(Icons.Filled.Block, null, tint = Red, modifier = Modifier.size(14.dp))
                        }
                        Spacer(Modifier.width(10.dp))
                        Text("Top Blocked", color = TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    }
                    Spacer(Modifier.height(12.dp))
                    if (state.topDomains.isEmpty()) {
                        HostShieldCompactState(
                            icon = Icons.Filled.Block,
                            title = "No blocked domains yet",
                            message = "Blocked domains will rank here as protection starts catching requests.",
                            accent = Red,
                        )
                    }
                }
            }
        }

        if (state.topDomains.isNotEmpty()) {
            val maxCount = state.topDomains.firstOrNull()?.cnt ?: 1
            itemsIndexed(state.topDomains) { index, item ->
                DomainBar(rank = index + 1, hostname = item.hostname, count = item.cnt, maxCount = maxCount, barColor = Red)
            }
        }

        // Top allowed domains
        if (state.topAllowed.isNotEmpty()) {
            item {
                Spacer(Modifier.height(2.dp))
                GlassCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(modifier = Modifier.size(28.dp).clip(RoundedCornerShape(8.dp)).background(Green.copy(alpha = 0.1f)), contentAlignment = Alignment.Center) {
                                Icon(Icons.Filled.CheckCircle, null, tint = Green, modifier = Modifier.size(14.dp))
                            }
                            Spacer(Modifier.width(10.dp))
                            Text("Top Allowed (7d)", color = TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        }
                    }
                }
            }
            val maxAllowed = state.topAllowed.firstOrNull()?.cnt ?: 1
            itemsIndexed(state.topAllowed) { index, item ->
                DomainBar(rank = index + 1, hostname = item.hostname, count = item.cnt, maxCount = maxAllowed, barColor = Green)
            }
        }

        // Top apps
        item {
            Spacer(Modifier.height(2.dp))
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(modifier = Modifier.size(28.dp).clip(RoundedCornerShape(8.dp)).background(Mauve.copy(alpha = 0.1f)), contentAlignment = Alignment.Center) {
                            Icon(Icons.Filled.Apps, null, tint = Mauve, modifier = Modifier.size(14.dp))
                        }
                        Spacer(Modifier.width(10.dp))
                        Text("Top Apps", color = TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    }
                    Spacer(Modifier.height(12.dp))
                    if (state.topApps.isEmpty()) {
                        HostShieldCompactState(
                            icon = Icons.Filled.Apps,
                            title = "Per-app data unavailable",
                            message = "Enable VPN mode to attribute DNS activity to individual apps.",
                            accent = Mauve,
                        )
                    } else {
                        state.topApps.forEachIndexed { idx, app ->
                            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text("${idx + 1}.", color = TextDim, modifier = Modifier.width(24.dp), fontSize = 11.sp)
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(app.appLabel.ifEmpty { app.appPackage }, color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                                    if (app.appLabel.isNotEmpty()) Text(app.appPackage, color = TextDim, fontSize = 10.sp)
                                }
                                Surface(shape = RoundedCornerShape(4.dp), color = Mauve.copy(alpha = 0.1f)) {
                                    Text(nf.format(app.cnt), modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp), color = Mauve, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        }

        // Tracker Company Attribution
        if (state.topTrackerOwners.isNotEmpty()) {
            item {
                Spacer(Modifier.height(2.dp))
                GlassCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(modifier = Modifier.size(28.dp).clip(RoundedCornerShape(8.dp)).background(Flamingo.copy(alpha = 0.1f)), contentAlignment = Alignment.Center) {
                                Icon(Icons.Filled.Business, null, tint = Flamingo, modifier = Modifier.size(14.dp))
                            }
                            Spacer(Modifier.width(10.dp))
                            Text("Tracking Companies (7d)", color = TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        }
                        Spacer(Modifier.height(12.dp))
                        val totalTrackerQueries = state.topTrackerOwners.sumOf { it.cnt }.coerceAtLeast(1)
                        state.topTrackerOwners.forEachIndexed { idx, owner ->
                            val pct = owner.cnt.toFloat() / totalTrackerQueries
                            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text("${idx + 1}.", color = TextDim, modifier = Modifier.width(24.dp), fontSize = 11.sp)
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(owner.owner, color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Medium, maxLines = 1)
                                    Text(owner.category, color = TextDim, fontSize = 10.sp)
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(nf.format(owner.cnt), color = Flamingo, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    Text("${(pct * 100).toInt()}%", color = TextDim, fontSize = 10.sp)
                                }
                            }
                        }
                    }
                }
            }
        }

        // Per-App DNS Activity (24h / 7d)
        if (state.topApps24h.isNotEmpty() || state.topApps7d.isNotEmpty()) {
            item {
                Spacer(Modifier.height(2.dp))
                GlassCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(modifier = Modifier.size(28.dp).clip(RoundedCornerShape(8.dp)).background(Green.copy(alpha = 0.1f)), contentAlignment = Alignment.Center) {
                                Icon(Icons.Filled.PhoneAndroid, null, tint = Green, modifier = Modifier.size(14.dp))
                            }
                            Spacer(Modifier.width(10.dp))
                            Text("Per-App DNS Activity", color = TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        }
                        Spacer(Modifier.height(12.dp))
                        var show7d by remember { mutableStateOf(false) }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(selected = !show7d, onClick = { show7d = false }, label = { Text("24h", fontSize = 11.sp) })
                            FilterChip(selected = show7d, onClick = { show7d = true }, label = { Text("7d", fontSize = 11.sp) })
                        }
                        Spacer(Modifier.height(8.dp))
                        val apps = if (show7d) state.topApps7d else state.topApps24h
                        if (apps.isEmpty()) {
                            Text("No per-app data for this period.", color = TextDim, fontSize = 11.sp)
                        } else {
                            apps.forEachIndexed { idx, app ->
                                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Text("${idx + 1}.", color = TextDim, modifier = Modifier.width(24.dp), fontSize = 11.sp)
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(app.appLabel.ifEmpty { app.appPackage }, color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Medium, maxLines = 1)
                                        if (app.appLabel.isNotEmpty()) Text(app.appPackage, color = TextDim, fontSize = 10.sp, maxLines = 1)
                                    }
                                    Column(horizontalAlignment = Alignment.End) {
                                        Text("${nf.format(app.totalQueries)} queries", color = Green, fontSize = 10.sp)
                                        Text("${nf.format(app.blockedQueries)} blocked", color = Red.copy(alpha = 0.7f), fontSize = 10.sp)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Network Insights: Most aggressively queried domains
        item {
            Spacer(Modifier.height(2.dp))
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(modifier = Modifier.size(28.dp).clip(RoundedCornerShape(8.dp)).background(Yellow.copy(alpha = 0.1f)), contentAlignment = Alignment.Center) {
                            Icon(Icons.Filled.Radar, null, tint = Yellow, modifier = Modifier.size(14.dp))
                        }
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text("Network Insights", color = TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                            Text("Most aggressively queried domains (7 days)", color = TextDim, fontSize = 10.sp)
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    if (state.mostQueried.isEmpty()) {
                        HostShieldCompactState(
                            icon = Icons.Filled.Radar,
                            title = "No noisy domains detected",
                            message = "High-frequency domains appear here after recent DNS traffic is collected.",
                            accent = Yellow,
                        )
                    } else {
                        val maxMq = state.mostQueried.maxOfOrNull { it.cnt } ?: 1
                        state.mostQueried.forEachIndexed { idx, dom ->
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Rank indicator
                                Box(
                                    modifier = Modifier
                                        .size(18.dp)
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(
                                            when {
                                                idx < 3 -> Red.copy(alpha = 0.12f)
                                                idx < 7 -> Yellow.copy(alpha = 0.08f)
                                                else -> Surface2
                                            }
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        "${idx + 1}",
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = when {
                                            idx < 3 -> Red
                                            idx < 7 -> Yellow
                                            else -> TextDim
                                        }
                                    )
                                }
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    dom.hostname,
                                    color = TextSecondary,
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace,
                                    modifier = Modifier.weight(1f),
                                    maxLines = 1
                                )
                                Spacer(Modifier.width(6.dp))
                                // Frequency bar
                                Box(
                                    modifier = Modifier
                                        .width(40.dp)
                                        .height(4.dp)
                                        .clip(RoundedCornerShape(2.dp))
                                        .background(Surface3)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth(dom.cnt.toFloat() / maxMq)
                                            .fillMaxHeight()
                                            .background(
                                                when {
                                                    idx < 3 -> Red.copy(alpha = 0.7f)
                                                    idx < 7 -> Yellow.copy(alpha = 0.6f)
                                                    else -> Teal.copy(alpha = 0.5f)
                                                }
                                            )
                                    )
                                }
                                Spacer(Modifier.width(6.dp))
                                Text(nf.format(dom.cnt), color = TextDim, fontSize = 10.sp)
                            }
                        }
                    }
                }
            }
        }

        // Daily history
        item {
            Spacer(Modifier.height(2.dp))
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(modifier = Modifier.size(28.dp).clip(RoundedCornerShape(8.dp)).background(Peach.copy(alpha = 0.1f)), contentAlignment = Alignment.Center) {
                            Icon(Icons.Filled.CalendarMonth, null, tint = Peach, modifier = Modifier.size(14.dp))
                        }
                        Spacer(Modifier.width(10.dp))
                        Text("Daily History", color = TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    }
                    Spacer(Modifier.height(12.dp))
                    if (state.dailyStats.isEmpty()) {
                        HostShieldCompactState(
                            icon = Icons.Filled.CalendarMonth,
                            title = "No daily history yet",
                            message = "Daily totals will appear once HostShield records a full activity window.",
                            accent = Peach,
                        )
                    } else {
                        state.dailyStats.forEach { day ->
                            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(day.date, color = TextSecondary, fontSize = 12.sp)
                                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                                    Text("${nf.format(day.blockedCount)} blocked", color = Red.copy(alpha = 0.7f), fontSize = 12.sp)
                                    Text("${nf.format(day.totalQueries)} total", color = TextDim, fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun HourlyBarChart(data: List<HourlyStat>, modifier: Modifier) {
    val hourData = FloatArray(24)
    data.forEach { if (it.hour in 0..23) hourData[it.hour] = it.cnt.toFloat() }
    val maxVal = hourData.maxOrNull()?.coerceAtLeast(1f) ?: 1f
    val textMeasurer = rememberTextMeasurer()
    val activeStartColor = Teal
    val activeEndColor = TealDim.copy(alpha = 0.4f)
    val emptyColor = Surface3
    val labelColor = TextDim

    Canvas(modifier = modifier) {
        val barWidth = size.width / 28f
        val chartHeight = size.height - 22f
        val gap = barWidth / 5f

        for (i in 0..23) {
            val frac = hourData[i] / maxVal
            val barH = frac * chartHeight * 0.9f
            val x = (i + 1f) * (barWidth + gap)
            val y = chartHeight - barH

            // Bar with gradient
            drawRoundRect(
                brush = Brush.verticalGradient(
                    colors = if (hourData[i] > 0) listOf(activeStartColor, activeEndColor) else listOf(emptyColor, emptyColor)
                ),
                topLeft = Offset(x, y),
                size = Size(barWidth, barH.coerceAtLeast(2f)),
                cornerRadius = CornerRadius(3f, 3f)
            )

            if (i % 6 == 0) {
                drawText(textMeasurer = textMeasurer, text = "${i}h", topLeft = Offset(x - 2f, chartHeight + 4f), style = TextStyle(color = labelColor, fontSize = 9.sp))
            }
        }
    }
}

@Composable
private fun ThreatIntelFeedHealthCard(
    state: StatsUiState,
    numberFormat: NumberFormat,
    onRefresh: () -> Unit
) {
    val failedCount = state.threatIntelFeeds.count {
        it.status == ThreatIntelFeedStatus.FAILED || it.status == ThreatIntelFeedStatus.DEGRADED
    }
    val staleCount = state.threatIntelFeeds.count { it.status == ThreatIntelFeedStatus.STALE }
    val statusColor = when {
        failedCount > 0 -> Red
        staleCount > 0 -> Yellow
        state.threatIntelFeeds.any { it.status == ThreatIntelFeedStatus.HEALTHY } -> Green
        else -> TextDim
    }
    val statusText = when {
        failedCount > 0 -> "$failedCount degraded"
        staleCount > 0 -> "$staleCount stale"
        state.threatIntelFeeds.any { it.status == ThreatIntelFeedStatus.HEALTHY } -> "Fresh"
        else -> "No cache"
    }

    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(statusColor.copy(alpha = 0.1f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.HealthAndSafety, null, tint = statusColor, modifier = Modifier.size(14.dp))
                }
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("Threat Feed Health", color = TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    Text(
                        "${numberFormat.format(state.threatIntelDomainCount)} domains, ${numberFormat.format(state.threatIntelIpCidrCount)} IP ranges",
                        color = TextDim,
                        fontSize = 10.sp,
                        lineHeight = 14.sp
                    )
                }
                Text(statusText, color = statusColor, fontWeight = FontWeight.Medium, fontSize = 12.sp)
                Spacer(Modifier.width(4.dp))
                IconButton(
                    onClick = onRefresh,
                    enabled = !state.isRefreshingThreatIntel,
                    modifier = Modifier.size(40.dp)
                ) {
                    if (state.isRefreshingThreatIntel) {
                        CircularProgressIndicator(color = Teal, strokeWidth = 2.dp, modifier = Modifier.size(16.dp))
                    } else {
                        Icon(Icons.Filled.Refresh, "Refresh threat feeds", tint = Teal, modifier = Modifier.size(17.dp))
                    }
                }
            }

            Spacer(Modifier.height(10.dp))
            Text(
                "Last complete update: ${formatThreatIntelTimestamp(state.threatIntelLastUpdated)}",
                color = TextDim,
                fontSize = 11.sp,
                lineHeight = 15.sp
            )
            state.threatIntelRefreshMessage?.let { message ->
                Spacer(Modifier.height(4.dp))
                Text(message, color = TextSecondary, fontSize = 11.sp, lineHeight = 15.sp)
            }

            Spacer(Modifier.height(10.dp))
            if (state.threatIntelFeeds.isEmpty()) {
                HostShieldCompactState(
                    icon = Icons.Filled.HealthAndSafety,
                    title = "No threat feed state yet",
                    message = "Feed health appears after the first cache load or manual refresh.",
                    accent = statusColor,
                )
            } else {
                state.threatIntelFeeds.forEachIndexed { index, feed ->
                    ThreatIntelFeedHealthRow(feed, numberFormat)
                    if (index != state.threatIntelFeeds.lastIndex) {
                        HorizontalDivider(color = Surface3.copy(alpha = 0.6f), thickness = 1.dp)
                    }
                }
            }
        }
    }
}

@Composable
private fun ThreatIntelFeedHealthRow(feed: ThreatIntelFeedHealthUi, numberFormat: NumberFormat) {
    val color = threatIntelStatusColor(feed.status)
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(7.dp).clip(RoundedCornerShape(4.dp)).background(color))
            Spacer(Modifier.width(8.dp))
            Text(feed.name, color = TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, modifier = Modifier.weight(1f))
            Text(threatIntelStatusLabel(feed.status), color = color, fontWeight = FontWeight.Medium, fontSize = 11.sp)
        }
        Spacer(Modifier.height(3.dp))
        Text(
            "Entries ${numberFormat.format(feed.entryCount)} | Last success ${formatThreatIntelTimestamp(feed.lastSuccess)} | HTTP ${if (feed.httpStatus > 0) feed.httpStatus else "-"}",
            color = TextDim,
            fontSize = 10.sp,
            lineHeight = 14.sp
        )
        Spacer(Modifier.height(2.dp))
        val bytes = formatThreatIntelBytes(feed.bytesDownloaded)
        val hash = feed.sha256Short.ifBlank { "-" }
        Text(
            "Bytes $bytes | SHA-256 $hash | Failures ${feed.consecutiveFailures}",
            color = TextDim,
            fontSize = 10.sp,
            lineHeight = 14.sp
        )
        if (feed.lastError.isNotBlank()) {
            Spacer(Modifier.height(2.dp))
            Text(feed.lastError, color = Red.copy(alpha = 0.85f), fontSize = 10.sp, lineHeight = 14.sp, maxLines = 2)
        }
    }
}

@Composable
private fun ThreatIntelImpactCard(state: StatsUiState, numberFormat: NumberFormat) {
    val total24h = state.threatIntelFeedImpact.sumOf { it.blocks24h }
    val total7d = state.threatIntelFeedImpact.sumOf { it.blocks7d }

    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Red.copy(alpha = 0.1f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.GppBad, null, tint = Red, modifier = Modifier.size(14.dp))
                }
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("Threat Feed Impact", color = TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    Text("Local blocks attributed by feed", color = TextDim, fontSize = 10.sp, lineHeight = 14.sp)
                }
            }

            Spacer(Modifier.height(12.dp))
            if (state.threatIntelFeedImpact.isEmpty()) {
                HostShieldCompactState(
                    icon = Icons.Filled.GppBad,
                    title = "No threat-intel blocks yet",
                    message = "Malware feed impact appears here after a feed blocks DNS traffic.",
                    accent = Red,
                )
                return@Column
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ThreatIntelImpactMetric(
                    label = "24h",
                    value = numberFormat.format(total24h),
                    color = if (total24h > 0) Red else TextDim,
                    modifier = Modifier.weight(1f),
                )
                ThreatIntelImpactMetric(
                    label = "7d",
                    value = numberFormat.format(total7d),
                    color = Red,
                    modifier = Modifier.weight(1f),
                )
            }

            Spacer(Modifier.height(12.dp))
            Text("Feeds", color = TextSecondary, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp))
            state.threatIntelFeedImpact.take(4).forEach { feed ->
                ThreatIntelFeedImpactRow(feed, numberFormat)
            }

            if (state.threatIntelTopDomains.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                Text("Affected Domains", color = TextSecondary, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                state.threatIntelTopDomains.take(4).forEach { domain ->
                    ThreatIntelDomainImpactRow(domain, numberFormat)
                }
            }

            if (state.threatIntelTopApps.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                Text("Affected Apps", color = TextSecondary, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                state.threatIntelTopApps.take(4).forEach { app ->
                    ThreatIntelAppImpactRow(app, numberFormat)
                }
            }

            if (state.threatIntelDailyImpact.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                Text("7-Day Feed Trend", color = TextSecondary, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                ThreatIntelDailyTrend(state.threatIntelDailyImpact, numberFormat)
            }
        }
    }
}

@Composable
private fun ThreatIntelImpactMetric(
    label: String,
    value: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Surface(shape = RoundedCornerShape(8.dp), color = color.copy(alpha = 0.08f), modifier = modifier) {
        Column(modifier = Modifier.padding(10.dp)) {
            Text(label, color = TextDim, fontSize = 10.sp)
            Text(value, color = color, fontWeight = FontWeight.Bold, fontSize = 18.sp, letterSpacing = 0.sp)
        }
    }
}

@Composable
private fun ThreatIntelFeedImpactRow(feed: ThreatIntelFeedImpact, numberFormat: NumberFormat) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(feed.feedName, color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Medium, maxLines = 1)
            Text(
                "Last ${formatThreatIntelTimestamp(feed.lastMatched)}",
                color = TextDim,
                fontSize = 10.sp,
                lineHeight = 14.sp,
            )
        }
        Text(
            "${numberFormat.format(feed.blocks24h)} / ${numberFormat.format(feed.blocks7d)}",
            color = Red,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun ThreatIntelDomainImpactRow(domain: ThreatIntelTopDomain, numberFormat: NumberFormat) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(domain.hostname, color = TextPrimary, fontSize = 11.sp, maxLines = 1, fontFamily = FontFamily.Monospace)
            val matchText = domain.matchedValue.ifBlank { domain.feedName }
            Text("$matchText | ${domain.feedName}", color = TextDim, fontSize = 9.sp, maxLines = 1)
        }
        Text(numberFormat.format(domain.cnt), color = Red, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun ThreatIntelAppImpactRow(app: ThreatIntelTopApp, numberFormat: NumberFormat) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(app.appLabel.ifBlank { app.appPackage }, color = TextPrimary, fontSize = 11.sp, maxLines = 1)
            Text("${app.appPackage} | ${app.feedName}", color = TextDim, fontSize = 9.sp, maxLines = 1)
        }
        Text(numberFormat.format(app.cnt), color = Red, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun ThreatIntelDailyTrend(trend: List<ThreatIntelDailyImpact>, numberFormat: NumberFormat) {
    val byDay = trend.groupBy { it.day }.toSortedMap()
    val maxCount = byDay.values.maxOfOrNull { rows -> rows.sumOf { it.cnt } }?.coerceAtLeast(1) ?: 1
    byDay.entries.toList().takeLast(7).forEach { (day, rows) ->
        val total = rows.sumOf { it.cnt }
        val topFeed = rows.maxByOrNull { it.cnt }?.feedName.orEmpty()
        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(shortThreatIntelDay(day), color = TextDim, fontSize = 10.sp, modifier = Modifier.width(44.dp))
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(12.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Surface3)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth((total.toFloat() / maxCount).coerceIn(0.04f, 1f))
                        .clip(RoundedCornerShape(4.dp))
                        .background(Red.copy(alpha = 0.65f))
                )
            }
            Spacer(Modifier.width(8.dp))
            Text(topFeed, color = TextDim, fontSize = 9.sp, maxLines = 1, modifier = Modifier.width(72.dp))
            Text(numberFormat.format(total), color = Red, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

private fun threatIntelStatusLabel(status: ThreatIntelFeedStatus): String = when (status) {
    ThreatIntelFeedStatus.HEALTHY -> "Fresh"
    ThreatIntelFeedStatus.STALE -> "Stale"
    ThreatIntelFeedStatus.DEGRADED -> "Degraded"
    ThreatIntelFeedStatus.FAILED -> "Failed"
    ThreatIntelFeedStatus.NEVER_REFRESHED -> "No cache"
}

@Composable
private fun threatIntelStatusColor(status: ThreatIntelFeedStatus): Color = when (status) {
    ThreatIntelFeedStatus.HEALTHY -> Green
    ThreatIntelFeedStatus.STALE -> Yellow
    ThreatIntelFeedStatus.DEGRADED -> Peach
    ThreatIntelFeedStatus.FAILED -> Red
    ThreatIntelFeedStatus.NEVER_REFRESHED -> TextDim
}

private fun formatThreatIntelTimestamp(timestampMs: Long): String {
    if (timestampMs <= 0L) return "never"
    val ageMs = (System.currentTimeMillis() - timestampMs).coerceAtLeast(0L)
    val minutes = ageMs / 60_000L
    return when {
        minutes < 1 -> "just now"
        minutes < 60 -> "${minutes}m ago"
        minutes < 48 * 60 -> "${minutes / 60}h ago"
        else -> "${minutes / (24 * 60)}d ago"
    }
}

private fun formatThreatIntelBytes(bytes: Long): String = when {
    bytes <= 0L -> "-"
    bytes < 1024L -> "${bytes} B"
    bytes < 1024L * 1024L -> "${bytes / 1024L} KiB"
    else -> "${bytes / (1024L * 1024L)} MiB"
}

private fun shortThreatIntelDay(day: String): String =
    if (day.length >= 10) day.substring(5) else day

@Composable
private fun MiniStat(modifier: Modifier, label: String, value: String, color: Color, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    GlassCard(modifier = modifier) {
        Column(modifier = Modifier.padding(14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(modifier = Modifier.size(28.dp).clip(RoundedCornerShape(8.dp)).background(color.copy(alpha = 0.1f)), contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = color, modifier = Modifier.size(14.dp))
            }
            Spacer(Modifier.height(8.dp))
            Text(value, color = color, fontWeight = FontWeight.Bold, fontSize = 20.sp, letterSpacing = 0.sp)
            Spacer(Modifier.height(2.dp))
            Text(label, color = TextSecondary, fontSize = 10.sp)
        }
    }
}

@Composable
private fun DomainBar(rank: Int, hostname: String, count: Int, maxCount: Int, barColor: Color = Red) {
    val fraction = if (maxCount > 0) count.toFloat() / maxCount else 0f
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp, horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("$rank.", color = TextDim, modifier = Modifier.width(24.dp), fontSize = 11.sp)
        Box(modifier = Modifier.weight(1f)) {
            Box(modifier = Modifier.fillMaxWidth(fraction.coerceIn(0.04f, 1f)).height(22.dp).background(barColor.copy(alpha = 0.08f), RoundedCornerShape(4.dp)))
            Text(hostname, modifier = Modifier.align(Alignment.CenterStart).padding(start = 8.dp), color = TextPrimary, fontSize = 11.sp, maxLines = 1, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
        }
        Spacer(Modifier.width(8.dp))
        Text(NumberFormat.getNumberInstance().format(count), color = barColor, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(44.dp))
    }
}

@Composable
private fun LatencyBarChart(data: List<com.hostshield.data.database.HourlyLatency>, modifier: Modifier) {
    val hourData = FloatArray(24)
    val maxData = FloatArray(24)
    data.forEach {
        if (it.hour in 0..23) {
            hourData[it.hour] = it.avgMs
            maxData[it.hour] = it.maxMs.toFloat()
        }
    }
    val maxVal = maxData.maxOrNull()?.coerceAtLeast(1f) ?: 1f
    val textMeasurer = rememberTextMeasurer()
    val maxBarColor = Red.copy(alpha = 0.15f)
    val avgStartColor = Peach
    val avgEndColor = Peach.copy(alpha = 0.4f)
    val emptyColor = Surface3
    val labelColor = TextDim

    Canvas(modifier = modifier) {
        val barWidth = size.width / 28f
        val chartHeight = size.height - 22f
        val gap = barWidth / 5f

        for (i in 0..23) {
            val avgFrac = hourData[i] / maxVal
            val maxFrac = maxData[i] / maxVal
            val x = (i + 1f) * (barWidth + gap)

            // Max bar (faded)
            val maxH = maxFrac * chartHeight * 0.9f
            drawRoundRect(
                color = maxBarColor,
                topLeft = Offset(x, chartHeight - maxH),
                size = Size(barWidth, maxH.coerceAtLeast(1f)),
                cornerRadius = CornerRadius(3f, 3f)
            )

            // Avg bar
            val avgH = avgFrac * chartHeight * 0.9f
            drawRoundRect(
                brush = Brush.verticalGradient(
                    colors = if (hourData[i] > 0) listOf(avgStartColor, avgEndColor) else listOf(emptyColor, emptyColor)
                ),
                topLeft = Offset(x, chartHeight - avgH),
                size = Size(barWidth, avgH.coerceAtLeast(1f)),
                cornerRadius = CornerRadius(3f, 3f)
            )

            if (i % 6 == 0) {
                drawText(textMeasurer = textMeasurer, text = "${i}h",
                    topLeft = Offset(x - 2f, chartHeight + 4f),
                    style = TextStyle(color = labelColor, fontSize = 9.sp))
            }
        }
    }
}

@Composable
private fun TrendLineChart(data: List<com.hostshield.data.database.DailyBreakdown>, modifier: Modifier) {
    val textMeasurer = rememberTextMeasurer()
    val gridColor = Surface3
    val totalLineColor = Blue.copy(alpha = 0.6f)
    val blockedLineColor = Red.copy(alpha = 0.8f)
    val blockedPointColor = Red
    val labelColor = TextDim
    val totalLabelColor = Blue
    Canvas(modifier = modifier) {
        if (data.size < 2) return@Canvas
        val maxVal = data.maxOfOrNull { it.total }?.coerceAtLeast(1)?.toFloat() ?: 1f
        val chartW = size.width - 40f
        val chartH = size.height - 24f
        val stepX = chartW / (data.size - 1).coerceAtLeast(1)

        // Grid lines
        for (i in 0..3) {
            val y = chartH * (1 - i / 4f)
            drawLine(gridColor, Offset(30f, y), Offset(size.width, y), strokeWidth = 0.5f)
        }

        // Total queries line (blue)
        val totalPath = Path()
        data.forEachIndexed { idx, d ->
            val x = 30f + idx * stepX
            val y = chartH * (1 - d.total / maxVal)
            if (idx == 0) totalPath.moveTo(x, y) else totalPath.lineTo(x, y)
        }
        drawPath(totalPath, totalLineColor, style = Stroke(width = 2f, cap = StrokeCap.Round))

        // Blocked line (red)
        val blockedPath = Path()
        data.forEachIndexed { idx, d ->
            val x = 30f + idx * stepX
            val y = chartH * (1 - d.blocked / maxVal)
            if (idx == 0) blockedPath.moveTo(x, y) else blockedPath.lineTo(x, y)
        }
        drawPath(blockedPath, blockedLineColor, style = Stroke(width = 2.5f, cap = StrokeCap.Round))

        // Data points on blocked line
        data.forEachIndexed { idx, d ->
            val x = 30f + idx * stepX
            val y = chartH * (1 - d.blocked / maxVal)
            drawCircle(blockedPointColor, 3f, Offset(x, y))
        }

        // Day labels
        val labelIndices = when {
            data.size <= 7 -> data.indices.toList()
            else -> listOf(0, data.size / 2, data.size - 1)
        }
        for (idx in labelIndices) {
            val x = 30f + idx * stepX
            val label = data[idx].day.takeLast(5) // MM-DD
            drawText(textMeasurer = textMeasurer, text = label,
                topLeft = Offset(x - 12f, chartH + 6f),
                style = TextStyle(color = labelColor, fontSize = 8.sp))
        }

        // Legend
        drawCircle(blockedPointColor, 4f, Offset(size.width - 90f, 8f))
        drawText(textMeasurer, "Blocked", Offset(size.width - 82f, 0f),
            TextStyle(color = blockedPointColor, fontSize = 9.sp))
        drawCircle(totalLineColor, 4f, Offset(size.width - 35f, 8f))
        drawText(textMeasurer, "Total", Offset(size.width - 27f, 0f),
            TextStyle(color = totalLabelColor, fontSize = 9.sp))
    }
}
