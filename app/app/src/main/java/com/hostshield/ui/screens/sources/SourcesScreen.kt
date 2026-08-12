package com.hostshield.ui.screens.sources

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hostshield.R
import com.hostshield.data.model.HostSource
import com.hostshield.data.model.SourceCategory
import com.hostshield.data.model.SourceHealth
import com.hostshield.data.model.hasActiveHealthFailure
import com.hostshield.data.model.hasActiveHealthWarning
import com.hostshield.data.source.SourceUrlPolicy
import com.hostshield.ui.accessibility.accessibilityLiveRegion
import com.hostshield.ui.accessibility.accessibilityToggle
import com.hostshield.ui.HostShieldTestTags
import com.hostshield.ui.components.ConfirmDestructiveDialog
import com.hostshield.ui.components.HostShieldActionIconButton
import com.hostshield.ui.components.HostShieldDenseListJumpBar
import com.hostshield.ui.components.HostShieldEmptyState
import com.hostshield.ui.components.HostShieldFilterChip
import com.hostshield.ui.components.HostShieldLoadingState
import com.hostshield.ui.components.HostShieldMetricTile
import com.hostshield.ui.components.HostShieldSavedFilterBar
import com.hostshield.ui.components.HostShieldScreenHeader
import com.hostshield.ui.components.HostShieldStatusBanner
import com.hostshield.ui.screens.home.GlassCard
import com.hostshield.ui.theme.*
import java.text.NumberFormat
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SourcesScreen(
    viewModel: SourcesViewModel = hiltViewModel(),
    onNavigateToGallery: () -> Unit = {}
) {
    val sources by viewModel.sources.collectAsStateWithLifecycle()
    val healthMsg by viewModel.healthCheckMessage.collectAsStateWithLifecycle()
    val allowlistMsg by viewModel.allowlistImpactMessage.collectAsStateWithLifecycle()
    val sourceImpactMsg by viewModel.sourceImpactMessage.collectAsStateWithLifecycle()
    val sourceImpactPreview by viewModel.sourceImpactPreview.collectAsStateWithLifecycle()
    val isChecking by viewModel.isCheckingHealth.collectAsStateWithLifecycle()
    val allowlistImpacts by viewModel.allowlistImpacts.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val sourceFilter by viewModel.filter.collectAsStateWithLifecycle()
    val savedFilters by viewModel.savedFilters.collectAsStateWithLifecycle()
    // rememberSaveable so a rotation mid-entry does not close the dialog and
    // discard its (saveable) URL/label/category fields. Matches RulesScreen.
    var showAddDialog by rememberSaveable { mutableStateOf(false) }
    var pendingDeleteSource by remember { mutableStateOf<HostSource?>(null) }
    val filteredSources = remember(sources, searchQuery, sourceFilter) {
        sources.filter { source ->
            (searchQuery.isBlank() ||
                source.label.contains(searchQuery, ignoreCase = true) ||
                source.url.contains(searchQuery, ignoreCase = true) ||
                source.description.contains(searchQuery, ignoreCase = true)) &&
                when (sourceFilter) {
                    SourceListFilter.ALL -> true
                    SourceListFilter.ENABLED -> source.enabled
                    SourceListFilter.DISABLED -> !source.enabled
                    SourceListFilter.UNHEALTHY -> source.hasActiveHealthWarning()
                    SourceListFilter.ALLOWLIST -> source.category == SourceCategory.ALLOWLIST
                }
        }
    }
    val hasActiveFilters = searchQuery.isNotBlank() || sourceFilter != SourceListFilter.ALL
    val groupedSources = remember(filteredSources) { filteredSources.groupBy { it.category } }
    val visibleCategories = remember(groupedSources) {
        SourceCategory.entries.mapNotNull { category ->
            groupedSources[category]?.takeIf { it.isNotEmpty() }?.let { category to it }
        }
    }
    val listState = rememberLazyListState()
    val lazyItemCount = 1 + if (filteredSources.isEmpty()) {
        1
    } else {
        visibleCategories.sumOf { (_, items) -> 1 + items.size } + 1
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Black)
    ) {
        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp),
                contentAlignment = Alignment.Center
            ) {
                HostShieldLoadingState(
                    title = "Loading sources",
                    message = "Checking configured blocklists and health data.",
                    accent = Teal,
                    modifier = Modifier.accessibilityLiveRegion("Loading configured sources"),
                )
            }
        } else {
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (error != null) {
                item {
                    HostShieldStatusBanner(
                        icon = Icons.Filled.Error,
                        title = "Source error",
                        message = error ?: "",
                        accent = Red,
                        onDismiss = { viewModel.clearError() },
                    )
                }
            }
            item {
                HostShieldScreenHeader(
                    title = "Sources",
                    subtitle = "Blocklists, allowlists, and source health.",
                ) {
                    HostShieldActionIconButton(
                        icon = Icons.Filled.CloudDownload,
                        contentDescription = "Browse curated sources",
                        onClick = onNavigateToGallery,
                        accent = Blue,
                    )
                    HostShieldActionIconButton(
                        icon = Icons.Filled.Add,
                        contentDescription = stringResource(R.string.sources_add_source),
                        onClick = { showAddDialog = true },
                        accent = Teal,
                        modifier = Modifier.testTag(HostShieldTestTags.Sources.AddButton),
                    )
                }
                healthMsg?.let { msg ->
                    Spacer(Modifier.height(8.dp))
                    HostShieldStatusBanner(
                        icon = if (isChecking) Icons.Filled.Sync else Icons.Filled.HealthAndSafety,
                        title = if (isChecking) "Checking source health" else "Source health",
                        message = msg,
                        accent = when {
                            msg.contains("unreachable", ignoreCase = true) -> Yellow
                            msg.contains("failed", ignoreCase = true) -> Red
                            else -> Teal
                        },
                    )
                }
                allowlistMsg?.let { msg ->
                    Spacer(Modifier.height(8.dp))
                    HostShieldStatusBanner(
                        icon = Icons.Filled.CheckCircle,
                        title = "Allowlist impact",
                        message = msg,
                        accent = Green,
                    )
                }
                sourceImpactMsg?.let { msg ->
                    Spacer(Modifier.height(8.dp))
                    HostShieldStatusBanner(
                        icon = Icons.Filled.Visibility,
                        title = "Source update impact",
                        message = msg,
                        accent = Blue,
                    )
                }
                sourceImpactPreview?.let { preview ->
                    Spacer(Modifier.height(8.dp))
                    SourceImpactPreviewCard(preview)
                }
                Spacer(Modifier.height(8.dp))

                // Summary stats
                val totalDomains = sources.filter { it.enabled }.sumOf { it.entryCount }
                val totalSize = sources.filter { it.enabled }.sumOf { it.sizeBytes }
                val unhealthy = sources.count { it.hasActiveHealthFailure() }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    HostShieldMetricTile(
                        value = NumberFormat.getNumberInstance().format(totalDomains),
                        label = "Domains",
                        accent = Teal,
                        modifier = Modifier.weight(1f),
                    )
                    val sizeLabel = if (totalSize > 1_000_000) "${"%.1f".format(totalSize / 1_000_000f)} MB"
                        else if (totalSize > 1000) "${totalSize / 1000} KB" else "$totalSize B"
                    HostShieldMetricTile(
                        value = sizeLabel,
                        label = "Total size",
                        accent = Blue,
                        modifier = Modifier.weight(1f),
                    )
                    if (unhealthy > 0) {
                        HostShieldMetricTile(
                            value = "$unhealthy",
                            label = "Unhealthy",
                            accent = Red,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))

                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { viewModel.setSearchQuery(it) },
                    placeholder = { Text("Search source name or URL", color = TextDim) },
                    leadingIcon = { Icon(Icons.Filled.Search, null, tint = TextDim) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(HostShieldTestTags.Sources.SearchField),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Teal,
                        unfocusedBorderColor = Surface3,
                        cursorColor = Teal,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    )
                )
                Spacer(Modifier.height(8.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    HostShieldFilterChip("All", sourceFilter == SourceListFilter.ALL, { viewModel.setFilter(SourceListFilter.ALL) }, accent = Teal)
                    HostShieldFilterChip("Enabled", sourceFilter == SourceListFilter.ENABLED, { viewModel.setFilter(SourceListFilter.ENABLED) }, accent = Green)
                    HostShieldFilterChip("Disabled", sourceFilter == SourceListFilter.DISABLED, { viewModel.setFilter(SourceListFilter.DISABLED) }, accent = TextDim)
                    HostShieldFilterChip("Needs review", sourceFilter == SourceListFilter.UNHEALTHY, { viewModel.setFilter(SourceListFilter.UNHEALTHY) }, accent = Red)
                    HostShieldFilterChip("Allowlists", sourceFilter == SourceListFilter.ALLOWLIST, { viewModel.setFilter(SourceListFilter.ALLOWLIST) }, accent = Blue)
                }
                if (hasActiveFilters || savedFilters.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    HostShieldSavedFilterBar(
                        screen = "sources",
                        savedFilters = savedFilters,
                        canSaveCurrent = hasActiveFilters,
                        onSaveCurrent = { viewModel.saveCurrentFilter() },
                        onApplyFilter = { viewModel.applySavedFilter(it) },
                        onClearSavedFilters = { viewModel.clearSavedFilters() },
                    )
                }
                HostShieldDenseListJumpBar(
                    screen = "sources",
                    label = "source results",
                    totalItems = lazyItemCount,
                    listState = listState,
                    minItems = 16,
                )
            }

            if (filteredSources.isEmpty()) {
                item {
                    HostShieldEmptyState(
                        icon = if (hasActiveFilters) Icons.Filled.FilterAltOff else Icons.Filled.CloudOff,
                        title = if (hasActiveFilters) "No matching sources" else "No sources configured",
                        message = if (hasActiveFilters) {
                            "Clear the search or saved filter to show configured blocklists again."
                        } else {
                            "Add a trusted blocklist or browse the curated gallery before enabling source-based protection."
                        },
                        accent = Teal,
                        primaryActionLabel = if (hasActiveFilters) "Clear filters" else "Browse gallery",
                        onPrimaryAction = if (hasActiveFilters) viewModel::clearFilters else onNavigateToGallery,
                        secondaryActionLabel = if (hasActiveFilters) null else "Add URL",
                        onSecondaryAction = if (hasActiveFilters) null else ({ showAddDialog = true }),
                    )
                }
            }
            visibleCategories.forEach { (category, items) ->
                item {
                    Text(
                        "${category.name.lowercase().replaceFirstChar { it.uppercase() }} (${items.size})",
                        style = MaterialTheme.typography.labelLarge,
                        color = categoryColor(category),
                        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
                        letterSpacing = 0.sp
                    )
                }
                items(items, key = { it.id }) { source ->
                    SourceItem(
                        source = source,
                        allowlistImpact = allowlistImpacts[source.id],
                        onToggle = { viewModel.toggleSource(source.id, it) },
                        onDelete = { pendingDeleteSource = source }
                    )
                }
            }
            item { Spacer(Modifier.height(140.dp)) }
        }
        } // end else (not loading)
    }

    if (showAddDialog) {
        AddSourceDialog(
            onDismiss = { showAddDialog = false },
            onAdd = { url, label, cat ->
                viewModel.addSource(url, label, cat)
                showAddDialog = false
            }
        )
    }

    pendingDeleteSource?.let { source ->
        ConfirmDestructiveDialog(
            title = "Delete source?",
            body = "This removes ${source.label} from configured blocklists. Downloaded counts and source health for this entry will no longer appear.",
            confirmLabel = "Delete source",
            onConfirm = { viewModel.deleteSource(source) },
            onDismiss = { pendingDeleteSource = null },
        )
    }
}

