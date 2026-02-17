package com.hostshield.ui.screens.logs

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
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
import com.hostshield.ui.screens.home.GlassCard
import com.hostshield.ui.theme.*
import com.hostshield.util.RootUtil
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject

// HostShield v1.6.0 — DNS Log

data class DedupedLogEntry(
    val hostname: String,
    val blocked: Boolean,
    val hitCount: Int,
    val latestTimestamp: Long,
    val appLabel: String,
    val appPackage: String
)

@HiltViewModel
class LogsViewModel @Inject constructor(
    private val repository: HostShieldRepository,
    private val blocklist: BlocklistHolder,
    private val rootUtil: RootUtil,
    private val prefs: AppPreferences
) : ViewModel() {
    val logs: StateFlow<List<DnsLogEntry>> = repository.getRecentLogs(2000)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _searchQuery = MutableStateFlow("")
    val searchQuery = _searchQuery.asStateFlow()
    private val _showBlocked = MutableStateFlow<Boolean?>(null)
    val showBlocked = _showBlocked.asStateFlow()

    // Authoritative set of blocked hostnames — loaded from DB + blocklist on init,
    // updated instantly on block/allow actions. Persists across sessions via DB.
    private val _blockedHostnames = MutableStateFlow<Set<String>>(emptySet())
    val blockedHostnames = _blockedHostnames.asStateFlow()

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
            val blockRules = repository.getEnabledRulesByType(RuleType.BLOCK)
            val allowRules = repository.getEnabledRulesByType(RuleType.ALLOW)
            val allowed = allowRules.map { it.hostname.lowercase() }.toSet()

            val all = mutableSetOf<String>()
            // From user block rules (these are always known)
            all.addAll(blockRules.map { it.hostname.lowercase() })
            // Remove explicit allows
            all.removeAll(allowed)

            _blockedHostnames.value = all
        }
    }

    fun setSearch(q: String) { _searchQuery.value = q }
    fun setFilter(blocked: Boolean?) { _showBlocked.value = blocked }

    fun blockDomain(hostname: String) {
        val host = hostname.lowercase()
        _blockedHostnames.update { it + host }

        viewModelScope.launch(Dispatchers.IO) {
            repository.addRule(UserRule(hostname = host, type = RuleType.BLOCK))
            blocklist.addDomain(host)
            val method = prefs.blockMethod.first()
            if (method == BlockMethod.ROOT_HOSTS) {
                rootUtil.appendHostEntry(host)
            }
        }
    }

    fun allowDomain(hostname: String) {
        val host = hostname.lowercase()
        _blockedHostnames.update { it - host }

        viewModelScope.launch(Dispatchers.IO) {
            repository.addRule(UserRule(hostname = host, type = RuleType.ALLOW))
            blocklist.removeDomain(host)
            val method = prefs.blockMethod.first()
            if (method == BlockMethod.ROOT_HOSTS) {
                rootUtil.removeHostEntry(host)
            }
        }
    }

    fun clearLogs() {
        viewModelScope.launch { repository.clearAllLogs() }
    }
}

@Composable
fun LogsScreen(viewModel: LogsViewModel = hiltViewModel(), onBack: (() -> Unit)? = null) {
    val logs by viewModel.logs.collectAsStateWithLifecycle()
    val query by viewModel.searchQuery.collectAsStateWithLifecycle()
    val blockedFilter by viewModel.showBlocked.collectAsStateWithLifecycle()
    val blockedSet by viewModel.blockedHostnames.collectAsStateWithLifecycle()

    val deduped = remember(logs, query, blockedFilter, blockedSet) {
        logs
            .groupBy { it.hostname.lowercase() }
            .map { (hostname, entries) ->
                val isBlocked = hostname in blockedSet || entries.any { it.blocked }
                DedupedLogEntry(
                    hostname = hostname,
                    blocked = isBlocked,
                    hitCount = entries.size,
                    latestTimestamp = entries.maxOf { it.timestamp },
                    appLabel = entries.firstOrNull { it.appLabel.isNotEmpty() }?.appLabel ?: "",
                    appPackage = entries.firstOrNull { it.appPackage.isNotEmpty() }?.appPackage ?: ""
                )
            }
            .filter { entry ->
                (query.isBlank() || entry.hostname.contains(query, ignoreCase = true) || entry.appPackage.contains(query, ignoreCase = true)) &&
                (blockedFilter == null || entry.blocked == blockedFilter)
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
                        Icon(Icons.Filled.ArrowBack, "Back", tint = TextPrimary)
                    }
                }
                Column {
                    Text("DNS Logs", style = MaterialTheme.typography.headlineMedium, color = TextPrimary)
                    Text(
                        "$totalDomains domains \u2022 $blockedCount blocked \u2022 ${logs.size} queries",
                        color = TextSecondary, fontSize = 12.sp
                    )
                }
            }
            IconButton(onClick = { viewModel.clearLogs() }) {
                Icon(Icons.Filled.DeleteSweep, "Clear", tint = TextDim)
            }
        }

        // Search
        OutlinedTextField(
            value = query, onValueChange = { viewModel.setSearch(it) },
            placeholder = { Text("Search domains, apps...", color = TextDim) },
            leadingIcon = { Icon(Icons.Filled.Search, null, tint = TextDim) },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
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

        Spacer(Modifier.height(8.dp))

        if (deduped.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Filled.Dns, null, tint = TextDim, modifier = Modifier.size(48.dp))
                    Spacer(Modifier.height(12.dp))
                    Text("No DNS logs yet", color = TextSecondary, fontSize = 14.sp)
                    Spacer(Modifier.height(4.dp))
                    Text("Logs populate as DNS queries are captured", color = TextDim, fontSize = 12.sp)
                }
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                items(deduped, key = { it.hostname }) { entry ->
                    LogItem(
                        entry = entry,
                        onBlock = { viewModel.blockDomain(entry.hostname) },
                        onAllow = { viewModel.allowDomain(entry.hostname) }
                    )
                }
                item { Spacer(Modifier.height(16.dp)) }
            }
        }
    }
}

@Composable
private fun LogFilter(label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(8.dp),
        color = if (selected) Teal.copy(alpha = 0.12f) else Surface2
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
private fun LogItem(entry: DedupedLogEntry, onBlock: () -> Unit, onAllow: () -> Unit) {
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
            .clip(RoundedCornerShape(14.dp))
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

            Column(
                modifier = Modifier
                    .weight(1f)
                    .clickable { expanded = !expanded }
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
                            fontWeight = if (blocked) FontWeight.SemiBold else FontWeight.Medium,
                            fontFamily = FontFamily.Monospace,
                            maxLines = 1,
                            textDecoration = if (blocked) TextDecoration.LineThrough else TextDecoration.None
                        )
                        Spacer(Modifier.height(2.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (entry.appLabel.isNotEmpty()) {
                                Text(entry.appLabel, color = TextDim, fontSize = 10.sp)
                            }
                            if (entry.hitCount > 1) {
                                Text("${entry.hitCount}x", color = TextDim, fontSize = 10.sp)
                            }
                            Text(formatTime(entry.latestTimestamp), color = TextDim, fontSize = 10.sp)
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
                                letterSpacing = 0.5.sp
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
