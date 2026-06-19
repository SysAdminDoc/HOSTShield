package com.hostshield.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hostshield.ui.components.ConfirmDestructiveDialog
import com.hostshield.ui.components.HostShieldActionIconButton
import com.hostshield.ui.components.HostShieldBackHeader
import com.hostshield.ui.components.HostShieldLoadingState
import com.hostshield.ui.screens.home.GlassCard
import com.hostshield.ui.theme.*
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

@Composable
fun CrashReporterScreen(
    onBack: () -> Unit,
    viewModel: CrashReporterViewModel = hiltViewModel(),
) {
    var expandedIndex by remember { mutableIntStateOf(-1) }
    var showClearReportsDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        HostShieldBackHeader(
            title = "Crash Reports",
            subtitle = if (viewModel.reports.isEmpty()) "No local crashes recorded" else "${viewModel.reports.size} local reports",
            onBack = onBack,
            horizontalPadding = 0.dp,
            actions = {
                HostShieldActionIconButton(
                    icon = Icons.Filled.Refresh,
                    contentDescription = "Refresh crash reports",
                    onClick = { viewModel.refresh() },
                    enabled = !viewModel.isLoading,
                )
                if (viewModel.reports.isNotEmpty()) {
                    HostShieldActionIconButton(
                        icon = Icons.Filled.DeleteSweep,
                        contentDescription = "Clear crash reports",
                        onClick = { showClearReportsDialog = true },
                        accent = Red,
                    )
                }
            }
        )

        if (viewModel.isLoading) {
            HostShieldLoadingState(
                title = "Reading local reports",
                message = "Crash reports stay on this device until you export or clear them.",
            )
            return@Column
        }

        // Summary
        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier.size(40.dp)
                        .background(
                            if (viewModel.reports.isEmpty()) Green.copy(alpha = 0.1f) else Red.copy(alpha = 0.1f),
                            RoundedCornerShape(10.dp),
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        if (viewModel.reports.isEmpty()) Icons.Filled.CheckCircle else Icons.Filled.BugReport,
                        null,
                        tint = if (viewModel.reports.isEmpty()) Green else Red,
                        modifier = Modifier.size(20.dp),
                    )
                }
                Spacer(Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        if (viewModel.reports.isEmpty()) "No crashes recorded"
                        else "${viewModel.reports.size} crash report${if (viewModel.reports.size != 1) "s" else ""}",
                        color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                    )
                    Text("Stored locally, never sent to any server", color = TextDim, fontSize = 11.sp)
                }
            }
        }

        // Crash reports
        viewModel.reports.forEachIndexed { index, report ->
            val isExpanded = expandedIndex == index
            val dateStr = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date(report.timestamp))
            val firstLine = report.stackTrace.lineSequence().firstOrNull() ?: "Unknown"

            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Surface(
                        onClick = { expandedIndex = if (isExpanded) -1 else index },
                        color = Color.Transparent,
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(firstLine.take(80), color = Red, fontSize = 12.sp, fontWeight = FontWeight.Medium, maxLines = 1)
                                Spacer(Modifier.height(2.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Text(dateStr, color = TextDim, fontSize = 10.sp)
                                    Text("SDK ${report.sdkVersion}", color = TextDim, fontSize = 10.sp)
                                    Text(report.deviceModel, color = TextDim, fontSize = 10.sp)
                                }
                            }
                            Icon(
                                if (isExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                                null, tint = TextDim, modifier = Modifier.size(20.dp),
                            )
                        }
                    }

                    if (isExpanded) {
                        Spacer(Modifier.height(10.dp))
                        HorizontalDivider(color = Surface3, thickness = 0.5.dp)
                        Spacer(Modifier.height(10.dp))

                        // Device info
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            InfoChip("App", report.appVersion)
                            InfoChip("Device", report.deviceModel)
                            InfoChip("Memory", "${report.freeMemoryMb}/${report.totalMemoryMb} MB")
                        }

                        Spacer(Modifier.height(10.dp))

                        // Stack trace
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Surface0,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                report.stackTrace.take(2000),
                                color = Red.copy(alpha = 0.8f),
                                fontSize = 9.sp,
                                lineHeight = 13.sp,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier.padding(10.dp),
                            )
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(24.dp))
    }

    if (showClearReportsDialog) {
        ConfirmDestructiveDialog(
            title = "Clear crash reports?",
            body = "This removes locally stored diagnostic reports from this device. It does not change protection settings or send anything externally.",
            confirmLabel = "Clear reports",
            onConfirm = {
                viewModel.clearAll()
                expandedIndex = -1
            },
            onDismiss = { showClearReportsDialog = false },
        )
    }
}

@Composable
private fun InfoChip(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, color = TextDim, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.sp)
        Text(value, color = TextSecondary, fontSize = 11.sp)
    }
}
