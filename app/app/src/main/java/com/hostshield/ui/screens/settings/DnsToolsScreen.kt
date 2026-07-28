package com.hostshield.ui.screens.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hostshield.ui.accessibility.accessibilityAction
import com.hostshield.ui.accessibility.accessibilityHeading
import com.hostshield.ui.accessibility.accessibilityLiveRegion
import com.hostshield.ui.accessibility.accessibilitySelection
import com.hostshield.ui.accessibility.accessibilityToggle
import com.hostshield.ui.components.HostShieldActionIconButton
import com.hostshield.ui.components.HostShieldBackHeader
import com.hostshield.ui.components.HostShieldCompactState
import com.hostshield.ui.components.HostShieldSegmentOption
import com.hostshield.ui.components.HostShieldSegmentedTabs
import com.hostshield.ui.theme.*

@Composable
fun DnsToolsScreen(
    viewModel: DnsToolsViewModel = hiltViewModel(),
    onBack: () -> Unit
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Column(modifier = Modifier.fillMaxSize().background(Black)) {
        HostShieldBackHeader(
            title = "DNS tools",
            subtitle = "${state.blocklistSize} domains loaded · ${state.resolverHealth.size} resolvers tracked",
            onBack = onBack,
            actions = {
                HostShieldActionIconButton(
                    icon = Icons.Filled.Refresh,
                    contentDescription = "Refresh DNS status",
                    onClick = { viewModel.refreshStatus() },
                    modifier = Modifier.accessibilityAction("Refresh DNS status"),
                )
            },
        )

        HostShieldSegmentedTabs(
            options = listOf(
                HostShieldSegmentOption(DnsToolsTab.LOOKUP, "Lookup", Teal, Icons.Filled.Search),
                HostShieldSegmentOption(DnsToolsTab.STATUS, "Status", Blue, Icons.Filled.Dns),
                HostShieldSegmentOption(DnsToolsTab.CONFIG, "Config", Yellow, Icons.Filled.Settings),
                HostShieldSegmentOption(DnsToolsTab.DIAG, "Diag", Peach, Icons.Filled.BugReport),
            ),
            selected = state.tab,
            onSelected = { viewModel.setTab(it) },
            modifier = Modifier.padding(horizontal = 16.dp),
            semanticsLabel = "DNS tools section",
        )

        Spacer(Modifier.height(12.dp))

        when (state.tab) {
            DnsToolsTab.LOOKUP -> LookupTab(state, viewModel)
            DnsToolsTab.STATUS -> StatusTab(state, viewModel)
            DnsToolsTab.CONFIG -> ConfigTab(state, viewModel)
            DnsToolsTab.DIAG -> DiagTab(state, viewModel)
        }
    }
}

