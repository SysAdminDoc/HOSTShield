package com.hostshield.util

import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import com.hostshield.data.database.TrackerScanCacheDao
import com.hostshield.data.model.TrackerScanCacheEntry
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.zip.ZipFile
import javax.inject.Inject
import javax.inject.Singleton

// HostShield v4.0.0 -- Exodus-style Tracker SDK Detection
// Scans installed APK dex files for known tracker library class signatures.
// Results are cached in Room DB, invalidated on app version change.

@Singleton
class TrackerSignatureDb @Inject constructor(
    @ApplicationContext private val context: Context,
    private val cacheDao: TrackerScanCacheDao
) {
    companion object {
        private const val TAG = "TrackerSigDb"
        private const val CACHE_MAX_AGE_MS = 7L * 24 * 60 * 60 * 1000 // 7 days
    }

    data class TrackerInfo(
        val name: String,
        val category: String,
        val signatures: List<String>
    )

    private val trackers = listOf(
        // Advertising
        TrackerInfo("Google AdMob", "Advertising", listOf("com.google.android.gms.ads")),
        TrackerInfo("Google Ads", "Advertising", listOf("com.google.ads")),
        TrackerInfo("Facebook Ads", "Advertising", listOf("com.facebook.ads")),
        TrackerInfo("Unity Ads", "Advertising", listOf("com.unity3d.services", "com.unity3d.ads")),
        TrackerInfo("AppLovin", "Advertising", listOf("com.applovin")),
        TrackerInfo("IronSource", "Advertising", listOf("com.ironsource")),
        TrackerInfo("AdColony", "Advertising", listOf("com.adcolony")),
        TrackerInfo("Chartboost", "Advertising", listOf("com.chartboost")),
        TrackerInfo("InMobi", "Advertising", listOf("com.inmobi")),
        TrackerInfo("Mopub", "Advertising", listOf("com.mopub")),
        TrackerInfo("Vungle", "Advertising", listOf("com.vungle")),
        TrackerInfo("Amazon Ads", "Advertising", listOf("com.amazon.device.ads")),
        TrackerInfo("Criteo", "Advertising", listOf("com.criteo")),
        TrackerInfo("Pangle (TikTok Ads)", "Advertising", listOf("com.bytedance.sdk.openadsdk")),
        // Analytics
        TrackerInfo("Google Firebase Analytics", "Analytics", listOf("com.google.firebase.analytics")),
        TrackerInfo("Google Analytics", "Analytics", listOf("com.google.android.gms.analytics")),
        TrackerInfo("Facebook Analytics", "Analytics", listOf("com.facebook.appevents")),
        TrackerInfo("Amplitude", "Analytics", listOf("com.amplitude")),
        TrackerInfo("Mixpanel", "Analytics", listOf("com.mixpanel")),
        TrackerInfo("Segment", "Analytics", listOf("com.segment")),
        TrackerInfo("Flurry", "Analytics", listOf("com.flurry")),
        TrackerInfo("AppsFlyer", "Analytics", listOf("com.appsflyer")),
        TrackerInfo("Adjust", "Analytics", listOf("com.adjust.sdk")),
        TrackerInfo("Branch", "Analytics", listOf("io.branch")),
        TrackerInfo("Kochava", "Analytics", listOf("com.kochava")),
        TrackerInfo("Singular", "Analytics", listOf("com.singular.sdk")),
        TrackerInfo("CleverTap", "Analytics", listOf("com.clevertap")),
        TrackerInfo("Leanplum", "Analytics", listOf("com.leanplum")),
        TrackerInfo("Countly", "Analytics", listOf("ly.count")),
        TrackerInfo("Heap", "Analytics", listOf("com.heapanalytics")),
        TrackerInfo("Snowplow", "Analytics", listOf("com.snowplowanalytics")),
        TrackerInfo("Braze (Appboy)", "Analytics", listOf("com.braze", "com.appboy")),
        TrackerInfo("OneSignal", "Analytics", listOf("com.onesignal")),
        TrackerInfo("Airship (UrbanAirship)", "Analytics", listOf("com.urbanairship")),
        // Crash Reporting
        TrackerInfo("Firebase Crashlytics", "Crash", listOf("com.google.firebase.crashlytics")),
        TrackerInfo("Sentry", "Crash", listOf("io.sentry")),
        TrackerInfo("Bugsnag", "Crash", listOf("com.bugsnag")),
        TrackerInfo("Instabug", "Crash", listOf("com.instabug")),
        TrackerInfo("ACRA", "Crash", listOf("org.acra")),
        TrackerInfo("Datadog", "Crash", listOf("com.datadog")),
        TrackerInfo("New Relic", "Crash", listOf("com.newrelic")),
        TrackerInfo("Embrace", "Crash", listOf("io.embrace")),
        TrackerInfo("Rollbar", "Crash", listOf("com.rollbar")),
        // Profiling / Social
        TrackerInfo("Google Firebase Performance", "Profiling", listOf("com.google.firebase.perf")),
        TrackerInfo("Google Firebase Remote Config", "Profiling", listOf("com.google.firebase.remoteconfig")),
        TrackerInfo("Facebook Login", "Social", listOf("com.facebook.login")),
        TrackerInfo("Facebook SDK Core", "Social", listOf("com.facebook.FacebookSdk")),
        TrackerInfo("Google Sign-In", "Social", listOf("com.google.android.gms.auth")),
        TrackerInfo("Twitter SDK", "Social", listOf("com.twitter.sdk")),
        TrackerInfo("LINE SDK", "Social", listOf("com.linecorp.linesdk")),
        // Location
        TrackerInfo("Google Location Services", "Location", listOf("com.google.android.gms.location")),
        TrackerInfo("Mapbox", "Location", listOf("com.mapbox")),
        TrackerInfo("Huq", "Location", listOf("me.huq")),
        TrackerInfo("Radar", "Location", listOf("io.radar")),
        // Telemetry
        TrackerInfo("Microsoft App Center", "Analytics", listOf("com.microsoft.appcenter")),
        TrackerInfo("Yandex AppMetrica", "Analytics", listOf("com.yandex.metrica")),
        TrackerInfo("Huawei Analytics", "Analytics", listOf("com.huawei.hms.analytics")),
        TrackerInfo("Samsung Analytics", "Analytics", listOf("com.samsung.context")),
        TrackerInfo("Tencent Bugly", "Crash", listOf("com.tencent.bugly")),
        TrackerInfo("ByteDance AppLog", "Analytics", listOf("com.bytedance.applog"))
    )

    data class ScanResult(
        val packageName: String,
        val appLabel: String,
        val foundTrackers: List<TrackerInfo>,
        val trackerCount: Int,
        val categories: Set<String>,
        val fromCache: Boolean = false
    )

    /** Scan a single app, using cache when available. */
    suspend fun scanApp(packageName: String): ScanResult? = withContext(Dispatchers.IO) {
        try {
            val pm = context.packageManager
            val appInfo = pm.getApplicationInfo(packageName, 0)
            val appLabel = pm.getApplicationLabel(appInfo).toString()
            val versionCode = getVersionCode(packageName)

            // Check cache
            val cached = cacheDao.getByPackage(packageName)
            if (cached != null && cached.appVersionCode == versionCode &&
                System.currentTimeMillis() - cached.scannedAt < CACHE_MAX_AGE_MS) {
                return@withContext cachedToScanResult(cached)
            }

            // Fresh scan
            val result = performScan(packageName, appLabel, appInfo.sourceDir)
            // Save to cache
            cacheDao.upsert(scanResultToCache(result, versionCode))
            result
        } catch (e: Exception) {
            Log.w(TAG, "Failed to scan $packageName: ${e.message}")
            null
        }
    }

    /** Scan all installed user apps with progress callback. Cache-first. */
    suspend fun scanAllApps(onProgress: ((current: Int, total: Int) -> Unit)? = null): List<ScanResult> = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        val packages = pm.getInstalledApplications(PackageManager.GET_META_DATA)
            .filter { it.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM == 0 }

        val total = packages.size
        val results = mutableListOf<ScanResult>()

        for ((index, appInfo) in packages.withIndex()) {
            onProgress?.invoke(index + 1, total)
            try {
                val appLabel = pm.getApplicationLabel(appInfo).toString()
                val pkg = appInfo.packageName
                val versionCode = getVersionCode(pkg)

                // Check cache first
                val cached = cacheDao.getByPackage(pkg)
                if (cached != null && cached.appVersionCode == versionCode &&
                    System.currentTimeMillis() - cached.scannedAt < CACHE_MAX_AGE_MS) {
                    cachedToScanResult(cached)?.let { results.add(it) }
                    continue
                }

                // Fresh scan
                val result = performScan(pkg, appLabel, appInfo.sourceDir)
                cacheDao.upsert(scanResultToCache(result, versionCode))
                results.add(result)
            } catch (e: Exception) {
                Log.w(TAG, "Scan failed for ${appInfo.packageName}: ${e.message}")
            }
        }
        results.sortedByDescending { it.trackerCount }
    }

    /** Invalidate cache for a specific app (e.g., after update). */
    suspend fun invalidateCache(packageName: String) {
        cacheDao.deleteByPackage(packageName)
    }

    /** Clear entire scan cache. */
    suspend fun clearCache() {
        cacheDao.deleteAll()
    }

    /** Number of cached scan results. */
    suspend fun cacheSize(): Int = cacheDao.count()

    private fun performScan(packageName: String, appLabel: String, apkPath: String): ScanResult {
        val classNames = extractClassPrefixes(apkPath)
        val found = trackers.filter { tracker ->
            tracker.signatures.any { sig ->
                classNames.any { it.startsWith(sig) }
            }
        }
        return ScanResult(
            packageName = packageName,
            appLabel = appLabel,
            foundTrackers = found,
            trackerCount = found.size,
            categories = found.map { it.category }.toSet()
        )
    }

    private fun scanResultToCache(result: ScanResult, versionCode: Long): TrackerScanCacheEntry {
        return TrackerScanCacheEntry(
            packageName = result.packageName,
            appLabel = result.appLabel,
            trackerCount = result.trackerCount,
            trackerNames = result.foundTrackers.joinToString(",") { it.name },
            categories = result.categories.joinToString(","),
            appVersionCode = versionCode
        )
    }

    private fun cachedToScanResult(cached: TrackerScanCacheEntry): ScanResult? {
        val trackerNames = cached.trackerNames.split(",").filter { it.isNotBlank() }
        val found = trackers.filter { it.name in trackerNames }
        return ScanResult(
            packageName = cached.packageName,
            appLabel = cached.appLabel,
            foundTrackers = found,
            trackerCount = cached.trackerCount,
            categories = cached.categories.split(",").filter { it.isNotBlank() }.toSet(),
            fromCache = true
        )
    }

    @Suppress("DEPRECATION")
    private fun getVersionCode(packageName: String): Long {
        return try {
            val pi = context.packageManager.getPackageInfo(packageName, 0)
            if (android.os.Build.VERSION.SDK_INT >= 28) pi.longVersionCode
            else pi.versionCode.toLong()
        } catch (_: Exception) { 0L }
    }

    private fun extractClassPrefixes(apkPath: String): Set<String> {
        val prefixes = mutableSetOf<String>()
        try {
            ZipFile(apkPath).use { zip ->
                val dexEntries = zip.entries().asSequence()
                    .filter { it.name.endsWith(".dex") && !it.isDirectory }
                    .toList()

                for (entry in dexEntries) {
                    try {
                        val bytes = zip.getInputStream(entry).use { it.readBytes() }
                        val content = String(bytes, Charsets.ISO_8859_1)
                        for (tracker in trackers) {
                            for (sig in tracker.signatures) {
                                val dexSig = "L${sig.replace('.', '/')}/"
                                if (content.contains(dexSig)) {
                                    prefixes.add(sig)
                                }
                            }
                        }
                    } catch (_: Exception) { }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to read APK $apkPath: ${e.message}")
        }
        return prefixes
    }
}
