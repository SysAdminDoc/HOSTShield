package com.hostshield.ui.screens.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.hostshield.service.NetworkStatsTracker
import com.hostshield.service.NetworkStatsTracker.AppNetStats
import com.hostshield.ui.theme.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class NetworkStatsViewModel @Inject constructor(
    val tracker: NetworkStatsTracker
) : ViewModel() {
    val appStats = tracker.appStats
    val totalRx = tracker.totalRx
    val totalTx = tracker.totalTx

    init { refresh() }

    fun refresh() {
        viewModelScope.launch { tracker.refresh() }
    }
}

@Composable
fun NetworkStatsScreen(
    viewModel: NetworkStatsViewModel = hiltViewModel(),
    onBack: () -> Unit
) {
    val stats by viewModel.appStats.collectAsStateWithLifecycle()
    val totalRx by viewModel.totalRx.collectAsStateWithLifecycle()
    val totalTx by viewModel.totalTx.collectAsStateWithLifecycle()
    val tracker = viewModel.tracker

    Column(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Filled.ArrowBack, "Back", tint = TextPrimary)
            }
            Text("Network Stats", style = MaterialTheme.typography.titleLarge, color = TextPrimary,
                modifier = Modifier.weight(1f))
            IconButton(onClick = { viewModel.refresh() }) {
                Icon(Icons.Filled.Refresh, "Refresh", tint = Teal)
            }
        }

        // Overview cards
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OverviewCard(
                modifier = Modifier.weight(1f),
                icon = Icons.Filled.ArrowDownward,
                label = "Download",
                value = tracker.formatBytes(totalRx),
                color = Teal
            )
            OverviewCard(
                modifier = Modifier.weight(1f),
                icon = Icons.Filled.ArrowUpward,
                label = "Upload",
                value = tracker.formatBytes(totalTx),
                color = Blue
            )
            OverviewCard(
                modifier = Modifier.weight(1f),
                icon = Icons.Filled.Apps,
                label = "Apps",
                value = stats.size.toString(),
                color = Yellow
            )
        }

        Spacer(Modifier.height(12.dp))

        // Column headers
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
            Text("#", modifier = Modifier.width(24.dp), color = TextDim, fontSize = 9.sp, fontWeight = FontWeight.Bold)
            Text("App", modifier = Modifier.weight(1f), color = TextDim, fontSize = 9.sp, fontWeight = FontWeight.Bold)
            Text("WiFi", modifier = Modifier.width(60.dp), color = TextDim, fontSize = 9.sp, fontWeight = FontWeight.Bold,
                textAlign = androidx.compose.ui.text.style.TextAlign.End)
            Text("Mobile", modifier = Modifier.width(60.dp), color = TextDim, fontSize = 9.sp, fontWeight = FontWeight.Bold,
                textAlign = androidx.compose.ui.text.style.TextAlign.End)
            Text("Total", modifier = Modifier.width(64.dp), color = TextDim, fontSize = 9.sp, fontWeight = FontWeight.Bold,
                textAlign = androidx.compose.ui.text.style.TextAlign.End)
        }

        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = Surface3)

        val maxBytes = stats.firstOrNull()?.totalBytes ?: 1L

        LazyColumn(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)) {
            itemsIndexed(stats, key = { _, s -> s.uid }) { index, stat ->
                AppStatsRow(index + 1, stat, maxBytes, tracker)
                HorizontalDivider(color = Surface2.copy(alpha = 0.3f))
            }

            if (stats.isEmpty()) {
                item {
                    Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Filled.DataUsage, null, tint = TextDim, modifier = Modifier.size(48.dp))
                            Spacer(Modifier.height(8.dp))
                            Text("No network stats available", color = TextDim)
                            Text("Grant Usage Access permission in Settings", color = TextDim, fontSize = 11.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AppStatsRow(
    rank: Int,
    stat: AppNetStats,
    maxBytes: Long,
    tracker: NetworkStatsTracker
) {
    val ratio = if (maxBytes > 0) stat.totalBytes.toFloat() / maxBytes else 0f

    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Rank
        Text(
            "$rank",
            modifier = Modifier.width(24.dp),
            color = when (rank) { 1 -> Yellow; 2 -> TextSecondary; 3 -> Color(0xFFCD7F32); else -> TextDim },
            fontSize = 11.sp, fontWeight = if (rank <= 3) FontWeight.Bold else FontWeight.Normal
        )

        // App info + bar
        Column(modifier = Modifier.weight(1f)) {
            Text(
                stat.appLabel,
                color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Medium,
                maxLines = 1, overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(2.dp))
            // Usage bar
            Box(
                modifier = Modifier.fillMaxWidth().height(3.dp)
                    .clip(RoundedCornerShape(2.dp)).background(Surface3)
            ) {
                Box(
                    modifier = Modifier.fillMaxWidth(ratio).fillMaxHeight()
                        .clip(RoundedCornerShape(2.dp))
                        .background(Brush.horizontalGradient(listOf(Teal, Blue)))
                )
            }
        }

        Spacer(Modifier.width(8.dp))

        // WiFi
        Text(
            tracker.formatBytes(stat.wifiRxBytes + stat.wifiTxBytes),
            modifier = Modifier.width(60.dp),
            color = TextSecondary, fontSize = 10.sp,
            textAlign = androidx.compose.ui.text.style.TextAlign.End
        )

        // Mobile
        Text(
            tracker.formatBytes(stat.mobileRxBytes + stat.mobileTxBytes),
            modifier = Modifier.width(60.dp),
            color = TextSecondary, fontSize = 10.sp,
            textAlign = androidx.compose.ui.text.style.TextAlign.End
        )

        // Total
        Text(
            tracker.formatBytes(stat.totalBytes),
            modifier = Modifier.width(64.dp),
            color = Teal, fontSize = 10.sp, fontWeight = FontWeight.SemiBold,
            textAlign = androidx.compose.ui.text.style.TextAlign.End
        )
    }
}

@Composable
private fun OverviewCard(
    modifier: Modifier,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String,
    color: Color
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = color.copy(alpha = 0.08f)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(icon, null, tint = color, modifier = Modifier.size(18.dp))
            Spacer(Modifier.height(4.dp))
            Text(value, color = color, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Text(label, color = TextDim, fontSize = 9.sp)
        }
    }
}
