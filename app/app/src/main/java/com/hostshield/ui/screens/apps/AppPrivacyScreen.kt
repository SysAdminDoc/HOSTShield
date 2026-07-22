package com.hostshield.ui.screens.apps

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hostshield.ui.accessibility.accessibilityAction
import com.hostshield.ui.accessibility.accessibilityHeading
import com.hostshield.ui.accessibility.accessibilityLiveRegion
import com.hostshield.ui.components.HostShieldActionIconButton
import com.hostshield.ui.components.HostShieldBackHeader
import com.hostshield.ui.components.HostShieldEmptyState
import com.hostshield.ui.components.HostShieldLoadingState
import com.hostshield.ui.components.HostShieldMetricTile
import com.hostshield.ui.screens.home.GlassCard
import com.hostshield.ui.theme.*
import com.hostshield.util.AppPrivacyScorer

data class AppPrivacyState(
    val isLoading: Boolean = false,
    val reports: List<AppPrivacyScorer.AppReport> = emptyList(),
    val averageScore: Int = 0,
    val worstApps: Int = 0,
    val totalTrackerSdks: Int = 0
)

@Composable
fun AppPrivacyScreen(
    viewModel: AppPrivacyViewModel = hiltViewModel(),
    onBack: () -> Unit
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Column(modifier = Modifier.fillMaxSize().background(Black)) {
        HostShieldBackHeader(
            title = "App Privacy",
            subtitle = "Tracker SDKs and DNS behavior by app",
            onBack = onBack,
            actions = {
                HostShieldActionIconButton(
                    icon = Icons.Filled.Refresh,
                    contentDescription = "Refresh app privacy reports",
                    onClick = { viewModel.loadReports() },
                    enabled = !state.isLoading,
                    modifier = Modifier.accessibilityAction("Refresh app privacy reports", !state.isLoading),
                )
            }
        )

        if (state.isLoading) {
            HostShieldLoadingState(
                title = "Analyzing installed apps",
                message = "Scanning tracker SDKs and recent DNS behavior locally on this device.",
                modifier = Modifier
                    .padding(horizontal = 20.dp, vertical = 20.dp)
                    .accessibilityLiveRegion("Loading app privacy reports"),
            )
            return
        }

        LazyColumn(
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Summary
            if (state.reports.isNotEmpty()) {
                item {
                    val color = gradeColor(if (state.averageScore >= 75) "B" else if (state.averageScore >= 50) "C" else "D")
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        HostShieldMetricTile("${state.averageScore}", "avg score", color, Modifier.weight(1f))
                        HostShieldMetricTile("${state.reports.size}", "apps", Mauve, Modifier.weight(1f))
                        HostShieldMetricTile("${state.worstApps}", "review", Peach, Modifier.weight(1f))
                        HostShieldMetricTile("${state.totalTrackerSdks}", "trackers", Red, Modifier.weight(1f))
                    }
                }
            }

            // App reports
            items(state.reports) { report ->
                AppReportCard(report)
            }

            if (state.reports.isEmpty()) {
                item {
                    HostShieldEmptyState(
                        icon = Icons.Filled.PrivacyTip,
                        title = "No app data yet",
                        message = "Run protection for a while so HostShield can grade apps from local DNS activity and embedded tracker signals.",
                        accent = Mauve,
                    )
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
                modifier = Modifier.fillMaxWidth(),
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
                    Text(
                        report.appLabel.ifEmpty { report.packageName },
                        color = TextPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        "${report.totalQueries} queries, ${report.blockedQueries} blocked (${(report.blockRate * 100).toInt()}%)",
                        color = TextDim,
                        fontSize = 10.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                IconButton(
                    onClick = { expanded = !expanded },
                    modifier = Modifier.size(48.dp).accessibilityAction(
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

@Composable
private fun gradeColor(grade: String): Color = when (grade) {
    "A" -> Green
    "B" -> Teal
    "C" -> Yellow
    "D" -> Peach
    "F" -> Red
    else -> TextDim
}

@Composable
private fun sdkCategoryColor(category: String): Color = when (category) {
    "Advertising" -> Red
    "Analytics" -> Peach
    "Crash" -> Yellow
    "Social" -> Mauve
    "Location" -> Blue
    "Profiling" -> Flamingo
    else -> TextSecondary
}