@Composable
private fun LookupTab(state: DnsToolsState, viewModel: DnsToolsViewModel) {
    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
        // Domain input
        OutlinedTextField(
            value = state.lookupDomain,
            onValueChange = { viewModel.setLookupDomain(it) },
            placeholder = { Text("example.com", color = TextDim) },
            leadingIcon = { Icon(Icons.Filled.Dns, null, tint = TextDim) },
            trailingIcon = {
                if (state.isLookingUp) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp).accessibilityLiveRegion("Looking up DNS result"),
                        color = Teal,
                        strokeWidth = 2.dp
                    )
                } else {
                    IconButton(onClick = { viewModel.performLookup() }) {
                        Icon(Icons.AutoMirrored.Filled.Send, "Lookup", tint = Teal)
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true, shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Teal, unfocusedBorderColor = Surface3,
                cursorColor = Teal, focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary
            )
        )

        Spacer(Modifier.height(12.dp))

        // Results
        LazyColumn {
            items(state.lookupResults) { result ->
                Surface(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                    shape = RoundedCornerShape(10.dp),
                    color = if (result.isBlocked) Red.copy(alpha = 0.06f) else Surface2
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(modifier = Modifier.size(6.dp).clip(CircleShape)
                                .background(if (result.isBlocked) Red else Green))
                            Spacer(Modifier.width(8.dp))
                            Text(
                                result.domain,
                                color = TextPrimary, fontWeight = FontWeight.Medium,
                                fontFamily = FontFamily.Monospace, fontSize = 13.sp
                            )
                            Spacer(Modifier.weight(1f))
                            if (result.isBlocked) {
                                Surface(shape = RoundedCornerShape(4.dp), color = Red.copy(alpha = 0.15f)) {
                                    Text("BLOCKED", modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
                                        color = Red, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                            Spacer(Modifier.width(8.dp))
                            Text("${result.latencyMs}ms", color = TextDim, fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace)
                        }
                        if (result.error.isNotEmpty()) {
                            Text(result.error, color = Red, fontSize = 11.sp)
                        }
                        for (addr in result.addresses) {
                            Text(addr, color = Teal, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusTab(state: DnsToolsState, viewModel: DnsToolsViewModel) {
    LazyColumn(
        modifier = Modifier.padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Current DNS servers
        item {
            GlassInfoCard("Active DNS Servers") {
                if (state.currentDns.isEmpty()) {
                    Text("No DNS servers detected", color = TextDim, fontSize = 12.sp)
                } else {
                    for (dns in state.currentDns) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(Green))
                            Spacer(Modifier.width(8.dp))
                            Text(dns, color = Teal, fontFamily = FontFamily.Monospace, fontSize = 13.sp)
                        }
                        Spacer(Modifier.height(4.dp))
                    }
                }
            }
        }

        // Private DNS status
        item {
            GlassInfoCard("Private DNS") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Mode: ", color = TextDim, fontSize = 12.sp)
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = when (state.privateDnsMode) {
                            "off" -> Green.copy(alpha = 0.12f)
                            "opportunistic" -> Yellow.copy(alpha = 0.12f)
                            "hostname" -> Blue.copy(alpha = 0.12f)
                            else -> Surface3
                        }
                    ) {
                        Text(
                            state.privateDnsMode,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            color = when (state.privateDnsMode) {
                                "off" -> Green; "opportunistic" -> Yellow
                                "hostname" -> Blue; else -> TextDim
                            },
                            fontSize = 11.sp, fontWeight = FontWeight.SemiBold, fontFamily = FontFamily.Monospace
                        )
                    }
                }
                if (state.privateDnsProvider.isNotBlank() && state.privateDnsProvider != "null") {
                    Spacer(Modifier.height(4.dp))
                    Text("Provider: ${state.privateDnsProvider}", color = TextSecondary, fontSize = 11.sp)
                }
            }
        }

        item {
            GlassInfoCard("Resolver Health (24h)") {
                if (state.resolverHealth.isEmpty()) {
                    HostShieldCompactState(
                        icon = Icons.Filled.MonitorHeart,
                        title = "No resolver health data yet",
                        message = "Health samples appear after DNS resolvers are used for lookups.",
                        accent = Blue,
                    )
                } else {
                    state.resolverHealth.forEachIndexed { index, resolver ->
                        ResolverHealthRow(resolver)
                        if (index != state.resolverHealth.lastIndex) {
                            HorizontalDivider(color = Surface3.copy(alpha = 0.6f), thickness = 1.dp)
                        }
                    }
                }
            }
        }

        // Blocklist stats
        item {
            GlassInfoCard("Blocklist") {
                Text("${state.blocklistSize} domains loaded", color = Teal, fontSize = 13.sp,
                    fontWeight = FontWeight.Medium)
            }
        }

        // DNS cache
        item {
            GlassInfoCard("DNS Cache (${state.cacheEntries.size} entries)") {
                Row {
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = { viewModel.flushDnsCache() }, enabled = !state.isFlushing) {
                        if (state.isFlushing) {
                            CircularProgressIndicator(Modifier.size(14.dp), color = Red, strokeWidth = 2.dp)
                        } else {
                            Text("Flush Cache", color = Red, fontSize = 11.sp)
                        }
                    }
                }
                for (entry in state.cacheEntries.take(20)) {
                    Text(entry.hostname, color = TextPrimary, fontSize = 11.sp, fontFamily = FontFamily.Monospace,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(entry.addresses, color = TextDim, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                    Spacer(Modifier.height(4.dp))
                }
                if (state.cacheEntries.isEmpty()) {
                    HostShieldCompactState(
                        icon = Icons.Filled.Storage,
                        title = "DNS cache is empty",
                        message = "Cached lookups will appear here after HostShield resolves domains.",
                        accent = Teal,
                    )
                }
            }
        }
    }
}

@Composable
private fun ResolverHealthRow(resolver: ResolverHealthUi) {
    val statusColor = when {
        resolver.attempts == 0 -> TextDim
        (resolver.successRatePercent ?: 0) >= 95 && resolver.pinFailures == 0 -> Green
        (resolver.successRatePercent ?: 0) >= 80 && resolver.pinFailures == 0 -> Yellow
        else -> Red
    }
    val latencyText = resolver.latencyMs?.let { "${it}ms" } ?: "no latency"
    val successText = resolver.successRatePercent?.let {
        "$it% (${resolver.successes}/${resolver.attempts})"
    } ?: "no samples"

    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(7.dp).clip(CircleShape).background(statusColor))
            Spacer(Modifier.width(8.dp))
            Text(
                resolver.provider.lowercase().replaceFirstChar { it.uppercase() },
                color = TextPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f)
            )
            if (resolver.selected) {
                Icon(Icons.Filled.CheckCircle, "Selected resolver", tint = Teal, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
            }
            Text(latencyText, color = statusColor, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
        }
        Spacer(Modifier.height(3.dp))
        Text(
            "Transport ${resolver.activeTransport} | Success $successText | EDE ${resolver.edeCount}",
            color = TextDim,
            fontSize = 10.sp
        )
        Spacer(Modifier.height(2.dp))
        Text(
            "Fallbacks ${resolver.failovers} | Pin failures ${resolver.pinFailures} | Failures ${resolver.failures}",
            color = TextDim,
            fontSize = 10.sp
        )
    }
}

