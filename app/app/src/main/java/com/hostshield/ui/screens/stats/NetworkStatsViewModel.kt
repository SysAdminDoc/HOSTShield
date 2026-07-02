package com.hostshield.ui.screens.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hostshield.service.NetworkStatsTracker
import com.hostshield.service.NetworkStatsTracker.AppNetStats
import com.hostshield.service.formatBytes
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class NetworkStatsViewModel @Inject constructor(
    val tracker: NetworkStatsTracker
) : ViewModel() {
    val appStats = tracker.appStats
    val totalRx = tracker.totalRx
    val totalTx = tracker.totalTx

    init { refresh() }

    fun refresh() {
        viewModelScope.launch { tracker.refresh() }
    }
}
