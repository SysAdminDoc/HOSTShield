package com.hostshield.ui.screens.stats

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
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
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.hostshield.data.database.HourlyStat
import com.hostshield.data.database.TopApp
import com.hostshield.data.database.TopHostname
import com.hostshield.data.model.BlockStats
import com.hostshield.data.repository.HostShieldRepository
import com.hostshield.ui.screens.home.GlassCard
import com.hostshield.ui.theme.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.text.NumberFormat
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
    val mostQueried: List<TopHostname> = emptyList() // Network insights: aggressive domains
)

@HiltViewModel
class StatsViewModel @Inject constructor(
    private val repository: HostShieldRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow(StatsUiState())
    val uiState: StateFlow<StatsUiState> = _uiState.asStateFlow()

    private val todayStart = java.time.LocalDate.now()
        .atStartOfDay(java.time.ZoneId.systemDefault())
        .toInstant().toEpochMilli()

    // 7 days ago for insights
    private val weekStart = todayStart - (7 * 24 * 60 * 60 * 1000L)

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
    }
}

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
                Text("Statistics", style = MaterialTheme.typography.headlineMedium, color = TextPrimary)
                TextButton(onClick = onNavigateToLogs) {
                    Text("View Logs", color = Teal, fontSize = 13.sp)
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
                        Box(modifier = Modifier.fillMaxWidth().height(80.dp), contentAlignment = Alignment.Center) {
                            Text("Charts populate with VPN mode logging.", color = TextDim, fontSize = 12.sp)
                        }
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
                        Text("No blocked domains recorded yet.", color = TextDim, fontSize = 12.sp)
                    }
                }
            }
        }

        if (state.topDomains.isNotEmpty()) {
            val maxCount = state.topDomains.firstOrNull()?.cnt ?: 1
            itemsIndexed(state.topDomains) { index, item ->
                DomainBar(rank = index + 1, hostname = item.hostname, count = item.cnt, maxCount = maxCount)
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
                        Text("Requires VPN mode for per-app data.", color = TextDim, fontSize = 12.sp)
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
                        Text("No data yet. DNS queries will appear after enabling blocking.", color = TextDim, fontSize = 12.sp)
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
                        Text("No daily stats recorded yet.", color = TextDim, fontSize = 12.sp)
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
    val hourData = FloatArray(24) { 0f }
    data.forEach { if (it.hour in 0..23) hourData[it.hour] = it.cnt.toFloat() }
    val maxVal = hourData.maxOrNull()?.coerceAtLeast(1f) ?: 1f
    val textMeasurer = rememberTextMeasurer()

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
                    colors = if (hourData[i] > 0) listOf(Teal, TealDim.copy(alpha = 0.4f)) else listOf(Surface3, Surface3)
                ),
                topLeft = Offset(x, y),
                size = Size(barWidth, barH.coerceAtLeast(2f)),
                cornerRadius = CornerRadius(3f, 3f)
            )

            if (i % 6 == 0) {
                drawText(textMeasurer = textMeasurer, text = "${i}h", topLeft = Offset(x - 2f, chartHeight + 4f), style = TextStyle(color = TextDim, fontSize = 9.sp))
            }
        }
    }
}

@Composable
private fun MiniStat(modifier: Modifier, label: String, value: String, color: Color, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    GlassCard(modifier = modifier) {
        Column(modifier = Modifier.padding(14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(modifier = Modifier.size(28.dp).clip(RoundedCornerShape(8.dp)).background(color.copy(alpha = 0.1f)), contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = color, modifier = Modifier.size(14.dp))
            }
            Spacer(Modifier.height(8.dp))
            Text(value, color = color, fontWeight = FontWeight.Bold, fontSize = 20.sp, letterSpacing = (-0.5).sp)
            Spacer(Modifier.height(2.dp))
            Text(label, color = TextSecondary, fontSize = 10.sp)
        }
    }
}

@Composable
private fun DomainBar(rank: Int, hostname: String, count: Int, maxCount: Int) {
    val fraction = if (maxCount > 0) count.toFloat() / maxCount else 0f
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp, horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("$rank.", color = TextDim, modifier = Modifier.width(24.dp), fontSize = 11.sp)
        Box(modifier = Modifier.weight(1f)) {
            Box(modifier = Modifier.fillMaxWidth(fraction.coerceIn(0.04f, 1f)).height(22.dp).background(Red.copy(alpha = 0.08f), RoundedCornerShape(4.dp)))
            Text(hostname, modifier = Modifier.align(Alignment.CenterStart).padding(start = 8.dp), color = TextPrimary, fontSize = 11.sp, maxLines = 1, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
        }
        Spacer(Modifier.width(8.dp))
        Text(NumberFormat.getNumberInstance().format(count), color = Red, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(44.dp))
    }
}
