package com.hostshield.ui.screens.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hostshield.R
import com.hostshield.ui.accessibility.accessibilityAction
import com.hostshield.ui.accessibility.accessibilityHeading
import com.hostshield.ui.accessibility.accessibilityLiveRegion
import com.hostshield.ui.accessibility.accessibilitySelection
import com.hostshield.ui.theme.*
import com.hostshield.util.PrivacyScorer

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HomeStatsSection(
    totalDomainsBlocked: Int,
    blockedToday: Int,
    totalQueriesToday: Int,
    enabledSources: Int,
    privacyScore: Int,
    privacyItems: List<PrivacyScorer.ScoreItem>,
    categoryCounts: Map<String, Pair<Int, Int>>,
    topApps: List<Triple<String, String, Int>>,
    onNavigateToLogs: () -> Unit,
    onToggleCategory: (String, Boolean) -> Unit,
    onNavigateToAppLogs: (String) -> Unit
) {
    // Stats grid
    Column(
        modifier = Modifier.padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            StatTile(
                modifier = Modifier.weight(1f),
                icon = Icons.Filled.Shield,
                value = formatNumber(totalDomainsBlocked),
                label = stringResource(R.string.home_stat_domains_blocked),
                accent = Teal,
                glowColor = TealGlow
            )
            StatTile(
                modifier = Modifier.weight(1f),
                icon = Icons.Filled.Block,
                value = formatNumber(blockedToday),
                label = stringResource(R.string.home_stat_blocked_today),
                accent = Red,
                glowColor = Red,
                onClick = onNavigateToLogs
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            StatTile(
                modifier = Modifier.weight(1f),
                icon = Icons.Filled.Dns,
                value = formatNumber(totalQueriesToday),
                label = stringResource(R.string.home_stat_queries_today),
                accent = Blue,
                glowColor = Blue,
                onClick = onNavigateToLogs
            )
            StatTile(
                modifier = Modifier.weight(1f),
                icon = Icons.Filled.CloudDownload,
                value = enabledSources.toString(),
                label = stringResource(R.string.home_stat_active_sources),
                accent = Mauve,
                glowColor = Mauve
            )
        }
    }

    // Privacy Score card
    if (privacyScore > 0) {
        Spacer(Modifier.height(10.dp))
        GlassCard(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
            Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                val scoreColor = when {
                    privacyScore >= 80 -> Green
                    privacyScore >= 50 -> Yellow
                    else -> Red
                }
                Box(
                    modifier = Modifier.size(44.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        progress = { privacyScore / 100f },
                        modifier = Modifier.size(44.dp).accessibilityLiveRegion(
                            stringResource(R.string.home_privacy_score_a11y, privacyScore)
                        ),
                        color = scoreColor,
                        trackColor = Surface3,
                        strokeWidth = 4.dp
                    )
                    Text(
                        "$privacyScore",
                        color = scoreColor, fontWeight = FontWeight.Bold, fontSize = 14.sp
                    )
                }
                Spacer(Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.stats_privacy_score),
                        color = TextPrimary,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp
                    )
                    val passCount = privacyItems.count { it.passed }
                    val totalCount = privacyItems.size
                    Text(
                        pluralStringResource(
                            R.plurals.home_privacy_checks_passed,
                            totalCount,
                            passCount,
                            totalCount
                        ),
                        color = TextDim,
                        fontSize = 11.sp
                    )
                }
                val failedItems = privacyItems.filter { !it.passed }
                if (failedItems.isNotEmpty()) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = Yellow.copy(alpha = 0.1f)
                    ) {
                        Text(
                            pluralStringResource(R.plurals.home_privacy_tips, failedItems.size, failedItems.size),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            color = Yellow, fontSize = 10.sp, fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    }

    // Category quick toggles
    if (categoryCounts.isNotEmpty()) {
        Spacer(Modifier.height(10.dp))
        GlassCard(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text(
                    stringResource(R.string.stats_source_categories),
                    color = TextDim,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.sp,
                    modifier = Modifier.accessibilityHeading()
                )
                Spacer(Modifier.height(8.dp))
                val catColors = mapOf(
                    "ADS" to Teal, "TRACKERS" to Blue, "MALWARE" to Red,
                    "ADULT" to Flamingo, "SOCIAL" to Mauve, "CRYPTO" to Peach,
                    "ALLOWLIST" to Green, "CUSTOM" to Yellow
                )
                val cats = categoryCounts.entries.sortedBy { it.key }
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    cats.forEach { (cat, counts) ->
                        val (enabled, total) = counts
                        val color = catColors[cat] ?: TextDim
                        val allEnabled = enabled == total
                        Surface(
                            onClick = { onToggleCategory(cat, !allEnabled) },
                            shape = RoundedCornerShape(8.dp),
                            color = if (allEnabled) color.copy(alpha = 0.12f) else Surface2,
                            modifier = Modifier
                                .heightIn(min = 48.dp)
                                .accessibilitySelection(
                                pluralStringResource(
                                    R.plurals.home_source_category_a11y,
                                    total,
                                    cat.lowercase().replaceFirstChar { it.uppercase() },
                                    enabled,
                                    total
                                ),
                                allEnabled
                            )
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    cat.lowercase().replaceFirstChar { it.uppercase() },
                                    color = if (allEnabled) color else TextDim,
                                    fontSize = 11.sp, fontWeight = FontWeight.SemiBold
                                )
                                Spacer(Modifier.width(4.dp))
                                Text("$enabled/$total", color = TextDim, fontSize = 9.sp)
                            }
                        }
                    }
                }
            }
        }
    }

    // Top Querying Apps
    if (topApps.isNotEmpty()) {
        Spacer(Modifier.height(10.dp))
        GlassCard(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text(
                    stringResource(R.string.stats_top_querying_apps),
                    color = TextDim,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.sp,
                    modifier = Modifier.accessibilityHeading()
                )
                Spacer(Modifier.height(8.dp))
                topApps.forEachIndexed { idx, (pkg, label, count) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 3.dp)
                            .heightIn(min = 48.dp)
                            .accessibilityAction(
                                stringResource(
                                    R.string.home_dns_queries_by_app,
                                    label.ifBlank { stringResource(R.string.home_unknown) },
                                    count
                                )
                            )
                            .clickable { if (pkg.isNotBlank()) onNavigateToAppLogs(pkg) },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val medalColor = when (idx) { 0 -> Teal; 1 -> Blue; else -> TextDim }
                        Text("${idx + 1}", color = medalColor, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                            modifier = Modifier.width(18.dp))
                        Text(label.ifBlank { stringResource(R.string.home_unknown) }, color = TextPrimary, fontSize = 12.sp,
                            maxLines = 1, modifier = Modifier.weight(1f),
                            overflow = TextOverflow.Ellipsis)
                        Icon(Icons.Filled.ChevronRight, null, tint = TextDim.copy(alpha = 0.4f), modifier = Modifier.size(14.dp))
                        Text("$count", color = TextDim, fontSize = 11.sp, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
                    }
                }
            }
        }
    }
}
