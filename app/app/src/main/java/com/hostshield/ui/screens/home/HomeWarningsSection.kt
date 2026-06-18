package com.hostshield.ui.screens.home

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.hostshield.R
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hostshield.data.model.BlockMethod
import com.hostshield.service.VpnRecoveryAdvisory
import com.hostshield.ui.theme.*

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HomeWarningsSection(
    // Private DNS
    privateDnsWarning: String?,
    privateDnsSettingsIntent: Intent?,
    onDismissPrivateDns: () -> Unit,
    // Battery
    batteryWarning: String?,
    onRequestBatteryExemption: () -> Unit,
    onDismissBattery: () -> Unit,
    // Android 16 VPN stack recovery
    vpnRecoveryAdvisory: VpnRecoveryAdvisory?,
    canRestartDevice: Boolean,
    onRestartDevice: () -> Unit,
    onDismissVpnRecovery: () -> Unit,
    // Private Space
    privateSpaceWarning: String?,
    onDismissPrivateSpace: () -> Unit,
    // Query anomaly
    queryAnomalyWarning: String?,
    // Dropped queries
    droppedQueries: Int,
    // Feature pills
    isEnabled: Boolean,
    blockMethod: BlockMethod,
    dohEnabled: Boolean,
    dnsTrapEnabled: Boolean,
    firewalledApps: Int,
    networkFirewallActive: Boolean,
    // Live rates
    queriesPerMinute: Int,
    blocksPerMinute: Int,
    avgLatencyMs: Int,
    latencySparkline: List<Int>,
    context: Context
) {
    // Private DNS warning banner
    privateDnsWarning?.let {
        Spacer(Modifier.height(8.dp))
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = Yellow.copy(alpha = 0.08f),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .clip(RoundedCornerShape(12.dp))
                .semantics {
                    role = Role.Button
                    contentDescription = "Private DNS is active. Open Network settings to turn it off."
                }
                .clickable(role = Role.Button) {
                    try { privateDnsSettingsIntent?.let { intent -> context.startActivity(intent) } }
                    catch (_: Exception) { }
                }
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.Top
            ) {
                Icon(
                    Icons.Filled.Warning, null,
                    tint = Yellow,
                    modifier = Modifier.size(16.dp).padding(top = 2.dp)
                )
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.warning_private_dns_title),
                        color = Yellow,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 12.sp,
                        lineHeight = 16.sp
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        stringResource(R.string.warning_private_dns_message),
                        color = Yellow.copy(alpha = 0.8f),
                        fontSize = 11.sp,
                        lineHeight = 16.sp
                    )
                }
                IconButton(
                    onClick = onDismissPrivateDns,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(Icons.Filled.Close, stringResource(R.string.warning_private_dns_dismiss), tint = TextDim, modifier = Modifier.size(14.dp))
                }
            }
        }
    }

    // Battery optimization warning banner
    batteryWarning?.let {
        Spacer(Modifier.height(8.dp))
        val batteryA11yDesc = stringResource(R.string.warning_battery_a11y)
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = Peach.copy(alpha = 0.08f),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .clip(RoundedCornerShape(12.dp))
                .semantics {
                    role = Role.Button
                    contentDescription = batteryA11yDesc
                }
                .clickable(role = Role.Button) { onRequestBatteryExemption() }
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.Top
            ) {
                Icon(
                    Icons.Filled.BatteryAlert, null,
                    tint = Peach,
                    modifier = Modifier.size(16.dp).padding(top = 2.dp)
                )
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.warning_battery_title),
                        color = Peach,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 12.sp,
                        lineHeight = 16.sp
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        stringResource(R.string.warning_battery_message),
                        color = Peach.copy(alpha = 0.8f),
                        fontSize = 11.sp,
                        lineHeight = 16.sp
                    )
                }
                IconButton(
                    onClick = onDismissBattery,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(Icons.Filled.Close, stringResource(R.string.warning_battery_dismiss), tint = TextDim, modifier = Modifier.size(14.dp))
                }
            }
        }
    }

    // Android 16 always-on lockdown recovery advisory
    vpnRecoveryAdvisory?.let { advisory ->
        Spacer(Modifier.height(8.dp))
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = Red.copy(alpha = 0.09f),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .semantics {
                    contentDescription = "${advisory.title}. ${advisory.message}"
                }
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(verticalAlignment = Alignment.Top) {
                    Icon(
                        Icons.Filled.RestartAlt,
                        contentDescription = null,
                        tint = Red,
                        modifier = Modifier.size(17.dp).padding(top = 2.dp)
                    )
                    Spacer(Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            advisory.title,
                            color = Red,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 12.sp,
                            lineHeight = 16.sp
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            stringResource(if (canRestartDevice) R.string.warning_vpn_recovery_root else R.string.warning_vpn_recovery_no_root),
                            color = Red.copy(alpha = 0.82f),
                            fontSize = 11.sp,
                            lineHeight = 16.sp
                        )
                    }
                    IconButton(
                        onClick = onDismissVpnRecovery,
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(Icons.Filled.Close, stringResource(R.string.warning_vpn_recovery_dismiss), tint = TextDim, modifier = Modifier.size(14.dp))
                    }
                }
                if (canRestartDevice) {
                    Spacer(Modifier.height(10.dp))
                    val restartA11yDesc = stringResource(R.string.warning_vpn_recovery_restart_a11y)
                    Surface(
                        onClick = onRestartDevice,
                        shape = RoundedCornerShape(8.dp),
                        color = Red.copy(alpha = 0.18f),
                        modifier = Modifier
                            .align(Alignment.End)
                            .heightIn(min = 44.dp)
                            .semantics {
                                role = Role.Button
                                contentDescription = restartA11yDesc
                            }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Filled.RestartAlt, null, tint = Red, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.warning_vpn_recovery_restart), color = Red, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }
    }

    // Private Space / work profile VPN bypass warning
    privateSpaceWarning?.let { warning ->
        Spacer(Modifier.height(8.dp))
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = Red.copy(alpha = 0.08f),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.Top
            ) {
                Icon(
                    Icons.Filled.Security, null,
                    tint = Red,
                    modifier = Modifier.size(16.dp).padding(top = 2.dp)
                )
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.warning_private_space_title),
                        color = Red,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 12.sp,
                        lineHeight = 16.sp
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        warning,
                        color = Red.copy(alpha = 0.8f),
                        fontSize = 11.sp,
                        lineHeight = 16.sp
                    )
                }
                IconButton(
                    onClick = onDismissPrivateSpace,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(Icons.Filled.Close, stringResource(R.string.warning_private_space_dismiss), tint = TextDim, modifier = Modifier.size(14.dp))
                }
            }
        }
    }

    // Query rate anomaly warning
    queryAnomalyWarning?.let { warning ->
        Spacer(Modifier.height(8.dp))
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = Peach.copy(alpha = 0.08f),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp)
        ) {
            Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.Top) {
                Icon(Icons.AutoMirrored.Filled.TrendingUp, null, tint = Peach, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.warning_high_query_rate), color = Peach, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, lineHeight = 16.sp)
                    Text(warning, color = Peach.copy(alpha = 0.8f), fontSize = 11.sp, lineHeight = 16.sp)
                }
            }
        }
    }

    // Buffer overflow warning
    if (droppedQueries > 0) {
        Spacer(Modifier.height(8.dp))
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = Red.copy(alpha = 0.08f),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp)
        ) {
            Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.Top) {
                Icon(Icons.Filled.Warning, null, tint = Red, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(10.dp))
                Text(
                    pluralStringResource(R.plurals.warning_dropped_queries, droppedQueries, droppedQueries),
                    color = Red,
                    fontSize = 11.sp,
                    lineHeight = 16.sp
                )
            }
        }
    }

    // Feature status pills (VPN mode only)
    if (isEnabled && blockMethod == BlockMethod.VPN) {
        Spacer(Modifier.height(8.dp))
        FlowRow(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (dohEnabled) {
                FeatureBadge("DoH", Blue)
            }
            if (dnsTrapEnabled) {
                FeatureBadge("DNS Trap", Teal)
            }
            if (firewalledApps > 0) {
                FeatureBadge("$firewalledApps Firewalled", Red)
            }
            if (networkFirewallActive) {
                FeatureBadge("iptables", Peach)
            }
        }
    }

    // Live query rate + latency sparkline
    if (isEnabled && (queriesPerMinute > 0 || blocksPerMinute > 0)) {
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("$queriesPerMinute", color = Blue, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Text(" ${stringResource(R.string.unit_queries_per_min)}", color = TextDim, fontSize = 10.sp)
            Spacer(Modifier.width(16.dp))
            Text("$blocksPerMinute", color = Red, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Text(" ${stringResource(R.string.unit_blocks_per_min)}", color = TextDim, fontSize = 10.sp)
            if (avgLatencyMs > 0) {
                Spacer(Modifier.width(16.dp))
                Text("$avgLatencyMs", color = Peach, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                Text(" ${stringResource(R.string.unit_ms)}", color = TextDim, fontSize = 10.sp)
            }
        }
        // Latency sparkline
        if (latencySparkline.size >= 3) {
            Spacer(Modifier.height(6.dp))
            val sparklineColor = Peach.copy(alpha = 0.6f)
            Canvas(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 40.dp).height(24.dp)
            ) {
                val points = latencySparkline
                val maxVal = points.max().toFloat().coerceAtLeast(1f)
                val stepX = size.width / (points.size - 1).coerceAtLeast(1)
                val path = androidx.compose.ui.graphics.Path()
                points.forEachIndexed { i, v ->
                    val x = i * stepX
                    val y = size.height - (v / maxVal * size.height * 0.9f)
                    if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                drawPath(
                    path = path,
                    color = sparklineColor,
                    style = androidx.compose.ui.graphics.drawscope.Stroke(
                        width = 2f,
                        cap = androidx.compose.ui.graphics.StrokeCap.Round
                    )
                )
            }
        }
    }
}
