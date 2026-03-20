package com.hostshield.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import com.hostshield.data.database.AutomationAuditDao
import com.hostshield.data.model.AutomationAuditEntry
import com.hostshield.ui.screens.home.GlassCard
import com.hostshield.ui.theme.*
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

@Composable
fun AutomationAuditScreen(
    viewModel: AutomationAuditViewModel = hiltViewModel(),
    onBack: () -> Unit = {}
) {
    val entries by viewModel.entries.collectAsStateWithLifecycle()
    val dateFormat = remember { SimpleDateFormat("MM/dd HH:mm:ss", Locale.getDefault()) }

    Column(
        modifier = Modifier.fillMaxSize().background(Color.Black)
    ) {
        // Top bar
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = TextPrimary)
            }
            Text("Automation Audit Log", color = TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 18.sp)
            Spacer(Modifier.weight(1f))
            Text("${entries.size} entries", color = TextDim, fontSize = 12.sp, modifier = Modifier.padding(end = 16.dp))
        }

        if (entries.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Filled.ReceiptLong, null, tint = TextDim, modifier = Modifier.size(48.dp))
                    Spacer(Modifier.height(12.dp))
                    Text("No automation commands recorded", color = TextDim, fontSize = 14.sp)
                    Text("Commands from Tasker, ADB, or other apps appear here", color = TextDim, fontSize = 11.sp)
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                itemsIndexed(entries, key = { _, e -> e.id }) { _, entry ->
                    AuditEntryCard(entry, dateFormat)
                }
            }
        }
    }
}

@Composable
private fun AuditEntryCard(entry: AutomationAuditEntry, dateFormat: SimpleDateFormat) {
    val resultColor = when (entry.result) {
        "OK" -> Green
        "DENIED" -> Red
        "RATE_LIMITED" -> Peach
        "ERROR" -> Red
        else -> TextDim
    }
    val actionShort = entry.action.removePrefix("com.hostshield.ACTION_").removePrefix("com.hostshield.")

    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Result badge
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = resultColor.copy(alpha = 0.12f),
                modifier = Modifier.width(52.dp)
            ) {
                Text(
                    entry.result,
                    color = resultColor,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                    fontFamily = FontFamily.Monospace
                )
            }
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(actionShort, color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                Text(
                    "from ${entry.callerPackage} (uid ${entry.callerUid})",
                    color = TextDim, fontSize = 10.sp
                )
            }
            Text(
                dateFormat.format(Date(entry.timestamp)),
                color = TextDim, fontSize = 10.sp, fontFamily = FontFamily.Monospace
            )
        }
    }
}
