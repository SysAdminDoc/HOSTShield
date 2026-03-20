package com.hostshield.util

import com.hostshield.data.database.AppQueryStat
import com.hostshield.data.database.DnsLogDao
import com.hostshield.data.database.TopHostname
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

// HostShield v3.7.0 -- Per-App Privacy Scorer
// Rates each app's tracking behavior based on DNS query patterns.

@Singleton
class AppPrivacyScorer @Inject constructor(
    private val dnsLogDao: DnsLogDao,
    private val suspiciousTldDetector: SuspiciousTldDetector
) {
    data class AppReport(
        val packageName: String,
        val appLabel: String,
        val privacyGrade: String, // A, B, C, D, F
        val score: Int, // 0-100 (higher = more private)
        val totalQueries: Int,
        val blockedQueries: Int,
        val blockRate: Float,
        val uniqueDomains: Int,
        val suspiciousTldCount: Int,
        val trackerDomains: List<String>, // top blocked domains for this app
        val insights: List<String>
    )

    // Known tracker domain patterns
    private val trackerPatterns = listOf(
        "analytics", "tracking", "telemetry", "metrics", "pixel",
        "adservice", "doubleclick", "adsystem", "adnxs", "criteo",
        "facebook.com", "graph.facebook", "fbcdn", "crashlytics",
        "appsflyer", "adjust.com", "branch.io", "amplitude",
        "mixpanel", "segment.io", "sentry.io"
    )

    suspend fun generateReport(packageName: String, appLabel: String): AppReport {
        val weekAgo = System.currentTimeMillis() - (7 * 24 * 60 * 60 * 1000L)
        val domains = dnsLogDao.getDomainsForApp(packageName, 500).first()

        val totalQueries = domains.sumOf { it.cnt }
        val blockedQueries = domains.filter { it.blocked }.sumOf { it.cnt }
        val blockRate = if (totalQueries > 0) blockedQueries.toFloat() / totalQueries else 0f
        val uniqueDomains = domains.size

        val suspiciousTldCount = domains.count { suspiciousTldDetector.isSuspicious(it.hostname) }
        val trackerCount = domains.count { d -> trackerPatterns.any { d.hostname.contains(it, ignoreCase = true) } }
        val topBlockedDomains = domains.filter { it.blocked }.sortedByDescending { it.cnt }.take(5).map { it.hostname }

        // Scoring
        var score = 100
        // High block rate = more tracking (-30 max)
        score -= (blockRate * 30).toInt()
        // Many unique domains = chatty (-20 max)
        score -= minOf(20, uniqueDomains / 5)
        // Suspicious TLDs (-10 max)
        score -= minOf(10, suspiciousTldCount * 3)
        // Known tracker domains (-20 max)
        score -= minOf(20, trackerCount * 4)
        // Very high query volume (-10 max)
        if (totalQueries > 500) score -= minOf(10, (totalQueries - 500) / 100)

        score = score.coerceIn(0, 100)

        val grade = when {
            score >= 90 -> "A"
            score >= 75 -> "B"
            score >= 60 -> "C"
            score >= 40 -> "D"
            else -> "F"
        }

        val insights = mutableListOf<String>()
        if (blockRate > 0.5f) insights.add("Over ${(blockRate * 100).toInt()}% of queries are blocked trackers")
        if (uniqueDomains > 50) insights.add("Contacts $uniqueDomains unique domains (very chatty)")
        if (suspiciousTldCount > 0) insights.add("$suspiciousTldCount queries to suspicious TLDs")
        if (trackerCount > 5) insights.add("Connects to $trackerCount known tracker domains")
        if (totalQueries > 1000) insights.add("${totalQueries} total queries in 7 days (high volume)")
        if (insights.isEmpty()) insights.add("Low tracking activity detected")

        return AppReport(
            packageName = packageName,
            appLabel = appLabel,
            privacyGrade = grade,
            score = score,
            totalQueries = totalQueries,
            blockedQueries = blockedQueries,
            blockRate = blockRate,
            uniqueDomains = uniqueDomains,
            suspiciousTldCount = suspiciousTldCount,
            trackerDomains = topBlockedDomains,
            insights = insights
        )
    }

    suspend fun generateAllReports(): List<AppReport> {
        val apps = dnsLogDao.getAllAppsWithCounts().first()
        return apps.filter { it.totalQueries >= 5 }
            .map { generateReport(it.appPackage, it.appLabel) }
            .sortedBy { it.score }
    }
}
