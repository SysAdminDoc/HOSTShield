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
import androidx.compose.ui.unit.sp
import com.hostshield.ui.theme.*

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
    SettingsSection("VPN", Icons.Filled.VpnLock, Teal) {
        SettingsRow("App exclusions", "Bypass VPN for specific apps", Icons.Filled.AppBlocking, onClick = onNavigateToAppExclusions)
        Spacer(Modifier.height(4.dp))
        SettingsRow(
            "Per-app firewall",
            if (firewalledApps > 0) "$firewalledApps apps firewalled" else "Block all DNS for specific apps",
            Icons.Filled.Block,
            onClick = onNavigateToFirewall
        )
    }

    // Content Protection
    SettingsSection("Protection", Icons.Filled.FilterList, Mauve) {
        SettingsRow("Content filtering", "Block categories: adult, gambling, social, etc.", Icons.Filled.FilterList, onClick = onNavigateToContentFilter)
        Spacer(Modifier.height(4.dp))
        SettingsRow("Parental controls", "Age profiles with PIN lock", Icons.Filled.AdminPanelSettings, onClick = onNavigateToParentalControls)
    }

    // Network Firewall (iptables)
    SettingsSection("Network Firewall", Icons.Filled.Security, Red) {
        SettingsRow(
            "Connection log",
            "View blocked connections from iptables",
            Icons.AutoMirrored.Filled.List,
            onClick = onNavigateToConnectionLog
        )
        Spacer(Modifier.height(4.dp))
        SettingsRow(
            "DNS tools",
            "DNS cache, lookup, diagnostics",
            Icons.Filled.Dns,
            onClick = onNavigateToDnsTools
        )
        Spacer(Modifier.height(4.dp))
        SettingsRow(
            "Network usage",
            "Per-app data usage since boot",
            Icons.Filled.DataUsage,
            onClick = onNavigateToNetworkStats
        )
        Spacer(Modifier.height(4.dp))

        // PCAP export
        when (pcapExport) {
            PcapExportState.Idle -> {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    OutlinedButton(
                        onClick = { onExportPcap("all") },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Teal)
                    ) {
                        Icon(Icons.Filled.SaveAlt, null, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Export PCAP", fontSize = 10.sp)
                    }
                }
            }
            PcapExportState.Exporting -> {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    CircularProgressIndicator(Modifier.size(14.dp), color = Teal, strokeWidth = 1.5.dp)
                    Text("Generating PCAP…", color = TextDim, fontSize = 11.sp)
                }
            }
            is PcapExportState.Ready -> {
                val sizeKb = pcapExport.sizeBytes / 1024
                Text(
                    "Contains DNS hostnames and connection destinations",
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
                        Text("Share (${sizeKb} KB)", fontSize = 10.sp)
                    }
                    OutlinedButton(
                        onClick = onSavePcap,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Blue)
                    ) {
                        Icon(Icons.Filled.Save, null, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Save As", fontSize = 10.sp)
                    }
                    OutlinedButton(
                        onClick = onDismissPcap,
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = TextDim)
                    ) {
                        Icon(Icons.Filled.Close, null, modifier = Modifier.size(14.dp))
                    }
                }
            }
            PcapExportState.Empty -> {
                Text("No blocked entries to export", color = TextDim, fontSize = 10.sp, modifier = Modifier.padding(vertical = 4.dp))
            }
            is PcapExportState.Failed -> {
                Text(pcapExport.error, color = Red, fontSize = 10.sp, modifier = Modifier.padding(vertical = 4.dp))
                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    OutlinedButton(
                        onClick = { onExportPcap("all") },
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Teal)
                    ) {
                        Text("Retry", fontSize = 10.sp)
                    }
                }
            }
        }
    }
}
