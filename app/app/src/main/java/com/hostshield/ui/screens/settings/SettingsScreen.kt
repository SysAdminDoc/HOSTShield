package com.hostshield.ui.screens.settings

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import androidx.core.net.toUri
import android.provider.Browser
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.CompareArrows
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.compose.ui.res.stringResource
import com.hostshield.BuildConfig
import com.hostshield.R
import com.hostshield.ui.accessibility.accessibilitySelection
import com.hostshield.ui.HostShieldTestTags
import com.hostshield.ui.components.HostShieldFilterChip
import com.hostshield.ui.components.HostShieldPanelHeader
import com.hostshield.ui.components.HostShieldScreenHeader
import com.hostshield.ui.components.HostShieldStatusBanner
import com.hostshield.ui.screens.home.GlassCard
import com.hostshield.ui.theme.*

internal const val HOSTSHIELD_GITHUB_REPOSITORY_URL = "https://github.com/SysAdminDoc/HostShield"

internal fun hostShieldGitHubRepositoryIntent(context: Context): Intent =
    Intent(Intent.ACTION_VIEW, HOSTSHIELD_GITHUB_REPOSITORY_URL.toUri()).apply {
        addCategory(Intent.CATEGORY_BROWSABLE)
        putExtra(Browser.EXTRA_APPLICATION_ID, context.packageName)
    }

