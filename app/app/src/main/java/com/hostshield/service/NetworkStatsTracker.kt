package com.hostshield.service

import android.app.usage.NetworkStats
import android.app.usage.NetworkStatsManager
import android.content.Context
import android.net.ConnectivityManager
import android.net.TrafficStats
import android.os.Build
import android.util.Log
import com.topjohnwu.superuser.Shell
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Per-app network usage tracker.
 *
 * Data sources (in priority order):
 * 1. NetworkStatsManager (Android 6+) -- official API, requires PACKAGE_USAGE_STATS
 * 2. /proc/net/xt_qtaguid/stats (root) -- kernel-level per-UID counters
 * 3. TrafficStats.getUidRx/TxBytes() -- simple per-UID counters
 *
 * Provides real-time and historical per-app data usage broken down by
 * WiFi vs mobile, upload vs download.
 */
@Singleton
class NetworkStatsTracker @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "NetworkStatsTracker"
    }

    data class AppNetStats(
        val uid: Int,
        val packageName: String,
        val appLabel: String,
        val wifiRxBytes: Long = 0,
        val wifiTxBytes: Long = 0,
        val mobileRxBytes: Long = 0,
        val mobileTxBytes: Long = 0
    ) {
        val totalBytes get() = wifiRxBytes + wifiTxBytes + mobileRxBytes + mobileTxBytes
        val totalRxBytes get() = wifiRxBytes + mobileRxBytes
        val totalTxBytes get() = wifiTxBytes + mobileTxBytes
    }

    private val _appStats = MutableStateFlow<List<AppNetStats>>(emptyList())
    val appStats: StateFlow<List<AppNetStats>> = _appStats.asStateFlow()

    private val _totalRx = MutableStateFlow(0L)
    val totalRx: StateFlow<Long> = _totalRx.asStateFlow()
    private val _totalTx = MutableStateFlow(0L)
    val totalTx: StateFlow<Long> = _totalTx.asStateFlow()

    /**
     * Refresh per-app network stats. Tries NetworkStatsManager first,
     * falls back to /proc/net/xt_qtaguid, then TrafficStats.
     */
    suspend fun refresh() = withContext(Dispatchers.IO) {
        val stats = tryNetworkStatsManager()
            ?: tryProcQtaguid()
            ?: tryTrafficStats()
            ?: emptyList()

        _appStats.value = stats.sortedByDescending { it.totalBytes }
        _totalRx.value = TrafficStats.getTotalRxBytes()
        _totalTx.value = TrafficStats.getTotalTxBytes()
    }

    /**
     * Method 1: NetworkStatsManager (requires PACKAGE_USAGE_STATS permission)
     */
    private fun tryNetworkStatsManager(): List<AppNetStats>? {
        try {
            val nsm = context.getSystemService(Context.NETWORK_STATS_SERVICE) as? NetworkStatsManager
                ?: return null

            val pm = context.packageManager
            val now = System.currentTimeMillis()
            val dayAgo = now - 86_400_000L
            val uidStats = ConcurrentHashMap<Int, AppNetStats>()

            // WiFi stats
            try {
                val bucket = NetworkStats.Bucket()
                @Suppress("DEPRECATION")
                val wifiStats = nsm.querySummary(
                    ConnectivityManager.TYPE_WIFI, null, dayAgo, now
                )
                while (wifiStats.hasNextBucket()) {
                    wifiStats.getNextBucket(bucket)
                    val uid = bucket.uid
                    if (uid < 1000) continue
                    val existing = uidStats[uid] ?: AppNetStats(
                        uid = uid,
                        packageName = pm.getPackagesForUid(uid)?.firstOrNull() ?: "uid:$uid",
                        appLabel = resolveLabel(pm, uid)
                    )
                    uidStats[uid] = existing.copy(
                        wifiRxBytes = existing.wifiRxBytes + bucket.rxBytes,
                        wifiTxBytes = existing.wifiTxBytes + bucket.txBytes
                    )
                }
                wifiStats.close()
            } catch (_: Exception) { }

            // Mobile stats
            try {
                val bucket = NetworkStats.Bucket()
                @Suppress("DEPRECATION")
                val mobileStats = nsm.querySummary(
                    ConnectivityManager.TYPE_MOBILE, null, dayAgo, now
                )
                while (mobileStats.hasNextBucket()) {
                    mobileStats.getNextBucket(bucket)
                    val uid = bucket.uid
                    if (uid < 1000) continue
                    val existing = uidStats[uid] ?: AppNetStats(
                        uid = uid,
                        packageName = pm.getPackagesForUid(uid)?.firstOrNull() ?: "uid:$uid",
                        appLabel = resolveLabel(pm, uid)
                    )
                    uidStats[uid] = existing.copy(
                        mobileRxBytes = existing.mobileRxBytes + bucket.rxBytes,
                        mobileTxBytes = existing.mobileTxBytes + bucket.txBytes
                    )
                }
                mobileStats.close()
            } catch (_: Exception) { }

            return if (uidStats.isNotEmpty()) uidStats.values.toList() else null
        } catch (e: Exception) {
            Log.w(TAG, "NetworkStatsManager failed: ${e.message}")
            return null
        }
    }

    /**
     * Method 2: Read /proc/net/xt_qtaguid/stats (root, Android <10)
     * Format: idx iface acct_tag_hex uid_tag_int cnt_set rx_bytes rx_packets tx_bytes tx_packets
     */
    private fun tryProcQtaguid(): List<AppNetStats>? {
        try {
            val result = Shell.cmd("cat /proc/net/xt_qtaguid/stats 2>/dev/null").exec()
            if (!result.isSuccess || result.out.size < 2) return null

            val pm = context.packageManager
            val uidStats = ConcurrentHashMap<Int, AppNetStats>()

            for (line in result.out.drop(1)) { // skip header
                val parts = line.trim().split(Regex("\\s+"))
                if (parts.size < 9) continue
                val iface = parts[1]
                val uid = parts[3].toIntOrNull() ?: continue
                if (uid < 1000) continue
                val rxBytes = parts[5].toLongOrNull() ?: 0
                val txBytes = parts[7].toLongOrNull() ?: 0
                val isWifi = iface.startsWith("wlan") || iface.startsWith("eth")

                val existing = uidStats[uid] ?: AppNetStats(
                    uid = uid,
                    packageName = pm.getPackagesForUid(uid)?.firstOrNull() ?: "uid:$uid",
                    appLabel = resolveLabel(pm, uid)
                )
                uidStats[uid] = if (isWifi) {
                    existing.copy(
                        wifiRxBytes = existing.wifiRxBytes + rxBytes,
                        wifiTxBytes = existing.wifiTxBytes + txBytes
                    )
                } else {
                    existing.copy(
                        mobileRxBytes = existing.mobileRxBytes + rxBytes,
                        mobileTxBytes = existing.mobileTxBytes + txBytes
                    )
                }
            }

            return if (uidStats.isNotEmpty()) uidStats.values.toList() else null
        } catch (e: Exception) {
            Log.w(TAG, "xt_qtaguid failed: ${e.message}")
            return null
        }
    }

    /**
     * Method 3: TrafficStats per-UID (no root required, but no WiFi/mobile breakdown)
     */
    private fun tryTrafficStats(): List<AppNetStats>? {
        try {
            val pm = context.packageManager
            val apps = pm.getInstalledApplications(0)
            val stats = mutableListOf<AppNetStats>()

            for (app in apps) {
                val uid = app.uid
                val rx = TrafficStats.getUidRxBytes(uid)
                val tx = TrafficStats.getUidTxBytes(uid)
                if (rx <= 0 && tx <= 0) continue

                stats.add(AppNetStats(
                    uid = uid,
                    packageName = app.packageName,
                    appLabel = pm.getApplicationLabel(app).toString(),
                    wifiRxBytes = rx, // Can't distinguish WiFi vs mobile here
                    wifiTxBytes = tx
                ))
            }

            return if (stats.isNotEmpty()) stats else null
        } catch (e: Exception) {
            Log.w(TAG, "TrafficStats failed: ${e.message}")
            return null
        }
    }

    private fun resolveLabel(pm: android.content.pm.PackageManager, uid: Int): String {
        return try {
            val pkg = pm.getPackagesForUid(uid)?.firstOrNull() ?: return "UID $uid"
            pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
        } catch (_: Exception) { "UID $uid" }
    }
}

/**
 * Format bytes to human-readable string.
 * Top-level so it can be used from any file via `import com.hostshield.service.formatBytes`.
 */
fun formatBytes(bytes: Long): String = when {
    bytes < 0 -> "0 B"
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> "%.1f KB".format(bytes / 1024.0)
    bytes < 1024 * 1024 * 1024 -> "%.1f MB".format(bytes / (1024.0 * 1024))
    else -> "%.2f GB".format(bytes / (1024.0 * 1024 * 1024))
}
