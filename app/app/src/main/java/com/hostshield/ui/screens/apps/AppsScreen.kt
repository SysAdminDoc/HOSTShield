package com.hostshield.ui.screens.apps

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hostshield.data.database.AppDomainStat
import com.hostshield.data.database.AppQueryStat
import com.hostshield.data.database.DnsLogDao
import com.hostshield.data.model.BlockMethod
import com.hostshield.data.model.RuleType
import com.hostshield.data.model.UserRule
import com.hostshield.data.preferences.AppPreferences
import com.hostshield.data.repository.HostShieldRepository
import com.hostshield.domain.BlocklistHolder
import com.hostshield.ui.accessibility.accessibilityAction
import com.hostshield.ui.components.HostShieldBackHeader
import com.hostshield.ui.components.HostShieldDenseListJumpBar
import com.hostshield.ui.components.HostShieldEmptyState
import com.hostshield.ui.components.HostShieldFilterChip
import com.hostshield.ui.components.HostShieldMetricTile
import com.hostshield.ui.components.HostShieldSavedFilterBar
import com.hostshield.util.RootUtil
import com.hostshield.ui.screens.home.GlassCard
import com.hostshield.ui.theme.*

// Apps screen

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AppsScreen(viewModel: AppsViewModel = hiltViewModel(), onBack: () -> Unit = {}) {
    val apps by viewModel.apps.collectAsStateWithLifecycle()
    val selectedApp by viewModel.selectedApp.collectAsStateWithLifecycle()
    val appDomains by viewModel.appDomains.collectAsStateWithLifecycle()
    val query by viewModel.searchQuery.collectAsStateWithLifecycle()
    val appFilter by viewModel.filter.collectAsStateWithLifecycle()
    val savedFilters by viewModel.savedFilters.collectAsStateWithLifecycle()
    val locallyBlocked by viewModel.locallyBlocked.collectAsStateWithLifecycle()

    val filtered = remember(apps, query, appFilter) {
        apps.filter { app ->
            (query.isBlank() ||
                app.appLabel.contains(query, ignoreCase = true) ||
                app.appPackage.contains(query, ignoreCase = true)) &&
                when (appFilter) {
                    AppsActivityFilter.ALL -> true
                    AppsActivityFilter.BLOCKED -> app.blockedQueries > 0
                    AppsActivityFilter.UNBLOCKED -> app.blockedQueries == 0
                }
        }
    }
    val totalQueries = remember(apps) { apps.sumOf { it.totalQueries } }
    val blockedQueries = remember(apps) { apps.sumOf { it.blockedQueries } }

    Column(modifier = Modifier.fillMaxSize().background(Black)) {
        HostShieldBackHeader(
            title = if (selectedApp != null) {
                apps.find { it.appPackage == selectedApp }?.appLabel ?: selectedApp.orEmpty()
            } else {
                "App activity"
            },
            subtitle = if (selectedApp == null) {
                "${apps.size} apps tracked"
            } else {
                selectedApp.orEmpty()
            },
            onBack = {
                if (selectedApp != null) viewModel.selectApp(null) else onBack()
            },
            verticalPadding = 12.dp,
        )

        if (selectedApp == null) {
            if (apps.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    HostShieldMetricTile(
                        value = "${apps.size}",
                        label = "tracked apps",
                        accent = Mauve,
                        modifier = Modifier.weight(1f),
                    )
                    HostShieldMetricTile(
                        value = formatCompact(totalQueries),
                        label = "queries",
                        accent = Blue,
                        modifier = Modifier.weight(1f),
                    )
                    HostShieldMetricTile(
                        value = formatCompact(blockedQueries),
                        label = "blocked",
                        accent = Red,
                        modifier = Modifier.weight(1f),
                    )
                }
                Spacer(Modifier.height(8.dp))
            }

            // Search
            OutlinedTextField(
                value = query, onValueChange = { viewModel.setSearch(it) },
                placeholder = { Text("Search app name or package", color = TextDim) },
                leadingIcon = { Icon(Icons.Filled.Search, "Search", tint = TextDim) },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                singleLine = true, shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Teal, unfocusedBorderColor = Surface3,
                    cursorColor = Teal, focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary
                )
            )

            Spacer(Modifier.height(8.dp))

            FlowRow(
                modifier = Modifier.padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                HostShieldFilterChip("All", appFilter == AppsActivityFilter.ALL, { viewModel.setFilter(AppsActivityFilter.ALL) }, accent = Mauve)
                HostShieldFilterChip("Blocked", appFilter == AppsActivityFilter.BLOCKED, { viewModel.setFilter(AppsActivityFilter.BLOCKED) }, accent = Red)
                HostShieldFilterChip("No blocks", appFilter == AppsActivityFilter.UNBLOCKED, { viewModel.setFilter(AppsActivityFilter.UNBLOCKED) }, accent = Teal)
            }

            Spacer(Modifier.height(8.dp))

            val hasActiveFilters = query.isNotBlank() || appFilter != AppsActivityFilter.ALL
            if (hasActiveFilters || savedFilters.isNotEmpty()) {
                HostShieldSavedFilterBar(
                    screen = "apps",
                    savedFilters = savedFilters,
                    canSaveCurrent = hasActiveFilters,
                    onSaveCurrent = { viewModel.saveCurrentFilter() },
                    onApplyFilter = { viewModel.applySavedFilter(it) },
                    onClearSavedFilters = { viewModel.clearSavedFilters() },
                    modifier = Modifier.padding(horizontal = 20.dp),
                )
                Spacer(Modifier.height(8.dp))
            }

            // App list
            if (filtered.isEmpty()) {
                HostShieldEmptyState(
                    icon = Icons.Filled.Apps,
                    title = if (query.isBlank()) "No app activity yet" else "No apps match this search",
                    message = if (query.isBlank()) {
                        "Enable VPN protection and open a few apps. HostShield will group DNS activity here by app."
                    } else {
                        "Try a different app name, package id, or saved filter."
                    },
                    accent = Mauve,
                    primaryActionLabel = if (hasActiveFilters) "Clear filters" else null,
                    onPrimaryAction = if (hasActiveFilters) viewModel::clearFilters else null,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 20.dp),
                )
            } else {
                val appListState = rememberLazyListState()
                HostShieldDenseListJumpBar(
                    screen = "apps",
                    label = "app activity results",
                    totalItems = filtered.size,
                    listState = appListState,
                    modifier = Modifier.padding(horizontal = 20.dp),
                )
                LazyColumn(
                    state = appListState,
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(filtered, key = { it.appPackage }) { app ->
                        AppListItem(app = app, onClick = { viewModel.selectApp(app.appPackage) })
                    }
                    item { Spacer(Modifier.height(16.dp)) }
                }
            }
        } else {
            // App detail: domains list
            // Merge locallyBlocked set with DB data so newly-blocked domains
            // show as blocked immediately without waiting for new DNS queries
            val effectiveDomains = remember(appDomains, locallyBlocked) {
                appDomains.map { d ->
                    if (!d.blocked && d.hostname.lowercase() in locallyBlocked)
                        d.copy(blocked = true)
                    else d
                }
            }
            val totalDomains = effectiveDomains.size
            val blockedDomains = effectiveDomains.count { it.blocked }
            val blockRate = if (totalDomains > 0) (blockedDomains * 100 / totalDomains) else 0

            // Summary cards
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                MiniStat(Modifier.weight(1f), "Domains", "$totalDomains", Teal)
                MiniStat(Modifier.weight(1f), "Blocked", "$blockedDomains", Red)
                MiniStat(Modifier.weight(1f), "Block Rate", "$blockRate%", Mauve)
            }

            Spacer(Modifier.height(4.dp))

            if (effectiveDomains.isEmpty()) {
                HostShieldEmptyState(
                    icon = Icons.Filled.Dns,
                    title = "No domains recorded for this app",
                    message = "Recent domains will appear after this app makes DNS requests while protection is active.",
                    accent = Blue,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
                )
            } else {
                val domainListState = rememberLazyListState()
                HostShieldDenseListJumpBar(
                    screen = "app_domains",
                    label = "selected app domains",
                    totalItems = effectiveDomains.size,
                    listState = domainListState,
                    modifier = Modifier.padding(horizontal = 20.dp),
                )
                LazyColumn(
                    state = domainListState,
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    items(effectiveDomains, key = { it.hostname }) { domain ->
                        DomainItem(domain = domain, onBlock = { viewModel.blockDomain(domain.hostname) })
                    }
                    item { Spacer(Modifier.height(16.dp)) }
                }
            }
        }
    }
}

