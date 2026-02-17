package com.hostshield.service

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.*
import com.hostshield.data.database.ProfileDao
import com.hostshield.data.model.BlockingProfile
import com.hostshield.data.model.RuleType
import com.hostshield.data.preferences.AppPreferences
import com.hostshield.data.repository.HostShieldRepository
import com.hostshield.data.source.SourceDownloader
import com.hostshield.domain.BlocklistHolder
import com.hostshield.domain.parser.HostsParser
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit

// HostShield v1.6.0 — Profile Scheduler
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

            var targetProfile: BlockingProfile? = null

            for (profile in profiles) {
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
                val sources = repository.getEnabledSourcesList()
                val allDomains = mutableSetOf<String>()
                for (source in sources) {
                    downloader.download(source).onSuccess { dl ->
                        if (!dl.notModified) {
                            HostsParser.parse(dl.content).forEach { allDomains.add(it.hostname) }
                        }
                    }
                }
                val blockRules = repository.getEnabledRulesByType(RuleType.BLOCK)
                blockRules.filter { !it.isWildcard }.forEach { allDomains.add(it.hostname.lowercase()) }
                val allowRules = repository.getEnabledRulesByType(RuleType.ALLOW)
                allowRules.filter { !it.isWildcard }.forEach { allDomains.remove(it.hostname.lowercase()) }
                blocklistHolder.update(allDomains, repository.getEnabledWildcards())

                prefs.setLastApplyTime(System.currentTimeMillis())
                prefs.setLastApplyCount(allDomains.size)

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
        } catch (_: Exception) { }

        return Result.success()
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
            ).build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                "profile_schedule", ExistingPeriodicWorkPolicy.KEEP, request
            )
        }
    }
}
