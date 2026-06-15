package com.hostshield.ui.screens.sources

import android.util.Log
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.hostshield.data.model.DnsLogEntry
import com.hostshield.data.model.HostSource
import com.hostshield.data.model.RuleType
import com.hostshield.data.model.SourceCategory
import com.hostshield.data.model.SourceHealth
import com.hostshield.data.repository.HostShieldRepository
import com.hostshield.data.source.SourceDownloader
import com.hostshield.data.source.SourceUrlPolicy
import com.hostshield.data.source.sourceHttpStatus
import com.hostshield.domain.BlocklistHolder
import com.hostshield.domain.parser.HostsParser
import com.hostshield.ui.accessibility.accessibilityHeading
import com.hostshield.ui.accessibility.accessibilityLiveRegion
import com.hostshield.ui.accessibility.accessibilityToggle
import com.hostshield.ui.HostShieldTestTags
import com.hostshield.ui.components.ConfirmDestructiveDialog
import com.hostshield.ui.components.HostShieldEmptyState
import com.hostshield.ui.components.HostShieldMetricTile
import com.hostshield.ui.components.HostShieldStatusBanner
import com.hostshield.ui.screens.home.GlassCard
import com.hostshield.ui.theme.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject

data class AllowlistImpact(
    val neutralizedCount: Int,
    val examples: List<String>
)

data class SourceImpactPreview(
    val addedCount: Int,
    val removedCount: Int,
    val currentEntryCount: Int,
    val previewEntryCount: Int,
    val sourceCount: Int,
    val failedSourceCount: Int,
    val changedQueries: List<SourceImpactQueryChange>
)

data class SourceImpactQueryChange(
    val hostname: String,
    val appLabel: String,
    val appPackage: String,
    val queryCount: Int,
    val currentBlocked: Boolean,
    val previewBlocked: Boolean
)

