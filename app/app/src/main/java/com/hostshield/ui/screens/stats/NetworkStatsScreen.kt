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
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.hostshield.service.NetworkStatsTracker
import com.hostshield.service.NetworkStatsTracker.AppNetStats
import com.hostshield.service.formatBytes
import com.hostshield.ui.components.HostShieldActionIconButton
import com.hostshield.ui.components.HostShieldBackHeader
import com.hostshield.ui.components.HostShieldEmptyState
import com.hostshield.ui.components.HostShieldMetricTile
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

    Column(modifier = Modifier.fillMaxSize().background(Black)) {
        HostShieldBackHeader(
            title = "Network stats",
            subtitle = "Traffic by app from Android usage counters",
            onBack = onBack,
            actions = {
                HostShieldActionIconButton(
                    icon = Icons.Filled.Refresh,
                    contentDescription = "Refresh network stats",
                    accent = Teal,
                    onClick = { viewModel.refresh() },
                )
            },
        )

        // Overview cards
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            HostShieldMetricTile(
                modifier = Modifier.weight(1f),
                label = "Download",
                value = formatBytes(totalRx),
                accent = Teal,
            )
            HostShieldMetricTile(
                modifier = Modifier.weight(1f),
                label = "Upload",
                value = formatBytes(totalTx),
                accent = Blue,
            )
            HostShieldMetricTile(
                modifier = Modifier.weight(1f),
                label = "Apps",
                value = stats.size.toString(),
                accent = Yellow,
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
                AppStatsRow(index + 1, stat, maxBytes)
                HorizontalDivider(color = Surface2.copy(alpha = 0.3f))
            }

            if (stats.isEmpty()) {
                item {
                    HostShieldEmptyState(
                        icon = Icons.Filled.DataUsage,
                        title = "No app traffic recorded",
                        message = "Grant usage access and refresh after apps have used the network.",
                        accent = Teal,
                        primaryActionLabel = "Refresh stats",
                        onPrimaryAction = { viewModel.refresh() },
                    )
                }
            }
        }
    }
}
@Composable
private fun AppStatsRow(
    rank: Int,
    stat: AppNetStats,
    maxBytes: Long
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
            formatBytes(stat.wifiRxBytes + stat.wifiTxBytes),
            modifier = Modifier.width(60.dp),
            color = TextSecondary, fontSize = 10.sp,
            textAlign = androidx.compose.ui.text.style.TextAlign.End
        )

        // Mobile
        Text(
            formatBytes(stat.mobileRxBytes + stat.mobileTxBytes),
            modifier = Modifier.width(60.dp),
            color = TextSecondary, fontSize = 10.sp,
            textAlign = androidx.compose.ui.text.style.TextAlign.End
        )

        // Total
        Text(
            formatBytes(stat.totalBytes),
            modifier = Modifier.width(64.dp),
            color = Teal, fontSize = 10.sp, fontWeight = FontWeight.SemiBold,
            textAlign = androidx.compose.ui.text.style.TextAlign.End
        )
    }
}
