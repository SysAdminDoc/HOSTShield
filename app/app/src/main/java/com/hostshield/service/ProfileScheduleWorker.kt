package com.hostshield.service

import android.content.Context
import android.content.Intent
import androidx.hilt.work.HiltWorker
import androidx.work.*
import com.hostshield.data.database.ProfileDao
import com.hostshield.data.model.BlockMethod
import com.hostshield.data.model.BlockingProfile
import com.hostshield.data.preferences.AppPreferences
import com.hostshield.data.repository.HostShieldRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit

// HostShield v1.0.0 — Profile Scheduler
// Runs every 15 minutes to check if a scheduled profile should be activated/deactivated.

@HiltWorker
class ProfileScheduleWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val profileDao: ProfileDao,
    private val repository: HostShieldRepository,
    private val prefs: AppPreferences
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

                // Re-apply blocking with the new profile's sources
                val method = prefs.blockMethod.first()
                if (method == BlockMethod.ROOT_HOSTS) {
                    repository.applyBlocking(
                        redirectIp4 = prefs.ipv4Redirect.first(),
                        redirectIp6 = prefs.ipv6Redirect.first(),
                        includeIpv6 = prefs.includeIpv6.first()
                    ) { }
                }
                // VPN mode picks up blocklist changes on next query cycle
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