private fun formatCompact(n: Int): String = when {
    n >= 1_000_000 -> "${n / 1_000_000}.${(n % 1_000_000) / 100_000}M"
    n >= 1_000 -> "${n / 1_000}.${(n % 1_000) / 100}K"
    else -> n.toString()
}

@Composable
private fun AppListItem(app: AppQueryStat, onClick: () -> Unit) {
    val blockRate = if (app.totalQueries > 0) (app.blockedQueries * 100 / app.totalQueries) else 0
    val barColor = when {
        blockRate > 60 -> Red
        blockRate > 30 -> Yellow
        else -> Teal
    }

    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .accessibilityAction(
                    "${app.appLabel.ifEmpty { app.appPackage }}. ${app.totalQueries} queries, ${app.blockedQueries} blocked. Open app DNS activity."
                )
                .clickable(onClick = onClick)
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // App icon placeholder
            Box(
                modifier = Modifier.size(38.dp).clip(RoundedCornerShape(10.dp))
                    .background(barColor.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.Apps, null, tint = barColor, modifier = Modifier.size(20.dp))  // decorative, app name follows
            }

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    app.appLabel.ifEmpty { app.appPackage },
                    color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium,
                    maxLines = 1
                )
                Text(
                    "${app.totalQueries} queries \u2022 ${app.blockedQueries} blocked",
                    color = TextDim, fontSize = 11.sp
                )
            }

            // Block rate badge
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = barColor.copy(alpha = 0.1f)
            ) {
                Text(
                    "$blockRate%",
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    color = barColor, fontSize = 11.sp, fontWeight = FontWeight.Bold
                )
            }

            Spacer(Modifier.width(6.dp))
            Icon(Icons.Filled.ChevronRight, "View app details", tint = TextDim, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun MiniStat(modifier: Modifier, label: String, value: String, color: Color) {
    GlassCard(modifier = modifier) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(value, color = color, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Text(label, color = TextDim, fontSize = 10.sp)
        }
    }
}

