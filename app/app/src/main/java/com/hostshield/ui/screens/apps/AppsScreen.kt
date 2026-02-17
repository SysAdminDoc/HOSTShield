package com.hostshield.ui.screens.apps

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.hostshield.data.database.AppDomainStat
import com.hostshield.data.database.AppQueryStat
import com.hostshield.data.database.DnsLogDao
import com.hostshield.data.model.BlockMethod
import com.hostshield.data.model.RuleType
import com.hostshield.data.model.UserRule
import com.hostshield.data.preferences.AppPreferences
import com.hostshield.data.repository.HostShieldRepository
import com.hostshield.domain.BlocklistHolder
import com.hostshield.util.RootUtil
import com.hostshield.ui.screens.home.GlassCard
import com.hostshield.ui.theme.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

// HostShield v1.6.0 — Apps View

@HiltViewModel
class AppsViewModel @Inject constructor(
    private val dnsLogDao: DnsLogDao,
    private val repository: HostShieldRepository,
    private val blocklist: BlocklistHolder,
    private val prefs: AppPreferences,
    private val rootUtil: RootUtil
) : ViewModel() {
    val apps: StateFlow<List<AppQueryStat>> = dnsLogDao.getAllAppsWithCounts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _selectedApp = MutableStateFlow<String?>(null)
    val selectedApp = _selectedApp.asStateFlow()

    private val _appDomains = MutableStateFlow<List<AppDomainStat>>(emptyList())
    val appDomains = _appDomains.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery = _searchQuery.asStateFlow()

    // Track domains the user blocked during this session so the UI
    // updates immediately (the DB logs still show historic blocked=false)
    private val _locallyBlocked = MutableStateFlow<Set<String>>(emptySet())
    val locallyBlocked = _locallyBlocked.asStateFlow()

    // Cancel the previous domain-collection coroutine when switching apps
    // so two collectors don't race to update _appDomains ("flipping" bug)
    private var domainCollectionJob: kotlinx.coroutines.Job? = null

    fun setSearch(q: String) { _searchQuery.value = q }

    fun selectApp(pkg: String?) {
        _selectedApp.value = pkg
        // Cancel any running domain collection first
        domainCollectionJob?.cancel()
        domainCollectionJob = null

        if (pkg != null) {
            domainCollectionJob = viewModelScope.launch {
                dnsLogDao.getDomainsForApp(pkg).collect { _appDomains.value = it }
            }
        } else {
            _appDomains.value = emptyList()
        }
    }

    fun blockDomain(hostname: String) {
        val host = hostname.lowercase()
        // Immediately mark as blocked in the UI
        _locallyBlocked.update { it + host }
        viewModelScope.launch(Dispatchers.IO) {
            repository.addRule(UserRule(hostname = host, type = RuleType.BLOCK))
            blocklist.addDomain(host)
            // Root mode: also write directly to /etc/hosts
            val method = prefs.blockMethod.first()
            if (method == BlockMethod.ROOT_HOSTS) {
                rootUtil.appendHostEntry(host)
            }
        }
    }
}

@Composable
fun AppsScreen(viewModel: AppsViewModel = hiltViewModel(), onBack: () -> Unit = {}) {
    val apps by viewModel.apps.collectAsStateWithLifecycle()
    val selectedApp by viewModel.selectedApp.collectAsStateWithLifecycle()
    val appDomains by viewModel.appDomains.collectAsStateWithLifecycle()
    val query by viewModel.searchQuery.collectAsStateWithLifecycle()
    val locallyBlocked by viewModel.locallyBlocked.collectAsStateWithLifecycle()

    val filtered = remember(apps, query) {
        if (query.isBlank()) apps
        else apps.filter {
            it.appLabel.contains(query, ignoreCase = true) ||
            it.appPackage.contains(query, ignoreCase = true)
        }
    }

    Column(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = {
                if (selectedApp != null) viewModel.selectApp(null) else onBack()
            }) {
                Icon(Icons.Filled.ArrowBack, "Back", tint = TextPrimary)
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    if (selectedApp != null) apps.find { it.appPackage == selectedApp }?.appLabel ?: selectedApp ?: ""
                    else "App Activity",
                    style = MaterialTheme.typography.headlineMedium, color = TextPrimary
                )
                if (selectedApp == null) {
                    Text("${apps.size} apps tracked", color = TextSecondary, fontSize = 12.sp)
                } else {
                    Text(selectedApp ?: "", color = TextDim, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                }
            }
        }

        if (selectedApp == null) {
            // Search
            OutlinedTextField(
                value = query, onValueChange = { viewModel.setSearch(it) },
                placeholder = { Text("Search apps...", color = TextDim) },
                leadingIcon = { Icon(Icons.Filled.Search, null, tint = TextDim) },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                singleLine = true, shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Teal, unfocusedBorderColor = Surface3,
                    cursorColor = Teal, focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary
                )
            )

            Spacer(Modifier.height(8.dp))

            // App list
            if (filtered.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Filled.Apps, null, tint = TextDim, modifier = Modifier.size(48.dp))
                        Spacer(Modifier.height(12.dp))
                        Text("No app data yet", color = TextSecondary, fontSize = 14.sp)
                        Text("DNS queries will appear here as apps make requests", color = TextDim, fontSize = 12.sp)
                    }
                }
            } else {
                LazyColumn(
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

            LazyColumn(
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
            modifier = Modifier.clickable(onClick = onClick).padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // App icon placeholder
            Box(
                modifier = Modifier.size(38.dp).clip(RoundedCornerShape(10.dp))
                    .background(barColor.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.Apps, null, tint = barColor, modifier = Modifier.size(20.dp))
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
            Icon(Icons.Filled.ChevronRight, null, tint = TextDim, modifier = Modifier.size(18.dp))
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

            Column(modifier = Modifier.weight(1f).clickable { expanded = !expanded }.padding(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (domain.blocked) {
                        Icon(Icons.Filled.Block, null, tint = Red.copy(alpha = 0.7f), modifier = Modifier.size(14.dp))
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
                                Icon(Icons.Filled.Block, null, tint = Red, modifier = Modifier.size(14.dp))
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
