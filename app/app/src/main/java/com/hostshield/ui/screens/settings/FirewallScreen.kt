package com.hostshield.ui.screens.settings

import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hostshield.data.model.FirewallRule
import com.hostshield.ui.accessibility.accessibilityLiveRegion
import com.hostshield.ui.accessibility.accessibilitySelection
import com.hostshield.ui.accessibility.accessibilityToggle
import com.hostshield.ui.components.HostShieldActionIconButton
import com.hostshield.ui.components.HostShieldBackHeader
import com.hostshield.ui.components.HostShieldEmptyState
import com.hostshield.ui.components.HostShieldLoadingState
import com.hostshield.ui.components.HostShieldSegmentOption
import com.hostshield.ui.components.HostShieldSegmentedTabs
import com.hostshield.ui.components.HostShieldStatusBanner
import com.hostshield.ui.theme.*

@Composable
fun FirewallScreen(viewModel: FirewallViewModel = hiltViewModel(), onBack: () -> Unit) {
    val context = LocalContext.current
    val pm = context.packageManager
    val blocked by viewModel.blockedApps.collectAsStateWithLifecycle()
    val excluded by viewModel.excludedApps.collectAsStateWithLifecycle()
    val firewallRules by viewModel.firewallRules.collectAsStateWithLifecycle()
    val blockedRuleCount by viewModel.blockedRuleCount.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val showSystem by viewModel.showSystem.collectAsStateWithLifecycle()
    val filter by viewModel.filter.collectAsStateWithLifecycle()
    val tab by viewModel.tab.collectAsStateWithLifecycle()
    val iptablesActive by viewModel.iptablesActive.collectAsStateWithLifecycle()
    val iptablesError by viewModel.iptablesError.collectAsStateWithLifecycle()
    val isSyncing by viewModel.isSyncing.collectAsStateWithLifecycle()
    val isApplyingIptables by viewModel.isApplyingIptables.collectAsStateWithLifecycle()
    val diagOutput by viewModel.diagnosticOutput.collectAsStateWithLifecycle()
    val isDiagnosing by viewModel.isDiagnosing.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()

    // Installed apps for DNS tab
    val allApps = remember {
        pm.getInstalledApplications(PackageManager.GET_META_DATA)
            .filter { it.packageName != context.packageName }
            .map {
                AppInfo(
                    it.packageName,
                    it.loadLabel(pm).toString(),
                    (it.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                )
            }
            .sortedBy { it.label.lowercase() }
    }

    Column(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        HostShieldBackHeader(
            title = "Firewall",
            subtitle = when (tab) {
                FirewallTab.DNS -> "${blocked.size} apps DNS-blocked"
                FirewallTab.NETWORK -> if (iptablesActive) "$blockedRuleCount network rules active" else "iptables inactive"
                FirewallTab.CONTEXT -> "${firewallRules.count { it.blockScreenOff || it.blockBackground || it.blockMetered }} context rules"
            },
            onBack = onBack,
            actions = {
                HostShieldActionIconButton(
                    icon = if (showSystem) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                    contentDescription = if (showSystem) "Hide system apps" else "Show system apps",
                    accent = Teal,
                    selected = showSystem,
                    onClick = { viewModel.toggleShowSystem() },
                    modifier = Modifier.accessibilityToggle("Show system apps", showSystem),
                )
            },
        )

        // Error banner
        if (error != null) {
            HostShieldStatusBanner(
                icon = Icons.Filled.Error,
                title = "Firewall action failed",
                message = error ?: "",
                accent = Red,
                onDismiss = { viewModel.clearError() },
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
        }

        // Loading indicator
        if (isLoading) {
            HostShieldLoadingState(
                title = "Syncing installed apps",
                message = "Preparing app firewall controls.",
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                    .accessibilityLiveRegion("Loading firewall apps"),
                accent = Teal,
            )
        }

        // Tab selector
        HostShieldSegmentedTabs(
            options = listOf(
                HostShieldSegmentOption(FirewallTab.DNS, "DNS", Red, Icons.Filled.Dns),
                HostShieldSegmentOption(FirewallTab.NETWORK, "Network", Teal, Icons.Filled.Security),
                HostShieldSegmentOption(FirewallTab.CONTEXT, "Context", Mauve, Icons.Filled.Tune),
            ),
            selected = tab,
            onSelected = { viewModel.setTab(it) },
            semanticsLabel = "Firewall mode",
            modifier = Modifier.padding(horizontal = 16.dp),
        )

        Spacer(Modifier.height(8.dp))

        when (tab) {
            FirewallTab.DNS -> DnsFirewallTab(viewModel, allApps, blocked, excluded, searchQuery, showSystem, filter)
            FirewallTab.NETWORK -> NetworkFirewallTab(viewModel, firewallRules, searchQuery, showSystem, iptablesActive, iptablesError, isSyncing, isApplyingIptables, diagOutput, isDiagnosing)
            FirewallTab.CONTEXT -> ContextFirewallTab(viewModel, firewallRules, searchQuery, showSystem)
        }
    }
}

// ---- DNS Firewall Tab -------------------------------------------

@Composable
private fun DnsFirewallTab(
    viewModel: FirewallViewModel,
    allApps: List<AppInfo>,
    blocked: Set<String>,
    excluded: Set<String>,
    searchQuery: String,
    showSystem: Boolean,
    filter: FirewallFilter
) {
    // Info banner
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        shape = RoundedCornerShape(10.dp), color = Surface2
    ) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Info, null, tint = Blue, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(8.dp))
            Text(
                "DNS-blocked apps receive NXDOMAIN for all queries, cutting off internet. Works in both VPN and root modes.",
                color = TextSecondary, fontSize = 11.sp, lineHeight = 15.sp
            )
        }
    }

    Spacer(Modifier.height(8.dp))

    // Search
    OutlinedTextField(
        value = searchQuery, onValueChange = { viewModel.setSearchQuery(it) },
        placeholder = { Text("Search apps...", color = TextDim) },
        leadingIcon = { Icon(Icons.Filled.Search, null, tint = TextDim) },
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        singleLine = true, shape = RoundedCornerShape(12.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Teal, unfocusedBorderColor = Surface3,
            cursorColor = Teal, focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary
        )
    )

    Spacer(Modifier.height(8.dp))

    // Filter chips
    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChipSmall("All", filter == FirewallFilter.ALL) { viewModel.setFilter(FirewallFilter.ALL) }
        FilterChipSmall("Blocked (${blocked.size})", filter == FirewallFilter.BLOCKED) { viewModel.setFilter(FirewallFilter.BLOCKED) }
        FilterChipSmall("Allowed", filter == FirewallFilter.UNBLOCKED) { viewModel.setFilter(FirewallFilter.UNBLOCKED) }
    }

    Spacer(Modifier.height(4.dp))

    val filteredApps = remember(searchQuery, showSystem, filter, allApps, blocked) {
        allApps.filter { app ->
            (showSystem || !app.isSystem) &&
            (searchQuery.isBlank() || app.label.contains(searchQuery, true) || app.packageName.contains(searchQuery, true)) &&
            when (filter) {
                FirewallFilter.ALL -> true
                FirewallFilter.BLOCKED -> app.packageName in blocked
                FirewallFilter.UNBLOCKED -> app.packageName !in blocked
            }
        }
    }

    LazyColumn(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)) {
        if (filteredApps.isEmpty()) {
            item {
                HostShieldEmptyState(
                    icon = Icons.Filled.SearchOff,
                    title = "No apps match this firewall view",
                    message = "Adjust the search, switch filters, or show system apps to broaden the list.",
                    accent = Red,
                )
            }
        }

        items(filteredApps, key = { it.packageName }) { app ->
            val isBlocked = app.packageName in blocked
            val isExcluded = app.packageName in excluded

            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(if (isBlocked) Red else Green))
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(app.label, color = if (isBlocked) Red.copy(alpha = 0.7f) else TextPrimary,
                            style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium,
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (isExcluded) {
                            Spacer(Modifier.width(6.dp))
                            Surface(shape = RoundedCornerShape(4.dp), color = Yellow.copy(alpha = 0.12f)) {
                                Text("EXCLUDED", modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                                    color = Yellow, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                    Text(app.packageName, color = TextDim, style = MaterialTheme.typography.labelSmall,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Switch(
                    checked = isBlocked, onCheckedChange = { viewModel.toggleDnsBlock(app.packageName) },
                    modifier = Modifier.accessibilityToggle("${app.label} DNS block", isBlocked),
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Red, checkedTrackColor = Red.copy(alpha = 0.25f),
                        uncheckedThumbColor = TextDim, uncheckedTrackColor = Surface3
                    )
                )
            }
            HorizontalDivider(color = Surface2.copy(alpha = 0.5f))
        }
    }
}

// ---- Network Firewall Tab (AFWall+ style) -----------------------

@Composable
private fun NetworkFirewallTab(
    viewModel: FirewallViewModel,
    rules: List<FirewallRule>,
    searchQuery: String,
    showSystem: Boolean,
    iptablesActive: Boolean,
    iptablesError: String,
    isSyncing: Boolean,
    isApplyingIptables: Boolean,
    diagOutput: String,
    isDiagnosing: Boolean
) {
    // Error banner
    if (iptablesError.isNotBlank()) {
        Surface(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            shape = RoundedCornerShape(10.dp),
            color = Red.copy(alpha = 0.1f)
        ) {
            Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Error, null, tint = Red, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                Text(iptablesError, color = Red, fontSize = 11.sp, lineHeight = 15.sp)
            }
        }
    }

    // Info banner
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        shape = RoundedCornerShape(10.dp),
        color = if (iptablesActive) Teal.copy(alpha = 0.08f) else Surface2
    ) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                if (iptablesActive) Icons.Filled.Shield else Icons.Filled.Warning,
                null, tint = if (iptablesActive) Teal else Yellow, modifier = Modifier.size(16.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                if (iptablesActive)
                    "iptables firewall active. Per-app WiFi and mobile data control enforced at kernel level."
                else
                    "Network firewall requires root. Configure rules below, then tap Apply to enforce via iptables.",
                color = TextSecondary, fontSize = 11.sp, lineHeight = 15.sp
            )
        }
    }

    Spacer(Modifier.height(8.dp))

    // Action buttons
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Button(
            onClick = { viewModel.applyIptables() },
            enabled = !isApplyingIptables,
            modifier = Modifier.weight(1f),
            colors = ButtonDefaults.buttonColors(containerColor = Teal),
            shape = RoundedCornerShape(10.dp)
        ) {
            if (isApplyingIptables) {
                CircularProgressIndicator(Modifier.size(14.dp), color = Color.Black, strokeWidth = 2.dp)
            } else {
                Icon(Icons.Filled.PlayArrow, null, modifier = Modifier.size(16.dp))
            }
            Spacer(Modifier.width(4.dp))
            Text(if (isApplyingIptables) "Working" else "Apply", fontSize = 12.sp)
        }
        OutlinedButton(
            onClick = { viewModel.clearIptables() },
            enabled = !isApplyingIptables,
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(10.dp),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = Red)
        ) {
            Icon(Icons.Filled.Stop, null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(4.dp))
            Text("Clear", fontSize = 12.sp)
        }
        OutlinedButton(
            onClick = { viewModel.resetAllNetwork() },
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(10.dp),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = TextDim)
        ) {
            Text("Reset", fontSize = 12.sp)
        }
    }

    // Diagnostic row
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        OutlinedButton(
            onClick = { viewModel.runDiagnostic() },
            enabled = !isDiagnosing,
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(10.dp),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = Blue)
        ) {
            if (isDiagnosing) {
                CircularProgressIndicator(
                    Modifier.size(12.dp).accessibilityLiveRegion("Running firewall diagnostic"),
                    color = Blue,
                    strokeWidth = 1.5.dp
                )
            } else {
                Icon(Icons.Filled.BugReport, null, modifier = Modifier.size(14.dp))
            }
            Spacer(Modifier.width(4.dp))
            Text("Diagnose", fontSize = 11.sp)
        }
        OutlinedButton(
            onClick = { viewModel.blockAllWifi() },
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(10.dp),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = Yellow)
        ) {
            Text("Block WiFi", fontSize = 11.sp)
        }
        OutlinedButton(
            onClick = { viewModel.blockAllMobile() },
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(10.dp),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = Yellow)
        ) {
            Text("Block Data", fontSize = 11.sp)
        }
    }

    // Diagnostic output
    if (diagOutput.isNotBlank()) {
        Surface(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            shape = RoundedCornerShape(8.dp),
            color = Surface1
        ) {
            Text(
                diagOutput,
                modifier = Modifier.padding(8.dp).heightIn(max = 200.dp)
                    .verticalScroll(rememberScrollState()),
                color = Teal, fontSize = 9.sp, fontFamily = FontFamily.Monospace,
                lineHeight = 12.sp
            )
        }
    }

    Spacer(Modifier.height(8.dp))

    // Column headers
    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Text("App", modifier = Modifier.weight(1f), color = TextDim, fontSize = 10.sp, fontWeight = FontWeight.Bold)
        Text("WiFi", modifier = Modifier.widthIn(min = 48.dp), color = TextDim, fontSize = 10.sp, fontWeight = FontWeight.Bold,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center, maxLines = 2, lineHeight = 12.sp)
        Text("Data", modifier = Modifier.widthIn(min = 48.dp), color = TextDim, fontSize = 10.sp, fontWeight = FontWeight.Bold,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center, maxLines = 2, lineHeight = 12.sp)
    }

    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = Surface3)

    val filtered = remember(rules, searchQuery, showSystem) {
        rules.filter { rule ->
            (showSystem || !rule.isSystem) &&
            (searchQuery.isBlank() || rule.appLabel.contains(searchQuery, true) || rule.packageName.contains(searchQuery, true))
        }
    }

    if (isSyncing) {
        HostShieldLoadingState(
            title = "Syncing firewall rules",
            message = "Refreshing installed app rules before applying network controls.",
            accent = Teal,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                .accessibilityLiveRegion("Syncing firewall rules"),
        )
    }

    LazyColumn(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 2.dp)) {
        if (filtered.isEmpty() && !isSyncing) {
            item {
                HostShieldEmptyState(
                    icon = Icons.Filled.Security,
                    title = "No network firewall rules shown",
                    message = "Sync installed apps, clear the search, or show system apps to populate this view.",
                    accent = Teal,
                )
            }
        }

        items(filtered, key = { it.uid }) { rule ->
            val anyBlocked = !rule.wifiAllowed || !rule.mobileAllowed

            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Status dot
                Box(modifier = Modifier.size(6.dp).clip(CircleShape)
                    .background(if (anyBlocked) Red else Green))
                Spacer(Modifier.width(8.dp))

                // App info
                Column(modifier = Modifier.weight(1f)) {
                    Text(rule.appLabel, color = if (anyBlocked) Red.copy(alpha = 0.7f) else TextPrimary,
                        fontSize = 13.sp, fontWeight = FontWeight.Medium,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("UID ${rule.uid}", color = TextDim, fontSize = 9.sp)
                }

                // WiFi toggle
                IconButton(
                    onClick = { viewModel.toggleWifi(rule.uid, !rule.wifiAllowed) },
                    modifier = Modifier.size(48.dp).accessibilityToggle("${rule.appLabel} WiFi access", rule.wifiAllowed)
                ) {
                    Icon(
                        Icons.Filled.Wifi,
                        if (rule.wifiAllowed) "WiFi allowed" else "WiFi blocked",
                        tint = if (rule.wifiAllowed) Green else Red,
                        modifier = Modifier.size(18.dp)
                    )
                }

                // Mobile data toggle
                IconButton(
                    onClick = { viewModel.toggleMobile(rule.uid, !rule.mobileAllowed) },
                    modifier = Modifier.size(48.dp).accessibilityToggle("${rule.appLabel} mobile data access", rule.mobileAllowed)
                ) {
                    Icon(
                        Icons.Filled.SignalCellularAlt,
                        if (rule.mobileAllowed) "Mobile data allowed" else "Mobile data blocked",
                        tint = if (rule.mobileAllowed) Green else Red,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
            HorizontalDivider(color = Surface2.copy(alpha = 0.3f))
        }
    }
}

// ---- Context-Aware Firewall Tab ----------------------------------

@Composable
private fun ContextFirewallTab(
    viewModel: FirewallViewModel,
    rules: List<FirewallRule>,
    searchQuery: String,
    showSystem: Boolean
) {
    // Info banner
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        shape = RoundedCornerShape(10.dp), color = Mauve.copy(alpha = 0.08f)
    ) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Tune, null, tint = Mauve, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(8.dp))
            Text(
                "Block apps based on context: screen off, background, or metered network. " +
                "DNS queries are blocked with NXDOMAIN when conditions match.",
                color = TextSecondary, fontSize = 11.sp, lineHeight = 15.sp
            )
        }
    }

    Spacer(Modifier.height(8.dp))

    // Column headers
    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Text("App", modifier = Modifier.weight(1f), color = TextDim, fontSize = 10.sp, fontWeight = FontWeight.Bold)
        Text("Screen off", modifier = Modifier.widthIn(min = 48.dp), color = TextDim, fontSize = 9.sp, fontWeight = FontWeight.Bold,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center, maxLines = 2, lineHeight = 11.sp)
        Text("Background", modifier = Modifier.widthIn(min = 48.dp), color = TextDim, fontSize = 9.sp, fontWeight = FontWeight.Bold,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center, maxLines = 2, lineHeight = 11.sp)
        Text("Metered", modifier = Modifier.widthIn(min = 48.dp), color = TextDim, fontSize = 9.sp, fontWeight = FontWeight.Bold,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center, maxLines = 2, lineHeight = 11.sp)
    }

    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = Surface3)

    val filtered = remember(rules, searchQuery, showSystem) {
        rules.filter { rule ->
            (showSystem || !rule.isSystem) &&
            (searchQuery.isBlank() || rule.appLabel.contains(searchQuery, true) || rule.packageName.contains(searchQuery, true))
        }
    }

    LazyColumn(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 2.dp)) {
        if (filtered.isEmpty()) {
            item {
                HostShieldEmptyState(
                    icon = Icons.Filled.Tune,
                    title = "No context rules shown",
                    message = "Clear the search or show system apps to configure context-aware blocking.",
                    accent = Mauve,
                )
            }
        }

        items(filtered, key = { "ctx_${it.uid}" }) { rule ->
            val hasContext = rule.blockScreenOff || rule.blockBackground || rule.blockMetered

            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(modifier = Modifier.size(6.dp).clip(CircleShape)
                    .background(if (hasContext) Mauve else Surface3))
                Spacer(Modifier.width(8.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(rule.appLabel, color = if (hasContext) Mauve.copy(alpha = 0.8f) else TextPrimary,
                        fontSize = 12.sp, fontWeight = FontWeight.Medium,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                }

                // Screen Off toggle
                IconButton(
                    onClick = { viewModel.toggleBlockScreenOff(rule.uid, !rule.blockScreenOff) },
                    modifier = Modifier.size(48.dp).accessibilityToggle("${rule.appLabel} screen-off blocking", rule.blockScreenOff)
                ) {
                    Icon(
                        if (rule.blockScreenOff) Icons.Filled.DarkMode else Icons.Filled.LightMode,
                        if (rule.blockScreenOff) "Screen-off blocking on" else "Screen-off blocking off",
                        tint = if (rule.blockScreenOff) Mauve else Surface4,
                        modifier = Modifier.size(16.dp)
                    )
                }

                // Background toggle
                IconButton(
                    onClick = { viewModel.toggleBlockBackground(rule.uid, !rule.blockBackground) },
                    modifier = Modifier.size(48.dp).accessibilityToggle("${rule.appLabel} background blocking", rule.blockBackground)
                ) {
                    Icon(
                        if (rule.blockBackground) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                        if (rule.blockBackground) "Background blocking on" else "Background blocking off",
                        tint = if (rule.blockBackground) Mauve else Surface4,
                        modifier = Modifier.size(16.dp)
                    )
                }

                // Metered toggle
                IconButton(
                    onClick = { viewModel.toggleBlockMetered(rule.uid, !rule.blockMetered) },
                    modifier = Modifier.size(48.dp).accessibilityToggle("${rule.appLabel} metered-network blocking", rule.blockMetered)
                ) {
                    Icon(
                        if (rule.blockMetered) Icons.Filled.MoneyOff else Icons.Filled.AttachMoney,
                        if (rule.blockMetered) "Metered-network blocking on" else "Metered-network blocking off",
                        tint = if (rule.blockMetered) Mauve else Surface4,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
            HorizontalDivider(color = Surface2.copy(alpha = 0.3f))
        }
    }
}

// ---- Shared Components ------------------------------------------

@Composable
private fun TabPill(label: String, selected: Boolean, accent: Color, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(10.dp),
        color = if (selected) accent.copy(alpha = 0.15f) else Surface2,
        modifier = Modifier.accessibilitySelection("$label firewall tab", selected)
    ) {
        Text(
            label,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
            color = if (selected) accent else TextDim,
            fontSize = 12.sp, fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun FilterChipSmall(label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(8.dp),
        color = if (selected) Red.copy(alpha = 0.12f) else Surface2,
        modifier = Modifier.accessibilitySelection("$label firewall filter", selected)
    ) {
        Text(
            label,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            color = if (selected) Red else TextDim,
            fontSize = 11.sp, fontWeight = FontWeight.SemiBold
        )
    }
}
