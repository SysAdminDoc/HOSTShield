package com.hostshield.util

import android.content.Context
import android.content.pm.PackageManager
import com.hostshield.data.database.DnsLogDao
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/**
 * v6.0: Enhanced Per-App Privacy Scorer
 *
 * Weighted scoring across three dimensions:
 * - **Tracker penalty** (40%): Embedded SDKs (APK scan) + network tracker domains (DNS log)
 * - **Permission penalty** (25%): Dangerous Android permissions (location, camera, etc.)
 * - **Network penalty** (35%): Block rate, suspicious TLDs, query volume, threat intel hits
 *
 * Score 0-100 (higher = more private). Grades: A (90+), B (75+), C (60+), D (40+), F (<40).
 */
@Singleton
class AppPrivacyScorer @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dnsLogDao: DnsLogDao,
    private val suspiciousTldDetector: SuspiciousTldDetector,
    private val trackerSignatureDb: TrackerSignatureDb,
    private val networkTrackerDb: NetworkTrackerDb
) {
    data class TrackerSdk(val name: String, val category: String)

    data class ScoreBreakdown(
        val trackerScore: Int,    // 0-40 penalty
        val permissionScore: Int, // 0-25 penalty
        val networkScore: Int     // 0-35 penalty
    )

    data class AppReport(
        val packageName: String,
        val appLabel: String,
        val privacyGrade: String, // A, B, C, D, F
        val score: Int, // 0-100 (higher = more private)
        val breakdown: ScoreBreakdown,
        val totalQueries: Int,
        val blockedQueries: Int,
        val blockRate: Float,
        val uniqueDomains: Int,
        val suspiciousTldCount: Int,
        val trackerDomains: List<String>, // top blocked domains for this app
        val insights: List<String>,
        val embeddedTrackers: List<TrackerSdk> = emptyList(), // tracker SDKs found in APK
        val trackerCategories: Set<String> = emptySet(),
        val networkTrackerCount: Int = 0,       // v6.0: domains matching NetworkTrackerDb
        val networkTrackerOwners: Set<String> = emptySet(), // v6.0: unique tracker owners contacted
        val dangerousPermissions: List<String> = emptyList() // v6.0: dangerous permissions held
    )

    // Dangerous permissions that impact privacy
    private val dangerousPermissions = setOf(
        "android.permission.ACCESS_FINE_LOCATION",
        "android.permission.ACCESS_COARSE_LOCATION",
        "android.permission.ACCESS_BACKGROUND_LOCATION",
        "android.permission.CAMERA",
        "android.permission.RECORD_AUDIO",
        "android.permission.READ_CONTACTS",
        "android.permission.READ_CALL_LOG",
        "android.permission.READ_SMS",
        "android.permission.READ_PHONE_STATE",
        "android.permission.READ_PHONE_NUMBERS",
        "android.permission.READ_EXTERNAL_STORAGE",
        "android.permission.READ_MEDIA_IMAGES",
        "android.permission.READ_MEDIA_VIDEO",
        "android.permission.BODY_SENSORS",
        "android.permission.ACTIVITY_RECOGNITION",
        "android.permission.READ_CALENDAR"
    )

    // High-risk permissions get extra penalty weight
    private val highRiskPermissions = setOf(
        "android.permission.ACCESS_FINE_LOCATION",
        "android.permission.ACCESS_BACKGROUND_LOCATION",
        "android.permission.CAMERA",
        "android.permission.RECORD_AUDIO",
        "android.permission.READ_CONTACTS",
        "android.permission.READ_SMS"
    )

    suspend fun generateReport(
        packageName: String,
        appLabel: String,
        scanResult: TrackerSignatureDb.ScanResult? = null
    ): AppReport {
        val domains = dnsLogDao.getDomainsForApp(packageName, 500).first()

        val totalQueries = domains.sumOf { it.cnt }
        val blockedQueries = domains.filter { it.blocked }.sumOf { it.cnt }
        val blockRate = if (totalQueries > 0) blockedQueries.toFloat() / totalQueries else 0f
        val uniqueDomains = domains.size

        val suspiciousTldCount = domains.count { suspiciousTldDetector.isSuspicious(it.hostname) }
        val topBlockedDomains = domains.filter { it.blocked }.sortedByDescending { it.cnt }.take(5).map { it.hostname }

        // Embedded tracker SDKs from APK scan
        val embeddedTrackers = scanResult?.foundTrackers?.map {
            TrackerSdk(it.name, it.category)
        } ?: emptyList()
        val trackerCategories = scanResult?.categories ?: emptySet()

        // v6.0: Network-based tracker detection — match DNS domains against NetworkTrackerDb
        val networkTrackerOwners = mutableSetOf<String>()
        var networkTrackerCount = 0
        for (d in domains) {
            val tracker = networkTrackerDb.lookup(d.hostname)
            if (tracker != null) {
                networkTrackerCount++
                networkTrackerOwners.add(tracker.owner)
            }
        }

        // v6.0: Permission analysis
        val heldDangerousPerms = getHeldDangerousPermissions(packageName)

        // ── Tracker Penalty (max 40) ──────────────────────────────
        var trackerPenalty = 0
        // Embedded SDKs: 3 points each, max 15
        trackerPenalty += minOf(15, embeddedTrackers.size * 3)
        // Ad SDKs are worse: extra 2 per ad SDK, max 8
        val adSdkCount = embeddedTrackers.count { it.category == "Advertising" }
        trackerPenalty += minOf(8, adSdkCount * 2)
        // Network tracker domains contacted: 2 per domain, max 12
        trackerPenalty += minOf(12, networkTrackerCount * 2)
        // Unique tracker owners (data shared widely): 1 per owner, max 5
        trackerPenalty += minOf(5, networkTrackerOwners.size)
        trackerPenalty = minOf(40, trackerPenalty)

        // ── Permission Penalty (max 25) ───────────────────────────
        var permissionPenalty = 0
        // Each dangerous permission: 2 points
        permissionPenalty += heldDangerousPerms.size * 2
        // High-risk permissions: extra 2 points each
        val highRiskCount = heldDangerousPerms.count { it in highRiskPermissions }
        permissionPenalty += highRiskCount * 2
        // Background location is especially invasive: extra 3
        if ("android.permission.ACCESS_BACKGROUND_LOCATION" in heldDangerousPerms) {
            permissionPenalty += 3
        }
        permissionPenalty = minOf(25, permissionPenalty)

        // ── Network Penalty (max 35) ──────────────────────────────
        var networkPenalty = 0
        // High block rate = tracking behavior: up to 12
        networkPenalty += (blockRate * 12).toInt()
        // Many unique domains = chatty: up to 8
        networkPenalty += minOf(8, uniqueDomains / 8)
        // Suspicious TLDs: 3 per TLD, max 6
        networkPenalty += minOf(6, suspiciousTldCount * 3)
        // Very high query volume: up to 5
        if (totalQueries > 500) networkPenalty += minOf(5, (totalQueries - 500) / 200)
        // v6.0: Tracker query ratio (percentage of queries to tracker domains): up to 4
        if (totalQueries > 0 && networkTrackerCount > 0) {
            val trackerRatio = networkTrackerCount.toFloat() / domains.size
            networkPenalty += (trackerRatio * 4).toInt()
        }
        networkPenalty = minOf(35, networkPenalty)

        val score = (100 - trackerPenalty - permissionPenalty - networkPenalty).coerceIn(0, 100)
        val breakdown = ScoreBreakdown(trackerPenalty, permissionPenalty, networkPenalty)

        val grade = when {
            score >= 90 -> "A"
            score >= 75 -> "B"
            score >= 60 -> "C"
            score >= 40 -> "D"
            else -> "F"
        }

        val insights = mutableListOf<String>()
        // Tracker insights
        if (embeddedTrackers.isNotEmpty()) {
            insights.add("${embeddedTrackers.size} tracker SDK${if (embeddedTrackers.size > 1) "s" else ""} embedded in APK")
        }
        if (adSdkCount > 0) insights.add("$adSdkCount advertising SDK${if (adSdkCount > 1) "s" else ""} found")
        if (networkTrackerCount > 0) {
            insights.add("Contacts $networkTrackerCount known tracker domain${if (networkTrackerCount > 1) "s" else ""} (${networkTrackerOwners.joinToString(", ").take(60)})")
        }
        // Permission insights
        if (heldDangerousPerms.isNotEmpty()) {
            insights.add("${heldDangerousPerms.size} dangerous permission${if (heldDangerousPerms.size > 1) "s" else ""}")
        }
        if (highRiskCount > 0) {
            val highRiskNames = heldDangerousPerms.filter { it in highRiskPermissions }
                .map { it.substringAfterLast(".").lowercase().replace("_", " ") }
            insights.add("High-risk: ${highRiskNames.joinToString(", ")}")
        }
        // Network insights
        if (blockRate > 0.5f) insights.add("Over ${(blockRate * 100).toInt()}% of queries are blocked trackers")
        if (uniqueDomains > 50) insights.add("Contacts $uniqueDomains unique domains (very chatty)")
        if (suspiciousTldCount > 0) insights.add("$suspiciousTldCount queries to suspicious TLDs")
        if (totalQueries > 1000) insights.add("${totalQueries} total queries in 7 days (high volume)")
        if (insights.isEmpty()) insights.add("Low tracking activity detected")

        return AppReport(
            packageName = packageName,
            appLabel = appLabel,
            privacyGrade = grade,
            score = score,
            breakdown = breakdown,
            totalQueries = totalQueries,
            blockedQueries = blockedQueries,
            blockRate = blockRate,
            uniqueDomains = uniqueDomains,
            suspiciousTldCount = suspiciousTldCount,
            trackerDomains = topBlockedDomains,
            insights = insights,
            embeddedTrackers = embeddedTrackers,
            trackerCategories = trackerCategories,
            networkTrackerCount = networkTrackerCount,
            networkTrackerOwners = networkTrackerOwners,
            dangerousPermissions = heldDangerousPerms
        )
    }

    suspend fun generateAllReports(): List<AppReport> {
        val apps = dnsLogDao.getAllAppsWithCounts().first()
        val scanResults = trackerSignatureDb.scanAllApps()
        val scanMap = scanResults.associateBy { it.packageName }

        return apps.filter { it.totalQueries >= 5 }
            .map { generateReport(it.appPackage, it.appLabel, scanMap[it.appPackage]) }
            .sortedBy { it.score }
    }

    private fun getHeldDangerousPermissions(packageName: String): List<String> {
        return try {
            val pm = context.packageManager
            val info = pm.getPackageInfo(packageName, PackageManager.GET_PERMISSIONS)
            val requested = info.requestedPermissions ?: return emptyList()
            val flags = info.requestedPermissionsFlags ?: return emptyList()
            requested.filterIndexed { index, perm ->
                perm in dangerousPermissions &&
                        (flags[index] and PackageManager.PERMISSION_GRANTED) != 0
            }
        } catch (_: Exception) {
            emptyList()
        }
    }
}
