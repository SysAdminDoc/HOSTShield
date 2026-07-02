package com.hostshield.ui.screens.settings

import androidx.compose.runtime.*
import androidx.compose.runtime.mutableIntStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hostshield.util.CrashReport
import com.hostshield.util.CrashReporter
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

@HiltViewModel
class CrashReporterViewModel @Inject constructor(
    private val crashReporter: CrashReporter,
) : ViewModel() {

    var reports by mutableStateOf<List<CrashReport>>(emptyList())
        private set
    var isLoading by mutableStateOf(true)
        private set

    init { refresh() }

    fun refresh() {
        viewModelScope.launch(Dispatchers.IO) {
            isLoading = true
            reports = crashReporter.getCrashReports()
            isLoading = false
        }
    }

    fun clearAll() {
        viewModelScope.launch(Dispatchers.IO) {
            crashReporter.clearCrashReports()
            reports = emptyList()
        }
    }
}
