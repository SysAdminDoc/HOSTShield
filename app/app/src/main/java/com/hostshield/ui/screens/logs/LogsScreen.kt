package com.hostshield.ui.screens.logs

import android.util.Log
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.hostshield.data.model.BlockMethod
import com.hostshield.data.model.DnsLogEntry
import com.hostshield.data.model.RuleType
import com.hostshield.data.model.UserRule
import com.hostshield.data.preferences.AppPreferences
import com.hostshield.data.repository.HostShieldRepository
import com.hostshield.domain.BlocklistHolder
import com.hostshield.ui.accessibility.accessibilityAction
import com.hostshield.ui.accessibility.accessibilityHeading
import com.hostshield.ui.accessibility.accessibilityLiveRegion
import com.hostshield.ui.accessibility.accessibilitySelection
import com.hostshield.ui.accessibility.accessibilityToggle
import com.hostshield.ui.HostShieldTestTags
import com.hostshield.ui.components.ConfirmDestructiveDialog
import com.hostshield.ui.components.HostShieldEmptyState
import com.hostshield.ui.components.HostShieldStatusBanner
import com.hostshield.ui.theme.*
import com.hostshield.util.GeoIpLookup
import com.hostshield.util.RootUtil
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject

// DNS log screen

data class DedupedLogEntry(
    val hostname: String,
    val blocked: Boolean,
    val hitCount: Int,
    val latestTimestamp: Long,
    val appLabel: String,
    val appPackage: String,
    val queryType: String = "A",
    val responseTimeMs: Int = 0,
    val upstreamServer: String = "",
    val cnameChain: String = "",
    val resolvedIps: String = "",
    val decisionReason: String = "",
    val decisionSource: String = "",
    val matchedValue: String = "",
    val decisionPrecedence: String = ""
)

