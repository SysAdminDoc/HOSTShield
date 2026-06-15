package com.hostshield.ui.screens.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.sp
import com.hostshield.R
import com.hostshield.ui.theme.*

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ProtectionSettingsSection(
    // VPN
    firewalledApps: Int,
    onNavigateToAppExclusions: () -> Unit,
    onNavigateToFirewall: () -> Unit,
    // Content Protection
    onNavigateToContentFilter: () -> Unit,
    onNavigateToParentalControls: () -> Unit,
    // Network Firewall
    onNavigateToConnectionLog: () -> Unit,
    onNavigateToDnsTools: () -> Unit,
    onNavigateToNetworkStats: () -> Unit,
    // PCAP
    pcapExport: PcapExportState,
    onExportPcap: (String) -> Unit,
    onSharePcap: () -> Unit,
    onSavePcap: () -> Unit,
    onDismissPcap: () -> Unit
) {
    // VPN Settings
    SettingsSection(stringResource(R.string.section_vpn), Icons.Filled.VpnLock, Teal) {
        SettingsRow(stringResource(R.string.protection_app_exclusions), stringResource(R.string.protection_app_exclusions_sub), Icons.Filled.AppBlocking, onClick = onNavigateToAppExclusions)
        Spacer(Modifier.height(4.dp))
        SettingsRow(
            stringResource(R.string.protection_per_app_firewall),
            if (firewalledApps > 0) stringResource(R.string.protection_apps_firewalled, firewalledApps) else stringResource(R.string.protection_per_app_firewall_sub),
            Icons.Filled.Block,
            onClick = onNavigateToFirewall
        )
    }

    // Content Protection
    SettingsSection(stringResource(R.string.section_protection), Icons.Filled.FilterList, Mauve) {
        SettingsRow(stringResource(R.string.protection_content_filtering), stringResource(R.string.protection_content_filtering_sub), Icons.Filled.FilterList, onClick = onNavigateToContentFilter)
        Spacer(Modifier.height(4.dp))
        SettingsRow(stringResource(R.string.protection_parental_controls), stringResource(R.string.protection_parental_controls_sub), Icons.Filled.AdminPanelSettings, onClick = onNavigateToParentalControls)
    }

    // Network Firewall (iptables)
    SettingsSection(stringResource(R.string.section_network_firewall), Icons.Filled.Security, Red) {
        SettingsRow(
            stringResource(R.string.protection_connection_log),
            stringResource(R.string.protection_connection_log_sub),
            Icons.AutoMirrored.Filled.List,
            onClick = onNavigateToConnectionLog
        )
        Spacer(Modifier.height(4.dp))
        SettingsRow(
            stringResource(R.string.protection_dns_tools),
            stringResource(R.string.protection_dns_tools_sub),
            Icons.Filled.Dns,
            onClick = onNavigateToDnsTools
        )
        Spacer(Modifier.height(4.dp))
        SettingsRow(
            stringResource(R.string.protection_network_usage),
            stringResource(R.string.protection_network_usage_sub),
            Icons.Filled.DataUsage,
            onClick = onNavigateToNetworkStats
        )
        Spacer(Modifier.height(4.dp))

        // PCAP export
        when (pcapExport) {
            PcapExportState.Idle -> {
                Text(
                    stringResource(R.string.pcap_privacy_warning),
                    color = Peach,
                    fontSize = 10.sp,
                    lineHeight = 14.sp,
                    modifier = Modifier.padding(top = 2.dp, bottom = 4.dp)
                )
                FlowRow(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    PcapExportButton(stringResource(R.string.pcap_export_all), "all", onExportPcap)
                    PcapExportButton(stringResource(R.string.pcap_export_dns), "dns", onExportPcap)
                    PcapExportButton(stringResource(R.string.pcap_export_firewall), "firewall", onExportPcap)
                }
            }
            PcapExportState.Exporting -> {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    CircularProgressIndicator(Modifier.size(14.dp), color = Teal, strokeWidth = 1.5.dp)
                    Text(stringResource(R.string.pcap_generating), color = TextDim, fontSize = 11.sp)
                }
            }
            is PcapExportState.Ready -> {
                val sizeKb = pcapExport.sizeBytes / 1024
                Text(
                    stringResource(R.string.pcap_ready_summary, pcapExport.mode, pcapExport.fileName),
                    color = TextSecondary,
                    fontSize = 10.sp,
                    lineHeight = 14.sp,
                    modifier = Modifier.padding(vertical = 2.dp)
                )
                Text(
                    stringResource(R.string.pcap_privacy_warning),
                    color = Peach,
                    fontSize = 10.sp,
                    modifier = Modifier.padding(vertical = 2.dp)
                )
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    OutlinedButton(
                        onClick = onSharePcap,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Teal)
                    ) {
                        Icon(Icons.Filled.Share, null, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("${stringResource(R.string.action_share)} (${sizeKb} KB)", fontSize = 10.sp)
                    }
                    OutlinedButton(
                        onClick = onSavePcap,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Blue)
                    ) {
                        Icon(Icons.Filled.Save, null, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(R.string.action_save_as), fontSize = 10.sp)
                    }
                    OutlinedButton(
                        onClick = onDismissPcap,
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = TextDim),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp)
                    ) {
                        Icon(
                            Icons.Filled.Close,
                            stringResource(R.string.action_discard),
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }
            PcapExportState.Empty -> {
                Text(stringResource(R.string.pcap_empty), color = TextDim, fontSize = 10.sp, modifier = Modifier.padding(vertical = 4.dp))
                OutlinedButton(
                    onClick = onDismissPcap,
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = TextDim)
                ) {
                    Text(stringResource(R.string.action_discard), fontSize = 10.sp)
                }
            }
            is PcapExportState.Failed -> {
                Text(pcapExport.error, color = Red, fontSize = 10.sp, modifier = Modifier.padding(vertical = 4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    OutlinedButton(
                        onClick = { onExportPcap("all") },
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Teal)
                    ) {
                        Text(stringResource(R.string.action_retry), fontSize = 10.sp)
                    }
                    OutlinedButton(
                        onClick = onDismissPcap,
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = TextDim)
                    ) {
                        Text(stringResource(R.string.action_discard), fontSize = 10.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun PcapExportButton(
    label: String,
    mode: String,
    onExportPcap: (String) -> Unit
) {
    OutlinedButton(
        onClick = { onExportPcap(mode) },
        shape = RoundedCornerShape(8.dp),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = Teal),
        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp)
    ) {
        Icon(Icons.Filled.SaveAlt, null, modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(4.dp))
        Text(label, fontSize = 10.sp)
    }
}
