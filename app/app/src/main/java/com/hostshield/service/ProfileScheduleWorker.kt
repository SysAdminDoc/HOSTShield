package com.hostshield.service

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.*
import com.hostshield.data.database.ProfileDao
import com.hostshield.data.model.BlockingProfile
import com.hostshield.data.preferences.AppPreferences
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import android.net.wifi.WifiManager
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit

// Profile scheduler worker
// Runs every 15 minutes to check if a scheduled profile should be activated/deactivated.

@HiltWorker
class ProfileScheduleWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val profileDao: ProfileDao,
    private val prefs: AppPreferences,
    private val iptablesManager: IptablesManager,
    private val sourceCoordinator: BlocklistSourceCoordinator,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        try {
            val profiles = profileDao.getAllProfilesList()
            if (profiles.isEmpty()) return Result.success()

            val now = LocalDateTime.now()
            val currentDay = now.dayOfWeek.value % 7 // 0=Sunday convention
            val currentTime = now.toLocalTime()

            // Get current WiFi SSID for network-aware matching
            val currentSsid = getCurrentSsid(applicationContext)

            var targetProfile: BlockingProfile? = null

            for (profile in profiles) {
                // WiFi SSID matching: if profile has SSIDs configured, check them first
                if (profile.wifiSsids.isNotBlank()) {
                    val ssids = profile.wifiSsids.split(",").map { it.trim().lowercase() }.filter { it.isNotBlank() }
                    if (ssids.isNotEmpty() && currentSsid != null && currentSsid.lowercase() in ssids) {
                        targetProfile = profile
                        break // WiFi match takes priority over time-based scheduling
                    }
                }

                if (profile.scheduleStart.isBlank() || profile.scheduleEnd.isBlank()) continue

                val days = profile.daysOfWeek.split(",").mapNotNull { it.trim().toIntOrNull() }.toSet()

                val start = parseTime(profile.scheduleStart) ?: continue
                val end = parseTime(profile.scheduleEnd) ?: continue

                val overnight = start > end
                val inWindow = if (!overnight) {
                    currentTime in start..end
                } else {
                    // Overnight: e.g. 22:00 to 06:00
                    currentTime >= start || currentTime <= end
                }
                if (!inWindow) continue

                // The scheduled day is the day the window STARTED. For the
                // after-midnight tail of an overnight window that's yesterday —
                // gating on the current day tore "Fri 22:00-06:00, days=Fri"
                // down at midnight, six hours early.
                val windowStartDay = if (overnight && currentTime <= end) {
                    (currentDay + 6) % 7
                } else {
                    currentDay
                }
                if (windowStartDay !in days) continue

                targetProfile = profile
                break
            }

            val activeProfile = profileDao.getActiveProfile()

            if (targetProfile != null && activeProfile?.id != targetProfile.id) {
                // Activate the scheduled profile (single transaction — a non-atomic
                // deactivateAll+activate pair can leave no profile active on interruption)
                profileDao.activateExclusive(targetProfile.id)

                // Rebuild in-memory blocklist — the running DNS proxy reads from
                // BlocklistHolder, so this takes effect immediately for both
                // root mode (RootDnsLogger) and VPN mode (DnsVpnService).
                val rebuild = sourceCoordinator.rebuildBlocklistHolder()

                prefs.setLastApplyTime(System.currentTimeMillis())
                prefs.setLastApplyCount(rebuild.domainCount)

                // Apply iptables firewall if enabled and auto-apply is on
                val fwEnabled = prefs.networkFirewallEnabled.first()
                val autoApply = prefs.autoApplyFirewall.first()
                if (fwEnabled && autoApply) {
                    iptablesManager.applyRules()
                }
            } else if (targetProfile == null && activeProfile != null &&
                (activeProfile.scheduleStart.isNotBlank() || activeProfile.wifiSsids.isNotBlank())
            ) {
                // The active profile is schedule- or SSID-driven and no longer
                // matches: deactivate it and rebuild so its narrowed source set
                // reverts to all enabled sources (mirrors the activation rebuild).
                profileDao.deactivateAll()
                val rebuild = sourceCoordinator.rebuildBlocklistHolder()
                prefs.setLastApplyTime(System.currentTimeMillis())
                prefs.setLastApplyCount(rebuild.domainCount)
            }
        } catch (e: Exception) {
            Log.e("ProfileSchedule", "Profile schedule check failed: ${e.message}", e)
            return if (runAttemptCount < 5) Result.retry() else Result.failure()
        }

        return Result.success()
    }

    @Suppress("DEPRECATION")
    private fun getCurrentSsid(context: android.content.Context): String? {
        return try {
            val wifiManager = context.applicationContext.getSystemService(android.content.Context.WIFI_SERVICE) as? WifiManager
            val info = wifiManager?.connectionInfo
            val ssid = info?.ssid?.removeSurrounding("\"")
            if (ssid.isNullOrBlank() || ssid == "<unknown ssid>") null else ssid
        } catch (_: Exception) { null }
    }

    private fun parseTime(time: String): LocalTime? = try {
        LocalTime.parse(time, DateTimeFormatter.ofPattern("HH:mm"))
    } catch (_: Exception) { null }

    companion object {
        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<ProfileScheduleWorker>(
                15, TimeUnit.MINUTES
            ).setConstraints(
                Constraints.Builder().setRequiresBatteryNotLow(true).build()
            ).setBackoffCriteria(
                BackoffPolicy.EXPONENTIAL,
                WorkRequest.MIN_BACKOFF_MILLIS,
                TimeUnit.MILLISECONDS
            ).build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                "profile_schedule", ExistingPeriodicWorkPolicy.KEEP, request
            )
        }
    }
}
