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
    pcapMessage: String,
    isExportingPcap: Boolean,
    onExportPcap: (String) -> Unit
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
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            OutlinedButton(
                onClick = { onExportPcap("all") },
                enabled = !isExportingPcap,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Teal)
            ) {
                if (isExportingPcap) CircularProgressIndicator(Modifier.size(12.dp), color = Teal, strokeWidth = 1.5.dp)
                else Icon(Icons.Filled.SaveAlt, null, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(4.dp))
                Text("Export PCAP", fontSize = 10.sp)
            }
        }
        if (pcapMessage.isNotBlank()) {
            Text(pcapMessage, color = TextDim, fontSize = 10.sp, modifier = Modifier.padding(top = 2.dp))
        }
    }
}
