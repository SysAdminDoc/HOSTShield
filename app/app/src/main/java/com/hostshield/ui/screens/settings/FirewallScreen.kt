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
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.hostshield.data.database.FirewallRuleDao
import com.hostshield.data.model.FirewallRule
import com.hostshield.data.preferences.AppPreferences
import com.hostshield.service.IptablesManager
import com.hostshield.service.NflogReader
import com.hostshield.ui.theme.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

// HostShield v1.6.0 - Per-App Firewall (DNS + Network)
//
// Two layers:
// 1. DNS Firewall: NXDOMAIN all queries for blocked apps (VPN + root mode)
// 2. Network Firewall: iptables per-app WiFi/Mobile control (root only)

@HiltViewModel
class FirewallViewModel @Inject constructor(
    private val prefs: AppPreferences,
    private val firewallRuleDao: FirewallRuleDao,
    private val iptablesManager: IptablesManager,
    private val nflogReader: NflogReader
) : ViewModel() {
    // DNS-level blocking (preferences-based, works in VPN + root)
    val blockedApps: StateFlow<Set<String>> = prefs.blockedApps
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())
    val excludedApps: StateFlow<Set<String>> = prefs.excludedApps
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    // Network-level firewall rules (Room, iptables)
    val firewallRules: StateFlow<List<FirewallRule>> = firewallRuleDao.getAllRules()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val blockedRuleCount: StateFlow<Int> = firewallRuleDao.getBlockedCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val iptablesActive: StateFlow<Boolean> = iptablesManager.isActive
    val iptablesError: StateFlow<String> = iptablesManager.lastError

    private val _searchQuery = MutableStateFlow("")
    val searchQuery = _searchQuery.asStateFlow()
    private val _showSystem = MutableStateFlow(false)
    val showSystem = _showSystem.asStateFlow()
    private val _filter = MutableStateFlow(FirewallFilter.ALL)
    val filter = _filter.asStateFlow()
    private val _tab = MutableStateFlow(FirewallTab.DNS)
    val tab = _tab.asStateFlow()
    private val _isSyncing = MutableStateFlow(false)
    val isSyncing = _isSyncing.asStateFlow()

    fun setSearchQuery(q: String) { _searchQuery.value = q }
    fun toggleShowSystem() { _showSystem.update { !it } }
    fun setFilter(f: FirewallFilter) { _filter.value = f }
    fun setTab(t: FirewallTab) { _tab.value = t }

    // ---- DNS Firewall -------------------------------------------

    fun toggleDnsBlock(packageName: String) {
        viewModelScope.launch {
            val current = blockedApps.value.toMutableSet()
            if (packageName in current) current.remove(packageName) else current.add(packageName)
            prefs.setBlockedApps(current)
        }
    }

    fun unblockAllDns() {
        viewModelScope.launch { prefs.setBlockedApps(emptySet()) }
    }

    // ---- Network Firewall (iptables) ----------------------------

    fun syncApps() {
        viewModelScope.launch(Dispatchers.IO) {
            _isSyncing.value = true
            iptablesManager.syncInstalledApps()
            _isSyncing.value = false
        }
    }

    fun toggleWifi(uid: Int, allowed: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            firewallRuleDao.setWifi(uid, allowed)
        }
    }

    fun toggleMobile(uid: Int, allowed: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            firewallRuleDao.setMobile(uid, allowed)
        }
    }

    fun toggleVpn(uid: Int, allowed: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            firewallRuleDao.setVpn(uid, allowed)
        }
    }

    fun blockAllNetwork(uid: Int) {
        viewModelScope.launch(Dispatchers.IO) { firewallRuleDao.blockAll(uid) }
    }

    fun allowAllNetwork(uid: Int) {
        viewModelScope.launch(Dispatchers.IO) { firewallRuleDao.allowAll(uid) }
    }

    fun resetAllNetwork() {
        viewModelScope.launch(Dispatchers.IO) { firewallRuleDao.resetAll() }
    }

    fun applyIptables() {
        viewModelScope.launch(Dispatchers.IO) {
            iptablesManager.applyRules()
            nflogReader.start()
        }
    }

    fun clearIptables() {
        viewModelScope.launch(Dispatchers.IO) {
            nflogReader.stop()
            iptablesManager.clearRules()
        }
    }

    // Diagnostic dump
    private val _diagnosticOutput = MutableStateFlow("")
    val diagnosticOutput: StateFlow<String> = _diagnosticOutput.asStateFlow()
    private val _isDiagnosing = MutableStateFlow(false)
    val isDiagnosing: StateFlow<Boolean> = _isDiagnosing.asStateFlow()

    fun runDiagnostic() {
        viewModelScope.launch(Dispatchers.IO) {
            _isDiagnosing.value = true
            _diagnosticOutput.value = iptablesManager.dumpFullDiagnostic()
            _isDiagnosing.value = false
        }
    }

    fun exportScript(callback: (String) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val script = iptablesManager.exportAsScript()
            callback(script)
        }
    }

    // Bulk operations
    fun blockAllWifi() {
        viewModelScope.launch(Dispatchers.IO) {
            val rules = firewallRuleDao.getAllRulesList()
            rules.filter { !it.isSystem }.forEach {
                firewallRuleDao.setWifi(it.uid, false)
            }
        }
    }

    fun blockAllMobile() {
        viewModelScope.launch(Dispatchers.IO) {
            val rules = firewallRuleDao.getAllRulesList()
            rules.filter { !it.isSystem }.forEach {
                firewallRuleDao.setMobile(it.uid, false)
            }
        }
    }

    init { syncApps() }
}

