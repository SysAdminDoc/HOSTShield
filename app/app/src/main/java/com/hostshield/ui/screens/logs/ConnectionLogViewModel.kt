package com.hostshield.ui.screens.logs

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hostshield.data.database.ConnectionLogDao
import com.hostshield.data.database.FirewallTopApp
import com.hostshield.data.model.ConnectionLogEntry
import com.hostshield.service.NflogReader
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

    // Accurate 24-hour count straight from the DAO, not derived from the
    // 500-row recent-logs window (which capped the "today" tile on busy devices).
    val blockedTodayCount: StateFlow<Int> =
        connectionLogDao.getBlockedCountSince(System.currentTimeMillis() - 86_400_000L)
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
