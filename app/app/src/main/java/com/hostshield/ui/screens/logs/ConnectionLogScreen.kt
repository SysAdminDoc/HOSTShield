package com.hostshield.ui.screens.logs

import androidx.compose.animation.*
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hostshield.data.database.ConnectionLogDao
import com.hostshield.data.database.FirewallTopApp
import com.hostshield.data.model.ConnectionLogEntry
import com.hostshield.service.NflogReader
import com.hostshield.ui.components.ConfirmDestructiveDialog
import com.hostshield.ui.components.HostShieldActionIconButton
import com.hostshield.ui.components.HostShieldBackHeader
import com.hostshield.ui.components.HostShieldEmptyState
import com.hostshield.ui.components.HostShieldMetricTile
import com.hostshield.ui.components.HostShieldSegmentOption
import com.hostshield.ui.components.HostShieldSegmentedTabs
import com.hostshield.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

enum class ConnLogTab { LIVE, TOP_APPS }

@Composable
fun ConnectionLogScreen(
    viewModel: ConnectionLogViewModel = hiltViewModel(),
    onBack: () -> Unit
) {
    val logs by viewModel.recentLogs.collectAsStateWithLifecycle()
    val blockedCount by viewModel.blockedCount.collectAsStateWithLifecycle()
    val blockedTodayCount by viewModel.blockedTodayCount.collectAsStateWithLifecycle()
    val topApps by viewModel.topBlockedApps.collectAsStateWithLifecycle()
    val isReading by viewModel.isReading.collectAsStateWithLifecycle()
    val liveCount by viewModel.liveCount.collectAsStateWithLifecycle()
    val tab by viewModel.tab.collectAsStateWithLifecycle()
    val timeFmt = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }
    var showClearLogsDialog by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize().background(Black)) {
        HostShieldBackHeader(
            title = "Connection log",
            subtitle = if (isReading) "$liveCount live blocks · $blockedCount total" else "NFLOG reader inactive",
            onBack = onBack,
            actions = {
                HostShieldActionIconButton(
                    icon = Icons.Filled.DeleteSweep,
                    contentDescription = "Clear connection log",
                    onClick = { showClearLogsDialog = true },
                    accent = Red,
                    enabled = logs.isNotEmpty(),
                )
            }
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            HostShieldMetricTile(
                value = formatCompactCount(blockedCount),
                label = "total blocked",
                accent = Red,
                modifier = Modifier.weight(1f),
            )
            HostShieldMetricTile(
                value = formatCompactCount(blockedTodayCount),
                label = "today",
                accent = Teal,
                modifier = Modifier.weight(1f),
            )
            HostShieldMetricTile(
                value = "${topApps.size}",
                label = "apps",
                accent = Blue,
                modifier = Modifier.weight(1f),
            )
        }

        Spacer(Modifier.height(8.dp))

        HostShieldSegmentedTabs(
            options = listOf(
                HostShieldSegmentOption(ConnLogTab.LIVE, "Live", Teal, Icons.Filled.ListAlt),
                HostShieldSegmentOption(ConnLogTab.TOP_APPS, "Top Apps", Red, Icons.Filled.Apps),
            ),
            selected = tab,
            onSelected = { viewModel.setTab(it) },
            modifier = Modifier.padding(horizontal = 16.dp),
            semanticsLabel = "Connection log view",
        )

        Spacer(Modifier.height(8.dp))

        when (tab) {
            ConnLogTab.LIVE -> {
                if (logs.isEmpty()) {
                    HostShieldEmptyState(
                        icon = Icons.Filled.Shield,
                        title = "No blocked connections yet",
                        message = "Apply network firewall rules from Firewall, then blocked root-mode traffic appears here.",
                        accent = Teal,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 20.dp),
                    )
                } else {
                    LazyColumn(contentPadding = PaddingValues(horizontal = 16.dp)) {
                        items(logs, key = { it.id }) { entry ->
                            ConnectionLogRow(entry, timeFmt)
                            HorizontalDivider(color = Surface2.copy(alpha = 0.3f))
                        }
                    }
                }
            }
            ConnLogTab.TOP_APPS -> {
                if (topApps.isEmpty()) {
                    HostShieldEmptyState(
                        icon = Icons.Filled.Apps,
                        title = "No blocked apps yet",
                        message = "Apps are ranked here after firewall rules block their network traffic.",
                        accent = Red,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 20.dp),
                    )
                } else {
                    LazyColumn(contentPadding = PaddingValues(horizontal = 16.dp)) {
                        items(topApps, key = { it.uid }) { app ->
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier.size(8.dp).clip(CircleShape).background(Red)
                                )
                                Spacer(Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        app.appLabel.ifBlank { app.packageName },
                                        color = TextPrimary, fontWeight = FontWeight.Medium,
                                        maxLines = 1, overflow = TextOverflow.Ellipsis
                                    )
                                    Text("UID ${app.uid}", color = TextDim, fontSize = 10.sp)
                                }
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = Red.copy(alpha = 0.12f)
                                ) {
                                    Text(
                                        "${app.cnt} blocked",
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                        color = Red, fontSize = 11.sp, fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                            HorizontalDivider(color = Surface2.copy(alpha = 0.3f))
                        }
                    }
                }
            }
        }
    }

    if (showClearLogsDialog) {
        ConfirmDestructiveDialog(
            title = "Clear connection log?",
            body = "This removes the local firewall connection history. Active firewall rules and protection settings stay unchanged.",
            confirmLabel = "Clear log",
            onConfirm = { viewModel.clearLogs() },
            onDismiss = { showClearLogsDialog = false },
        )
    }
}