@HiltViewModel
class SourcesViewModel @Inject constructor(
    private val repository: HostShieldRepository,
    private val downloader: SourceDownloader,
    private val blocklistHolder: BlocklistHolder
) : ViewModel() {
    private companion object {
        const val TAG = "SourcesViewModel"
    }

    val sources: StateFlow<List<HostSource>> = repository.getAllSources()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _isLoading = MutableStateFlow(true)
    val isLoading = _isLoading.asStateFlow()
    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()
    fun clearError() { _error.value = null }

    private val _healthCheckMessage = MutableStateFlow<String?>(null)
    val healthCheckMessage: StateFlow<String?> = _healthCheckMessage.asStateFlow()
    private val _isCheckingHealth = MutableStateFlow(false)
    val isCheckingHealth: StateFlow<Boolean> = _isCheckingHealth.asStateFlow()
    private val _allowlistImpactMessage = MutableStateFlow<String?>(null)
    val allowlistImpactMessage: StateFlow<String?> = _allowlistImpactMessage.asStateFlow()
    private val _isAnalyzingAllowlists = MutableStateFlow(false)
    val isAnalyzingAllowlists: StateFlow<Boolean> = _isAnalyzingAllowlists.asStateFlow()
    private val _allowlistImpacts = MutableStateFlow<Map<Long, AllowlistImpact>>(emptyMap())
    val allowlistImpacts: StateFlow<Map<Long, AllowlistImpact>> = _allowlistImpacts.asStateFlow()
    private val _sourceImpactMessage = MutableStateFlow<String?>(null)
    val sourceImpactMessage: StateFlow<String?> = _sourceImpactMessage.asStateFlow()
    private val _isPreviewingSourceImpact = MutableStateFlow(false)
    val isPreviewingSourceImpact: StateFlow<Boolean> = _isPreviewingSourceImpact.asStateFlow()
    private val _sourceImpactPreview = MutableStateFlow<SourceImpactPreview?>(null)
    val sourceImpactPreview: StateFlow<SourceImpactPreview?> = _sourceImpactPreview.asStateFlow()

    init {
        // Track when sources have emitted their first real value
        viewModelScope.launch {
            sources.first { true }
            _isLoading.value = false
        }
    }

    fun toggleSource(id: Long, enabled: Boolean) {
        viewModelScope.launch {
            try {
                repository.toggleSource(id, enabled)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to toggle source $id", e)
                _error.value = "Could not update the source. Try again."
            }
        }
    }
    fun deleteSource(source: HostSource) {
        viewModelScope.launch {
            try {
                repository.deleteSource(source)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to delete source ${source.id}", e)
                _error.value = "Could not delete the source. Try again."
            }
        }
    }
    fun addSource(url: String, label: String, category: SourceCategory) {
        viewModelScope.launch {
            try {
                val validation = SourceUrlPolicy.validate(url)
                if (!validation.isValid) {
                    _error.value = validation.errorMessage
                    return@launch
                }
                repository.addSource(
                    HostSource(url = validation.normalizedUrl, label = label, category = category)
                )
            } catch (e: Exception) {
                Log.e(TAG, "Failed to add source", e)
                _error.value = "Could not add the source. Check the URL and try again."
            }
        }
    }

    fun checkAllSourceHealth() {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            _isCheckingHealth.value = true
            _healthCheckMessage.value = "Checking sources..."
            try {
                val allSources = sources.value
                var ok = 0; var fail = 0
                for (source in allSources) {
                    if (!source.enabled) continue
                    val result = downloader.validate(source.url)
                    result.onSuccess { lineCount ->
                        if (lineCount == 0) {
                            fail++
                            repository.updateSourceHealth(
                                source.id,
                                SourceHealth.ERROR,
                                "Source returned 0 entries",
                                source.consecutiveFailures,
                                0
                            )
                        } else {
                            ok++
                            repository.updateSourceHealth(source.id, SourceHealth.OK, "", 0, 0)
                        }
                    }.onFailure { err ->
                        fail++
                        val failures = source.consecutiveFailures + 1
                        val health = if (failures >= 5) SourceHealth.DEAD else SourceHealth.ERROR
                        repository.updateSourceHealth(
                            source.id,
                            health,
                            err.message ?: "Unknown error",
                            failures,
                            err.sourceHttpStatus()
                        )
                    }
                }
                _healthCheckMessage.value = "$ok reachable, $fail unreachable"
            } catch (e: Exception) {
                Log.e(TAG, "Source health check failed", e)
                _error.value = "Could not finish the source health check. Try again."
                _healthCheckMessage.value = null
            } finally {
                _isCheckingHealth.value = false
            }
        }
    }

    fun analyzeAllowlistImpact() {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            _isAnalyzingAllowlists.value = true
            _allowlistImpactMessage.value = "Analyzing allowlist overrides..."
            try {
                val enabledSources = sources.value.filter { it.enabled }
                val blockSources = enabledSources.filter { it.category != SourceCategory.ALLOWLIST }
                val allowlistSources = enabledSources.filter { it.category == SourceCategory.ALLOWLIST }
                if (allowlistSources.isEmpty()) {
                    _allowlistImpacts.value = emptyMap()
                    _allowlistImpactMessage.value = "No enabled allowlist sources."
                    return@launch
                }
                if (blockSources.isEmpty()) {
                    _allowlistImpacts.value = allowlistSources.associate { it.id to AllowlistImpact(0, emptyList()) }
                    _allowlistImpactMessage.value = "No enabled block sources to compare."
                    return@launch
                }

                val exactBlocks = mutableSetOf<String>()
                val wildcardBlocks = mutableSetOf<String>()
                for (source in blockSources) {
                    downloader.download(source, forceDownload = true).onSuccess { dl ->
                        val parsed = HostsParser.parseForBlocking(dl.content)
                        exactBlocks.addAll(parsed.blockDomains)
                        wildcardBlocks.addAll(parsed.wildcardBlockDomains)
                    }
                }

                val impacts = mutableMapOf<Long, AllowlistImpact>()
                for (source in allowlistSources) {
                    downloader.download(source, forceDownload = true).onSuccess { dl ->
                        val parsed = HostsParser.parseForAllowing(dl.content)
                        val neutralized = findNeutralizedDomains(
                            parsed.allowDomains,
                            parsed.wildcardAllowDomains,
                            exactBlocks,
                            wildcardBlocks
                        )
                        impacts[source.id] = AllowlistImpact(
                            neutralizedCount = neutralized.size,
                            examples = neutralized.take(6)
                        )
                    }.onFailure {
                        impacts[source.id] = AllowlistImpact(0, emptyList())
                    }
                }
                _allowlistImpacts.value = impacts
                val total = impacts.values.sumOf { it.neutralizedCount }
                _allowlistImpactMessage.value = "$total blocked domains neutralized by enabled allowlists"
            } catch (e: Exception) {
                Log.e(TAG, "Allowlist impact analysis failed", e)
                _error.value = "Could not analyze allowlist impact. Try again."
                _allowlistImpactMessage.value = null
            } finally {
                _isAnalyzingAllowlists.value = false
            }
        }
    }

    fun previewSourceImpact() {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            _isPreviewingSourceImpact.value = true
            _sourceImpactMessage.value = "Previewing source updates..."
            try {
                val enabledSources = sources.value.filter { it.enabled }
                val blockSources = enabledSources.filter { it.category != SourceCategory.ALLOWLIST }
                val allowlistSources = enabledSources.filter { it.category == SourceCategory.ALLOWLIST }
                val sourceCount = blockSources.size + allowlistSources.size

                val candidateDomains = mutableSetOf<String>()
                val sourceAllowDomains = mutableSetOf<String>()
                val sourceWildcardBlocks = mutableSetOf<String>()
                val sourceWildcardAllows = mutableSetOf<String>()
                var failedSources = 0

                for (source in blockSources) {
                    downloader.download(source, forceDownload = true).onSuccess { dl ->
                        val parsed = HostsParser.parseForBlocking(dl.content)
                        candidateDomains.addAll(parsed.blockDomains)
                        sourceAllowDomains.addAll(parsed.allowDomains)
                        sourceWildcardBlocks.addAll(parsed.wildcardBlockDomains)
                        sourceWildcardAllows.addAll(parsed.wildcardAllowDomains)
                    }.onFailure {
                        failedSources++
                    }
                }
                for (source in allowlistSources) {
                    downloader.download(source, forceDownload = true).onSuccess { dl ->
                        val parsed = HostsParser.parseForAllowing(dl.content)
                        sourceAllowDomains.addAll(parsed.allowDomains)
                        sourceWildcardAllows.addAll(parsed.wildcardAllowDomains)
                    }.onFailure {
                        failedSources++
                    }
                }

                repository.getEnabledRulesByType(RuleType.BLOCK)
                    .filter { !it.isWildcard && !it.isRegex }
                    .forEach { rule -> candidateDomains.add(normalizePreviewHostname(rule.hostname)) }

                repository.getEnabledRulesByType(RuleType.ALLOW)
                    .filter { !it.isWildcard && !it.isRegex }
                    .forEach { rule -> candidateDomains.remove(normalizePreviewHostname(rule.hostname)) }

                candidateDomains.removeAll(sourceAllowDomains)

                val previewHolder = BlocklistHolder()
                previewHolder.update(
                    newDomains = candidateDomains,
                    wildcards = repository.getEnabledWildcards(),
                    regexRules = repository.getEnabledRegexRules(),
                    sourceWildcardBlocks = sourceWildcardBlocks,
                    sourceWildcardAllows = sourceWildcardAllows
                )

                val currentKeys = blocklistHolder.exportBlockKeysForPreview()
                val previewKeys = buildPreviewKeys(candidateDomains, sourceWildcardBlocks)
                val added = (previewKeys - currentKeys).size
                val removed = (currentKeys - previewKeys).size
                val recentLogs = repository.getRecentLogs(500).first()

                _sourceImpactPreview.value = SourceImpactPreview(
                    addedCount = added,
                    removedCount = removed,
                    currentEntryCount = currentKeys.size,
                    previewEntryCount = previewKeys.size,
                    sourceCount = sourceCount,
                    failedSourceCount = failedSources,
                    changedQueries = findChangedRecentQueries(recentLogs, blocklistHolder, previewHolder)
                )

                _sourceImpactMessage.value = when {
                    sourceCount == 0 -> "No enabled sources; preview uses current user rules only."
                    failedSources > 0 -> "Previewed ${sourceCount - failedSources}/$sourceCount enabled sources; $failedSources failed."
                    else -> "Previewed $sourceCount enabled sources without applying changes."
                }
            } catch (e: Exception) {
                Log.e(TAG, "Source impact preview failed", e)
                _error.value = "Could not preview source update impact. Try again."
                _sourceImpactMessage.value = null
            } finally {
                _isPreviewingSourceImpact.value = false
            }
        }
    }

    private fun findNeutralizedDomains(
        allowDomains: Set<String>,
        wildcardAllowDomains: Set<String>,
        exactBlocks: Set<String>,
        wildcardBlocks: Set<String>
    ): List<String> {
        val neutralized = linkedSetOf<String>()
        val candidates = allowDomains + wildcardAllowDomains
        candidates.sorted().forEach { domain ->
            if (domain in exactBlocks || wildcardBlocks.any { matchesDomainOrSubdomain(domain, it) }) {
                neutralized.add(domain)
            }
        }
        exactBlocks.sorted().forEach { blocked ->
            if (wildcardAllowDomains.any { matchesDomainOrSubdomain(blocked, it) }) {
                neutralized.add(blocked)
            }
        }
        return neutralized.toList()
    }

    private fun matchesDomainOrSubdomain(domain: String, base: String): Boolean =
        domain == base || domain.endsWith(".$base")

    private fun buildPreviewKeys(
        exactBlocks: Set<String>,
        sourceWildcardBlocks: Set<String>
    ): Set<String> {
        val keys = HashSet<String>(exactBlocks.size + sourceWildcardBlocks.size)
        exactBlocks.forEach { domain ->
            normalizePreviewHostname(domain).takeIf { it.isNotBlank() }?.let(keys::add)
        }
        sourceWildcardBlocks.forEach { domain ->
            normalizePreviewHostname(domain).takeIf { it.isNotBlank() }?.let { keys.add("*.$it") }
        }
        return keys
    }

    private fun findChangedRecentQueries(
        logs: List<DnsLogEntry>,
        currentHolder: BlocklistHolder,
        previewHolder: BlocklistHolder
    ): List<SourceImpactQueryChange> {
        val recent = linkedMapOf<String, RecentQueryBucket>()
        logs.forEach { log ->
            val hostname = normalizePreviewHostname(log.hostname)
            if (hostname.isBlank()) return@forEach
            val bucket = recent.getOrPut(hostname) {
                RecentQueryBucket(appLabel = log.appLabel, appPackage = log.appPackage)
            }
            bucket.queryCount++
            if (bucket.appLabel.isBlank() && log.appLabel.isNotBlank()) bucket.appLabel = log.appLabel
            if (bucket.appPackage.isBlank() && log.appPackage.isNotBlank()) bucket.appPackage = log.appPackage
        }

        return recent.mapNotNull { (hostname, bucket) ->
            val currentBlocked = currentHolder.isBlocked(hostname)
            val previewBlocked = previewHolder.isBlocked(hostname)
            if (currentBlocked == previewBlocked) {
                null
            } else {
                SourceImpactQueryChange(
                    hostname = hostname,
                    appLabel = bucket.appLabel,
                    appPackage = bucket.appPackage,
                    queryCount = bucket.queryCount,
                    currentBlocked = currentBlocked,
                    previewBlocked = previewBlocked
                )
            }
        }.take(8)
    }

    private fun normalizePreviewHostname(hostname: String): String =
        hostname.trim().lowercase().removeSuffix(".")
}