@Composable
private fun SourceImpactPreviewCard(preview: SourceImpactPreview) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        color = Surface2,
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Visibility, null, tint = Blue, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.sources_update_preview),
                        color = TextPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        "Active ${NumberFormat.getNumberInstance().format(preview.currentEntryCount)} -> preview ${NumberFormat.getNumberInstance().format(preview.previewEntryCount)} entries from ${preview.sourceCount} sources.",
                        color = TextDim,
                        fontSize = 10.sp,
                        lineHeight = 14.sp
                    )
                }
                if (preview.failedSourceCount > 0) {
                    Text(
                        "${preview.failedSourceCount} failed",
                        color = Red.copy(alpha = 0.85f),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                PreviewStat(
                    label = "Added",
                    value = "+${NumberFormat.getNumberInstance().format(preview.addedCount)}",
                    color = Green,
                    modifier = Modifier.weight(1f)
                )
                PreviewStat(
                    label = "Removed",
                    value = "-${NumberFormat.getNumberInstance().format(preview.removedCount)}",
                    color = Red,
                    modifier = Modifier.weight(1f)
                )
                PreviewStat(
                    label = "Preview total",
                    value = NumberFormat.getNumberInstance().format(preview.previewEntryCount),
                    color = Blue,
                    modifier = Modifier.weight(1f)
                )
            }

            if (preview.changedQueries.isEmpty()) {
                Text(
                    "No recent DNS queries would change verdict.",
                    color = TextSecondary,
                    fontSize = 11.sp,
                    lineHeight = 15.sp
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        "Recent verdict changes",
                        color = TextSecondary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                    preview.changedQueries.forEach { change ->
                        SourceImpactQueryChangeRow(change)
                    }
                }
            }
        }
    }
}

