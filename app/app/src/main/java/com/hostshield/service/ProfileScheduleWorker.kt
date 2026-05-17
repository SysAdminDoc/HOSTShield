package com.hostshield.service

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.*
import com.hostshield.data.database.ProfileDao
import com.hostshield.data.model.BlockingProfile
import com.hostshield.data.model.RuleType
import com.hostshield.data.model.SourceHealth
import com.hostshield.data.preferences.AppPreferences
import com.hostshield.data.repository.HostShieldRepository
import com.hostshield.data.source.SourceDownloader
import com.hostshield.data.source.sourceHttpStatus
import com.hostshield.domain.BlocklistHolder
import com.hostshield.domain.parser.HostsParser
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
    private val repository: HostShieldRepository,
    private val prefs: AppPreferences,
    private val iptablesManager: IptablesManager,
    private val downloader: SourceDownloader,
    private val blocklistHolder: BlocklistHolder
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
                if (currentDay !in days) continue

                val start = parseTime(profile.scheduleStart) ?: continue
                val end = parseTime(profile.scheduleEnd) ?: continue

                val inWindow = if (start <= end) {
                    currentTime in start..end
                } else {
                    // Overnight: e.g. 22:00 to 06:00
                    currentTime >= start || currentTime <= end
                }

                if (inWindow) {
                    targetProfile = profile
                    break
                }
            }

            val activeProfile = profileDao.getActiveProfile()

            if (targetProfile != null && activeProfile?.id != targetProfile.id) {
                // Activate the scheduled profile
                profileDao.deactivateAll()
                profileDao.activate(targetProfile.id)

                // Rebuild in-memory blocklist — the running DNS proxy reads from
                // BlocklistHolder, so this takes effect immediately for both
                // root mode (RootDnsLogger) and VPN mode (DnsVpnService).
                val blockSources = repository.getEnabledBlockSources()
                val allowlistSources = repository.getEnabledAllowlistSources()
                val allDomains = mutableSetOf<String>()
                val sourceAllowDomains = mutableSetOf<String>()
                val sourceWildcardBlocks = mutableSetOf<String>()
                val sourceWildcardAllows = mutableSetOf<String>()
                for (source in blockSources) {
                    downloader.download(source).onSuccess { dl ->
                        repository.updateSourceHealth(source.id, SourceHealth.OK, "", 0, 0)
                        if (!dl.notModified) {
                            val parsed = HostsParser.parseForBlocking(dl.content)
                            allDomains.addAll(parsed.blockDomains)
                            sourceAllowDomains.addAll(parsed.allowDomains)
                            sourceWildcardBlocks.addAll(parsed.wildcardBlockDomains)
                            sourceWildcardAllows.addAll(parsed.wildcardAllowDomains)
                        }
                    }.onFailure { err ->
                        val failures = source.consecutiveFailures + 1
                        val health = if (failures >= 5) SourceHealth.DEAD else SourceHealth.ERROR
                        repository.updateSourceHealth(
                            source.id,
                            health,
                            err.message ?: "Unknown error",
                            failures,
                            err.sourceHttpStatus(),
                        )
                    }
                }
                for (source in allowlistSources) {
                    downloader.download(source).onSuccess { dl ->
                        repository.updateSourceHealth(source.id, SourceHealth.OK, "", 0, 0)
                        if (!dl.notModified) {
                            val parsed = HostsParser.parseForAllowing(dl.content)
                            sourceAllowDomains.addAll(parsed.allowDomains)
                            sourceWildcardAllows.addAll(parsed.wildcardAllowDomains)
                        }
                    }.onFailure { err ->
                        val failures = source.consecutiveFailures + 1
                        val health = if (failures >= 5) SourceHealth.DEAD else SourceHealth.ERROR
                        repository.updateSourceHealth(
                            source.id,
                            health,
                            err.message ?: "Unknown error",
                            failures,
                            err.sourceHttpStatus(),
                        )
                    }
                }
                val blockRules = repository.getEnabledRulesByType(RuleType.BLOCK)
                blockRules.filter { !it.isWildcard }.forEach { allDomains.add(it.hostname.lowercase()) }
                val allowRules = repository.getEnabledRulesByType(RuleType.ALLOW)
                allowRules.filter { !it.isWildcard }.forEach { allDomains.remove(it.hostname.lowercase()) }
                allDomains.removeAll(sourceAllowDomains)
                blocklistHolder.updateAsync(
                    allDomains,
                    repository.getEnabledWildcards(),
                    sourceWildcardBlocks = sourceWildcardBlocks,
                    sourceWildcardAllows = sourceWildcardAllows
                )
                val blockingDomainCount = allDomains.size + sourceWildcardBlocks.size

                prefs.setLastApplyTime(System.currentTimeMillis())
                prefs.setLastApplyCount(blockingDomainCount)

                // Apply iptables firewall if enabled and auto-apply is on
                val fwEnabled = prefs.networkFirewallEnabled.first()
                val autoApply = prefs.autoApplyFirewall.first()
                if (fwEnabled && autoApply) {
                    iptablesManager.applyRules()
                }
            } else if (targetProfile == null && activeProfile != null && activeProfile.scheduleStart.isNotBlank()) {
                // No scheduled profile is active; deactivate the scheduled one
                // and fall back to default (no profile active = use all enabled sources)
                profileDao.deactivateAll()
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