@HiltViewModel
class LogsViewModel @Inject constructor(
    private val repository: HostShieldRepository,
    private val blocklist: BlocklistHolder,
    private val rootUtil: RootUtil,
    private val prefs: AppPreferences,
    val geoIpLookup: GeoIpLookup
) : ViewModel() {
    val logs: StateFlow<List<DnsLogEntry>> = repository.getRecentLogs(2000)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _searchQuery = MutableStateFlow("")
    val searchQuery = _searchQuery.asStateFlow()
    private val _searchHistory = MutableStateFlow<List<String>>(emptyList())
    val searchHistory = _searchHistory.asStateFlow()
    private val _showBlocked = MutableStateFlow<Boolean?>(null)
    val showBlocked = _showBlocked.asStateFlow()

    // Authoritative set of blocked hostnames — loaded from DB + blocklist on init,
    // updated instantly on block/allow actions. Persists across sessions via DB.
    private val _blockedHostnames = MutableStateFlow<Set<String>>(emptySet())
    val blockedHostnames = _blockedHostnames.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()
    fun clearError() { _error.value = null }

    init {
        loadBlockedState()
    }

    /**
     * Load all blocked hostnames from:
     * 1. User BLOCK rules in the database (persists across sessions)
     * 2. The active in-memory blocklist (source lists + user rules)
     * Subtract any ALLOW rules. This ensures every launch shows correct state.
     */
    private fun loadBlockedState() {
        viewModelScope.launch(Dispatchers.IO) {
            _isLoading.value = true
            try {
                val blockRules = repository.getEnabledRulesByType(RuleType.BLOCK)
                val allowRules = repository.getEnabledRulesByType(RuleType.ALLOW)
                val allowed = allowRules.map { it.hostname.lowercase() }.toSet()

                val all = mutableSetOf<String>()
                // From user block rules (these are always known)
                all.addAll(blockRules.map { it.hostname.lowercase() })
                // Remove explicit allows
                all.removeAll(allowed)

                _blockedHostnames.value = all
            } catch (e: Exception) {
                Log.e("LogsViewModel", "Failed to load blocked state", e)
                _error.value = "Failed to load blocked domains: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun setSearch(q: String) {
        _searchQuery.value = q
        if (q.length >= 3 && q !in _searchHistory.value) {
            _searchHistory.update { (listOf(q) + it).distinct().take(10) }
        }
    }
    fun clearSearchHistory() { _searchHistory.value = emptyList() }
    fun setFilter(blocked: Boolean?) { _showBlocked.value = blocked }

    fun blockDomain(hostname: String) {
        val host = hostname.lowercase()
        _blockedHostnames.update { it + host }

        viewModelScope.launch(Dispatchers.IO) {
            try {
                repository.addRule(UserRule(hostname = host, type = RuleType.BLOCK))
                blocklist.addDomain(host)
                val method = prefs.blockMethod.first()
                if (method == BlockMethod.ROOT_HOSTS) {
                    rootUtil.appendHostEntry(host)
                }
            } catch (e: Exception) {
                Log.e("LogsViewModel", "Failed to block domain: $host", e)
                _error.value = "Failed to block $host: ${e.message}"
            }
        }
    }

    fun allowDomain(hostname: String) {
        val host = hostname.lowercase()
        _blockedHostnames.update { it - host }

        viewModelScope.launch(Dispatchers.IO) {
            try {
                repository.addRule(UserRule(hostname = host, type = RuleType.ALLOW))
                blocklist.removeDomain(host)
                val method = prefs.blockMethod.first()
                if (method == BlockMethod.ROOT_HOSTS) {
                    rootUtil.removeHostEntry(host)
                }
            } catch (e: Exception) {
                Log.e("LogsViewModel", "Failed to allow domain: $host", e)
                _error.value = "Failed to allow $host: ${e.message}"
            }
        }
    }

    fun clearLogs() {
        viewModelScope.launch {
            try {
                repository.clearAllLogs()
            } catch (e: Exception) {
                Log.e("LogsViewModel", "Failed to clear logs", e)
                _error.value = "Failed to clear logs: ${e.message}"
            }
        }
    }

    fun blockDomains(hostnames: Set<String>) {
        val hosts = hostnames.map { it.lowercase() }
        _blockedHostnames.update { it + hosts }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                hosts.forEach { host ->
                    repository.addRule(UserRule(hostname = host, type = RuleType.BLOCK))
                    blocklist.addDomain(host)
                }
                val method = prefs.blockMethod.first()
                if (method == BlockMethod.ROOT_HOSTS) {
                    hosts.forEach { rootUtil.appendHostEntry(it) }
                }
            } catch (e: Exception) {
                Log.e("LogsViewModel", "Failed to block domains", e)
                _error.value = "Failed to block domains: ${e.message}"
            }
        }
    }

    val pinnedDomains: StateFlow<Set<String>> = prefs.pinnedDomains
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    /** Temporarily allow a domain for N minutes, then re-block. */
    fun temporaryAllow(hostname: String, minutes: Int) {
        val host = hostname.lowercase()
        _blockedHostnames.update { it - host }
        blocklist.removeDomain(host)

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val method = prefs.blockMethod.first()
                if (method == BlockMethod.ROOT_HOSTS) rootUtil.removeHostEntry(host)

                // Wait then re-block
                kotlinx.coroutines.delay(minutes * 60_000L)

                _blockedHostnames.update { it + host }
                blocklist.addDomain(host)
                if (method == BlockMethod.ROOT_HOSTS) rootUtil.appendHostEntry(host)
            } catch (e: Exception) {
                Log.e("LogsViewModel", "Failed to temporarily allow domain: $host", e)
                _error.value = "Failed to temporarily allow $host: ${e.message}"
            }
        }
    }

    fun togglePin(domain: String) {
        viewModelScope.launch {
            try {
                val current = prefs.pinnedDomains.first()
                if (domain.lowercase() in current) prefs.unpinDomain(domain)
                else prefs.pinDomain(domain)
            } catch (e: Exception) {
                Log.e("LogsViewModel", "Failed to toggle pin for: $domain", e)
                _error.value = "Failed to toggle pin: ${e.message}"
            }
        }
    }

    fun allowDomains(hostnames: Set<String>) {
        val hosts = hostnames.map { it.lowercase() }
        _blockedHostnames.update { it - hosts.toSet() }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                hosts.forEach { host ->
                    repository.addRule(UserRule(hostname = host, type = RuleType.ALLOW))
                    blocklist.removeDomain(host)
                }
                val method = prefs.blockMethod.first()
                if (method == BlockMethod.ROOT_HOSTS) {
                    hosts.forEach { rootUtil.removeHostEntry(it) }
                }
            } catch (e: Exception) {
                Log.e("LogsViewModel", "Failed to allow domains", e)
                _error.value = "Failed to allow domains: ${e.message}"
            }
        }
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun LogsScreen(viewModel: LogsViewModel = hiltViewModel(), onBack: (() -> Unit)? = null) {
    val logs by viewModel.logs.collectAsStateWithLifecycle()
    val query by viewModel.searchQuery.collectAsStateWithLifecycle()
    val blockedFilter by viewModel.showBlocked.collectAsStateWithLifecycle()
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

    // Query type filter
    var queryTypeFilter by remember { mutableStateOf<String?>(null) }

    val deduped = remember(logs, query, blockedFilter, blockedSet, queryTypeFilter) {
        logs
            .groupBy { it.hostname.lowercase() }
            .map { (hostname, entries) ->
                val isBlocked = hostname in blockedSet || entries.any { it.blocked }
                val latest = entries.maxByOrNull { it.timestamp }
                DedupedLogEntry(
                    hostname = hostname,
                    blocked = isBlocked,
                    hitCount = entries.size,
                    latestTimestamp = entries.maxOf { it.timestamp },
                    appLabel = entries.firstOrNull { it.appLabel.isNotEmpty() }?.appLabel ?: "",
                    appPackage = entries.firstOrNull { it.appPackage.isNotEmpty() }?.appPackage ?: "",
                    queryType = latest?.queryType ?: "A",
                    responseTimeMs = latest?.responseTimeMs ?: 0,
                    upstreamServer = latest?.upstreamServer ?: "",
                    cnameChain = latest?.cnameChain ?: "",
                    resolvedIps = latest?.resolvedIps ?: "",
                    decisionReason = latest?.decisionReason ?: "",
                    decisionSource = latest?.decisionSource ?: "",
                    matchedValue = latest?.matchedValue ?: "",
                    decisionPrecedence = latest?.decisionPrecedence ?: ""
                )
            }
            .filter { entry ->
                (query.isBlank() || entry.hostname.contains(query, ignoreCase = true) || entry.appPackage.contains(query, ignoreCase = true)) &&
                (blockedFilter == null || entry.blocked == blockedFilter) &&
                (queryTypeFilter == null || entry.queryType.equals(queryTypeFilter, ignoreCase = true))
            }
            .sortedByDescending { it.latestTimestamp }
    }

    val totalDomains = remember(logs) { logs.map { it.hostname.lowercase() }.distinct().size }
    val blockedCount = remember(deduped) { deduped.count { it.blocked } }

    Column(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = if (onBack != null) 8.dp else 20.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (onBack != null) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = TextPrimary)
                    }
                }
                Column {
                    Text(
                        "DNS Logs",
                        style = MaterialTheme.typography.headlineMedium,
                        color = TextPrimary,
                        modifier = Modifier.accessibilityHeading()
                    )
                    Text(
                        "$totalDomains domains \u2022 $blockedCount blocked \u2022 ${logs.size} queries",
                        color = TextSecondary, fontSize = 12.sp
                    )
                }
            }
            IconButton(onClick = {
                multiSelectMode = !multiSelectMode
                if (!multiSelectMode) selectedHostnames = emptySet()
            }, modifier = Modifier.accessibilityToggle("DNS log multi-select mode", multiSelectMode)) {
                Icon(
                    if (multiSelectMode) Icons.Filled.Close else Icons.Filled.Checklist,
                    if (multiSelectMode) "Exit multi-select" else "Enter multi-select",
                    tint = if (multiSelectMode) Teal else TextDim
                )
            }
            IconButton(
                onClick = { showClearLogsDialog = true },
                enabled = logs.isNotEmpty(),
                modifier = Modifier.accessibilityAction("Clear DNS logs", logs.isNotEmpty())
            ) {
                Icon(
                    Icons.Filled.DeleteSweep,
                    "Clear DNS logs",
                    tint = if (logs.isNotEmpty()) TextDim else TextDim.copy(alpha = 0.35f)
                )
            }
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
            Box(
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    color = Teal,
                    modifier = Modifier.size(24.dp).accessibilityLiveRegion("Loading DNS logs"),
                    strokeWidth = 2.dp
                )
            }
        }

        // Multi-select action bar
        if (multiSelectMode && selectedHostnames.isNotEmpty()) {
            Surface(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                shape = RoundedCornerShape(10.dp),
                color = Surface2
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        "${selectedHostnames.size} selected",
                        color = Teal, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f)
                    )
                    Surface(
                        onClick = {
                            viewModel.blockDomains(selectedHostnames)
                            selectedHostnames = emptySet()
                            multiSelectMode = false
                        },
                        shape = RoundedCornerShape(8.dp),
                        color = Red.copy(alpha = 0.12f)
                    ) {
                        Row(modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
                            Icon(Icons.Filled.Block, null, tint = Red, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Block All", color = Red, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                    Surface(
                        onClick = {
                            viewModel.allowDomains(selectedHostnames)
                            selectedHostnames = emptySet()
                            multiSelectMode = false
                        },
                        shape = RoundedCornerShape(8.dp),
                        color = Green.copy(alpha = 0.12f)
                    ) {
                        Row(modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
                            Icon(Icons.Filled.CheckCircle, null, tint = Green, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Allow All", color = Green, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
        }

        // Search
        OutlinedTextField(
            value = query, onValueChange = { viewModel.setSearch(it) },
            placeholder = { Text("Search domains, apps...", color = TextDim) },
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
            LogFilter("Blocked", blockedFilter == true) { viewModel.setFilter(true) }
            LogFilter("Allowed", blockedFilter == false) { viewModel.setFilter(false) }
        }

        Spacer(Modifier.height(4.dp))
        Row(
            modifier = Modifier.padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            val types = listOf(null to "All Types", "A" to "A", "AAAA" to "AAAA", "CNAME" to "CNAME", "MX" to "MX", "TXT" to "TXT")
            types.forEach { (type, label) ->
                val selected = queryTypeFilter == type
                Surface(
                    onClick = { queryTypeFilter = type },
                    shape = RoundedCornerShape(6.dp),
                    color = if (selected) Blue.copy(alpha = 0.12f) else Surface2
                ) {
                    Text(
                        label, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        color = if (selected) Blue else TextDim, fontSize = 10.sp, fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        val hasActiveFilters = query.isNotBlank() || blockedFilter != null || queryTypeFilter != null
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
                        "No captured query matches the current search and filter combination."
                    } else {
                        "Captured DNS queries will appear here with verdicts, apps, timing, and rule actions."
                    },
                    accent = if (hasActiveFilters) Blue else Teal,
                    primaryActionLabel = if (hasActiveFilters) "Clear filters" else null,
                    onPrimaryAction = if (hasActiveFilters) {
                        {
                            viewModel.setSearch("")
                            viewModel.setFilter(null)
                            queryTypeFilter = null
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
                                    .size(28.dp)
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
                geoIpLookup = viewModel.geoIpLookup
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
private fun LogFilter(label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(8.dp),
        color = if (selected) Teal.copy(alpha = 0.12f) else Surface2,
        modifier = Modifier
            .heightIn(min = 34.dp)
            .accessibilitySelection("$label DNS log filter", selected)
    ) {
        Text(
            label,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            color = if (selected) Teal else TextDim,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
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
                            textDecoration = if (blocked) TextDecoration.LineThrough else TextDecoration.None
                        )
                        Spacer(Modifier.height(2.dp))
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            if (entry.appLabel.isNotEmpty()) {
                                Text(entry.appLabel, color = TextDim, fontSize = 10.sp, lineHeight = 13.sp)
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
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (entry.appPackage.isNotEmpty()) {
                            Text(
                                entry.appPackage, color = TextDim, fontSize = 10.sp,
                                modifier = Modifier.weight(1f), fontFamily = FontFamily.Monospace
                            )
                        } else {
                            Spacer(Modifier.weight(1f))
                        }

                        // Detail button
                        Surface(
                            onClick = onTap,
                            shape = RoundedCornerShape(8.dp),
                            color = Blue.copy(alpha = 0.1f)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Filled.Info, null, tint = Blue, modifier = Modifier.size(14.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Details", color = Blue, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                        Spacer(Modifier.width(6.dp))

                        if (!blocked) {
                            Surface(
                                onClick = onBlock,
                                shape = RoundedCornerShape(8.dp),
                                color = Red.copy(alpha = 0.1f)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Filled.Block, null, tint = Red, modifier = Modifier.size(14.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Block", color = Red, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                }
                            }
                        } else {
                            Surface(
                                onClick = onAllow,
                                shape = RoundedCornerShape(8.dp),
                                color = Green.copy(alpha = 0.1f)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Filled.CheckCircle, null, tint = Green, modifier = Modifier.size(14.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Allow", color = Green, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                }
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
private fun QueryDetailSheet(entry: DedupedLogEntry, onDismiss: () -> Unit, isPinned: Boolean = false, onTogglePin: () -> Unit = {}, onTemporaryAllow: (Int) -> Unit = {}, onBlock: () -> Unit = {}, onAllow: () -> Unit = {}, geoIpLookup: GeoIpLookup? = null) {
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
            IconButton(onClick = onTogglePin, modifier = Modifier.size(32.dp)) {
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
            // GeoIP lookup for resolved IPs
            var geoResults by remember { mutableStateOf<List<GeoIpLookup.GeoInfo>>(emptyList()) }
            if (geoIpLookup != null) {
                LaunchedEffect(entry.resolvedIps) {
                    geoResults = geoIpLookup.lookupAll(ips)
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
                    android.net.Uri.parse("https://www.virustotal.com/gui/domain/${entry.hostname}")
                )
                context.startActivity(intent)
            }
            ReputationButton("URLhaus", Red) {
                val intent = android.content.Intent(
                    android.content.Intent.ACTION_VIEW,
                    android.net.Uri.parse("https://urlhaus.abuse.ch/browse.php?search=${entry.hostname}")
                )
                context.startActivity(intent)
            }
            ReputationButton("Whois", Teal) {
                val intent = android.content.Intent(
                    android.content.Intent.ACTION_VIEW,
                    android.net.Uri.parse("https://who.is/whois/${entry.hostname}")
                )
                context.startActivity(intent)
            }
        }
    }
}

@Composable
private fun ReputationButton(label: String, color: Color, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(8.dp),
        color = color.copy(alpha = 0.1f)
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
            maxLines = 2
        )
    }
}
