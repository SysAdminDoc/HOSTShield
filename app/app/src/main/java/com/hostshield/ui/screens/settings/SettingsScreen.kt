package com.hostshield.ui.screens.settings

import android.app.Activity
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.hostshield.BuildConfig
import com.hostshield.ui.screens.home.GlassCard
import com.hostshield.ui.theme.*

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

    // Re-check battery status when returning from system settings
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        viewModel.refreshBattery()
    }

    val backupLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri -> uri?.let { viewModel.backupToUri(it) } }

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

    val shareableLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/plain")
    ) { uri ->
        if (uri != null) viewModel.writeShareableToUri(uri)
        else viewModel.clearExportResult()
    }

    // When exportResult is ready and user hasn't picked a file yet, offer share
    val exportResult = state.exportResult
    LaunchedEffect(exportResult) {
        if (exportResult != null) {
            // Auto-launch file picker
            shareableLauncher.launch("hostshield_blocklist.txt")
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text("Settings", style = MaterialTheme.typography.headlineMedium, color = TextPrimary)
        Spacer(Modifier.height(4.dp))

        // DNS Configuration
        SettingsSection("DNS", Icons.Filled.Dns, Blue) {
            SettingsToggle("DNS-over-HTTPS", "Encrypt DNS queries", Icons.Filled.Lock, state.dohEnabled) {
                viewModel.setDohEnabled(it)
            }
            if (state.dohEnabled) {
                Spacer(Modifier.height(6.dp))
                DohProviderSelector(state.dohProvider) { viewModel.setDohProvider(it) }
            }
            Spacer(Modifier.height(8.dp))
            SettingsToggle("DNS-over-TLS", "TLS-encrypted DNS (RFC 7858)", Icons.Filled.Shield, state.dotEnabled) {
                viewModel.setDotEnabled(it)
            }
            if (state.dotEnabled) {
                Spacer(Modifier.height(6.dp))
                DotProviderSelector(state.dotProvider) { viewModel.setDotProvider(it) }
            }
            Spacer(Modifier.height(8.dp))
            SettingsToggle("DNS-over-QUIC", "QUIC-encrypted DNS (RFC 9250)", Icons.Filled.Bolt, state.doqEnabled) {
                viewModel.setDoqEnabled(it)
            }
            if (state.doqEnabled) {
                Spacer(Modifier.height(6.dp))
                DoqProviderSelector(state.doqProvider) { viewModel.setDoqProvider(it) }
            }
            Spacer(Modifier.height(8.dp))
            SettingsToggle("WireGuard DNS", "Tunnel DNS through WireGuard VPN", Icons.Filled.VpnKey, state.wireGuardEnabled) {
                viewModel.setWireGuardEnabled(it)
            }
            if (state.wireGuardEnabled) {
                Spacer(Modifier.height(6.dp))
                var wgEndpoint by remember { mutableStateOf(state.wireGuardEndpoint) }
                LaunchedEffect(state.wireGuardEndpoint) { wgEndpoint = state.wireGuardEndpoint }
                OutlinedTextField(
                    value = wgEndpoint,
                    onValueChange = { wgEndpoint = it },
                    placeholder = { Text("Endpoint (host:port)", color = TextDim, fontSize = 12.sp) },
                    modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 48.dp),
                    singleLine = true,
                    textStyle = LocalTextStyle.current.copy(fontSize = 13.sp),
                    shape = RoundedCornerShape(10.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Blue, unfocusedBorderColor = Surface3,
                        cursorColor = Blue, focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary
                    ),
                    trailingIcon = {
                        if (wgEndpoint != state.wireGuardEndpoint) {
                            IconButton(onClick = { viewModel.setWireGuardEndpoint(wgEndpoint) }) {
                                Icon(Icons.Filled.Check, null, tint = Green, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                )
                Spacer(Modifier.height(4.dp))
                var wgDnsIp by remember { mutableStateOf(state.wireGuardDnsIp) }
                LaunchedEffect(state.wireGuardDnsIp) { wgDnsIp = state.wireGuardDnsIp }
                OutlinedTextField(
                    value = wgDnsIp,
                    onValueChange = { wgDnsIp = it },
                    placeholder = { Text("DNS IP inside tunnel (e.g. 1.1.1.1)", color = TextDim, fontSize = 12.sp) },
                    modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 48.dp),
                    singleLine = true,
                    textStyle = LocalTextStyle.current.copy(fontSize = 13.sp),
                    shape = RoundedCornerShape(10.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Blue, unfocusedBorderColor = Surface3,
                        cursorColor = Blue, focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary
                    ),
                    trailingIcon = {
                        if (wgDnsIp != state.wireGuardDnsIp) {
                            IconButton(onClick = { viewModel.setWireGuardDnsIp(wgDnsIp) }) {
                                Icon(Icons.Filled.Check, null, tint = Green, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                )
                Spacer(Modifier.height(4.dp))
                Text("Configure keys in WireGuard settings", color = TextDim, fontSize = 10.sp)
            }
            Spacer(Modifier.height(8.dp))
            SettingsToggle(
                "DNS Trap",
                "Catch hardcoded DNS + block DoH/DoT bypass",
                Icons.Filled.FilterAlt,
                state.dnsTrapEnabled
            ) { viewModel.setDnsTrapEnabled(it) }
            Spacer(Modifier.height(8.dp))
            // Custom upstream DNS
            var customDns by remember { mutableStateOf(state.customUpstreamDns) }
            LaunchedEffect(state.customUpstreamDns) { customDns = state.customUpstreamDns }
            Text("Custom Upstream DNS", color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Text("Comma-separated IPs (leave empty for system default)", color = TextDim, fontSize = 10.sp)
            Spacer(Modifier.height(4.dp))
            OutlinedTextField(
                value = customDns,
                onValueChange = { customDns = it },
                placeholder = { Text("e.g. 1.1.1.1, 9.9.9.9", color = TextDim, fontSize = 12.sp) },
                modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 52.dp),
                singleLine = true,
                textStyle = LocalTextStyle.current.copy(fontSize = 13.sp),
                shape = RoundedCornerShape(10.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Blue, unfocusedBorderColor = Surface3,
                    cursorColor = Blue, focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary
                ),
                trailingIcon = {
                    if (customDns != state.customUpstreamDns) {
                        IconButton(onClick = { viewModel.setCustomUpstreamDns(customDns) }) {
                            Icon(Icons.Filled.Check, null, tint = Green, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            )
            Spacer(Modifier.height(8.dp))
            SettingsRow("DNS benchmark", "Test latency to public DNS resolvers", Icons.Filled.Speed, onClick = onNavigateToDnsBenchmark)
            Spacer(Modifier.height(8.dp))
            SettingsRow("DNS leak test", "Verify queries go through HostShield", Icons.Filled.VerifiedUser, onClick = onNavigateToDnsLeakTest)
            Spacer(Modifier.height(8.dp))
            // Block response type selector
            Text("Block Response", color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Text(
                "How blocked domains are answered",
                color = TextDim, fontSize = 11.sp
            )
            Spacer(Modifier.height(6.dp))
            BlockResponseSelector(state.blockResponseType) { viewModel.setBlockResponseType(it) }

            // DNS Cache management
            Spacer(Modifier.height(12.dp))
            Text("DNS Cache", color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp))
            val cacheStats = com.hostshield.service.DnsVpnService.currentCacheStats
            if (cacheStats != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("${cacheStats.size + cacheStats.negativeSize + cacheStats.failureSize} entries", color = TextDim, fontSize = 11.sp)
                    Text("${(cacheStats.hitRate * 100).toInt()}% hit rate", color = Green, fontSize = 11.sp)
                    Text("${cacheStats.staleHits} stale", color = TextDim, fontSize = 11.sp)
                }
                Spacer(Modifier.height(6.dp))
            }
            Surface(
                onClick = { viewModel.clearDnsCache() },
                shape = RoundedCornerShape(8.dp),
                color = Surface2,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Filled.Cached, null, tint = Blue, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(10.dp))
                    Text("Clear DNS cache", color = TextPrimary, fontSize = 13.sp)
                }
            }
        }

        // VPN Settings
        SettingsSection("VPN", Icons.Filled.VpnLock, Teal) {
            SettingsRow("App exclusions", "Bypass VPN for specific apps", Icons.Filled.AppBlocking, onClick = onNavigateToAppExclusions)
            Spacer(Modifier.height(4.dp))
            SettingsRow(
                "Per-app firewall",
                if (state.firewalledApps > 0) "${state.firewalledApps} apps firewalled" else "Block all DNS for specific apps",
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
                Icons.Filled.List,
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
            val pcapMessage by viewModel.pcapMessage.collectAsStateWithLifecycle()
            val isExportingPcap by viewModel.isExportingPcap.collectAsStateWithLifecycle()

            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                OutlinedButton(
                    onClick = { viewModel.exportPcap("all") },
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

        // Battery Optimization — only show when exemption has NOT been granted
        if (state.batteryOptimized) {
            SettingsSection("Battery", Icons.Filled.BatteryAlert, Yellow) {
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
                            "Battery optimization may stop HostShield in the background",
                            color = Yellow,
                            fontSize = 11.sp,
                            lineHeight = 14.sp,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                Spacer(Modifier.height(6.dp))
                SettingsRow(
                    "Disable battery optimization",
                    "Prevents Android from killing HostShield",
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
        SettingsSection("Schedule", Icons.Filled.Schedule, Sky) {
            SettingsToggle(
                "Scheduled blocking", "Auto-enable/disable at set times",
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
                        Text("Start", color = TextDim, fontSize = 10.sp)
                        Text(state.scheduleStart, color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                    }
                    Icon(Icons.Filled.ArrowForward, null, tint = TextDim, modifier = Modifier.padding(top = 12.dp).size(16.dp))
                    Column {
                        Text("End", color = TextDim, fontSize = 10.sp)
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
        SettingsSection("Configuration", Icons.Filled.Tune, Yellow) {
            SettingsToggle("Include IPv6", "Block domains on IPv6 as well", Icons.Filled.Language, state.includeIpv6) {
                viewModel.setIncludeIpv6(it)
            }
            Spacer(Modifier.height(4.dp))
            SettingsToggle("DNS logging", "Record DNS queries for stats", Icons.Filled.Analytics, state.dnsLogging) {
                viewModel.setDnsLogging(it)
            }
        }

        // Tools
        SettingsSection("Tools", Icons.Filled.Build, Peach) {
            SettingsRow("View hosts file", "Inspect current blocking rules", Icons.Filled.Description, onClick = onNavigateToHostsDiff)
            Spacer(Modifier.height(4.dp))
            SettingsRow("Edit hosts file", "Direct editor for /etc/hosts (root)", Icons.Filled.Edit, onClick = onNavigateToHostsEditor)
            Spacer(Modifier.height(4.dp))
            SettingsRow("Overlap analysis", "Find redundant domains across sources", Icons.Filled.CompareArrows, onClick = onNavigateToOverlapAnalysis)
            Spacer(Modifier.height(4.dp))
            SettingsRow("Rule tester", "Test if domains match your rules", Icons.Filled.Science, onClick = onNavigateToRuleTest)
            Spacer(Modifier.height(4.dp))
            SettingsRow("App privacy report", "Grade each app's tracking behavior", Icons.Filled.PrivacyTip, onClick = onNavigateToAppPrivacy)
            Spacer(Modifier.height(4.dp))
            SettingsRow("Automation audit log", "View commands from Tasker, ADB, etc", Icons.Filled.ReceiptLong, onClick = onNavigateToAutomationAudit)
            Spacer(Modifier.height(4.dp))
            SettingsRow("Export firewall rules", "Save per-app network rules as JSON", Icons.Filled.Shield) {
                viewModel.exportFirewallRules()
            }
            Spacer(Modifier.height(4.dp))
            SettingsRow("Import rules", "From JSON or hosts file", Icons.Filled.FileUpload) {
                importLauncher.launch(arrayOf("application/json", "text/plain", "*/*"))
            }
            Spacer(Modifier.height(4.dp))
            SettingsRow("Export rules", "Save rules as JSON", Icons.Filled.FileDownload) {
                exportLauncher.launch("hostshield_rules.json")
            }
        }

        // Backup
        SettingsSection("Backup", Icons.Filled.Backup, Mauve) {
            SettingsRow("Create backup", "Sources, rules, profiles, preferences", Icons.Filled.SaveAlt) {
                backupLauncher.launch("hostshield_backup.json")
            }
            Spacer(Modifier.height(4.dp))
            SettingsRow("Restore backup", "Restore from previous backup", Icons.Filled.RestorePage) {
                restoreLauncher.launch(arrayOf("application/json"))
            }
            Spacer(Modifier.height(4.dp))
            SettingsRow("WebDAV sync", "Sync backups to Nextcloud, ownCloud", Icons.Filled.Cloud, onClick = onNavigateToWebDavSync)
            Spacer(Modifier.height(4.dp))
            SettingsRow("QR config sharing", "Share config via QR code", Icons.Filled.QrCode2, onClick = onNavigateToQrConfig)
        }

        // Migration from other blockers
        SettingsSection("Import From", Icons.Filled.SwapHoriz, Flamingo) {
            SettingsRow("AdAway backup", "Import hosts, sources, and rules", Icons.Filled.ImportExport) {
                importLauncher.launch(arrayOf("application/json", "text/plain", "*/*"))
            }
            Spacer(Modifier.height(4.dp))
            SettingsRow("Blokada / NextDNS config", "Auto-detects format on import", Icons.Filled.CloudDownload) {
                importLauncher.launch(arrayOf("application/json", "text/plain", "*/*"))
            }
            Spacer(Modifier.height(4.dp))
            SettingsRow("Hosts file", "Standard hosts format from any blocker", Icons.Filled.Description) {
                importLauncher.launch(arrayOf("text/plain", "*/*"))
            }
            Spacer(Modifier.height(4.dp))
            SettingsRow("Pi-hole teleporter", "domainlist CSV or gravity list export", Icons.Filled.Dns) {
                importLauncher.launch(arrayOf("text/csv", "text/plain", "*/*"))
            }
        }

        // Export shareable
        SettingsSection("Share", Icons.Filled.Share, Blue) {
            SettingsRow(
                "Export shareable blocklist",
                "Hosts file format \u2014 share on GitHub or use as source URL",
                Icons.Filled.FileUpload
            ) {
                viewModel.exportShareableBlocklist()
            }
        }

        // Diagnostics & Export
        SettingsSection("Diagnostics & Export", Icons.Filled.BugReport, Yellow) {
            SettingsRow(
                "Generate diagnostic report",
                "Device info, config, logs, network state",
                Icons.Filled.Description
            ) { viewModel.generateDiagnosticReport() }
            Spacer(Modifier.height(4.dp))
            SettingsRow("Crash reports", "View stored crash logs", Icons.Filled.BugReport, onClick = onNavigateToCrashReports)
            Spacer(Modifier.height(4.dp))
            SettingsRow("TLS fingerprints", "JA3/JA4 per-app TLS library identification", Icons.Filled.Fingerprint, onClick = onNavigateToTlsFingerprints)
            Spacer(Modifier.height(4.dp))

            val csvMessage by viewModel.csvMessage.collectAsStateWithLifecycle()
            val isExportingCsv by viewModel.isExportingCsv.collectAsStateWithLifecycle()

            val csvLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.CreateDocument("text/csv")
            ) { uri -> uri?.let { viewModel.writeCsvToUri(it) } }

            val pendingCsv by viewModel.pendingCsv.collectAsStateWithLifecycle()
            LaunchedEffect(pendingCsv) {
                if (pendingCsv != null) {
                    csvLauncher.launch("hostshield_stats_${java.time.LocalDate.now()}.csv")
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                OutlinedButton(
                    onClick = { viewModel.exportStatsCsv() },
                    enabled = !isExportingCsv,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Yellow)
                ) {
                    if (isExportingCsv) CircularProgressIndicator(Modifier.size(12.dp), color = Yellow, strokeWidth = 1.5.dp)
                    else Icon(Icons.Filled.TableChart, null, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Export Stats CSV", fontSize = 10.sp)
                }
            }
            csvMessage?.let { msg ->
                Text(msg, color = TextDim, fontSize = 10.sp, modifier = Modifier.padding(top = 2.dp))
            }
        }

        // About
        SettingsSection("About", Icons.Filled.Info, TextSecondary) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Version", color = TextSecondary, fontSize = 13.sp)
                Text(BuildConfig.VERSION_NAME, color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            }
            if (state.isRootAvailable) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Root", color = TextSecondary, fontSize = 13.sp)
                    Text("Available", color = Green, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                }
            }

            // Accent color picker
            Spacer(Modifier.height(8.dp))
            Text("Accent Color", color = TextDim, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
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
                        modifier = Modifier.size(32.dp)
                    ) {
                        Box(
                            modifier = Modifier.fillMaxSize().padding(6.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(color),
                            contentAlignment = Alignment.Center
                        ) {
                            if (isSelected) {
                                Icon(Icons.Filled.Check, null, tint = Color.Black, modifier = Modifier.size(14.dp))
                            }
                        }
                    }
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
                        if (state.isCheckingUpdate) "Checking..." else "Check for Updates",
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
                                modifier = Modifier.size(20.dp)
                            ) {
                                Icon(Icons.Filled.Close, null, tint = TextDim, modifier = Modifier.size(12.dp))
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
                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(state.updateDownloadUrl))
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
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/SysAdminDoc/HostShield"))
                    context.startActivity(intent)
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
                    Text("View on GitHub", color = TextSecondary, fontSize = 13.sp)
                }
            }
        }

        // Status messages
        val statusMsg = state.backupMessage ?: state.importMessage
        statusMsg?.let { msg ->
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = if (msg.contains("fail", ignoreCase = true)) Red.copy(alpha = 0.08f) else Teal.copy(alpha = 0.08f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        if (msg.contains("fail", ignoreCase = true)) Icons.Filled.Error else Icons.Filled.CheckCircle,
                        null,
                        tint = if (msg.contains("fail", ignoreCase = true)) Red else Teal,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(msg, color = TextSecondary, fontSize = 12.sp, modifier = Modifier.weight(1f))
                    IconButton(
                        onClick = { viewModel.clearBackupMessage(); viewModel.clearImportMessage() },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(Icons.Filled.Close, null, tint = TextDim, modifier = Modifier.size(14.dp))
                    }
                }
            }
        }

        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun SettingsSection(
    title: String,
    icon: ImageVector,
    color: Color,
    content: @Composable ColumnScope.() -> Unit
) {
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier.size(28.dp).clip(RoundedCornerShape(8.dp)).background(color.copy(alpha = 0.1f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(icon, null, tint = color, modifier = Modifier.size(14.dp))
                }
                Spacer(Modifier.width(10.dp))
                Text(title, color = TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            }
            Spacer(Modifier.height(14.dp))
            content()
        }
    }
}

@Composable
private fun SettingsToggle(
    title: String,
    subtitle: String,
    icon: ImageVector,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = TextSecondary, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            Text(subtitle, color = TextDim, fontSize = 11.sp)
        }
        Switch(
            checked = checked, onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Teal, checkedTrackColor = Teal.copy(alpha = 0.25f),
                uncheckedThumbColor = TextDim, uncheckedTrackColor = Surface3
            )
        )
    }
}

@Composable
private fun SettingsRow(
    title: String,
    subtitle: String,
    icon: ImageVector,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = TextSecondary, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            Text(subtitle, color = TextDim, fontSize = 11.sp)
        }
        Icon(Icons.Filled.ChevronRight, null, tint = TextDim, modifier = Modifier.size(18.dp))
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun DohProviderSelector(current: String, onSelect: (String) -> Unit) {
    val providers = listOf(
        "cloudflare" to "Cloudflare",
        "google" to "Google",
        "quad9" to "Quad9",
        "nextdns" to "NextDNS",
        "adguard" to "AdGuard"
    )
    androidx.compose.foundation.layout.FlowRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        providers.forEach { (key, label) ->
            val selected = current == key
            Surface(
                onClick = { onSelect(key) },
                shape = RoundedCornerShape(8.dp),
                color = if (selected) Blue.copy(alpha = 0.12f) else Surface2
            ) {
                Text(
                    label,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                    color = if (selected) Blue else TextDim,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
private fun DotProviderSelector(current: String, onSelect: (String) -> Unit) {
    val providers = listOf(
        "cloudflare" to "Cloudflare",
        "google" to "Google",
        "quad9" to "Quad9",
        "adguard" to "AdGuard"
    )
    androidx.compose.foundation.layout.FlowRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        providers.forEach { (key, label) ->
            val selected = current == key
            Surface(
                onClick = { onSelect(key) },
                shape = RoundedCornerShape(8.dp),
                color = if (selected) Blue.copy(alpha = 0.12f) else Surface2
            ) {
                Text(
                    label,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                    color = if (selected) Blue else TextDim,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
private fun DoqProviderSelector(current: String, onSelect: (String) -> Unit) {
    val providers = listOf(
        "adguard" to "AdGuard",
        "nextdns" to "NextDNS",
        "mullvad" to "Mullvad"
    )
    androidx.compose.foundation.layout.FlowRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        providers.forEach { (key, label) ->
            val selected = current == key
            Surface(
                onClick = { onSelect(key) },
                shape = RoundedCornerShape(8.dp),
                color = if (selected) Blue.copy(alpha = 0.12f) else Surface2
            ) {
                Text(
                    label,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                    color = if (selected) Blue else TextDim,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
private fun BlockResponseSelector(current: String, onSelect: (String) -> Unit) {
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
                modifier = Modifier.fillMaxWidth()
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
                        modifier = Modifier.size(20.dp)
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
