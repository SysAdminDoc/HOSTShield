package com.hostshield.ui.screens.logs

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hostshield.data.database.AppDomainStat
import com.hostshield.data.database.DnsLogDao
import com.hostshield.data.model.DnsLogEntry
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import javax.inject.Inject
import java.text.SimpleDateFormat
import java.util.*

@HiltViewModel
class AppLogsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val dnsLogDao: DnsLogDao
) : ViewModel() {
    val packageName: String = savedStateHandle["pkg"] ?: ""

    val recentLogs: StateFlow<List<DnsLogEntry>> = dnsLogDao.getLogsForApp(packageName, 200)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val domainStats: StateFlow<List<AppDomainStat>> = dnsLogDao.getDomainsForApp(packageName, 50)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
}
