package com.hostshield.ui.screens.apps

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hostshield.ui.accessibility.accessibilityAction
import com.hostshield.ui.accessibility.accessibilityHeading
import com.hostshield.ui.accessibility.accessibilityLiveRegion
import com.hostshield.util.AppPrivacyScorer
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AppPrivacyViewModel @Inject constructor(
    private val scorer: AppPrivacyScorer
) : ViewModel() {
    private val _state = MutableStateFlow(AppPrivacyState())
    val state = _state.asStateFlow()

    init { loadReports() }

    fun loadReports() {
        viewModelScope.launch(Dispatchers.IO) {
            _state.update { it.copy(isLoading = true) }
            val reports = scorer.generateAllReports()
            val avg = if (reports.isNotEmpty()) reports.map { it.score }.average().toInt() else 0
            val worst = reports.count { it.privacyGrade == "F" || it.privacyGrade == "D" }
            val totalSdks = reports.sumOf { it.embeddedTrackers.size }
            _state.update { it.copy(isLoading = false, reports = reports, averageScore = avg, worstApps = worst, totalTrackerSdks = totalSdks) }
        }
    }
}
