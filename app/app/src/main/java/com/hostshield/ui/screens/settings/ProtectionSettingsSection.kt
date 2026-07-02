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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
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
    // LAN DNS
    lanDnsEnabled: Boolean,
    lanDnsRunning: Boolean,
    lanDnsPort: Int,
    lanDnsAllowExternalClients: Boolean,
    lanDnsQueriesHandled: Int,
    lanDnsQueriesBlocked: Int,
    lanDnsStatusMessage: String,
    onLanDnsEnabledChange: (Boolean) -> Unit,
    onLanDnsPortChange: (String) -> Unit,
    onLanDnsAllowExternalClientsChange: (Boolean) -> Unit,
    // PCAP
    pcapExport: PcapExportState,
    onExportPcap: (String) -> Unit,
    onSharePcap: () -> Unit,
    onSavePcap: () -> Unit,
    onDismissPcap: () -> Unit,
    // Evidence JSONL
    evidenceJsonlExport: EvidenceJsonlExportState,
    onExportEvidenceJsonl: (String, Int, String, String, Boolean) -> Unit,
    onShareEvidenceJsonl: () -> Unit,
    onSaveEvidenceJsonl: () -> Unit,
    onDismissEvidenceJsonl: () -> Unit
) {
    var evidenceDays by remember { mutableIntStateOf(7) }
    var evidenceQuery by remember { mutableStateOf("") }
    var evidenceAppFilter by remember { mutableStateOf("") }
    var evidenceRedacted by remember { mutableStateOf(true) }
    var lanDnsPortText by remember(lanDnsPort) { mutableStateOf(lanDnsPort.toString()) }

    // VPN Settings
    SettingsSection(stringResource(R.string.section_vpn), Icons.Filled.VpnLock, Teal) {
        SettingsRow(stringResource(R.string.protection_app_exclusions), stringResource(R.string.protection_app_exclusions_sub), Icons.Filled.AppBlocking, onClick = onNavigateToAppExclusions)
        Spacer(Modifier.height(4.dp))
        SettingsRow(
            stringResource(R.string.protection_per_app_firewall),
            if (firewalledApps > 0) {
                pluralStringResource(
                    R.plurals.protection_apps_firewalled,
                    firewalledApps,
                    firewalledApps
                )
            } else {
                stringResource(R.string.protection_per_app_firewall_sub)
            },
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
    }

    SettingsSection(stringResource(R.string.section_lan_dns), Icons.Filled.Dns, Green) {
        SettingsToggle(
            stringResource(R.string.lan_dns_enable),
            stringResource(R.string.lan_dns_enable_sub),
            Icons.Filled.Router,
            lanDnsEnabled,
            onLanDnsEnabledChange
        )
        Spacer(Modifier.height(6.dp))
        Text(
            if (lanDnsRunning) {
                stringResource(
                    R.string.lan_dns_status_running,
                    lanDnsPort,
                    lanDnsQueriesHandled,
                    lanDnsQueriesBlocked
                )
            } else {
                lanDnsStatusMessage
            },
            color = if (lanDnsRunning) Green else TextDim,
            fontSize = 11.sp,
            lineHeight = 15.sp
        )
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = lanDnsPortText,
                onValueChange = { value ->
                    lanDnsPortText = value.filter(Char::isDigit).take(5)
                },
                modifier = Modifier.weight(1f),
                singleLine = true,
                label = { Text(stringResource(R.string.lan_dns_port), fontSize = 11.sp) },
                leadingIcon = { Icon(Icons.Filled.SettingsEthernet, null, modifier = Modifier.size(16.dp), tint = TextDim) },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Green,
                    unfocusedBorderColor = Surface3,
                    cursorColor = Green,
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary,
                    focusedLabelColor = TextSecondary,
                    unfocusedLabelColor = TextDim
                )
            )
            IconButton(
                onClick = { onLanDnsPortChange(lanDnsPortText) },
                modifier = Modifier.size(44.dp)
            ) {
                Icon(
                    Icons.Filled.Check,
                    stringResource(R.string.lan_dns_apply_port),
                    tint = Green
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        SettingsToggle(
            stringResource(R.string.lan_dns_allow_external),
            stringResource(R.string.lan_dns_allow_external_sub),
            Icons.Filled.Public,
            lanDnsAllowExternalClients,
            onLanDnsAllowExternalClientsChange
        )
        Spacer(Modifier.height(6.dp))
        Text(
            stringResource(R.string.lan_dns_permission_note),
            color = Peach,
            fontSize = 11.sp,
            lineHeight = 15.sp
        )
    }

    SettingsSection(stringResource(R.string.section_packet_evidence), Icons.Filled.SaveAlt, Red) {
        // PCAP export
        when (pcapExport) {
            PcapExportState.Idle -> {
                Text(
                    stringResource(R.string.pcap_privacy_warning),
                    color = Peach,
                    fontSize = 11.sp,
                    lineHeight = 15.sp,
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
                val sizeKb = (pcapExport.sizeBytes + 1023) / 1024
                Text(
                    stringResource(R.string.pcap_ready_summary, pcapExport.mode, pcapExport.fileName),
                    color = TextSecondary,
                    fontSize = 11.sp,
                    lineHeight = 15.sp,
                    modifier = Modifier.padding(vertical = 2.dp)
                )
                Text(
                    stringResource(R.string.pcap_privacy_warning),
                    color = Peach,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(vertical = 2.dp)
                )
                FlowRow(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    OutlinedButton(
                        onClick = onSharePcap,
                        modifier = Modifier.heightIn(min = 44.dp),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Teal)
                    ) {
                        Icon(Icons.Filled.Share, null, modifier = Modifier.size(15.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("${stringResource(R.string.action_share)} (${sizeKb} KB)", fontSize = 11.sp)
                    }
                    OutlinedButton(
                        onClick = onSavePcap,
                        modifier = Modifier.heightIn(min = 44.dp),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Blue)
                    ) {
                        Icon(Icons.Filled.Save, null, modifier = Modifier.size(15.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(R.string.action_save_as), fontSize = 11.sp)
                    }
                    OutlinedButton(
                        onClick = onDismissPcap,
                        modifier = Modifier.heightIn(min = 44.dp),
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
                Text(stringResource(R.string.pcap_empty), color = TextDim, fontSize = 11.sp, modifier = Modifier.padding(vertical = 4.dp))
                OutlinedButton(
                    onClick = onDismissPcap,
                    modifier = Modifier.heightIn(min = 44.dp),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = TextDim)
                ) {
                    Text(stringResource(R.string.action_discard), fontSize = 11.sp)
                }
            }
            is PcapExportState.Failed -> {
                Text(pcapExport.error, color = Red, fontSize = 11.sp, lineHeight = 15.sp, modifier = Modifier.padding(vertical = 4.dp))
                FlowRow(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    OutlinedButton(
                        onClick = { onExportPcap("all") },
                        modifier = Modifier.heightIn(min = 44.dp),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Teal)
                    ) {
                        Text(stringResource(R.string.action_retry), fontSize = 11.sp)
                    }
                    OutlinedButton(
                        onClick = onDismissPcap,
                        modifier = Modifier.heightIn(min = 44.dp),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = TextDim)
                    ) {
                        Text(stringResource(R.string.action_discard), fontSize = 11.sp)
                    }
                }
            }
        }

        Spacer(Modifier.height(8.dp))
        HorizontalDivider(color = Surface3.copy(alpha = 0.7f))
        Spacer(Modifier.height(8.dp))

        Text(
            stringResource(R.string.evidence_jsonl_title),
            color = TextPrimary,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            stringResource(
                if (evidenceRedacted) {
                    R.string.evidence_jsonl_privacy_redacted
                } else {
                    R.string.evidence_jsonl_privacy_raw
                }
            ),
            color = if (evidenceRedacted) TextDim else Peach,
            fontSize = 11.sp,
            lineHeight = 15.sp,
            modifier = Modifier.padding(top = 2.dp, bottom = 4.dp)
        )

        when (evidenceJsonlExport) {
            EvidenceJsonlExportState.Idle -> {
                EvidenceJsonlControls(
                    days = evidenceDays,
                    query = evidenceQuery,
                    appFilter = evidenceAppFilter,
                    redacted = evidenceRedacted,
                    onDaysChange = { evidenceDays = it },
                    onQueryChange = { evidenceQuery = it.take(120) },
                    onAppFilterChange = { evidenceAppFilter = it.take(120) },
                    onRedactedChange = { evidenceRedacted = it },
                    onExport = { mode ->
                        onExportEvidenceJsonl(mode, evidenceDays, evidenceQuery, evidenceAppFilter, evidenceRedacted)
                    }
                )
            }
            EvidenceJsonlExportState.Exporting -> {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    CircularProgressIndicator(Modifier.size(14.dp), color = Teal, strokeWidth = 1.5.dp)
                    Text(stringResource(R.string.evidence_jsonl_generating), color = TextDim, fontSize = 11.sp)
                }
            }
            is EvidenceJsonlExportState.Ready -> {
                val sizeKb = (evidenceJsonlExport.sizeBytes + 1023) / 1024
                Text(
                    stringResource(
                        R.string.evidence_jsonl_ready_summary,
                        evidenceJsonlExport.mode,
                        evidenceJsonlExport.rowCount,
                        evidenceJsonlExport.chunkCount
                    ),
                    color = TextSecondary,
                    fontSize = 11.sp,
                    lineHeight = 15.sp,
                    modifier = Modifier.padding(vertical = 2.dp)
                )
                if (evidenceJsonlExport.truncated) {
                    Text(
                        stringResource(R.string.evidence_jsonl_truncated),
                        color = Peach,
                        fontSize = 11.sp,
                        lineHeight = 15.sp,
                        modifier = Modifier.padding(vertical = 2.dp)
                    )
                }
                FlowRow(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    OutlinedButton(
                        onClick = onShareEvidenceJsonl,
                        modifier = Modifier.heightIn(min = 44.dp),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Teal)
                    ) {
                        Icon(Icons.Filled.Share, null, modifier = Modifier.size(15.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("${stringResource(R.string.action_share)} (${sizeKb} KB)", fontSize = 11.sp)
                    }
                    OutlinedButton(
                        onClick = onSaveEvidenceJsonl,
                        modifier = Modifier.heightIn(min = 44.dp),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Blue)
                    ) {
                        Icon(Icons.Filled.Save, null, modifier = Modifier.size(15.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(R.string.action_save_as), fontSize = 11.sp)
                    }
                    OutlinedButton(
                        onClick = onDismissEvidenceJsonl,
                        modifier = Modifier.heightIn(min = 44.dp),
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
            EvidenceJsonlExportState.Empty -> {
                Text(
                    stringResource(R.string.evidence_jsonl_empty),
                    color = TextDim,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
                EvidenceJsonlControls(
                    days = evidenceDays,
                    query = evidenceQuery,
                    appFilter = evidenceAppFilter,
                    redacted = evidenceRedacted,
                    onDaysChange = { evidenceDays = it },
                    onQueryChange = { evidenceQuery = it.take(120) },
                    onAppFilterChange = { evidenceAppFilter = it.take(120) },
                    onRedactedChange = { evidenceRedacted = it },
                    onExport = { mode ->
                        onExportEvidenceJsonl(mode, evidenceDays, evidenceQuery, evidenceAppFilter, evidenceRedacted)
                    }
                )
                OutlinedButton(
                    onClick = onDismissEvidenceJsonl,
                    modifier = Modifier.heightIn(min = 44.dp),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = TextDim)
                ) {
                    Text(stringResource(R.string.action_discard), fontSize = 11.sp)
                }
            }
            is EvidenceJsonlExportState.Failed -> {
                Text(
                    evidenceJsonlExport.error,
                    color = Red,
                    fontSize = 11.sp,
                    lineHeight = 15.sp,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
                EvidenceJsonlControls(
                    days = evidenceDays,
                    query = evidenceQuery,
                    appFilter = evidenceAppFilter,
                    redacted = evidenceRedacted,
                    onDaysChange = { evidenceDays = it },
                    onQueryChange = { evidenceQuery = it.take(120) },
                    onAppFilterChange = { evidenceAppFilter = it.take(120) },
                    onRedactedChange = { evidenceRedacted = it },
                    onExport = { mode ->
                        onExportEvidenceJsonl(mode, evidenceDays, evidenceQuery, evidenceAppFilter, evidenceRedacted)
                    }
                )
                OutlinedButton(
                    onClick = onDismissEvidenceJsonl,
                    modifier = Modifier.heightIn(min = 44.dp),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = TextDim)
                ) {
                    Text(stringResource(R.string.action_discard), fontSize = 11.sp)
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
        modifier = Modifier.heightIn(min = 44.dp),
        shape = RoundedCornerShape(8.dp),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = Teal),
        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp)
    ) {
        Icon(Icons.Filled.SaveAlt, null, modifier = Modifier.size(15.dp))
        Spacer(Modifier.width(4.dp))
        Text(label, fontSize = 11.sp)
    }
}

@Composable
private fun EvidenceJsonlControls(
    days: Int,
    query: String,
    appFilter: String,
    redacted: Boolean,
    onDaysChange: (Int) -> Unit,
    onQueryChange: (String) -> Unit,
    onAppFilterChange: (String) -> Unit,
    onRedactedChange: (Boolean) -> Unit,
    onExport: (String) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 2.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            stringResource(R.string.evidence_jsonl_redact),
            color = TextSecondary,
            fontSize = 11.sp,
            modifier = Modifier.weight(1f)
        )
        Switch(
            checked = redacted,
            onCheckedChange = onRedactedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Teal,
                checkedTrackColor = Teal.copy(alpha = 0.35f),
                uncheckedThumbColor = TextDim,
                uncheckedTrackColor = Surface3
            )
        )
    }

    FlowRow(
        modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        listOf(1, 7, 30).forEach { option ->
            FilterChip(
                selected = days == option,
                onClick = { onDaysChange(option) },
                label = {
                    Text(
                        stringResource(R.string.evidence_jsonl_window_days, option),
                        fontSize = 11.sp
                    )
                }
            )
        }
    }

    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        label = { Text(stringResource(R.string.evidence_jsonl_query_label), fontSize = 11.sp) },
        leadingIcon = { Icon(Icons.Filled.Search, null, modifier = Modifier.size(16.dp), tint = TextDim) },
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Teal,
            unfocusedBorderColor = Surface3,
            cursorColor = Teal,
            focusedTextColor = TextPrimary,
            unfocusedTextColor = TextPrimary,
            focusedLabelColor = TextSecondary,
            unfocusedLabelColor = TextDim
        )
    )
    Spacer(Modifier.height(6.dp))
    OutlinedTextField(
        value = appFilter,
        onValueChange = onAppFilterChange,
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        label = { Text(stringResource(R.string.evidence_jsonl_app_label), fontSize = 11.sp) },
        leadingIcon = { Icon(Icons.Filled.Apps, null, modifier = Modifier.size(16.dp), tint = TextDim) },
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Teal,
            unfocusedBorderColor = Surface3,
            cursorColor = Teal,
            focusedTextColor = TextPrimary,
            unfocusedTextColor = TextPrimary,
            focusedLabelColor = TextSecondary,
            unfocusedLabelColor = TextDim
        )
    )

    FlowRow(
        modifier = Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        EvidenceJsonlExportButton(stringResource(R.string.evidence_jsonl_export_all), "all", onExport)
        EvidenceJsonlExportButton(stringResource(R.string.evidence_jsonl_export_dns), "dns", onExport)
        EvidenceJsonlExportButton(stringResource(R.string.evidence_jsonl_export_firewall), "firewall", onExport)
    }
}

@Composable
private fun EvidenceJsonlExportButton(
    label: String,
    mode: String,
    onExport: (String) -> Unit
) {
    OutlinedButton(
        onClick = { onExport(mode) },
        modifier = Modifier.heightIn(min = 44.dp),
        shape = RoundedCornerShape(8.dp),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = Teal),
        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp)
    ) {
        Icon(Icons.Filled.SaveAlt, null, modifier = Modifier.size(15.dp))
        Spacer(Modifier.width(4.dp))
        Text(label, fontSize = 11.sp)
    }
}
