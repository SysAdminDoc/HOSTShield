package com.hostshield.ui.screens.logs

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.hostshield.data.database.ConnectionLogDao
import com.hostshield.data.database.FirewallTopApp
import com.hostshield.data.model.ConnectionLogEntry
import com.hostshield.service.NflogReader
import com.hostshield.ui.theme.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject

@HiltViewModel
class ConnectionLogViewModel @Inject constructor(
    private val connectionLogDao: ConnectionLogDao,
    private val nflogReader: NflogReader
) : ViewModel() {
    val recentLogs: StateFlow<List<ConnectionLogEntry>> = connectionLogDao.getRecentLogs(500)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val blockedCount: StateFlow<Int> = connectionLogDao.getTotalBlockedCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val topBlockedApps: StateFlow<List<FirewallTopApp>> =
        connectionLogDao.getTopBlockedApps(
            since = System.currentTimeMillis() - 86_400_000L,
            limit = 10
        ).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val isReading: StateFlow<Boolean> = nflogReader.isRunning
    val liveCount: StateFlow<Int> = nflogReader.liveBlockCount

    private val _tab = MutableStateFlow(ConnLogTab.LIVE)
    val tab = _tab.asStateFlow()

    fun setTab(t: ConnLogTab) { _tab.value = t }

    fun clearLogs() {
        viewModelScope.launch { connectionLogDao.deleteAll() }
    }
}

enum class ConnLogTab { LIVE, TOP_APPS }

@Composable
fun ConnectionLogScreen(
    viewModel: ConnectionLogViewModel = hiltViewModel(),
    onBack: () -> Unit
) {
    val logs by viewModel.recentLogs.collectAsStateWithLifecycle()
    val blockedCount by viewModel.blockedCount.collectAsStateWithLifecycle()
    val topApps by viewModel.topBlockedApps.collectAsStateWithLifecycle()
    val isReading by viewModel.isReading.collectAsStateWithLifecycle()
    val liveCount by viewModel.liveCount.collectAsStateWithLifecycle()
    val tab by viewModel.tab.collectAsStateWithLifecycle()
    val timeFmt = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }

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
                Text("Connection Log", style = MaterialTheme.typography.titleLarge, color = TextPrimary)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier.size(6.dp).clip(CircleShape)
                            .background(if (isReading) Green else Red)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        if (isReading) "$liveCount blocked connections" else "NFLOG reader inactive",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isReading) TextSecondary else Red
                    )
                }
            }
            IconButton(onClick = { viewModel.clearLogs() }) {
                Icon(Icons.Filled.DeleteSweep, "Clear", tint = Red)
            }
        }

        // Stats bar
        Surface(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            shape = RoundedCornerShape(10.dp),
            color = Surface2
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                StatItem("Total Blocked", blockedCount.toString(), Red)
                StatItem("Today", logs.count {
                    it.timestamp > System.currentTimeMillis() - 86_400_000L
                }.toString(), Teal)
                StatItem("Apps", topApps.size.toString(), Blue)
            }
        }

        Spacer(Modifier.height(8.dp))

        // Tab bar
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            TabPill("Live Log", tab == ConnLogTab.LIVE, Teal) { viewModel.setTab(ConnLogTab.LIVE) }
            TabPill("Top Blocked Apps", tab == ConnLogTab.TOP_APPS, Red) { viewModel.setTab(ConnLogTab.TOP_APPS) }
        }

        Spacer(Modifier.height(8.dp))

        when (tab) {
            ConnLogTab.LIVE -> {
                if (logs.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Filled.Shield, null, tint = TextDim, modifier = Modifier.size(48.dp))
                            Spacer(Modifier.height(8.dp))
                            Text("No blocked connections yet", color = TextDim)
                            Text("Apply iptables rules from Firewall > Network tab", color = TextDim, fontSize = 11.sp)
                        }
                    }
                } else {
                    LazyColumn(contentPadding = PaddingValues(horizontal = 16.dp)) {
                        items(logs, key = { it.id }) { entry ->
                            ConnectionLogRow(entry, timeFmt)
                            HorizontalDivider(color = Surface2.copy(alpha = 0.3f))
                        }
                    }
                }
            }
            ConnLogTab.TOP_APPS -> {
                LazyColumn(contentPadding = PaddingValues(horizontal = 16.dp)) {
                    items(topApps, key = { it.uid }) { app ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier.size(8.dp).clip(CircleShape).background(Red)
                            )
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    app.appLabel.ifBlank { app.packageName },
                                    color = TextPrimary, fontWeight = FontWeight.Medium,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis
                                )
                                Text("UID ${app.uid}", color = TextDim, fontSize = 10.sp)
                            }
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = Red.copy(alpha = 0.12f)
                            ) {
                                Text(
                                    "${app.cnt} blocked",
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                    color = Red, fontSize = 11.sp, fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                        HorizontalDivider(color = Surface2.copy(alpha = 0.3f))
                    }
                }
            }
        }
    }
}

@Composable
private fun ConnectionLogRow(entry: ConnectionLogEntry, timeFmt: SimpleDateFormat) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Protocol badge
        Surface(
            shape = RoundedCornerShape(4.dp),
            color = when (entry.protocol) {
                "TCP" -> Blue.copy(alpha = 0.12f)
                "UDP" -> Teal.copy(alpha = 0.12f)
                else -> Surface3
            }
        ) {
            Text(
                entry.protocol,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                color = when (entry.protocol) {
                    "TCP" -> Blue
                    "UDP" -> Teal
                    else -> TextDim
                },
                fontSize = 8.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace
            )
        }

        Spacer(Modifier.width(8.dp))

        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    entry.destination,
                    color = TextPrimary, fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (entry.port > 0) {
                    Text(
                        ":${entry.port}",
                        color = Teal, fontSize = 12.sp, fontFamily = FontFamily.Monospace
                    )
                }
            }
            Row {
                Text(
                    entry.appLabel.ifBlank { entry.packageName.ifBlank { "UID ${entry.uid}" } },
                    color = TextDim, fontSize = 10.sp,
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
                if (entry.interfaceName.isNotBlank()) {
                    Text(" via ${entry.interfaceName}", color = TextDim.copy(alpha = 0.5f), fontSize = 10.sp)
                }
            }
        }

        Spacer(Modifier.width(8.dp))

        Text(
            timeFmt.format(Date(entry.timestamp)),
            color = TextDim.copy(alpha = 0.6f),
            fontSize = 9.sp, fontFamily = FontFamily.Monospace
        )
    }
}

@Composable
private fun StatItem(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, color = color, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Text(label, color = TextDim, fontSize = 9.sp)
    }
}

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