@Composable
private fun DomainItem(domain: AppDomainStat, onBlock: () -> Unit) {
    var expanded by remember { mutableStateOf(false) }

    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth()) {
            // Status strip
            Box(
                modifier = Modifier.width(4.dp).heightIn(min = 44.dp)
                    .background(if (domain.blocked) Red else Green.copy(alpha = 0.5f))
            )

            Column(
                modifier = Modifier
                    .weight(1f)
                    .accessibilityAction(
                        "${domain.hostname}, ${if (domain.blocked) "blocked" else "allowed"}, ${domain.cnt} recent ${if (domain.cnt == 1) "query" else "queries"}"
                    )
                    .clickable { expanded = !expanded }
                    .padding(10.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (domain.blocked) {
                        Icon(Icons.Filled.Block, "Blocked", tint = Red.copy(alpha = 0.7f), modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(6.dp))
                    }
                    Text(
                        domain.hostname, color = if (domain.blocked) Red.copy(alpha = 0.65f) else TextPrimary,
                        fontSize = 12.sp, fontFamily = FontFamily.Monospace, maxLines = 1,
                        modifier = Modifier.weight(1f)
                    )
                    Text("${domain.cnt}x", color = TextDim, fontSize = 10.sp)
                }

                AnimatedVisibility(visible = expanded && !domain.blocked) {
                    Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.End) {
                        Surface(onClick = onBlock, shape = RoundedCornerShape(8.dp), color = Red.copy(alpha = 0.1f)) {
                            Row(modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.Block, null, tint = Red, modifier = Modifier.size(14.dp))  // decorative, "Block" label follows
                                Spacer(Modifier.width(6.dp))
                                Text("Block", color = Red, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            }
        }
    }
}