enum class FirewallFilter { ALL, BLOCKED, UNBLOCKED }
enum class FirewallTab { DNS, NETWORK }

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
    val diagOutput by viewModel.diagnosticOutput.collectAsStateWithLifecycle()
    val isDiagnosing by viewModel.isDiagnosing.collectAsStateWithLifecycle()

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
        // Header
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Filled.ArrowBack, "Back", tint = TextPrimary)
            }
            Column(modifier = Modifier.weight(1f)) {
                Text("Firewall", style = MaterialTheme.typography.titleLarge, color = TextPrimary)
                Text(
                    when (tab) {
                        FirewallTab.DNS -> "${blocked.size} DNS-blocked"
                        FirewallTab.NETWORK -> if (iptablesActive) "$blockedRuleCount rules active" else "iptables inactive"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (tab == FirewallTab.DNS) Red else if (iptablesActive) Teal else TextDim
                )
            }
            IconButton(onClick = { viewModel.toggleShowSystem() }) {
                Icon(
                    if (showSystem) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                    "System apps", tint = if (showSystem) Teal else TextDim
                )
            }
        }

        // Tab selector
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TabPill("DNS Block", tab == FirewallTab.DNS, Red) { viewModel.setTab(FirewallTab.DNS) }
            TabPill("Network", tab == FirewallTab.NETWORK, Teal) { viewModel.setTab(FirewallTab.NETWORK) }
        }

        Spacer(Modifier.height(8.dp))

        when (tab) {
            FirewallTab.DNS -> DnsFirewallTab(viewModel, allApps, blocked, excluded, searchQuery, showSystem, filter)
            FirewallTab.NETWORK -> NetworkFirewallTab(viewModel, firewallRules, searchQuery, showSystem, iptablesActive, iptablesError, isSyncing, diagOutput, isDiagnosing)
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
            modifier = Modifier.weight(1f),
            colors = ButtonDefaults.buttonColors(containerColor = Teal),
            shape = RoundedCornerShape(10.dp)
        ) {
            Icon(Icons.Filled.PlayArrow, null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(4.dp))
            Text("Apply", fontSize = 12.sp)
        }
        OutlinedButton(
            onClick = { viewModel.clearIptables() },
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
                CircularProgressIndicator(Modifier.size(12.dp), color = Blue, strokeWidth = 1.5.dp)
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
        Text("WiFi", modifier = Modifier.width(48.dp), color = TextDim, fontSize = 10.sp, fontWeight = FontWeight.Bold,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        Text("Data", modifier = Modifier.width(48.dp), color = TextDim, fontSize = 10.sp, fontWeight = FontWeight.Bold,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center)
    }

    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = Surface3)

    val filtered = remember(rules, searchQuery, showSystem) {
        rules.filter { rule ->
            (showSystem || !rule.isSystem) &&
            (searchQuery.isBlank() || rule.appLabel.contains(searchQuery, true) || rule.packageName.contains(searchQuery, true))
        }
    }

    if (isSyncing) {
        Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = Teal, modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
        }
    }

    LazyColumn(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 2.dp)) {
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
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        Icons.Filled.Wifi,
                        "WiFi",
                        tint = if (rule.wifiAllowed) Green else Red,
                        modifier = Modifier.size(18.dp)
                    )
                }

                // Mobile data toggle
                IconButton(
                    onClick = { viewModel.toggleMobile(rule.uid, !rule.mobileAllowed) },
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        Icons.Filled.SignalCellularAlt,
                        "Mobile",
                        tint = if (rule.mobileAllowed) Green else Red,
                        modifier = Modifier.size(18.dp)
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
        color = if (selected) accent.copy(alpha = 0.15f) else Surface2
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
        color = if (selected) Red.copy(alpha = 0.12f) else Surface2
    ) {
        Text(
            label,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            color = if (selected) Red else TextDim,
            fontSize = 11.sp, fontWeight = FontWeight.SemiBold
        )
    }
}