@Composable
private fun PreviewStat(label: String, value: String, color: Color, modifier: Modifier = Modifier) {
    Surface(shape = RoundedCornerShape(8.dp), color = Surface3, modifier = modifier) {
        Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 7.dp)) {
            Text(value, color = color, fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 1)
            Text(label, color = TextDim, fontSize = 9.sp, lineHeight = 12.sp)
        }
    }
}

@Composable
private fun SourceImpactQueryChangeRow(change: SourceImpactQueryChange) {
    val appText = change.appLabel.ifBlank { change.appPackage }
    val directionColor = if (change.previewBlocked) Red else Green
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .padding(top = 5.dp)
                .size(6.dp)
                .clip(CircleShape)
                .background(directionColor.copy(alpha = 0.75f))
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(change.hostname, color = TextPrimary, fontSize = 11.sp, lineHeight = 15.sp, maxLines = 2)
            if (appText.isNotBlank()) {
                Text(
                    "$appText - ${change.queryCount} recent ${if (change.queryCount == 1) "query" else "queries"}",
                    color = TextDim,
                    fontSize = 10.sp,
                    lineHeight = 14.sp,
                    maxLines = 1
                )
            }
        }
        Text(
            "${verdictLabel(change.currentBlocked)} -> ${verdictLabel(change.previewBlocked)}",
            color = directionColor,
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

private fun verdictLabel(blocked: Boolean): String = if (blocked) "Blocked" else "Allowed"

@Composable
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
private fun SourceItem(
    source: HostSource,
    allowlistImpact: AllowlistImpact?,
    onToggle: (Boolean) -> Unit,
    onDelete: () -> Unit
) {
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(9.dp))
                    .background(categoryColor(source.category).copy(alpha = 0.10f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    categoryIcon(source.category),
                    contentDescription = null,
                    tint = categoryColor(source.category),
                    modifier = Modifier.size(17.dp)
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        source.label,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                        color = TextPrimary,
                        fontSize = 14.sp,
                        lineHeight = 18.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    if (source.isBuiltin) {
                        Spacer(Modifier.width(8.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(Mauve.copy(alpha = 0.1f))
                                .padding(horizontal = 5.dp, vertical = 1.dp)
                        ) {
                            Text(
                                stringResource(R.string.sources_built_in),
                                style = MaterialTheme.typography.labelSmall,
                                color = Mauve,
                                fontSize = 9.sp
                            )
                        }
                    }
                    val healthBadge = if (source.hasActiveHealthWarning()) {
                        when (source.health) {
                            SourceHealth.ERROR -> "ERROR" to Red
                            SourceHealth.DEAD -> "DEAD" to Red
                            SourceHealth.STALE -> "STALE" to Yellow
                            SourceHealth.UNKNOWN, SourceHealth.OK -> null
                        }
                    } else null
                    healthBadge?.let { (text, color) ->
                        Spacer(Modifier.width(6.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(color.copy(alpha = 0.1f))
                                .padding(horizontal = 5.dp, vertical = 1.dp)
                        ) {
                            Text(text, style = MaterialTheme.typography.labelSmall, color = color, fontSize = 9.sp)
                        }
                    }
                }
                if (source.description.isNotEmpty()) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        source.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                        lineHeight = 16.sp
                    )
                }
                Spacer(Modifier.height(6.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (source.entryCount > 0) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(5.dp)
                                    .clip(CircleShape)
                                    .background(Teal.copy(alpha = 0.6f))
                            )
                            Spacer(Modifier.width(5.dp))
                            Text(
                                "${NumberFormat.getNumberInstance().format(source.entryCount)} entries",
                                style = MaterialTheme.typography.labelSmall,
                                color = Teal.copy(alpha = 0.8f),
                                lineHeight = 14.sp
                            )
                        }
                    }
                    if (source.lastUpdated > 0) {
                        Text(
                            formatTimestamp(source.lastUpdated),
                            style = MaterialTheme.typography.labelSmall,
                            color = TextDim,
                            lineHeight = 14.sp
                        )
                    }
                    if (source.domainsAdded > 0 || source.domainsRemoved > 0) {
                        if (source.domainsAdded > 0) {
                            Text(
                                "+${source.domainsAdded}",
                                style = MaterialTheme.typography.labelSmall,
                                color = Green.copy(alpha = 0.8f),
                                lineHeight = 14.sp
                            )
                        }
                        if (source.domainsRemoved > 0) {
                            Text(
                                "-${source.domainsRemoved}",
                                style = MaterialTheme.typography.labelSmall,
                                color = Red.copy(alpha = 0.7f),
                                lineHeight = 14.sp
                            )
                        }
                    }
                }
                if (source.health == SourceHealth.OK && source.lastError.isNotBlank()) {
                    Spacer(Modifier.height(7.dp))
                    Row(verticalAlignment = Alignment.Top) {
                        Icon(
                            Icons.Filled.Info,
                            contentDescription = null,
                            tint = Yellow.copy(alpha = 0.8f),
                            modifier = Modifier.size(13.dp).padding(top = 1.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            stringResource(R.string.sources_parse_diagnostics, source.lastError),
                            color = Yellow.copy(alpha = 0.85f),
                            fontSize = 10.sp,
                            lineHeight = 14.sp,
                            maxLines = 4,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                if (source.hasActiveHealthFailure()) {
                    Spacer(Modifier.height(7.dp))
                    Row(verticalAlignment = Alignment.Top) {
                        Icon(
                            Icons.Filled.ReportProblem,
                            contentDescription = null,
                            tint = Red.copy(alpha = 0.75f),
                            modifier = Modifier.size(13.dp).padding(top = 1.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Column {
                            Text(
                                sourceFailureText(source),
                                color = Red.copy(alpha = 0.85f),
                                fontSize = 11.sp,
                                lineHeight = 15.sp
                            )
                            Text(
                                sourceLastSuccessText(source.lastUpdated),
                                color = TextDim,
                                fontSize = 10.sp,
                                lineHeight = 14.sp
                            )
                        }
                    }
                }
                if (source.category == SourceCategory.ALLOWLIST) {
                    val neutralized = allowlistImpact?.neutralizedCount ?: source.domainsRemoved
                    if (neutralized > 0) {
                        Spacer(Modifier.height(7.dp))
                        Row(verticalAlignment = Alignment.Top) {
                            Icon(Icons.Filled.CheckCircle, null, tint = Green, modifier = Modifier.size(13.dp).padding(top = 1.dp))
                            Spacer(Modifier.width(6.dp))
                            Column {
                                Text(
                                    "Neutralizes ${NumberFormat.getNumberInstance().format(neutralized)} blocked domains",
                                    color = Green.copy(alpha = 0.9f),
                                    fontSize = 11.sp,
                                    lineHeight = 15.sp
                                )
                                allowlistImpact?.examples?.takeIf { it.isNotEmpty() }?.let { examples ->
                                    Text(
                                        examples.joinToString(", "),
                                        color = TextDim,
                                        fontSize = 10.sp,
                                        lineHeight = 14.sp,
                                        maxLines = 2
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.width(8.dp))

            if (!source.isBuiltin) {
                IconButton(onClick = onDelete, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.Filled.Delete, "Delete ${source.label}", tint = Red.copy(alpha = 0.5f), modifier = Modifier.size(16.dp))
                }
            }

            Switch(
                checked = source.enabled,
                onCheckedChange = onToggle,
                modifier = Modifier.accessibilityToggle("${source.label} source", source.enabled),
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Teal,
                    checkedTrackColor = Teal.copy(alpha = 0.25f),
                    uncheckedThumbColor = TextDim,
                    uncheckedTrackColor = Surface3
                )
            )
        }
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun AddSourceDialog(
    onDismiss: () -> Unit,
    onAdd: (url: String, label: String, category: SourceCategory) -> Unit
) {
    var url by rememberSaveable { mutableStateOf("") }
    var label by rememberSaveable { mutableStateOf("") }
    var category by rememberSaveable { mutableStateOf(SourceCategory.ADS) }

    val urlValidation = remember(url) { SourceUrlPolicy.validate(url) }
    val urlValid = urlValidation.isValid
    val labelValid = label.trim().isNotEmpty()
    val showLabelError = url.isNotBlank() && !labelValid
    val canSubmit = urlValid && labelValid

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Surface1,
        shape = RoundedCornerShape(12.dp),
        title = {
            Text(
                stringResource(R.string.sources_add_source),
                color = TextPrimary,
                fontWeight = FontWeight.SemiBold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = label, onValueChange = { label = it },
                    label = { Text(stringResource(R.string.sources_source_name)) }, singleLine = true,
                    isError = showLabelError,
                    supportingText = if (showLabelError) {
                        { Text(stringResource(R.string.sources_enter_name), color = Red, fontSize = 11.sp) }
                    } else null,
                    keyboardOptions = KeyboardOptions(
                        capitalization = androidx.compose.ui.text.input.KeyboardCapitalization.Words,
                        imeAction = ImeAction.Next
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(HostShieldTestTags.Sources.NameField),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Teal, unfocusedBorderColor = Surface3,
                        cursorColor = Teal, focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary
                    )
                )
                OutlinedTextField(
                    value = url, onValueChange = { url = it },
                    label = { Text(stringResource(R.string.sources_source_url)) }, singleLine = true,
                    isError = url.isNotBlank() && !urlValid,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done),
                    supportingText = if (url.isNotBlank() && !urlValid) {
                        {
                            Text(
                                urlValidation.errorMessage ?: "Use a complete https:// URL.",
                                color = Red,
                                fontSize = 11.sp
                            )
                        }
                    } else null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(HostShieldTestTags.Sources.UrlField),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Teal, unfocusedBorderColor = Surface3,
                        cursorColor = Teal, focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary
                    )
                )

                Text(
                    "Category",
                    color = TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.Medium
                )
                androidx.compose.foundation.layout.FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    SourceCategory.entries.forEach { c ->
                        val selected = c == category
                        HostShieldFilterChip(
                            label = c.name,
                            selected = selected,
                            onClick = { category = c },
                            accent = categoryColor(c),
                            semanticsLabel = "${c.name} source category",
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { if (canSubmit) onAdd(url.trim(), label.trim(), category) },
                enabled = canSubmit,
                modifier = Modifier.testTag(HostShieldTestTags.Sources.ConfirmAddButton)
            ) { Text(stringResource(R.string.sources_add_source), color = Teal, fontWeight = FontWeight.SemiBold) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel), color = TextSecondary) }
        }
    )
}

@Composable
private fun categoryColor(cat: SourceCategory): Color = when (cat) {
    SourceCategory.ADS -> Teal
    SourceCategory.TRACKERS -> Blue
    SourceCategory.MALWARE -> Red
    SourceCategory.ADULT -> Flamingo
    SourceCategory.SOCIAL -> Mauve
    SourceCategory.CRYPTO -> Peach
    SourceCategory.ALLOWLIST -> Green
    SourceCategory.CUSTOM -> Yellow
}

private fun formatTimestamp(ms: Long): String = try {
    Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault())
        .format(DateTimeFormatter.ofPattern("MMM d, h:mm a"))
} catch (e: Exception) { "Unknown" }

private fun sourceFailureText(source: HostSource): String {
    val reason = sanitizeSourceFailureReason(source.lastError, source.lastHttpStatus)
    val failures = if (source.consecutiveFailures > 0) " (${source.consecutiveFailures}x)" else ""
    return "Last failure: $reason$failures"
}

private fun sanitizeSourceFailureReason(rawError: String, storedHttpStatus: Int): String {
    val error = rawError.trim()
    val httpStatus = Regex("""HTTP\s+(\d{3})""", RegexOption.IGNORE_CASE)
        .find(error)
        ?.groupValues
        ?.getOrNull(1)
        ?.toIntOrNull()
        ?: storedHttpStatus.takeIf { it > 0 }
    if (error.isBlank()) return if (httpStatus != null) "Source returned HTTP $httpStatus" else "Update failed"
    return when {
        httpStatus in 200..299 -> "Update failed after download"
        httpStatus in 300..399 -> "Source redirected unexpectedly"
        httpStatus in 400..499 -> "Source URL returned HTTP $httpStatus"
        httpStatus in 500..599 -> "Source server returned HTTP $httpStatus"
        httpStatus != null -> "Source returned HTTP $httpStatus"
        error.contains("timeout", ignoreCase = true) -> "Connection timed out"
        error.contains("unable to resolve host", ignoreCase = true) -> "DNS lookup failed"
        error.contains("certificate", ignoreCase = true) || error.contains("ssl", ignoreCase = true) -> "Secure connection failed"
        error.contains("0 entries", ignoreCase = true) -> "Source returned no entries"
        else -> "Download failed"
    }
}

private fun sourceLastSuccessText(lastUpdated: Long): String {
    return if (lastUpdated > 0L) {
        "Last successful update: ${formatTimestamp(lastUpdated)}"
    } else {
        "Last successful update: never"
    }
}

@Composable
private fun categoryIcon(cat: SourceCategory): ImageVector = when (cat) {
    SourceCategory.MALWARE -> Icons.Filled.Security
    SourceCategory.ALLOWLIST -> Icons.Filled.CheckCircle
    SourceCategory.CUSTOM -> Icons.Filled.Tune
    else -> Icons.Filled.CloudDownload
}
