package com.hostshield.ui.screens.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hostshield.BuildConfig
import com.hostshield.data.database.AutomationAuditDao
import com.hostshield.data.model.AutomationAuditEntry
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import javax.inject.Inject
import java.text.SimpleDateFormat
import java.util.*

@HiltViewModel
class AutomationAuditViewModel @Inject constructor(
    private val auditDao: AutomationAuditDao
) : ViewModel() {
    val entries: StateFlow<List<AutomationAuditEntry>> = auditDao.getRecent(200)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
}