internal fun openHostShieldGitHubRepository(context: Context): Boolean =
    try {
        context.startActivity(hostShieldGitHubRepositoryIntent(context))
        true
    } catch (_: ActivityNotFoundException) {
        false
    }

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = hiltViewModel(),
    onNavigateToAppExclusions: () -> Unit = {},
    onNavigateToHostsDiff: () -> Unit = {},
    onNavigateToFirewall: () -> Unit = {},
    onNavigateToConnectionLog: () -> Unit = {},
    onNavigateToDnsTools: () -> Unit = {},
    onNavigateToNetworkStats: () -> Unit = {},
    onNavigateToOverlapAnalysis: () -> Unit = {},
    onNavigateToDnsLeakTest: () -> Unit = {},
    onNavigateToRuleTest: () -> Unit = {},
    onNavigateToHostsEditor: () -> Unit = {},
    onNavigateToAppPrivacy: () -> Unit = {},
    onNavigateToAutomationAudit: () -> Unit = {},
    onNavigateToContentFilter: () -> Unit = {},
    onNavigateToParentalControls: () -> Unit = {},
    onNavigateToDnsBenchmark: () -> Unit = {},
    onNavigateToWebDavSync: () -> Unit = {},
    onNavigateToCrashReports: () -> Unit = {},
    onNavigateToQrConfig: () -> Unit = {},
    onNavigateToTlsFingerprints: () -> Unit = {}
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val githubUnavailableMessage = stringResource(R.string.settings_github_unavailable)

    // Re-check battery status when returning from system settings
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        viewModel.refreshBattery()
    }

    val plaintextBackupLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri -> uri?.let { viewModel.backupToUri(it) } }

    val encryptedBackupLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri ->
        uri?.let { viewModel.startEncryptedExport(it) }
            ?: viewModel.dismissBackupDialog()
    }

    val restoreLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let { viewModel.restoreFromUri(it) } }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let { viewModel.importFromUri(it) } }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        uri?.let { viewModel.exportRulesToUri(it) }
    }

    val pendingJsonSaveLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) viewModel.savePendingExportToUri(uri)
        else viewModel.clearPendingExportSave()
    }

    val pendingTextSaveLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/plain")
    ) { uri ->
        if (uri != null) viewModel.savePendingExportToUri(uri)
        else viewModel.clearPendingExportSave()
    }

    val pendingCsvSaveLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv")
    ) { uri ->
        if (uri != null) viewModel.savePendingExportToUri(uri)
        else viewModel.clearPendingExportSave()
    }

    val pendingExportSave = state.pendingExportSave
    LaunchedEffect(pendingExportSave?.requestId) {
        pendingExportSave?.let { artifact ->
            when (artifact.kind) {
                ExportArtifactKind.FIREWALL_RULES,
                ExportArtifactKind.RULES_JSON -> pendingJsonSaveLauncher.launch(artifact.fileName)
                ExportArtifactKind.SHAREABLE_BLOCKLIST -> pendingTextSaveLauncher.launch(artifact.fileName)
                ExportArtifactKind.STATS_CSV -> pendingCsvSaveLauncher.launch(artifact.fileName)
                else -> Unit
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Black)
            .verticalScroll(rememberScrollState())
            .imePadding()
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        HostShieldScreenHeader(
            title = stringResource(R.string.screen_settings),
            subtitle = stringResource(R.string.screen_settings_subtitle),
        )
        Spacer(Modifier.height(4.dp))

        // DNS Configuration (extracted)
        val pcapSaveLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.CreateDocument("application/vnd.tcpdump.pcap")
        ) { uri -> uri?.let { viewModel.savePcapToUri(it) } }

        val evidenceJsonlSaveLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.CreateDocument("application/x-ndjson")
        ) { uri -> uri?.let { viewModel.saveEvidenceJsonlToUri(it) } }

        val diagnosticSaveLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.CreateDocument("application/zip")
        ) { uri -> uri?.let { viewModel.saveDiagnosticReportToUri(it) } }

        DnsSettingsSection(
            dohEnabled = state.dohEnabled,
            onDohEnabledChange = { viewModel.setDohEnabled(it) },
            dohProvider = state.dohProvider,
            onDohProviderChange = { viewModel.setDohProvider(it) },
            dotEnabled = state.dotEnabled,
            onDotEnabledChange = { viewModel.setDotEnabled(it) },
            dotProvider = state.dotProvider,
            onDotProviderChange = { viewModel.setDotProvider(it) },
            doqEnabled = state.doqEnabled,
            onDoqEnabledChange = { viewModel.setDoqEnabled(it) },
            doqProvider = state.doqProvider,
            onDoqProviderChange = { viewModel.setDoqProvider(it) },
            wireGuardEnabled = state.wireGuardEnabled,
            onWireGuardEnabledChange = { viewModel.setWireGuardEnabled(it) },
            wireGuardEndpoint = state.wireGuardEndpoint,
            onWireGuardEndpointChange = { viewModel.setWireGuardEndpoint(it) },
            wireGuardDnsIp = state.wireGuardDnsIp,
            onWireGuardDnsIpChange = { viewModel.setWireGuardDnsIp(it) },
            dnsTrapEnabled = state.dnsTrapEnabled,
            onDnsTrapEnabledChange = { viewModel.setDnsTrapEnabled(it) },
            customUpstreamDns = state.customUpstreamDns,
            onCustomUpstreamDnsChange = { viewModel.setCustomUpstreamDns(it) },
            blockResponseType = state.blockResponseType,
            onBlockResponseTypeChange = { viewModel.setBlockResponseType(it) },
            edeEnabled = state.edeEnabled,
            onEdeEnabledChange = { viewModel.setEdeEnabled(it) },
            onClearDnsCache = { viewModel.clearDnsCache() },
            onNavigateToDnsBenchmark = onNavigateToDnsBenchmark,
            onNavigateToDnsLeakTest = onNavigateToDnsLeakTest
        )

        // VPN, Content Protection, Network Firewall (extracted)
        ProtectionSettingsSection(
            firewalledApps = state.firewalledApps,
            onNavigateToAppExclusions = onNavigateToAppExclusions,
            onNavigateToFirewall = onNavigateToFirewall,
            onNavigateToContentFilter = onNavigateToContentFilter,
            onNavigateToParentalControls = onNavigateToParentalControls,
            onNavigateToConnectionLog = onNavigateToConnectionLog,
            onNavigateToDnsTools = onNavigateToDnsTools,
            onNavigateToNetworkStats = onNavigateToNetworkStats,
            lanDnsEnabled = state.lanDnsEnabled,
            lanDnsRunning = state.lanDnsRunning,
            lanDnsPort = state.lanDnsPort,
            lanDnsAllowExternalClients = state.lanDnsAllowExternalClients,
            lanDnsQueriesHandled = state.lanDnsQueriesHandled,
            lanDnsQueriesBlocked = state.lanDnsQueriesBlocked,
            lanDnsStatusMessage = state.lanDnsStatusMessage,
            onLanDnsEnabledChange = { viewModel.setLanDnsEnabled(it) },
            onLanDnsPortChange = { viewModel.setLanDnsPort(it) },
            onLanDnsAllowExternalClientsChange = { viewModel.setLanDnsAllowExternalClients(it) },
            pcapExport = state.pcapExport,
            onExportPcap = { viewModel.exportPcap(it) },
            onSharePcap = { viewModel.sharePcap() },
            onSavePcap = {
                val name = (state.pcapExport as? PcapExportState.Ready)?.fileName
                    ?: "hostshield_export.pcap"
                pcapSaveLauncher.launch(name)
            },
            onDismissPcap = { viewModel.dismissPcapExport() },
            evidenceJsonlExport = state.evidenceJsonlExport,
            onExportEvidenceJsonl = { mode, days, query, appFilter, redacted ->
                viewModel.exportEvidenceJsonl(mode, days, query, appFilter, redacted)
            },
            onShareEvidenceJsonl = { viewModel.shareEvidenceJsonl() },
            onSaveEvidenceJsonl = {
                val name = (state.evidenceJsonlExport as? EvidenceJsonlExportState.Ready)?.fileName
                    ?: "hostshield_evidence.jsonl"
                evidenceJsonlSaveLauncher.launch(name)
            },
            onDismissEvidenceJsonl = { viewModel.dismissEvidenceJsonlExport() }
        )

        // Battery Optimization — only show when exemption has NOT been granted
        if (state.batteryOptimized) {
            SettingsSection(stringResource(R.string.section_battery), Icons.Filled.BatteryAlert, Yellow) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    color = Yellow.copy(alpha = 0.08f)
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Filled.Warning, null, tint = Yellow, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            stringResource(R.string.settings_battery_warning),
                            color = Yellow,
                            fontSize = 11.sp,
                            lineHeight = 14.sp,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                Spacer(Modifier.height(6.dp))
                SettingsRow(
                    stringResource(R.string.settings_disable_battery_opt),
                    stringResource(R.string.settings_disable_battery_opt_sub),
                    Icons.Filled.BatteryChargingFull
                ) {
                    viewModel.requestBatteryExemption(context)
                }
                if (state.oemBatteryKiller != null) {
                    Spacer(Modifier.height(4.dp))
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        color = Peach.copy(alpha = 0.08f)
                    ) {
                        Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.PhoneAndroid, null, tint = Peach, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text("${state.oemBatteryKiller} detected", color = Peach, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                                Text(
                                    "Add HostShield to its whitelist for reliable protection. Visit dontkillmyapp.com for device-specific instructions.",
                                    color = TextDim, fontSize = 10.sp, lineHeight = 13.sp
                                )
                            }
                        }
                    }
                }
            }
        }

        // Scheduled Blocking
        SettingsSection(stringResource(R.string.section_schedule), Icons.Filled.Schedule, Sky) {
            SettingsToggle(
                stringResource(R.string.settings_scheduled_blocking), stringResource(R.string.settings_scheduled_blocking_sub),
                Icons.Filled.Timer, state.scheduleEnabled
            ) { viewModel.setScheduleEnabled(it) }
            if (state.scheduleEnabled) {
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val modes = listOf("block" to "Block during", "unblock" to "Unblock during")
                    modes.forEach { (key, label) ->
                        val selected = state.scheduleMode == key
                        Surface(
                            onClick = { viewModel.setScheduleMode(key) },
                            shape = RoundedCornerShape(8.dp),
                            color = if (selected) Sky.copy(alpha = 0.12f) else Surface2
                        ) {
                            Text(label, modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                color = if (selected) Sky else TextDim, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
                Spacer(Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(stringResource(R.string.label_start), color = TextDim, fontSize = 10.sp)
                        Text(state.scheduleStart, color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                    }
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, null, tint = TextDim, modifier = Modifier.padding(top = 12.dp).size(16.dp))
                    Column {
                        Text(stringResource(R.string.label_end), color = TextDim, fontSize = 10.sp)
                        Text(state.scheduleEnd, color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    if (state.scheduleMode == "block") "Blocking is active ${state.scheduleStart} - ${state.scheduleEnd}"
                    else "Blocking is paused ${state.scheduleStart} - ${state.scheduleEnd} (bedtime mode)",
                    color = TextDim, fontSize = 10.sp
                )
            }
        }

        // Hosts Configuration
        SettingsSection(stringResource(R.string.section_configuration), Icons.Filled.Tune, Yellow) {
            SettingsToggle(stringResource(R.string.settings_include_ipv6), stringResource(R.string.settings_include_ipv6_sub), Icons.Filled.Language, state.includeIpv6) {
                viewModel.setIncludeIpv6(it)
            }
            Spacer(Modifier.height(4.dp))
            SettingsToggle(stringResource(R.string.settings_dns_logging), stringResource(R.string.settings_dns_logging_sub), Icons.Filled.Analytics, state.dnsLogging) {
                viewModel.setDnsLogging(it)
            }
            Spacer(Modifier.height(4.dp))
            SettingsToggle(stringResource(R.string.settings_online_ip_lookup), stringResource(R.string.settings_online_ip_lookup_sub), Icons.Filled.Public, state.onlineGeoIpEnabled) {
                viewModel.setOnlineGeoIpEnabled(it)
            }
        }

        // Tools
        SettingsSection(stringResource(R.string.section_tools), Icons.Filled.Build, Peach) {
            SettingsRow(stringResource(R.string.settings_view_hosts), stringResource(R.string.settings_view_hosts_sub), Icons.Filled.Description, onClick = onNavigateToHostsDiff)
            Spacer(Modifier.height(4.dp))
            SettingsRow(stringResource(R.string.settings_edit_hosts), stringResource(R.string.settings_edit_hosts_sub), Icons.Filled.Edit, onClick = onNavigateToHostsEditor)
            Spacer(Modifier.height(4.dp))
            SettingsRow(stringResource(R.string.settings_overlap_analysis), stringResource(R.string.settings_overlap_analysis_sub), Icons.AutoMirrored.Filled.CompareArrows, onClick = onNavigateToOverlapAnalysis)
            Spacer(Modifier.height(4.dp))
            SettingsRow(stringResource(R.string.settings_rule_tester), stringResource(R.string.settings_rule_tester_sub), Icons.Filled.Science, onClick = onNavigateToRuleTest)
            Spacer(Modifier.height(4.dp))
            SettingsRow(stringResource(R.string.settings_app_privacy), stringResource(R.string.settings_app_privacy_sub), Icons.Filled.PrivacyTip, onClick = onNavigateToAppPrivacy)
            Spacer(Modifier.height(4.dp))
            SettingsRow(stringResource(R.string.settings_automation_audit), stringResource(R.string.settings_automation_audit_sub), Icons.AutoMirrored.Filled.ReceiptLong, onClick = onNavigateToAutomationAudit)
            Spacer(Modifier.height(4.dp))
            SettingsRow(stringResource(R.string.settings_export_firewall), stringResource(R.string.settings_export_firewall_sub), Icons.Filled.Shield) {
                viewModel.exportFirewallRules()
            }
            Spacer(Modifier.height(4.dp))
            SettingsRow(stringResource(R.string.settings_import_rules), stringResource(R.string.settings_import_rules_sub), Icons.Filled.FileUpload) {
                importLauncher.launch(arrayOf("application/json", "text/plain", "*/*"))
            }
            Spacer(Modifier.height(4.dp))
            SettingsRow(stringResource(R.string.settings_export_rules), stringResource(R.string.settings_export_rules_sub), Icons.Filled.FileDownload) {
                exportLauncher.launch("hostshield_rules.json")
            }
        }

        // Backup
        SettingsSection(stringResource(R.string.section_backup), Icons.Filled.Backup, Mauve) {
            SettingsRow(stringResource(R.string.settings_create_backup), stringResource(R.string.settings_create_backup_sub), Icons.Filled.SaveAlt) {
                viewModel.showBackupExportChoice()
            }
            Spacer(Modifier.height(4.dp))
            SettingsRow(stringResource(R.string.settings_restore_backup), stringResource(R.string.settings_restore_backup_sub), Icons.Filled.RestorePage) {
                restoreLauncher.launch(arrayOf("application/json", "application/octet-stream", "*/*"))
            }
            Spacer(Modifier.height(4.dp))
            SettingsRow(stringResource(R.string.settings_webdav), stringResource(R.string.settings_webdav_sub), Icons.Filled.Cloud, onClick = onNavigateToWebDavSync)
            Spacer(Modifier.height(4.dp))
            SettingsRow(stringResource(R.string.settings_qr_config), stringResource(R.string.settings_qr_config_sub), Icons.Filled.QrCode2, onClick = onNavigateToQrConfig)
        }

        // Migration from other blockers
        SettingsSection(stringResource(R.string.section_import_from), Icons.Filled.SwapHoriz, Flamingo) {
            SettingsRow(stringResource(R.string.import_adaway), stringResource(R.string.import_adaway_sub), Icons.Filled.ImportExport) {
                importLauncher.launch(arrayOf("application/json", "text/plain", "*/*"))
            }
            Spacer(Modifier.height(4.dp))
            SettingsRow(stringResource(R.string.import_blokada), stringResource(R.string.import_blokada_sub), Icons.Filled.CloudDownload) {
                importLauncher.launch(arrayOf("application/json", "text/plain", "*/*"))
            }
            Spacer(Modifier.height(4.dp))
            SettingsRow(stringResource(R.string.import_hosts_file), stringResource(R.string.import_hosts_file_sub), Icons.Filled.Description) {
                importLauncher.launch(arrayOf("text/plain", "*/*"))
            }
            Spacer(Modifier.height(4.dp))
            SettingsRow(stringResource(R.string.import_pihole), stringResource(R.string.import_pihole_sub), Icons.Filled.Dns) {
                importLauncher.launch(arrayOf("text/csv", "text/plain", "*/*"))
            }
        }

        // Export shareable
        SettingsSection(stringResource(R.string.section_share), Icons.Filled.Share, Blue) {
            SettingsRow(
                stringResource(R.string.share_export_blocklist),
                stringResource(R.string.share_export_blocklist_sub),
                Icons.Filled.FileUpload
            ) {
                viewModel.exportShareableBlocklist()
            }
        }

        // Diagnostics & Export
        SettingsSection(stringResource(R.string.section_diagnostics), Icons.Filled.BugReport, Yellow) {
            val diagState = state.diagnosticExport
            when (diagState) {
                DiagnosticExportState.Idle -> {
                    SettingsRow(
                        stringResource(R.string.settings_generate_diagnostic),
                        stringResource(R.string.settings_generate_diagnostic_sub),
                        Icons.Filled.Description
                    ) { viewModel.generateDiagnosticReport() }
                }
                DiagnosticExportState.Generating -> {
                    SettingsRow(
                        stringResource(R.string.settings_generating_diagnostic),
                        stringResource(R.string.settings_generating_diagnostic_sub),
                        Icons.Filled.HourglassTop
                    ) {}
                }
                is DiagnosticExportState.Ready -> {
                    val sizeKb = (diagState.sizeBytes + 1023) / 1024
                    Text(
                        stringResource(R.string.settings_diagnostic_ready_summary, diagState.fileName),
                        color = TextSecondary,
                        fontSize = 11.sp,
                        lineHeight = 15.sp,
                        modifier = Modifier.padding(vertical = 2.dp)
                    )
                    Text(
                        diagState.privacyNotice,
                        color = Peach,
                        fontSize = 11.sp,
                        lineHeight = 15.sp,
                        modifier = Modifier.padding(vertical = 2.dp)
                    )
                    FlowRow(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        OutlinedButton(
                            onClick = { viewModel.shareDiagnosticReport() },
                            modifier = Modifier.heightIn(min = 44.dp),
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Teal)
                        ) {
                            Icon(Icons.Filled.Share, null, modifier = Modifier.size(15.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("${stringResource(R.string.action_share)} (${sizeKb} KB)", fontSize = 11.sp)
                        }
                        OutlinedButton(
                            onClick = { diagnosticSaveLauncher.launch(diagState.fileName) },
                            modifier = Modifier.heightIn(min = 44.dp),
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Blue)
                        ) {
                            Icon(Icons.Filled.Save, null, modifier = Modifier.size(15.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(stringResource(R.string.action_save_as), fontSize = 11.sp)
                        }
                        OutlinedButton(
                            onClick = { viewModel.dismissDiagnosticExport() },
                            modifier = Modifier.heightIn(min = 44.dp),
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = TextDim)
                        ) {
                            Icon(Icons.Filled.Close, null, modifier = Modifier.size(15.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(stringResource(R.string.action_discard), fontSize = 11.sp)
                        }
                    }
                }
                is DiagnosticExportState.Failed -> {
                    SettingsRow(
                        stringResource(R.string.settings_export_failed),
                        diagState.error,
                        Icons.Filled.Error
                    ) { viewModel.generateDiagnosticReport() }
                }
            }
            Spacer(Modifier.height(4.dp))
            SettingsRow(stringResource(R.string.settings_crash_reports), stringResource(R.string.settings_crash_reports_sub), Icons.Filled.BugReport, onClick = onNavigateToCrashReports)
            Spacer(Modifier.height(4.dp))
            SettingsRow(stringResource(R.string.settings_tls_fingerprints), stringResource(R.string.settings_tls_fingerprints_sub), Icons.Filled.Fingerprint, onClick = onNavigateToTlsFingerprints)
            Spacer(Modifier.height(4.dp))

            val csvMessage by viewModel.csvMessage.collectAsStateWithLifecycle()
            val isExportingCsv by viewModel.isExportingCsv.collectAsStateWithLifecycle()

            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                OutlinedButton(
                    onClick = { viewModel.exportStatsCsv() },
                    enabled = !isExportingCsv,
                    modifier = Modifier.weight(1f).heightIn(min = 44.dp),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Yellow)
                ) {
                    if (isExportingCsv) CircularProgressIndicator(Modifier.size(14.dp), color = Yellow, strokeWidth = 1.5.dp)
                    else Icon(Icons.Filled.TableChart, null, modifier = Modifier.size(15.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(stringResource(R.string.action_export_csv), fontSize = 11.sp)
                }
            }
            csvMessage?.let { msg ->
                val isError = msg.contains("failed", ignoreCase = true)
                Text(
                    msg,
                    color = if (isError) Red else TextDim,
                    fontSize = 11.sp,
                    lineHeight = 15.sp,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }

        // Appearance
        SettingsSection(stringResource(R.string.section_appearance), Icons.Filled.Visibility, Mauve) {
            Text(stringResource(R.string.label_theme_mode), color = TextDim, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.sp)
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("dark" to stringResource(R.string.theme_dark), "light" to stringResource(R.string.theme_light), "system" to stringResource(R.string.theme_system)).forEach { (key, label) ->
                    FilterChip(
                        selected = state.themeMode == key,
                        onClick = { viewModel.setThemeMode(key) },
                        label = { Text(label, fontSize = 12.sp) }
                    )
                }
            }
            Spacer(Modifier.height(8.dp))

            if (state.themeMode != "light") {
                SettingsToggle(
                    stringResource(R.string.settings_high_contrast),
                    stringResource(R.string.settings_high_contrast_sub),
                    Icons.Filled.Visibility,
                    state.highContrastAmoled
                ) {
                    viewModel.setHighContrastAmoled(it)
                }
            }

            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                SettingsToggle(
                    stringResource(R.string.settings_dynamic_color),
                    stringResource(R.string.settings_dynamic_color_sub),
                    Icons.Filled.Palette,
                    state.dynamicColor
                ) {
                    viewModel.setDynamicColor(it)
                }
            }

            Spacer(Modifier.height(12.dp))
            Text(stringResource(R.string.label_accent_color), color = TextDim, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.sp)
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val colors = listOf(
                    "teal" to Teal,
                    "blue" to Blue,
                    "purple" to Mauve,
                    "green" to Green,
                    "pink" to Red,
                    "peach" to Peach
                )
                colors.forEach { (key, color) ->
                    val isSelected = state.accentColor == key
                    Surface(
                        onClick = { viewModel.setAccentColor(key) },
                        shape = RoundedCornerShape(8.dp),
                        color = if (isSelected) color.copy(alpha = 0.2f) else Surface2,
                        modifier = Modifier
                            .size(40.dp)
                            .semantics {
                                role = Role.Button
                                contentDescription = "Set accent color to $key"
                                stateDescription = if (isSelected) "Selected" else "Not selected"
                            }
                    ) {
                        Box(
                            modifier = Modifier.fillMaxSize().padding(6.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(color),
                            contentAlignment = Alignment.Center
                        ) {
                            if (isSelected) {
                                Icon(Icons.Filled.Check, "Selected accent color", tint = Color.Black, modifier = Modifier.size(14.dp))
                            }
                        }
                    }
                }
            }
        }

        // About
        SettingsSection(stringResource(R.string.section_about), Icons.Filled.Info, TextSecondary) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(stringResource(R.string.label_version), color = TextSecondary, fontSize = 13.sp)
                Text(BuildConfig.VERSION_NAME, color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            }
            if (state.isRootAvailable) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(stringResource(R.string.label_root), color = TextSecondary, fontSize = 13.sp)
                    Text(stringResource(R.string.label_available), color = Green, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                }
            }

            Spacer(Modifier.height(10.dp))

            // Check for Updates button
            Surface(
                onClick = { viewModel.checkForUpdate() },
                shape = RoundedCornerShape(10.dp),
                color = Surface2,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (state.isCheckingUpdate) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            color = Teal,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(Icons.Filled.SystemUpdate, null, tint = Teal, modifier = Modifier.size(16.dp))
                    }
                    Spacer(Modifier.width(10.dp))
                    Text(
                        if (state.isCheckingUpdate) stringResource(R.string.label_checking) else stringResource(R.string.label_check_for_updates),
                        color = TextPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            // Update result
            state.updateMessage?.let { msg ->
                Spacer(Modifier.height(8.dp))
                val isUpdate = state.updateAvailable
                val isError = msg.contains("failed", ignoreCase = true)
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = when {
                        isUpdate -> Teal.copy(alpha = 0.08f)
                        isError -> Red.copy(alpha = 0.08f)
                        else -> Green.copy(alpha = 0.08f)
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                when {
                                    isUpdate -> Icons.Filled.NewReleases
                                    isError -> Icons.Filled.Error
                                    else -> Icons.Filled.CheckCircle
                                },
                                null,
                                tint = when {
                                    isUpdate -> Teal
                                    isError -> Red
                                    else -> Green
                                },
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(msg, color = TextPrimary, fontSize = 12.sp, modifier = Modifier.weight(1f))
                            IconButton(
                                onClick = { viewModel.dismissUpdateMessage() },
                                modifier = Modifier.size(40.dp)
                            ) {
                                Icon(Icons.Filled.Close, "Dismiss update message", tint = TextDim, modifier = Modifier.size(12.dp))
                            }
                        }
                        if (isUpdate && state.updatePublishedAt.isNotEmpty()) {
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "Released ${state.updatePublishedAt}",
                                color = TextDim, fontSize = 11.sp
                            )
                        }
                        if (isUpdate && state.updateReleaseNotes.isNotEmpty()) {
                            Spacer(Modifier.height(6.dp))
                            Text(
                                state.updateReleaseNotes.take(200) +
                                    if (state.updateReleaseNotes.length > 200) "..." else "",
                                color = TextSecondary,
                                fontSize = 11.sp,
                                lineHeight = 15.sp
                            )
                        }
                        if (isUpdate && state.updateDownloadUrl.isNotEmpty()) {
                            Spacer(Modifier.height(8.dp))
                            Surface(
                                onClick = {
                                    val intent = Intent(Intent.ACTION_VIEW, state.updateDownloadUrl.toUri())
                                    context.startActivity(intent)
                                },
                                shape = RoundedCornerShape(8.dp),
                                color = Teal.copy(alpha = 0.15f)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Filled.Download, null, tint = Teal, modifier = Modifier.size(14.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        "Download v${state.latestVersion}",
                                        color = Teal, fontSize = 12.sp, fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            // GitHub link
            Surface(
                onClick = {
                    if (!openHostShieldGitHubRepository(context)) {
                        Toast.makeText(
                            context,
                            githubUnavailableMessage,
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                },
                shape = RoundedCornerShape(10.dp),
                color = Surface2,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Filled.Code, null, tint = TextSecondary, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(10.dp))
                    Text(stringResource(R.string.label_view_on_github), color = TextSecondary, fontSize = 13.sp)
                }
            }
        }

        // Status messages
        val statusMsg = state.backupMessage ?: state.importMessage
        statusMsg?.let { msg ->
            val isError = msg.contains("fail", ignoreCase = true) || msg.contains("error", ignoreCase = true)
            HostShieldStatusBanner(
                icon = if (isError) Icons.Filled.Error else Icons.Filled.CheckCircle,
                title = if (isError) stringResource(R.string.status_action_failed) else stringResource(R.string.status_action_complete),
                message = msg,
                accent = if (isError) Red else Teal,
                onDismiss = {
                    viewModel.clearBackupMessage()
                    viewModel.clearImportMessage()
                },
            )
        }

        Spacer(Modifier.height(24.dp))
    }

    // Backup dialogs
    when (val dialog = state.backupDialog) {
        BackupDialogState.None -> {}
        BackupDialogState.ExportChoice -> {
            BackupExportChoiceDialog(
                onPlaintext = {
                    viewModel.dismissBackupDialog()
                    plaintextBackupLauncher.launch("hostshield_backup.json")
                },
                onEncrypted = {
                    encryptedBackupLauncher.launch("hostshield_backup.hsbackup")
                },
                onDismiss = { viewModel.dismissBackupDialog() }
            )
        }
        is BackupDialogState.ExportPassphrase -> {
            BackupPassphraseDialog(
                title = stringResource(R.string.backup_encrypt_title),
                confirmLabel = stringResource(R.string.action_export),
                showConfirmField = true,
                error = null,
                onConfirm = { passphrase ->
                    viewModel.dismissBackupDialog()
                    viewModel.backupToUri(dialog.uri, passphrase)
                },
                onDismiss = { viewModel.dismissBackupDialog() }
            )
        }
        is BackupDialogState.ImportPassphrase -> {
            BackupPassphraseDialog(
                title = stringResource(R.string.backup_encrypted),
                confirmLabel = stringResource(R.string.action_restore),
                showConfirmField = false,
                error = dialog.error,
                onConfirm = { passphrase ->
                    viewModel.restoreFromUri(dialog.uri, passphrase)
                },
                onDismiss = { viewModel.dismissBackupDialog() }
            )
        }
    }
}

@Composable
private fun BackupExportChoiceDialog(
    onPlaintext: () -> Unit,
    onEncrypted: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Surface0,
        titleContentColor = TextPrimary,
        textContentColor = TextSecondary,
        title = { Text(stringResource(R.string.backup_dialog_title)) },
        text = { Text(stringResource(R.string.backup_dialog_message)) },
        confirmButton = {
            TextButton(onClick = onEncrypted) {
                Text(stringResource(R.string.backup_encrypted), color = Teal)
            }
        },
        dismissButton = {
            TextButton(onClick = onPlaintext) {
                Text(stringResource(R.string.backup_plaintext), color = TextSecondary)
            }
        }
    )
}

@Composable
private fun BackupPassphraseDialog(
    title: String,
    confirmLabel: String,
    showConfirmField: Boolean,
    error: String?,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var passphrase by remember { mutableStateOf("") }
    var confirmPassphrase by remember { mutableStateOf("") }
    var visible by remember { mutableStateOf(false) }
    val mismatch = showConfirmField && passphrase.isNotEmpty() &&
        confirmPassphrase.isNotEmpty() && passphrase != confirmPassphrase
    val canConfirm = passphrase.isNotEmpty() &&
        (!showConfirmField || passphrase == confirmPassphrase)

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Surface0,
        titleContentColor = TextPrimary,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (error != null) {
                    Text(error, color = Red, fontSize = 13.sp)
                }
                OutlinedTextField(
                    value = passphrase,
                    onValueChange = { passphrase = it },
                    label = { Text(stringResource(R.string.backup_passphrase)) },
                    singleLine = true,
                    visualTransformation = if (visible)
                        androidx.compose.ui.text.input.VisualTransformation.None
                    else
                        androidx.compose.ui.text.input.PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { visible = !visible }) {
                            Icon(
                                if (visible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                "Toggle visibility",
                                tint = TextDim
                            )
                        }
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        focusedBorderColor = Teal,
                        unfocusedBorderColor = Surface2,
                        focusedLabelColor = Teal,
                        unfocusedLabelColor = TextDim,
                        cursorColor = Teal
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                if (showConfirmField) {
                    OutlinedTextField(
                        value = confirmPassphrase,
                        onValueChange = { confirmPassphrase = it },
                        label = { Text(stringResource(R.string.backup_confirm_passphrase)) },
                        singleLine = true,
                        isError = mismatch,
                        supportingText = if (mismatch) {
                            { Text(stringResource(R.string.backup_passphrase_mismatch), color = Red) }
                        } else null,
                        visualTransformation = if (visible)
                            androidx.compose.ui.text.input.VisualTransformation.None
                        else
                            androidx.compose.ui.text.input.PasswordVisualTransformation(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary,
                            focusedBorderColor = Teal,
                            unfocusedBorderColor = Surface2,
                            focusedLabelColor = Teal,
                            unfocusedLabelColor = TextDim,
                            cursorColor = Teal,
                            errorBorderColor = Red,
                            errorLabelColor = Red,
                            errorCursorColor = Red
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(passphrase) }, enabled = canConfirm) {
                Text(confirmLabel, color = if (canConfirm) Teal else TextDim)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel), color = TextSecondary)
            }
        }
    )
}

// ── Shared Settings Components ──────────────────────────────

@Composable
internal fun SettingsSection(
    title: String,
    icon: ImageVector,
    color: Color,
    content: @Composable ColumnScope.() -> Unit
) {
    GlassCard(modifier = Modifier.fillMaxWidth().testTag(HostShieldTestTags.Settings.section(title))) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .animateContentSize()
        ) {
            HostShieldPanelHeader(
                icon = icon,
                title = title,
                accent = color,
            )
            Spacer(Modifier.height(14.dp))
            content()
        }
    }
}

@Composable
internal fun SettingsToggle(
    title: String,
    subtitle: String,
    icon: ImageVector,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    val rowShape = RoundedCornerShape(10.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 58.dp)
            .clip(rowShape)
            .background(if (checked) Teal.copy(alpha = 0.055f) else Surface2.copy(alpha = 0.32f))
            .border(
                width = 1.dp,
                color = if (checked) Teal.copy(alpha = 0.22f) else Surface3.copy(alpha = 0.32f),
                shape = rowShape,
            )
            .testTag(HostShieldTestTags.Settings.toggle(title))
            .semantics(mergeDescendants = true) {
                role = Role.Switch
                contentDescription = "$title. $subtitle"
                stateDescription = if (checked) "On" else "Off"
            }
            .clickable(role = Role.Switch) { onCheckedChange(!checked) }
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(RoundedCornerShape(9.dp))
                .background((if (checked) Teal else Surface3).copy(alpha = if (checked) 0.14f else 0.75f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, null, tint = if (checked) Teal else TextSecondary, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                title,
                color = TextPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                subtitle,
                color = TextDim,
                fontSize = 11.sp,
                lineHeight = 15.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Switch(
            checked = checked, onCheckedChange = null,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Teal, checkedTrackColor = Teal.copy(alpha = 0.25f),
                uncheckedThumbColor = TextDim, uncheckedTrackColor = Surface3
            )
        )
    }
}

@Composable
internal fun SettingsRow(
    title: String,
    subtitle: String,
    icon: ImageVector,
    onClick: () -> Unit
) {
    val rowShape = RoundedCornerShape(10.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 58.dp)
            .clip(rowShape)
            .background(Surface2.copy(alpha = 0.32f))
            .border(1.dp, Surface3.copy(alpha = 0.32f), rowShape)
            .testTag(HostShieldTestTags.Settings.row(title))
            .semantics(mergeDescendants = true) {
                role = Role.Button
                contentDescription = "$title. $subtitle"
            }
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(RoundedCornerShape(9.dp))
                .background(Surface3.copy(alpha = 0.75f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, null, tint = TextSecondary, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                title,
                color = TextPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                subtitle,
                color = TextDim,
                fontSize = 11.sp,
                lineHeight = 15.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Icon(Icons.Filled.ChevronRight, null, tint = TextDim, modifier = Modifier.size(18.dp))
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun DohProviderSelector(current: String, onSelect: (String) -> Unit) {
    val providers = listOf(
        "cloudflare" to "Cloudflare",
        "google" to "Google",
        "quad9" to "Quad9",
        "nextdns" to "NextDNS",
        "adguard" to "AdGuard"
    )
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        providers.forEach { (key, label) ->
            val selected = current == key
            HostShieldFilterChip(
                label = label,
                selected = selected,
                onClick = { onSelect(key) },
                accent = Blue,
                semanticsLabel = "$label DNS-over-HTTPS provider",
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun DotProviderSelector(current: String, onSelect: (String) -> Unit) {
    val providers = listOf(
        "cloudflare" to "Cloudflare",
        "google" to "Google",
        "quad9" to "Quad9",
        "adguard" to "AdGuard"
    )
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        providers.forEach { (key, label) ->
            val selected = current == key
            HostShieldFilterChip(
                label = label,
                selected = selected,
                onClick = { onSelect(key) },
                accent = Blue,
                semanticsLabel = "$label DNS-over-TLS provider",
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun DoqProviderSelector(current: String, onSelect: (String) -> Unit) {
    val providers = listOf(
        "adguard" to "AdGuard",
        "nextdns" to "NextDNS",
        "mullvad" to "Mullvad"
    )
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        providers.forEach { (key, label) ->
            val selected = current == key
            HostShieldFilterChip(
                label = label,
                selected = selected,
                onClick = { onSelect(key) },
                accent = Blue,
                semanticsLabel = "$label DNS-over-QUIC provider",
            )
        }
    }
}

@Composable
internal fun BlockResponseSelector(current: String, onSelect: (String) -> Unit) {
    val options = listOf(
        Triple("nxdomain", "NXDOMAIN", "Standard (domain not found)"),
        Triple("zero_ip", "Null IP", "0.0.0.0 / :: (recommended)"),
        Triple("refused", "Refused", "Administrative refusal")
    )
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        options.forEach { (key, label, desc) ->
            val selected = current == key
            Surface(
                onClick = { onSelect(key) },
                shape = RoundedCornerShape(8.dp),
                color = if (selected) Blue.copy(alpha = 0.12f) else Surface2,
                modifier = Modifier.fillMaxWidth().accessibilitySelection("$label block response type", selected)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = selected,
                        onClick = { onSelect(key) },
                        colors = RadioButtonDefaults.colors(
                            selectedColor = Blue,
                            unselectedColor = TextDim
                        ),
                        modifier = Modifier.size(20.dp).accessibilitySelection("$label block response type", selected)
                    )
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Text(label, color = if (selected) Blue else TextPrimary,
                            fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        Text(desc, color = TextDim, fontSize = 10.sp)
                    }
                }
            }
        }
    }
}
