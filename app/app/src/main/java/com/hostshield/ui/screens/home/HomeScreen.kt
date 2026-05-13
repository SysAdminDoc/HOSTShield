package com.hostshield.ui.screens.home

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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.hostshield.ui.theme.*

// HostShield v1.6.0 — Premium Home Dashboard

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel = hiltViewModel(),
    onNavigateToLogs: () -> Unit = {},
    onNavigateToApps: () -> Unit = {},
    onNavigateToFirewall: () -> Unit = {},
    onNavigateToConnectionLog: () -> Unit = {},
    onRequestVpnPermission: ((Boolean) -> Unit) -> Unit = {},
    onNavigateToAppLogs: (String) -> Unit = {}
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val liveLogs by viewModel.liveLogs.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    // Re-check battery + Private DNS when user returns from system settings
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        viewModel.recheckWarnings()
    }

    // Direct VPN permission request — no LaunchedEffect, no state flags
    val requestVpnThenApply: () -> Unit = {
        onRequestVpnPermission { granted ->
            viewModel.onVpnPermissionResult(granted)
        }
    }

    // Show snackbar messages
    LaunchedEffect(state.snackbarMessage) {
        state.snackbarMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg, duration = SnackbarDuration.Short)
            viewModel.dismissSnackbar()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(20.dp))

        // Brand header
        BrandHeader()

        // Universal search
        var searchQuery by remember { mutableStateOf("") }
        var searchExpanded by remember { mutableStateOf(false) }
        val searchHistory by viewModel.searchHistory.collectAsStateWithLifecycle()

        HomeSearchSection(
            searchQuery = searchQuery,
            onSearchQueryChange = { searchQuery = it },
            searchExpanded = searchExpanded,
            onSearchExpandedChange = { searchExpanded = it },
            searchHistory = searchHistory,
            onSaveSearch = { viewModel.saveSearch(it) },
            onNavigateToLogs = onNavigateToLogs,
            onNavigateToApps = onNavigateToApps
        )

        Spacer(Modifier.height(24.dp))

        // Shield Orb — the centerpiece
        ShieldOrb(
            isEnabled = state.isEnabled,
            isApplying = state.isApplying,
            blockedCount = state.totalDomainsBlocked,
            onToggle = {
                if (state.isEnabled) {
                    viewModel.disableBlocking()
                } else if (state.blockMethod == com.hostshield.data.model.BlockMethod.VPN) {
                    requestVpnThenApply()
                } else {
                    viewModel.applyRootMode()
                }
            }
        )

        Spacer(Modifier.height(6.dp))

        // Status label
        StatusLabel(state.isEnabled, state.isApplying)

        // Progress message
        AnimatedVisibility(
            visible = state.isApplying && state.progressMessage.isNotEmpty(),
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            Text(
                text = state.progressMessage,
                color = TextSecondary,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 6.dp),
                textAlign = TextAlign.Center
            )
        }

        // Error banner
        state.errorMessage?.let { error ->
            ErrorBanner(error) { viewModel.dismissError() }
        }

        // Warning banners, feature pills, live rates
        HomeWarningsSection(
            privateDnsWarning = state.privateDnsWarning,
            privateDnsSettingsIntent = try { viewModel.getPrivateDnsSettingsIntent() } catch (_: Exception) { null },
            onDismissPrivateDns = { viewModel.dismissPrivateDnsWarning() },
            batteryWarning = state.batteryWarning,
            onRequestBatteryExemption = { viewModel.requestBatteryExemption(context) },
            onDismissBattery = { viewModel.dismissBatteryWarning() },
            privateSpaceWarning = state.privateSpaceWarning,
            onDismissPrivateSpace = { viewModel.dismissPrivateSpaceWarning() },
            queryAnomalyWarning = state.queryAnomalyWarning,
            droppedQueries = state.droppedQueries,
            isEnabled = state.isEnabled,
            blockMethod = state.blockMethod,
            dohEnabled = state.dohEnabled,
            dnsTrapEnabled = state.dnsTrapEnabled,
            firewalledApps = state.firewalledApps,
            networkFirewallActive = state.networkFirewallActive,
            queriesPerMinute = state.queriesPerMinute,
            blocksPerMinute = state.blocksPerMinute,
            avgLatencyMs = state.avgLatencyMs,
            latencySparkline = state.latencySparkline,
            context = context
        )

        Spacer(Modifier.height(24.dp))

        // Stats grid, privacy score, categories, top apps
        HomeStatsSection(
            totalDomainsBlocked = state.totalDomainsBlocked,
            blockedToday = state.blockedToday,
            totalQueriesToday = state.totalQueriesToday,
            enabledSources = state.enabledSources,
            privacyScore = state.privacyScore,
            privacyItems = state.privacyItems,
            categoryCounts = state.categoryCounts,
            topApps = state.topApps,
            onNavigateToLogs = onNavigateToLogs,
            onToggleCategory = { cat, enable -> viewModel.toggleCategory(cat, enable) },
            onNavigateToAppLogs = onNavigateToAppLogs
        )

        Spacer(Modifier.height(20.dp))

        // ── Protection Modules ──────────────────────────────
        Column(modifier = Modifier.padding(horizontal = 20.dp)) {
            Text(
                "Protection Modules",
                style = MaterialTheme.typography.titleSmall,
                color = TextSecondary,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(bottom = 10.dp)
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Hosts blocking module
                ModuleCard(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Filled.Storage,
                    title = "Hosts",
                    status = if (state.isEnabled && state.blockMethod == com.hostshield.data.model.BlockMethod.ROOT_HOSTS)
                        "Active" else "Off",
                    detail = "${formatCompact(state.totalDomainsBlocked)} rules",
                    accent = Teal,
                    isActive = state.isEnabled && state.blockMethod == com.hostshield.data.model.BlockMethod.ROOT_HOSTS,
                    onClick = {
                        if (!state.isApplying) {
                            viewModel.setBlockMethod(com.hostshield.data.model.BlockMethod.ROOT_HOSTS)
                            if (state.activeMethod != com.hostshield.data.model.BlockMethod.ROOT_HOSTS) {
                                viewModel.applyRootMode()
                            }
                        }
                    }
                )
                // VPN blocking module
                ModuleCard(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Filled.VpnLock,
                    title = "VPN",
                    status = if (state.isEnabled && state.blockMethod == com.hostshield.data.model.BlockMethod.VPN)
                        "Active" else "Off",
                    detail = if (state.dohEnabled) "DoH on" else "DNS filter",
                    accent = Blue,
                    isActive = state.isEnabled && state.blockMethod == com.hostshield.data.model.BlockMethod.VPN,
                    onClick = {
                        if (!state.isApplying) {
                            viewModel.setBlockMethod(com.hostshield.data.model.BlockMethod.VPN)
                            if (state.activeMethod != com.hostshield.data.model.BlockMethod.VPN) {
                                requestVpnThenApply()
                            }
                        }
                    }
                )
                // Firewall module
                ModuleCard(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Filled.LocalFireDepartment,
                    title = "Firewall",
                    status = if (state.networkFirewallActive) "Active" else "Off",
                    detail = if (state.networkFirewallActive)
                        "${state.networkFirewallRules} rules" else "iptables",
                    accent = Peach,
                    isActive = state.networkFirewallActive,
                    onClick = onNavigateToFirewall
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        // ── Live DNS Activity Feed ──────────────────────────
        Column(modifier = Modifier.padding(horizontal = 20.dp)) {
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Blue.copy(alpha = 0.1f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Filled.Dns, null, tint = Blue, modifier = Modifier.size(16.dp))
                            }
                            Spacer(Modifier.width(10.dp))
                            Text(
                                "Live DNS Activity",
                                style = MaterialTheme.typography.titleSmall,
                                color = TextPrimary,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        Surface(
                            onClick = onNavigateToLogs,
                            shape = RoundedCornerShape(8.dp),
                            color = Surface2
                        ) {
                            Text(
                                "View all",
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                color = Teal,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    Spacer(Modifier.height(12.dp))

                    if (!state.dnsLoggingEnabled) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(Yellow.copy(alpha = 0.06f))
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Filled.Warning, null, tint = Yellow, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "DNS logging is disabled. Enable it in Settings to see activity here.",
                                color = Yellow.copy(alpha = 0.8f),
                                fontSize = 11.sp,
                                lineHeight = 15.sp
                            )
                        }
                    } else if (liveLogs.isEmpty()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(Surface2.copy(alpha = 0.5f))
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(Icons.Filled.HourglassEmpty, null, tint = TextDim, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(
                                if (state.isEnabled) "Waiting for DNS queries"
                                else "Enable protection to see DNS activity",
                                color = TextDim,
                                fontSize = 12.sp
                            )
                        }
                    } else {
                        val recentEntries = liveLogs.take(8)
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            for (entry in recentEntries) {
                                LiveLogRow(entry)
                            }
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // ── Feature Access Cards ────────────────────────────
        Column(
            modifier = Modifier.padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                "Tools",
                style = MaterialTheme.typography.titleSmall,
                color = TextSecondary,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(bottom = 2.dp)
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                FeatureAccessCard(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Filled.Dns,
                    title = "DNS Logs",
                    subtitle = "${formatCompact(state.blockedToday)} blocked today",
                    accent = Blue,
                    gradientEnd = Teal,
                    onClick = onNavigateToLogs
                )
                FeatureAccessCard(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Filled.LocalFireDepartment,
                    title = "Firewall Log",
                    subtitle = "${formatCompact(state.firewallBlockedConnections)} blocked",
                    accent = Peach,
                    gradientEnd = Red,
                    onClick = onNavigateToConnectionLog
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                FeatureAccessCard(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Filled.Apps,
                    title = "App Activity",
                    subtitle = "Per-app DNS queries",
                    accent = Mauve,
                    gradientEnd = Flamingo,
                    onClick = onNavigateToApps
                )
                FeatureAccessCard(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Filled.Security,
                    title = "Firewall Rules",
                    subtitle = "${state.firewalledApps} apps firewalled",
                    accent = Red,
                    gradientEnd = Peach,
                    onClick = onNavigateToFirewall
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        // ── Blocking Mode & Actions ─────────────────────────
        Column(modifier = Modifier.padding(horizontal = 20.dp)) {
            // Mode selector
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Teal.copy(alpha = 0.1f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Filled.Tune, null, tint = Teal, modifier = Modifier.size(16.dp))
                        }
                        Spacer(Modifier.width(10.dp))
                        Text(
                            "Blocking Mode",
                            style = MaterialTheme.typography.titleSmall,
                            color = TextPrimary,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ModeChip(
                            label = "Root",
                            icon = Icons.Filled.AdminPanelSettings,
                            selected = state.blockMethod == com.hostshield.data.model.BlockMethod.ROOT_HOSTS,
                            enabled = state.isRootAvailable,
                            onClick = {
                                viewModel.setBlockMethod(com.hostshield.data.model.BlockMethod.ROOT_HOSTS)
                                if (state.activeMethod != com.hostshield.data.model.BlockMethod.ROOT_HOSTS && !state.isApplying) {
                                    viewModel.applyRootMode()
                                }
                            }
                        )
                        ModeChip(
                            label = "VPN",
                            icon = Icons.Filled.VpnLock,
                            selected = state.blockMethod == com.hostshield.data.model.BlockMethod.VPN,
                            enabled = true,
                            onClick = {
                                viewModel.setBlockMethod(com.hostshield.data.model.BlockMethod.VPN)
                                if (state.activeMethod != com.hostshield.data.model.BlockMethod.VPN && !state.isApplying) {
                                    requestVpnThenApply()
                                }
                            }
                        )
                    }
                    if (state.lastApplyTime > 0L) {
                        Spacer(Modifier.height(10.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Schedule, null, tint = TextDim, modifier = Modifier.size(13.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(
                                "Applied ${formatLastApply(state.lastApplyTime)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = TextDim
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(10.dp))

            // Quick Actions
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Peach.copy(alpha = 0.1f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Filled.FlashOn, null, tint = Peach, modifier = Modifier.size(16.dp))
                        }
                        Spacer(Modifier.width(10.dp))
                        Text(
                            "Quick Actions",
                            style = MaterialTheme.typography.titleSmall,
                            color = TextPrimary,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    ActionRow(
                        icon = Icons.Filled.Refresh,
                        label = "Update and apply",
                        subtitle = "Download latest sources and apply",
                        color = Teal,
                        enabled = !state.isApplying,
                        onClick = {
                            if (state.blockMethod == com.hostshield.data.model.BlockMethod.VPN) {
                                requestVpnThenApply()
                            } else {
                                viewModel.applyRootMode()
                            }
                        }
                    )
                    Spacer(Modifier.height(4.dp))
                    ActionRow(
                        icon = Icons.Filled.RestartAlt,
                        label = "Pause protection",
                        subtitle = "Stop blocking and restore the system hosts file",
                        color = TextSecondary,
                        enabled = !state.isApplying,
                        onClick = { viewModel.disableBlocking() }
                    )
                }
            }
        }

        // Root warning
        if (!state.isRootAvailable) {
            Spacer(Modifier.height(12.dp))
            GlassCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                borderBrush = Brush.linearGradient(
                    colors = listOf(Yellow.copy(alpha = 0.3f), Yellow.copy(alpha = 0.05f))
                )
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Icon(Icons.Filled.Warning, null, tint = Yellow, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text("Root Not Detected", color = Yellow, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                        Spacer(Modifier.height(2.dp))
                        Text(
                            "Grant root permission or use VPN mode.",
                            color = TextSecondary,
                            style = MaterialTheme.typography.bodySmall,
                            lineHeight = 16.sp
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(32.dp))
    }

    // Snackbar overlay
    SnackbarHost(
        hostState = snackbarHostState,
        modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 12.dp)
    ) { data ->
        Snackbar(
            snackbarData = data,
            containerColor = Surface2,
            contentColor = TextPrimary,
            shape = RoundedCornerShape(12.dp)
        )
    }
    } // Box
}