@Composable
private fun ConnectionLogRow(entry: ConnectionLogEntry, timeFmt: SimpleDateFormat) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Protocol badge
        Surface(
            shape = RoundedCornerShape(4.dp),
            color = when (entry.protocol) {
                "TCP" -> Blue.copy(alpha = 0.12f)
                "UDP" -> Teal.copy(alpha = 0.12f)
                else -> Surface3
            }
        ) {
            Text(
                entry.protocol,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                color = when (entry.protocol) {
                    "TCP" -> Blue
                    "UDP" -> Teal
                    else -> TextDim
                },
                fontSize = 8.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace
            )
        }

        Spacer(Modifier.width(8.dp))

        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    entry.destination,
                    color = TextPrimary, fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (entry.port > 0) {
                    Text(
                        ":${entry.port}",
                        color = Teal, fontSize = 12.sp, fontFamily = FontFamily.Monospace
                    )
                }
            }
            Row {
                Text(
                    entry.appLabel.ifBlank { entry.packageName.ifBlank { "UID ${entry.uid}" } },
                    color = TextDim, fontSize = 10.sp,
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
                if (entry.interfaceName.isNotBlank()) {
                    val ifLabel = interfaceLabel(entry.interfaceName)
                    Text(" via $ifLabel", color = TextDim.copy(alpha = 0.5f), fontSize = 10.sp)
                }
            }
        }

        Spacer(Modifier.width(8.dp))

        Text(
            timeFmt.format(Date(entry.timestamp)),
            color = TextDim.copy(alpha = 0.6f),
            fontSize = 9.sp, fontFamily = FontFamily.Monospace
        )
    }
}

private fun formatCompactCount(n: Int): String = when {
    n >= 1_000_000 -> "${n / 1_000_000}.${(n % 1_000_000) / 100_000}M"
    n >= 1_000 -> "${n / 1_000}.${(n % 1_000) / 100}K"
    else -> n.toString()
}

/** Map raw interface names to human-readable labels. */
private fun interfaceLabel(iface: String): String = when {
    iface.startsWith("wlan") -> "WiFi"
    iface.startsWith("rmnet") || iface.startsWith("ccmni") -> "Mobile"
    iface.startsWith("tun") || iface.startsWith("vpn") -> "VPN"
    iface.startsWith("bt-") || iface.startsWith("bnep") -> "Bluetooth"
    iface.startsWith("eth") || iface.startsWith("usb") -> "Ethernet"
    iface.startsWith("lo") -> "Loopback"
    iface.startsWith("dummy") -> "Dummy"
    iface.startsWith("p2p") -> "WiFi Direct"
    iface.startsWith("swlan") -> "WiFi Hotspot"
    else -> iface
}