@Composable
private fun ConfigTab(state: DnsToolsState, viewModel: DnsToolsViewModel) {
    LazyColumn(
        modifier = Modifier.padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // DoH toggle
        item {
            GlassInfoCard("DNS-over-HTTPS") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Encrypted DNS", color = TextPrimary, fontWeight = FontWeight.Medium)
                        Text("Route DNS queries over HTTPS for privacy", color = TextDim, fontSize = 11.sp)
                    }
                    Switch(
                        checked = state.dohEnabled,
                        onCheckedChange = { viewModel.toggleDoh(it) },
                        modifier = Modifier.accessibilityToggle("Encrypted DNS", state.dohEnabled),
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Teal, checkedTrackColor = Teal.copy(alpha = 0.25f),
                            uncheckedThumbColor = TextDim, uncheckedTrackColor = Surface3
                        )
                    )
                }
            }
        }

        // Custom upstream DNS servers
        item {
            GlassInfoCard("Upstream DNS Servers") {
                Text("Comma-separated list with fallback order:", color = TextDim, fontSize = 11.sp)
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(
                    value = state.customUpstreamDns,
                    onValueChange = { viewModel.setCustomUpstreamDns(it) },
                    placeholder = { Text("1.1.1.1, 8.8.8.8, 9.9.9.9", color = TextDim) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true, shape = RoundedCornerShape(8.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Teal, unfocusedBorderColor = Surface3,
                        cursorColor = Teal, focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary
                    )
                )
                val count = state.customUpstreamDns.split(",").filter { it.trim().isNotBlank() }.size
                if (state.customUpstreamDns.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text("$count server(s) configured. First available is used; others are fallbacks.", color = TextDim, fontSize = 10.sp)
                }
            }
        }

        // Remote rule sync
        item {
            GlassInfoCard("Remote Rule Sync") {
                Text("Subscribe to remote block/allow lists (one URL per line):", color = TextDim, fontSize = 11.sp)
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(
                    value = state.ruleSyncUrls.replace(",", "\n"),
                    onValueChange = { viewModel.setRuleSyncUrls(it.lines().joinToString(",") { l -> l.trim() }) },
                    placeholder = { Text("https://example.com/blocklist.txt", color = TextDim) },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 60.dp, max = 100.dp),
                    maxLines = 5, shape = RoundedCornerShape(8.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Teal, unfocusedBorderColor = Surface3,
                        cursorColor = Teal, focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary
                    )
                )
                val count = state.ruleSyncUrls.split(",").filter { it.trim().startsWith("http") }.size
                if (count > 0) {
                    Spacer(Modifier.height(4.dp))
                    Text("$count URL(s) subscribed. Synced during periodic blocklist updates.", color = TextDim, fontSize = 10.sp)
                }
            }
        }

        // DoH provider selection
        item {
            GlassInfoCard("DoH Provider") {
                val providers = listOf(
                    "cloudflare" to "Cloudflare (1.1.1.1)",
                    "google" to "Google (8.8.8.8)",
                    "quad9" to "Quad9 (9.9.9.9)",
                    "adguard" to "AdGuard DNS",
                    "mullvad" to "Mullvad DNS"
                )
                for ((key, label) in providers) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = state.dohProvider == key,
                            onClick = { viewModel.setDohProvider(key) },
                            modifier = Modifier.accessibilitySelection("$label DoH provider", state.dohProvider == key),
                            colors = RadioButtonDefaults.colors(selectedColor = Teal, unselectedColor = TextDim)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(label, color = if (state.dohProvider == key) TextPrimary else TextSecondary,
                            fontSize = 13.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun DiagTab(state: DnsToolsState, viewModel: DnsToolsViewModel) {
    var pingTarget by remember { mutableStateOf("") }

    LazyColumn(
        modifier = Modifier.padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Batch domain test
        item {
            GlassInfoCard("Batch Domain Test") {
                Text("Paste domains (one per line) to test blocklist + resolution:", color = TextDim, fontSize = 11.sp)
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(
                    value = state.batchInput,
                    onValueChange = { viewModel.setBatchInput(it) },
                    placeholder = { Text("example.com\nad.doubleclick.net\ngoogle.com", color = TextDim) },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 80.dp, max = 140.dp),
                    maxLines = 10,
                    shape = RoundedCornerShape(8.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Teal, unfocusedBorderColor = Surface3,
                        cursorColor = Teal, focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary
                    )
                )
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Button(
                        onClick = { viewModel.runBatchTest() },
                        enabled = !state.isBatchRunning && state.batchInput.isNotBlank(),
                        colors = ButtonDefaults.buttonColors(containerColor = Teal),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        if (state.isBatchRunning) {
                            CircularProgressIndicator(
                                Modifier.size(14.dp).accessibilityLiveRegion("Running batch DNS test"),
                                color = Color.Black,
                                strokeWidth = 2.dp
                            )
                            Spacer(Modifier.width(6.dp))
                            Text("${state.batchProgress}/${state.batchTotal}", fontSize = 12.sp)
                        } else {
                            Text("Test all", fontSize = 12.sp)
                        }
                    }
                    if (state.batchResults.isNotEmpty()) {
                        Spacer(Modifier.width(12.dp))
                        val blocked = state.batchResults.count { it.isBlocked }
                        val failed = state.batchResults.count { it.error.isNotEmpty() }
                        Text("$blocked blocked, $failed failed, ${state.batchResults.size - blocked - failed} allowed",
                            color = TextDim, fontSize = 10.sp)
                    }
                }
            }
        }

        // Batch results
        if (state.batchResults.isNotEmpty()) {
            items(state.batchResults) { result ->
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    color = if (result.isBlocked) Red.copy(alpha = 0.06f) else Surface2.copy(alpha = 0.5f)
                ) {
                    Row(
                        modifier = Modifier.padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(modifier = Modifier.size(6.dp).clip(CircleShape)
                            .background(if (result.isBlocked) Red else if (result.error.isNotEmpty()) Yellow else Green))
                        Spacer(Modifier.width(8.dp))
                        Text(result.domain, color = TextPrimary, fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace, modifier = Modifier.weight(1f),
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("${result.latencyMs}ms", color = TextDim, fontSize = 9.sp, fontFamily = FontFamily.Monospace)
                    }
                }
            }
        }

        // Ping
        item {
            GlassInfoCard("Ping / Traceroute") {
                OutlinedTextField(
                    value = pingTarget,
                    onValueChange = { pingTarget = it },
                    placeholder = { Text("8.8.8.8 or google.com", color = TextDim) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true, shape = RoundedCornerShape(8.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Peach, unfocusedBorderColor = Surface3,
                        cursorColor = Peach, focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary
                    )
                )
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { viewModel.runPing(pingTarget) },
                        enabled = !state.isPinging && pingTarget.isNotBlank(),
                        colors = ButtonDefaults.buttonColors(containerColor = Peach),
                        shape = RoundedCornerShape(8.dp)
                    ) { Text("Ping", fontSize = 12.sp) }
                    OutlinedButton(
                        onClick = { viewModel.runTraceroute(pingTarget) },
                        enabled = !state.isPinging && pingTarget.isNotBlank(),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Blue)
                    ) { Text("Traceroute", fontSize = 12.sp) }
                    if (state.isPinging) {
                        CircularProgressIndicator(
                            Modifier
                                .size(18.dp)
                                .align(Alignment.CenterVertically)
                                .accessibilityLiveRegion("Running network diagnostic"),
                            color = Peach,
                            strokeWidth = 2.dp
                        )
                    }
                }
                if (state.pingResult.isNotBlank()) {
                    Spacer(Modifier.height(8.dp))
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(6.dp), color = Surface1
                    ) {
                        Text(
                            state.pingResult,
                            modifier = Modifier.padding(8.dp),
                            color = Teal, fontSize = 10.sp, fontFamily = FontFamily.Monospace,
                            lineHeight = 14.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun GlassInfoCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = Surface1.copy(alpha = 0.94f),
        border = BorderStroke(1.dp, Surface3.copy(alpha = 0.58f)),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                title,
                color = TextSecondary,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.sp,
                modifier = Modifier.accessibilityHeading()
            )
            Spacer(Modifier.height(8.dp))
            content()
        }
    }
}
