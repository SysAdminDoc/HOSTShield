package com.hostshield.ui.screens.logs

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
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hostshield.data.database.AppDomainStat
import com.hostshield.data.database.DnsLogDao
import com.hostshield.data.model.DnsLogEntry
import com.hostshield.ui.components.HostShieldBackHeader
import com.hostshield.ui.components.HostShieldEmptyState
import com.hostshield.ui.components.HostShieldMetricTile
import com.hostshield.ui.components.HostShieldSegmentOption
import com.hostshield.ui.components.HostShieldSegmentedTabs
import com.hostshield.ui.screens.home.GlassCard
import com.hostshield.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun AppLogsScreen(
    packageName: String,
    viewModel: AppLogsViewModel = hiltViewModel(),
    onBack: () -> Unit = {}
) {
    val logs by viewModel.recentLogs.collectAsStateWithLifecycle()
    val domains by viewModel.domainStats.collectAsStateWithLifecycle()
    var showDomains by remember { mutableStateOf(true) }
    val timeFmt = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }

    Column(modifier = Modifier.fillMaxSize().background(Black)) {
        HostShieldBackHeader(
            title = "App DNS Log",
            subtitle = "$packageName · ${logs.size} queries",
            onBack = onBack,
        )

        val blocked = logs.count { it.blocked }
        val allowed = logs.size - blocked
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            HostShieldMetricTile("$allowed", "allowed", Green, Modifier.weight(1f))
            HostShieldMetricTile("$blocked", "blocked", Red, Modifier.weight(1f))
            HostShieldMetricTile("${domains.size}", "domains", Blue, Modifier.weight(1f))
        }

        Spacer(Modifier.height(8.dp))

        HostShieldSegmentedTabs(
            options = listOf(
                HostShieldSegmentOption(true, "Domains", Teal, Icons.Filled.Dns),
                HostShieldSegmentOption(false, "Timeline", Blue, Icons.Filled.History),
            ),
            selected = showDomains,
            onSelected = { showDomains = it },
            modifier = Modifier.padding(horizontal = 16.dp),
            semanticsLabel = "App DNS log view",
        )

        Spacer(Modifier.height(8.dp))

        if (showDomains) {
            // Domain breakdown
            if (domains.isEmpty()) {
                HostShieldEmptyState(
                    icon = Icons.Filled.Dns,
                    title = "No domains recorded",
                    message = "Domains appear here after this app makes DNS requests while HostShield is active.",
                    accent = Teal,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 20.dp),
                )
            } else {
                LazyColumn(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)) {
                    items(domains, key = { it.hostname }) { stat ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier.size(6.dp).clip(CircleShape)
                                    .background(if (stat.blocked) Red else Green)
                            )
                            Spacer(Modifier.width(10.dp))
                            Text(
                                stat.hostname,
                                color = TextPrimary, fontSize = 12.sp, fontFamily = FontFamily.Monospace,
                                maxLines = 1, overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            Text("${stat.cnt}x", color = TextDim, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                        }
                        HorizontalDivider(color = Surface2.copy(alpha = 0.3f))
                    }
                }
            }
        } else {
            // Timeline view
            if (logs.isEmpty()) {
                HostShieldEmptyState(
                    icon = Icons.Filled.History,
                    title = "No recent queries",
                    message = "The timeline fills as this app sends DNS traffic through HostShield.",
                    accent = Blue,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 20.dp),
                )
            } else {
                LazyColumn(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)) {
                    items(logs, key = { it.id }) { entry ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier.size(6.dp).clip(CircleShape)
                                    .background(if (entry.blocked) Red else Green)
                            )
                            Spacer(Modifier.width(10.dp))
                            Text(
                                entry.hostname,
                                color = TextPrimary, fontSize = 11.sp, fontFamily = FontFamily.Monospace,
                                maxLines = 1, overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            if (entry.responseTimeMs > 0) {
                                Text("${entry.responseTimeMs}ms", color = TextDim.copy(alpha = 0.5f), fontSize = 9.sp)
                                Spacer(Modifier.width(6.dp))
                            }
                            Text(
                                timeFmt.format(Date(entry.timestamp)),
                                color = TextDim.copy(alpha = 0.6f), fontSize = 9.sp, fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }
            }
        }
    }
}
