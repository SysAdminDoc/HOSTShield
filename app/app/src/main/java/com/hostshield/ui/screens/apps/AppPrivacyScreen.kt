package com.hostshield.ui.screens.apps

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.hostshield.ui.accessibility.accessibilityAction
import com.hostshield.ui.accessibility.accessibilityHeading
import com.hostshield.ui.accessibility.accessibilityLiveRegion
import com.hostshield.ui.screens.home.GlassCard
import com.hostshield.ui.theme.*
import com.hostshield.util.AppPrivacyScorer
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AppPrivacyState(
    val isLoading: Boolean = false,
    val reports: List<AppPrivacyScorer.AppReport> = emptyList(),
    val averageScore: Int = 0,
    val worstApps: Int = 0,
    val totalTrackerSdks: Int = 0
)

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

@Composable
fun AppPrivacyScreen(
    viewModel: AppPrivacyViewModel = hiltViewModel(),
    onBack: () -> Unit
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Column(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = TextPrimary) }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "App Privacy Report",
                    style = MaterialTheme.typography.titleLarge,
                    color = TextPrimary,
                    modifier = Modifier.accessibilityHeading()
                )
                Text("Privacy grade for each app based on DNS behavior", color = TextDim, fontSize = 11.sp)
            }
            IconButton(
                onClick = { viewModel.loadReports() },
                enabled = !state.isLoading,
                modifier = Modifier.accessibilityAction("Refresh app privacy reports", !state.isLoading)
            ) {
                Icon(Icons.Filled.Refresh, "Refresh", tint = Teal)
            }
        }

        if (state.isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(
                    color = Teal,
                    modifier = Modifier.accessibilityLiveRegion("Loading app privacy reports")
                )
            }
            return
        }

        LazyColumn(
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Summary
            if (state.reports.isNotEmpty()) {
                item {
                    GlassCard(modifier = Modifier.fillMaxWidth()) {
                        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            val color = gradeColor(if (state.averageScore >= 75) "B" else if (state.averageScore >= 50) "C" else "D")
                            Box(
                                modifier = Modifier.size(48.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(
                                    progress = { state.averageScore / 100f },
                                    modifier = Modifier.size(48.dp),
                                    color = color, trackColor = Surface3, strokeWidth = 4.dp
                                )
                                Text("${state.averageScore}", color = color, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            }
                            Spacer(Modifier.width(16.dp))
                            Column {
                                Text("Average Privacy Score", color = TextPrimary, fontWeight = FontWeight.SemiBold)
                                Text("${state.reports.size} apps analyzed, ${state.worstApps} need attention, ${state.totalTrackerSdks} tracker SDKs",
                                    color = TextDim, fontSize = 11.sp)
                            }
                        }
                    }
                }
            }

            // App reports
            items(state.reports) { report ->
                AppReportCard(report)
            }

            if (state.reports.isEmpty()) {
                item {
                    GlassCard(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(32.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Filled.PrivacyTip, null, tint = TextDim, modifier = Modifier.size(40.dp))
                            Spacer(Modifier.height(12.dp))
                            Text("No app data yet", color = TextSecondary, fontSize = 13.sp)
                            Text("DNS logs are needed to analyze app behavior", color = TextDim, fontSize = 11.sp)
                        }
                    }
                }
            }

            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun AppReportCard(report: AppPrivacyScorer.AppReport) {
    var expanded by remember { mutableStateOf(false) }
    val color = gradeColor(report.privacyGrade)

    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth()
                    .let { if (report.insights.isNotEmpty()) it.clip(RoundedCornerShape(8.dp)).let { m ->
                        @Suppress("DEPRECATION")
                        m
                    } else it },
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Grade badge
                Surface(
                    shape = CircleShape,
                    color = color.copy(alpha = 0.15f),
                    modifier = Modifier.size(36.dp)
                ) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                        Text(report.privacyGrade, color = color, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    }
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(report.appLabel.ifEmpty { report.packageName }, color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    Text("${report.totalQueries} queries, ${report.blockedQueries} blocked (${(report.blockRate * 100).toInt()}%)",
                        color = TextDim, fontSize = 10.sp)
                }
                IconButton(
                    onClick = { expanded = !expanded },
                    modifier = Modifier.size(40.dp).accessibilityAction(
                        if (expanded) "Collapse privacy report for ${report.appLabel.ifEmpty { report.packageName }}"
                        else "Expand privacy report for ${report.appLabel.ifEmpty { report.packageName }}"
                    )
                ) {
                    Icon(
                        if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                        if (expanded) "Collapse report" else "Expand report",
                        tint = TextDim,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            if (expanded) {
                Spacer(Modifier.height(10.dp))
                // Insights
                report.insights.forEach { insight ->
                    Row(modifier = Modifier.padding(vertical = 2.dp)) {
                        Text("  -  ", color = TextDim, fontSize = 11.sp)
                        Text(insight, color = TextSecondary, fontSize = 11.sp)
                    }
                }
                // Embedded tracker SDKs
                if (report.embeddedTrackers.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Text("Embedded SDKs:", color = TextDim, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                    report.embeddedTrackers.forEach { sdk ->
                        Row(modifier = Modifier.padding(start = 8.dp, top = 1.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(5.dp)
                                    .clip(CircleShape)
                                    .background(sdkCategoryColor(sdk.category).copy(alpha = 0.6f))
                            )
                            Spacer(Modifier.width(5.dp))
                            Text(sdk.name, color = sdkCategoryColor(sdk.category).copy(alpha = 0.8f),
                                fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                            Spacer(Modifier.width(6.dp))
                            Text(sdk.category, color = TextDim, fontSize = 9.sp)
                        }
                    }
                }
                // Top blocked domains
                if (report.trackerDomains.isNotEmpty()) {
                    Spacer(Modifier.height(6.dp))
                    Text("Top blocked:", color = TextDim, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                    report.trackerDomains.forEach { domain ->
                        Text(domain, color = Red.copy(alpha = 0.7f), fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace, modifier = Modifier.padding(start = 8.dp))
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text(report.packageName, color = TextDim, fontSize = 9.sp, fontFamily = FontFamily.Monospace)
            }
        }
    }
}

private fun gradeColor(grade: String): Color = when (grade) {
    "A" -> Green
    "B" -> Teal
    "C" -> Yellow
    "D" -> Peach
    "F" -> Red
    else -> TextDim
}

private fun sdkCategoryColor(category: String): Color = when (category) {
    "Advertising" -> Red
    "Analytics" -> Peach
    "Crash" -> Yellow
    "Social" -> Mauve
    "Location" -> Blue
    "Profiling" -> Flamingo
    else -> TextSecondary
}
