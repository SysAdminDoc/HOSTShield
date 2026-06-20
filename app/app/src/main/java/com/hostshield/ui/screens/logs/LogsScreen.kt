package com.hostshield.ui.screens.logs

import androidx.core.net.toUri
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hostshield.ui.accessibility.accessibilityAction
import com.hostshield.ui.accessibility.accessibilityHeading
import com.hostshield.ui.accessibility.accessibilityToggle
import com.hostshield.ui.HostShieldTestTags
import com.hostshield.ui.components.ConfirmDestructiveDialog
import com.hostshield.ui.components.HostShieldActionIconButton
import com.hostshield.ui.components.HostShieldEmptyState
import com.hostshield.ui.components.HostShieldFilterChip
import com.hostshield.ui.components.HostShieldInlineAction
import com.hostshield.ui.components.HostShieldLoadingState
import com.hostshield.ui.components.HostShieldScreenHeader
import com.hostshield.ui.components.HostShieldStatusBanner
import com.hostshield.ui.theme.*
import com.hostshield.util.GeoIpLookup
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun LogsScreen(viewModel: LogsViewModel = hiltViewModel(), onBack: (() -> Unit)? = null) {
    val logs by viewModel.logs.collectAsStateWithLifecycle()
    val query by viewModel.searchQuery.collectAsStateWithLifecycle()
    val blockedFilter by viewModel.showBlocked.collectAsStateWithLifecycle()
    val threatIntelOnly by viewModel.threatIntelOnly.collectAsStateWithLifecycle()
    val blockedSet by viewModel.blockedHostnames.collectAsStateWithLifecycle()
    val pinnedSet by viewModel.pinnedDomains.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()

    var selectedEntry by remember { mutableStateOf<DedupedLogEntry?>(null) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // Multi-select state
    var multiSelectMode by remember { mutableStateOf(false) }
    var selectedHostnames by remember { mutableStateOf(setOf<String>()) }
    var showClearLogsDialog by remember { mutableStateOf(false) }

    val queryTypeFilter by viewModel.queryTypeFilter.collectAsStateWithLifecycle()
    val deduped by viewModel.deduped.collectAsStateWithLifecycle()
    val totalDomains by viewModel.totalDomains.collectAsStateWithLifecycle()
    val blockedCount by viewModel.blockedCount.collectAsStateWithLifecycle()
    val backAction = onBack

    Column(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        // Header
        HostShieldScreenHeader(
            title = "DNS Logs",
            subtitle = "$totalDomains domains - $blockedCount blocked - ${logs.size} queries",
            modifier = Modifier.padding(horizontal = if (backAction != null) 8.dp else 20.dp, vertical = 12.dp),
            leadingContent = if (backAction != null) {
                {
                    IconButton(onClick = backAction) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = TextPrimary)
                    }
                }
            } else {
                null
            },
        ) {
            HostShieldActionIconButton(
                icon = if (multiSelectMode) Icons.Filled.Close else Icons.Filled.Checklist,
                contentDescription = if (multiSelectMode) "Exit multi-select" else "Enter multi-select",
                onClick = {
                    multiSelectMode = !multiSelectMode
                    if (!multiSelectMode) selectedHostnames = emptySet()
                },
                accent = Teal,
                selected = multiSelectMode,
            )
            HostShieldActionIconButton(
                icon = Icons.Filled.DeleteSweep,
                contentDescription = "Clear DNS logs",
                onClick = { showClearLogsDialog = true },
                accent = Red,
                enabled = logs.isNotEmpty(),
            )
        }

        if (multiSelectMode && selectedHostnames.isEmpty()) {
            HostShieldStatusBanner(
                icon = Icons.Filled.Checklist,
                title = "Selection mode",
                message = "Long-press or check domains to apply block or allow actions together.",
                accent = Teal,
                announce = false,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
            )
        }

        if (error != null) {
            HostShieldStatusBanner(
                icon = Icons.Filled.Error,
                title = "DNS log error",
                message = error ?: "",
                accent = Red,
                onDismiss = { viewModel.clearError() },
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
            )
        }

        // Loading indicator
        if (isLoading) {
            HostShieldLoadingState(
                title = "Loading DNS decisions",
                message = "Restoring pinned, blocked, and allowed domain state.",
                accent = Teal,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
            )
        }

        // Multi-select action bar
        if (multiSelectMode && selectedHostnames.isNotEmpty()) {
            Surface(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                shape = RoundedCornerShape(10.dp),
                color = Surface2.copy(alpha = 0.86f),
                border = androidx.compose.foundation.BorderStroke(1.dp, Teal.copy(alpha = 0.20f)),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        "${selectedHostnames.size} selected",
                        color = Teal,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f)
                    )
                    HostShieldInlineAction(
                        label = "Block",
                        icon = Icons.Filled.Block,
                        accent = Red,
                        onClick = {
                            viewModel.blockDomains(selectedHostnames)
                            selectedHostnames = emptySet()
                            multiSelectMode = false
                        },
                    )
                    HostShieldInlineAction(
                        label = "Allow",
                        icon = Icons.Filled.CheckCircle,
                        accent = Green,
                        onClick = {
                            viewModel.allowDomains(selectedHostnames)
                            selectedHostnames = emptySet()
                            multiSelectMode = false
                        },
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
        }

        // Search
        OutlinedTextField(
            value = query, onValueChange = { viewModel.setSearch(it) },
            placeholder = { Text("Search domains or apps", color = TextDim) },
            leadingIcon = { Icon(Icons.Filled.Search, null, tint = TextDim) },
            trailingIcon = {
                if (query.isNotBlank()) {
                    IconButton(onClick = { viewModel.setSearch("") }) {
                        Icon(Icons.Filled.Close, "Clear DNS log search", tint = TextDim, modifier = Modifier.size(16.dp))
                    }
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .testTag(HostShieldTestTags.Logs.SearchField),
            singleLine = true, shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Teal, unfocusedBorderColor = Surface3,
                cursorColor = Teal, focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary
            )
        )

        Spacer(Modifier.height(8.dp))

        // Filters
        Row(modifier = Modifier.padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            LogFilter("All", blockedFilter == null) { viewModel.setFilter(null) }
            LogFilter("Blocked", blockedFilter == true, accent = Red) { viewModel.setFilter(true) }
            LogFilter("Allowed", blockedFilter == false, accent = Green) { viewModel.setFilter(false) }
        }

        Spacer(Modifier.height(4.dp))
        FlowRow(
            modifier = Modifier.padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            HostShieldFilterChip(
                label = "Threat review",
                selected = threatIntelOnly,
                onClick = { viewModel.setThreatIntelOnly(!threatIntelOnly) },
                accent = Red,
                semanticsLabel = "Threat intel review queue filter",
            )
            val types = listOf(null to "All types", "A" to "A", "AAAA" to "AAAA", "CNAME" to "CNAME", "MX" to "MX", "TXT" to "TXT")
            types.forEach { (type, label) ->
                HostShieldFilterChip(
                    label = label,
                    selected = queryTypeFilter == type,
                    onClick = { viewModel.setQueryTypeFilter(type) },
                    accent = Blue,
                    semanticsLabel = "$label DNS query type filter",
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        val hasActiveFilters = query.isNotBlank() || blockedFilter != null || queryTypeFilter != null || threatIntelOnly
        if (deduped.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp),
                contentAlignment = Alignment.Center
            ) {
                HostShieldEmptyState(
                    icon = if (hasActiveFilters) Icons.Filled.FilterAltOff else Icons.Filled.Dns,
                    title = if (hasActiveFilters) "No matching DNS activity" else "No DNS activity yet",
                    message = if (hasActiveFilters) {
                        if (threatIntelOnly) {
                            "No recent threat-intel block matches the current search and filter combination."
                        } else {
                            "No captured query matches the current search and filter combination."
                        }
                    } else {
                        "Captured DNS queries will appear here with verdicts, apps, timing, and rule actions."
                    },
                    accent = if (hasActiveFilters) Blue else Teal,
                    primaryActionLabel = if (hasActiveFilters) "Clear filters" else null,
                    onPrimaryAction = if (hasActiveFilters) {
                        {
                            viewModel.setSearch("")
                            viewModel.setFilter(null)
                            viewModel.setQueryTypeFilter(null)
                            viewModel.setThreatIntelOnly(false)
                        }
                    } else {
                        null
                    },
                )
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                items(deduped, key = { it.hostname }) { entry ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (multiSelectMode) {
                            Checkbox(
                                checked = entry.hostname in selectedHostnames,
                                onCheckedChange = { checked ->
                                    selectedHostnames = if (checked) selectedHostnames + entry.hostname
                                    else selectedHostnames - entry.hostname
                                },
                                colors = CheckboxDefaults.colors(
                                    checkedColor = Teal,
                                    uncheckedColor = TextDim,
                                    checkmarkColor = Color.Black
                                ),
                                modifier = Modifier
                                    .size(48.dp)
                                    .accessibilityToggle(
                                        "Select ${entry.hostname}",
                                        entry.hostname in selectedHostnames
                                    )
                            )
                            Spacer(Modifier.width(4.dp))
                        }
                        Box(modifier = Modifier.weight(1f)) {
                            LogItem(
                                entry = entry,
                                onBlock = { viewModel.blockDomain(entry.hostname) },
                                onAllow = { viewModel.allowDomain(entry.hostname) },
                                onTap = { selectedEntry = entry },
                                onLongPress = {
                                    if (!multiSelectMode) {
                                        multiSelectMode = true
                                        selectedHostnames = setOf(entry.hostname)
                                    }
                                }
                            )
                        }
                    }
                }
                item { Spacer(Modifier.height(16.dp)) }
            }
        }
    }

    // Detail bottom sheet
    if (selectedEntry != null) {
        ModalBottomSheet(
            onDismissRequest = { selectedEntry = null },
            sheetState = sheetState,
            containerColor = Surface1,
            scrimColor = Color.Black.copy(alpha = 0.6f)
        ) {
            val entry = selectedEntry ?: return@ModalBottomSheet
            QueryDetailSheet(
                entry = entry,
                onDismiss = { selectedEntry = null },
                isPinned = entry.hostname in pinnedSet,
                onTogglePin = { viewModel.togglePin(entry.hostname) },
                onTemporaryAllow = { mins -> viewModel.temporaryAllow(entry.hostname, mins) },
                onBlock = { viewModel.blockDomain(entry.hostname) },
                onAllow = { viewModel.allowDomain(entry.hostname) },
                onAllowForApp = { viewModel.allowThreatIntelForApp(entry.hostname, entry.appPackage) },
                geoLookup = viewModel::lookupAllGeo
            )
        }
    }

    if (showClearLogsDialog) {
        ConfirmDestructiveDialog(
            title = "Clear DNS logs?",
            body = "This removes the local DNS query history used by this screen. Rules, sources, and blocking settings stay unchanged.",
            confirmLabel = "Clear logs",
            onConfirm = { viewModel.clearLogs() },
            onDismiss = { showClearLogsDialog = false },
        )
    }
}

