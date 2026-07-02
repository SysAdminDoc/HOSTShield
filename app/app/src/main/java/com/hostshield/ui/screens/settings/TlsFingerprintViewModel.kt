package com.hostshield.ui.screens.settings

import androidx.compose.runtime.*
import androidx.compose.runtime.mutableIntStateOf
import androidx.lifecycle.ViewModel
import com.hostshield.util.TlsFingerprinter
import dagger.hilt.android.lifecycle.HiltViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

@HiltViewModel
class TlsFingerprintViewModel @Inject constructor(
    private val fingerprinter: TlsFingerprinter,
) : ViewModel() {

    var fingerprints by mutableStateOf<List<TlsFingerprinter.CapturedFingerprint>>(emptyList())
        private set
    var groupedByApp by mutableStateOf<Map<String, List<TlsFingerprinter.CapturedFingerprint>>>(emptyMap())
        private set
    var viewMode by mutableStateOf(ViewMode.TIMELINE)
        private set

    enum class ViewMode { TIMELINE, BY_APP }

    init { refresh() }

    fun refresh() {
        fingerprints = fingerprinter.getHistory()
        groupedByApp = fingerprinter.getByApp()
    }

    fun clear() {
        fingerprinter.clearHistory()
        fingerprints = emptyList()
        groupedByApp = emptyMap()
    }

    fun setMode(mode: ViewMode) { viewMode = mode }
}
