package com.hostshield.ui.screens.sources

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hostshield.data.model.DnsLogEntry
import com.hostshield.data.model.HostSource
import com.hostshield.data.model.RuleType
import com.hostshield.data.model.SourceCategory
import com.hostshield.data.model.SourceHealth
import com.hostshield.data.preferences.SavedDenseListFilter
import com.hostshield.data.preferences.UiPreferences
import com.hostshield.data.repository.HostShieldRepository
import com.hostshield.data.source.SourceDownloader
import com.hostshield.data.source.SourceUrlPolicy
import com.hostshield.data.source.sourceHttpStatus
import com.hostshield.domain.BlocklistHolder
import com.hostshield.domain.parser.HostsParser
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import org.json.JSONObject
import javax.inject.Inject

private const val SOURCES_SAVED_FILTER_SCREEN = "sources"

enum class SourceListFilter { ALL, ENABLED, DISABLED, UNHEALTHY, ALLOWLIST }

private fun describeSourceFilter(query: String, filter: SourceListFilter): String = buildList {
    if (query.isNotBlank()) add("\"${query.take(18)}\"")
    when (filter) {
        SourceListFilter.ALL -> Unit
        SourceListFilter.ENABLED -> add("Enabled")
        SourceListFilter.DISABLED -> add("Disabled")
        SourceListFilter.UNHEALTHY -> add("Needs review")
        SourceListFilter.ALLOWLIST -> add("Allowlists")
    }
}.joinToString(" + ").ifBlank { "Source filter" }

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
    private val blocklistHolder: BlocklistHolder,
    private val uiPreferences: UiPreferences
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
    private val _searchQuery = MutableStateFlow("")
    val searchQuery = _searchQuery.asStateFlow()
    private val _filter = MutableStateFlow(SourceListFilter.ALL)
    val filter = _filter.asStateFlow()
    val savedFilters: StateFlow<List<SavedDenseListFilter>> = uiPreferences
        .savedDenseListFilters(SOURCES_SAVED_FILTER_SCREEN)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

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

    fun setSearchQuery(query: String) { _searchQuery.value = query }
    fun setFilter(filter: SourceListFilter) { _filter.value = filter }
    fun clearFilters() {
        _searchQuery.value = ""
        _filter.value = SourceListFilter.ALL
    }

    fun saveCurrentFilter() {
        val query = _searchQuery.value.trim()
        val filter = _filter.value
        if (query.isBlank() && filter == SourceListFilter.ALL) return
        viewModelScope.launch {
            uiPreferences.saveDenseListFilter(
                SOURCES_SAVED_FILTER_SCREEN,
                describeSourceFilter(query, filter),
                JSONObject()
                    .put("query", query)
                    .put("filter", filter.name)
                    .toString()
            )
        }
    }

    fun applySavedFilter(saved: SavedDenseListFilter) {
        runCatching {
            val json = JSONObject(saved.payload)
            _searchQuery.value = json.optString("query")
            _filter.value = runCatching {
                SourceListFilter.valueOf(json.optString("filter"))
            }.getOrDefault(SourceListFilter.ALL)
        }
    }

    fun clearSavedFilters() {
        viewModelScope.launch {
            uiPreferences.clearDenseListFilters(SOURCES_SAVED_FILTER_SCREEN)
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
                val dnsTypeRules = mutableListOf<com.hostshield.domain.DnsTypeRule>()
                var failedSources = 0

                for (source in blockSources) {
                    downloader.download(source, forceDownload = true).onSuccess { dl ->
                        val parsed = HostsParser.parseForBlocking(dl.content)
                        candidateDomains.addAll(parsed.blockDomains)
                        sourceAllowDomains.addAll(parsed.allowDomains)
                        sourceWildcardBlocks.addAll(parsed.wildcardBlockDomains)
                        sourceWildcardAllows.addAll(parsed.wildcardAllowDomains)
                        dnsTypeRules.addAll(parsed.dnsTypeRules.map { it.normalized(source.label) })
                    }.onFailure {
                        failedSources++
                    }
                }
                for (source in allowlistSources) {
                    downloader.download(source, forceDownload = true).onSuccess { dl ->
                        val parsed = HostsParser.parseForAllowing(dl.content)
                        sourceAllowDomains.addAll(parsed.allowDomains)
                        sourceWildcardAllows.addAll(parsed.wildcardAllowDomains)
                        dnsTypeRules.addAll(parsed.dnsTypeAllowRules.map { it.normalized(source.label) })
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
                    sourceWildcardAllows = sourceWildcardAllows,
                    dnsTypeRules = dnsTypeRules
                )

                val currentKeys = blocklistHolder.exportBlockKeysForPreview()
                val previewKeys = previewHolder.exportBlockKeysForPreview()
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