private data class RecentQueryBucket(
    var appLabel: String,
    var appPackage: String,
    var queryCount: Int = 0
)

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
    var showAddDialog by remember { mutableStateOf(false) }
    var pendingDeleteSource by remember { mutableStateOf<HostSource?>(null) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp),
                contentAlignment = Alignment.Center
            ) {
                GlassCard(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier
                            .padding(24.dp)
                            .accessibilityLiveRegion("Loading configured sources"),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(Icons.Filled.CloudDownload, null, tint = Teal, modifier = Modifier.size(32.dp))
                        Spacer(Modifier.height(14.dp))
                        Text("Loading sources", color = TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Checking configured blocklists and health data.",
                            color = TextSecondary,
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Spacer(Modifier.height(16.dp))
                        CircularProgressIndicator(color = Teal, modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                    }
                }
            }
        } else {
        LazyColumn(
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
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Sources",
                        style = MaterialTheme.typography.headlineMedium,
                        color = TextPrimary,
                        modifier = Modifier.accessibilityHeading()
                    )
                    Surface(
                        onClick = { showAddDialog = true },
                        shape = RoundedCornerShape(8.dp),
                        color = Teal.copy(alpha = 0.14f),
                        contentColor = Teal,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag(HostShieldTestTags.Sources.AddButton)
                    ) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Icon(Icons.Filled.Add, "Add source", modifier = Modifier.size(20.dp))
                        }
                    }
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
                val unhealthy = sources.count { it.health == SourceHealth.ERROR || it.health == SourceHealth.DEAD }
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
            }

            val grouped = sources.groupBy { it.category }
            if (sources.isEmpty()) {
                item {
                    HostShieldEmptyState(
                        icon = Icons.Filled.CloudOff,
                        title = "No sources configured",
                        message = "Add a trusted blocklist or browse the curated gallery before enabling source-based protection.",
                        accent = Teal,
                        primaryActionLabel = "Browse gallery",
                        onPrimaryAction = onNavigateToGallery,
                        secondaryActionLabel = "Add URL",
                        onSecondaryAction = { showAddDialog = true },
                    )
                }
            }
            SourceCategory.entries.forEach { category ->
                val items = grouped[category] ?: return@forEach
                item {
                    Text(
                        category.name.lowercase().replaceFirstChar { it.uppercase() },
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
                    Text("Source update preview", color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
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
                            Text("BUILT-IN", style = MaterialTheme.typography.labelSmall, color = Mauve, fontSize = 9.sp)
                        }
                    }
                    val healthBadge = when (source.health) {
                        SourceHealth.ERROR -> "ERROR" to Red
                        SourceHealth.DEAD -> "DEAD" to Red
                        SourceHealth.STALE -> "STALE" to Yellow
                        else -> null
                    }
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
                if (source.health == SourceHealth.ERROR || source.health == SourceHealth.DEAD) {
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
    var url by remember { mutableStateOf("") }
    var label by remember { mutableStateOf("") }
    var category by remember { mutableStateOf(SourceCategory.ADS) }

    val urlValidation = remember(url) { SourceUrlPolicy.validate(url) }
    val urlValid = urlValidation.isValid
    val labelValid = label.trim().isNotEmpty()
    val showLabelError = url.isNotBlank() && !labelValid
    val canSubmit = urlValid && labelValid

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Surface1,
        shape = RoundedCornerShape(12.dp),
        title = { Text("Add source", color = TextPrimary, fontWeight = FontWeight.SemiBold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = label, onValueChange = { label = it },
                    label = { Text("Source name") }, singleLine = true,
                    isError = showLabelError,
                    supportingText = if (showLabelError) {
                        { Text("Enter a source name.", color = Red, fontSize = 11.sp) }
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
                    label = { Text("Source URL") }, singleLine = true,
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
                        FilterChip(
                            selected = selected,
                            onClick = { category = c },
                            label = { Text(c.name, fontSize = 11.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                containerColor = Surface2,
                                labelColor = TextDim,
                                selectedContainerColor = categoryColor(c).copy(alpha = 0.18f),
                                selectedLabelColor = categoryColor(c),
                            ),
                            shape = RoundedCornerShape(8.dp),
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
            ) { Text("Add source", color = Teal, fontWeight = FontWeight.SemiBold) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = TextSecondary) }
        }
    )
}

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
    val status = if (source.lastHttpStatus > 0) "HTTP ${source.lastHttpStatus}" else "Network"
    val reason = sanitizeSourceFailureReason(source.lastError)
    val failures = if (source.consecutiveFailures > 0) " (${source.consecutiveFailures}x)" else ""
    return "Last failure: $status - $reason$failures"
}

private fun sanitizeSourceFailureReason(rawError: String): String {
    val error = rawError.trim()
    if (error.isBlank()) return "Update failed"
    return when {
        error.startsWith("HTTP ", ignoreCase = true) -> error.take(80)
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