@Composable
private fun LogFilter(label: String, selected: Boolean, accent: Color = Teal, onClick: () -> Unit) {
    HostShieldFilterChip(
        label = label,
        selected = selected,
        onClick = onClick,
        accent = accent,
        semanticsLabel = "$label DNS log filter",
    )
}

@Composable
private fun LogItem(entry: DedupedLogEntry, onBlock: () -> Unit, onAllow: () -> Unit, onTap: () -> Unit = {}, onLongPress: () -> Unit = {}) {
    var expanded by remember { mutableStateOf(false) }

    val blocked = entry.blocked

    // ── Animated color transitions ──
    val cardBg by animateColorAsState(
        if (blocked) Red.copy(alpha = 0.07f) else Color.Transparent, tween(300), label = "bg"
    )
    val stripColor by animateColorAsState(
        if (blocked) Red else Green.copy(alpha = 0.5f), tween(250), label = "strip"
    )
    val hostColor by animateColorAsState(
        if (blocked) Red.copy(alpha = 0.65f) else TextPrimary, tween(300), label = "host"
    )
    val badgeBg by animateColorAsState(
        if (blocked) Red.copy(alpha = 0.15f) else Green.copy(alpha = 0.08f), tween(300), label = "badgeBg"
    )
    val badgeText by animateColorAsState(
        if (blocked) Red else Green, tween(300), label = "badgeText"
    )

    // Outer card
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .accessibilityAction(
                "${entry.hostname}, ${if (blocked) "blocked" else "allowed"}, ${entry.hitCount} recent ${if (entry.hitCount == 1) "query" else "queries"}"
            )
            .background(
                Brush.horizontalGradient(
                    colors = if (blocked)
                        listOf(Red.copy(alpha = 0.10f), cardBg, Surface1.copy(alpha = 0.5f))
                    else
                        listOf(Surface1.copy(alpha = 0.5f), Surface1.copy(alpha = 0.4f))
                )
            )
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            // ── Left color strip — 4dp solid bar ──
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .heightIn(min = 52.dp)
                    .clip(RoundedCornerShape(topStart = 14.dp, bottomStart = 14.dp))
                    .background(stripColor)
            )

            @OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .combinedClickable(
                        onClick = { expanded = !expanded },
                        onLongClick = onLongPress
                    )
                    .padding(start = 10.dp, end = 12.dp, top = 10.dp, bottom = 10.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // ── Block icon only for blocked entries ──
                    if (blocked) {
                        Icon(
                            Icons.Filled.Block,
                            contentDescription = null,
                            tint = Red.copy(alpha = 0.7f),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(7.dp))
                    }

                    // ── Hostname + metadata ──
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            entry.hostname,
                            color = hostColor,
                            fontSize = 12.sp,
                            lineHeight = 16.sp,
                            fontWeight = if (blocked) FontWeight.SemiBold else FontWeight.Medium,
                            fontFamily = FontFamily.Monospace,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            textDecoration = if (blocked) TextDecoration.LineThrough else TextDecoration.None
                        )
                        Spacer(Modifier.height(2.dp))
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            if (entry.appLabel.isNotEmpty()) {
                                Text(entry.appLabel, color = TextDim, fontSize = 10.sp, lineHeight = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            if (entry.hitCount > 1) {
                                Text("${entry.hitCount}x", color = TextDim, fontSize = 10.sp, lineHeight = 13.sp)
                            }
                            Text(formatTime(entry.latestTimestamp), color = TextDim, fontSize = 10.sp, lineHeight = 13.sp)
                        }
                    }

                    // ── Status badge ──
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = badgeBg
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                if (blocked) Icons.Filled.Block else Icons.Filled.CheckCircle,
                                null,
                                tint = badgeText,
                                modifier = Modifier.size(11.dp)
                            )
                            Text(
                                if (blocked) "BLOCKED" else "OK",
                                color = badgeText,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.ExtraBold,
                                letterSpacing = 0.sp
                            )
                        }
                    }
                }

                // ── Expanded: actions ──
                AnimatedVisibility(visible = expanded) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (entry.appPackage.isNotEmpty()) {
                            Text(
                                entry.appPackage, color = TextDim, fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace,
                                maxLines = 1, overflow = TextOverflow.Ellipsis
                            )
                        }

                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.End),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            HostShieldInlineAction(
                                label = "Details",
                                icon = Icons.Filled.Info,
                                onClick = onTap,
                                accent = Blue,
                            )
                            if (!blocked) {
                                HostShieldInlineAction(
                                    label = "Block",
                                    icon = Icons.Filled.Block,
                                    onClick = onBlock,
                                    accent = Red,
                                )
                            } else {
                                HostShieldInlineAction(
                                    label = "Allow",
                                    icon = Icons.Filled.CheckCircle,
                                    onClick = onAllow,
                                    accent = Green,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun formatTime(ms: Long): String = try {
    Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("h:mm:ss a"))
} catch (_: Exception) { "" }

private fun formatDecisionReason(reason: String): String =
    reason.split('_')
        .filter { it.isNotBlank() }
        .joinToString(" ") { word -> word.replaceFirstChar { it.uppercase() } }

@Composable
private fun QueryDetailSheet(
    entry: DedupedLogEntry,
    onDismiss: () -> Unit,
    isPinned: Boolean = false,
    onTogglePin: () -> Unit = {},
    onTemporaryAllow: (Int) -> Unit = {},
    onBlock: () -> Unit = {},
    onAllow: () -> Unit = {},
    onAllowForApp: () -> Unit = {},
    geoLookup: (suspend (List<String>) -> List<GeoIpLookup.GeoInfo>)? = null
) {
    val context = androidx.compose.ui.platform.LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 8.dp)
            .padding(bottom = 32.dp)
    ) {
        // Title
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                if (entry.blocked) Icons.Filled.Block else Icons.Filled.CheckCircle,
                null,
                tint = if (entry.blocked) Red else Green,
                modifier = Modifier.size(22.dp)
            )
            Spacer(Modifier.width(10.dp))
            Text(
                "Query Details",
                color = TextPrimary,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                modifier = Modifier.weight(1f).accessibilityHeading()
            )
            IconButton(onClick = onTogglePin, modifier = Modifier.size(48.dp)) {
                Icon(
                    if (isPinned) Icons.Filled.PushPin else Icons.Outlined.PushPin,
                    if (isPinned) "Unpin" else "Pin",
                    tint = if (isPinned) Yellow else TextDim,
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        // Domain
        DetailRow("Domain", entry.hostname)
        DetailRow("Status", if (entry.blocked) "BLOCKED" else "ALLOWED",
            valueColor = if (entry.blocked) Red else Green)
        if (entry.decisionReason.isNotBlank() && entry.decisionReason != "none") {
            DetailRow("Decision", formatDecisionReason(entry.decisionReason))
        }
        if (entry.decisionSource.isNotBlank()) {
            DetailRow("Source", entry.decisionSource)
        }
        if (entry.matchedValue.isNotBlank()) {
            DetailRow("Matched", entry.matchedValue)
        }
        if (entry.decisionPrecedence.isNotBlank()) {
            DetailRow("Precedence", entry.decisionPrecedence)
        }
        DetailRow("Query Type", entry.queryType)
        DetailRow("Hit Count", "${entry.hitCount}x")
        DetailRow("Last Seen", formatTime(entry.latestTimestamp))

        if (entry.appLabel.isNotEmpty()) {
            DetailRow("App", entry.appLabel)
        }
        if (entry.appPackage.isNotEmpty()) {
            DetailRow("Package", entry.appPackage)
        }
        if (entry.responseTimeMs > 0) {
            DetailRow("Response Time", "${entry.responseTimeMs} ms")
        }
        if (entry.upstreamServer.isNotEmpty()) {
            // Pretty-print upstream server label
            val serverLabel = when {
                entry.upstreamServer.startsWith("DoH:") -> {
                    val provider = entry.upstreamServer.removePrefix("DoH:")
                    "DoH: ${provider.lowercase().replaceFirstChar { it.uppercase() }}"
                }
                entry.upstreamServer.contains("(fallback)") -> entry.upstreamServer
                else -> entry.upstreamServer
            }
            DetailRow("Upstream Server", serverLabel)
        }
        if (entry.cnameChain.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("CNAME Chain", color = TextDim, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                if (entry.blocked && entry.cnameChain.isNotBlank()) {
                    Spacer(Modifier.width(8.dp))
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = Red.copy(alpha = 0.15f)
                    ) {
                        Text(
                            "CNAME CLOAK",
                            color = Red,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            letterSpacing = 0.sp
                        )
                    }
                }
            }
            entry.cnameChain.split(",").filter { it.isNotBlank() }.forEach { cname ->
                Row(modifier = Modifier.padding(start = 8.dp, top = 2.dp)) {
                    Text("\u2192 ", color = TextDim, fontSize = 12.sp)
                    Text(cname.trim(), color = TextSecondary, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                }
            }
        }
        if (entry.resolvedIps.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Text("Resolved IPs", color = TextDim, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)

            val ips = entry.resolvedIps.split(",").filter { it.isNotBlank() }
            var geoResults by remember { mutableStateOf<List<GeoIpLookup.GeoInfo>>(emptyList()) }
            if (geoLookup != null) {
                LaunchedEffect(entry.resolvedIps) {
                    geoResults = geoLookup(ips)
                }
            }

            ips.forEach { ip ->
                val trimmedIp = ip.trim()
                val geo = geoResults.find { it.ip == trimmedIp }
                Row(modifier = Modifier.padding(start = 8.dp, top = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        trimmedIp,
                        color = TextSecondary, fontSize = 12.sp, fontFamily = FontFamily.Monospace
                    )
                    if (geo != null) {
                        Spacer(Modifier.width(8.dp))
                        if (geo.flag.isNotEmpty()) {
                            Text(geo.flag, fontSize = 12.sp)
                            Spacer(Modifier.width(4.dp))
                        }
                        Text(
                            buildString {
                                if (geo.country.isNotEmpty()) append(geo.country)
                                if (geo.org.isNotEmpty()) { append(" - "); append(geo.org) }
                            },
                            color = Sky, fontSize = 10.sp, maxLines = 1
                        )
                    }
                }
            }

            // ASN detail for first IP
            geoResults.firstOrNull()?.let { geo ->
                if (geo.asn.isNotEmpty()) {
                    Spacer(Modifier.height(4.dp))
                    Text(geo.asn, color = TextDim, fontSize = 10.sp, fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(start = 8.dp))
                }
            }
        }

        if (entry.isThreatIntelBlock()) {
            Spacer(Modifier.height(12.dp))
            ThreatIntelReviewSection(
                entry = entry,
                onAllowDomain = {
                    onAllow()
                    onDismiss()
                },
                onAllowForApp = if (entry.appPackage.isNotBlank()) {
                    {
                        onAllowForApp()
                        onDismiss()
                    }
                } else {
                    null
                }
            )
        }

        // Quick rule actions
        Spacer(Modifier.height(12.dp))
        Text("QUICK ACTIONS", color = TextDim, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.sp)
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (!entry.blocked) {
                Surface(
                    onClick = { onBlock(); onDismiss() },
                    shape = RoundedCornerShape(8.dp),
                    color = Red.copy(alpha = 0.1f)
                ) {
                    Row(modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Block, null, tint = Red, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Block Domain", color = Red, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            } else {
                Surface(
                    onClick = { onAllow(); onDismiss() },
                    shape = RoundedCornerShape(8.dp),
                    color = Green.copy(alpha = 0.1f)
                ) {
                    Row(modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.CheckCircle, null, tint = Green, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Allow Domain", color = Green, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }

        // Temporary allow (for blocked domains)
        if (entry.blocked) {
            Spacer(Modifier.height(12.dp))
            Text("TEMPORARY ALLOW", color = TextDim, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.sp)
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(5 to "5 min", 15 to "15 min", 30 to "30 min", 60 to "1 hour").forEach { (mins, label) ->
                    Surface(
                        onClick = { onTemporaryAllow(mins); onDismiss() },
                        shape = RoundedCornerShape(8.dp),
                        color = Yellow.copy(alpha = 0.1f)
                    ) {
                        Text(label, modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            color = Yellow, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }

        // Domain reputation lookup
        Spacer(Modifier.height(16.dp))
        Text("REPUTATION CHECK", color = TextDim, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.sp)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ReputationButton("VirusTotal", Blue) {
                val intent = android.content.Intent(
                    android.content.Intent.ACTION_VIEW,
                    "https://www.virustotal.com/gui/domain/${entry.hostname}".toUri()
                )
                context.startActivity(intent)
            }
            ReputationButton("URLhaus", Red) {
                val intent = android.content.Intent(
                    android.content.Intent.ACTION_VIEW,
                    "https://urlhaus.abuse.ch/browse.php?search=${entry.hostname}".toUri()
                )
                context.startActivity(intent)
            }
            ReputationButton("Whois", Teal) {
                val intent = android.content.Intent(
                    android.content.Intent.ACTION_VIEW,
                    "https://who.is/whois/${entry.hostname}".toUri()
                )
                context.startActivity(intent)
            }
        }
    }
}

@Composable
private fun ThreatIntelReviewSection(
    entry: DedupedLogEntry,
    onAllowDomain: () -> Unit,
    onAllowForApp: (() -> Unit)?
) {
    val matchKind = when (entry.decisionReason) {
        "threat_intel_ip" -> "resolved IP"
        else -> "domain"
    }
    val source = entry.decisionSource.ifBlank { "Threat feed" }
    val matchedValue = entry.matchedValue.ifBlank { entry.hostname }
    Surface(shape = RoundedCornerShape(10.dp), color = Red.copy(alpha = 0.08f)) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.GppBad, null, tint = Red, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                Text("THREAT REVIEW", color = Red, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.sp)
            }
            Spacer(Modifier.height(6.dp))
            Text(
                "$source flagged $matchKind $matchedValue.",
                color = TextSecondary,
                fontSize = 11.sp,
                lineHeight = 15.sp
            )
            Text(
                if (entry.decisionReason == "threat_intel_ip") {
                    "Allowing the domain keeps this destination reachable while leaving threat feeds enabled for other apps and hosts."
                } else {
                    "Use the narrowest allow scope that matches the false positive."
                },
                color = TextDim,
                fontSize = 10.sp,
                lineHeight = 14.sp
            )
            if (entry.appPackage.isNotBlank()) {
                Text(
                    "App scope only affects ${entry.appLabel.ifBlank { entry.appPackage }}.",
                    color = TextDim,
                    fontSize = 10.sp,
                    lineHeight = 14.sp
                )
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ThreatReviewAction("Allow domain", Green, onAllowDomain)
                if (onAllowForApp != null) {
                    ThreatReviewAction("Allow app only", Blue, onAllowForApp)
                }
            }
        }
    }
}

@Composable
private fun ThreatReviewAction(label: String, color: Color, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(8.dp),
        color = color.copy(alpha = 0.1f),
        modifier = Modifier.heightIn(min = 40.dp)
    ) {
        Text(
            label,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            color = color,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun ReputationButton(label: String, color: Color, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(8.dp),
        color = color.copy(alpha = 0.1f),
        modifier = Modifier.heightIn(min = 40.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Filled.Search, null, tint = color, modifier = Modifier.size(12.dp))
            Spacer(Modifier.width(4.dp))
            Text(label, color = color, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String, valueColor: Color = TextPrimary) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = TextDim, fontSize = 12.sp, modifier = Modifier.weight(0.35f))
        Text(
            value, color = valueColor, fontSize = 12.sp, fontWeight = FontWeight.Medium,
            fontFamily = if (label == "Domain" || label == "Package") FontFamily.Monospace else FontFamily.Default,
            modifier = Modifier.weight(0.65f),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}
