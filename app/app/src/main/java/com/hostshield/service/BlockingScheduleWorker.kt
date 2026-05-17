package com.hostshield.service

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.*
import com.hostshield.data.model.BlockMethod
import com.hostshield.data.preferences.AppPreferences
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit

// Scheduled blocking worker
// Checks every 10 minutes if blocking should be auto-enabled/disabled
// based on the user's time schedule.
//
// Modes:
//   "block"   = blocking is ACTIVE during the schedule window
//   "unblock" = blocking is DISABLED during the schedule window (bedtime mode)

@HiltWorker
class BlockingScheduleWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val prefs: AppPreferences
) : CoroutineWorker(context, params) {

    companion object {
        const val WORK_NAME = "hostshield_blocking_schedule"
        private const val TAG = "BlockSchedule"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<BlockingScheduleWorker>(
                10, TimeUnit.MINUTES
            ).setBackoffCriteria(
                BackoffPolicy.EXPONENTIAL,
                WorkRequest.MIN_BACKOFF_MILLIS,
                TimeUnit.MILLISECONDS
            ).build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request
            )
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        }
    }

    override suspend fun doWork(): Result {
        try {
            val enabled = prefs.scheduleEnabled.first()
            if (!enabled) return Result.success()

            val startStr = prefs.scheduleStart.first()
            val endStr = prefs.scheduleEnd.first()
            val mode = prefs.scheduleMode.first()

            val start = parseTime(startStr) ?: return Result.success()
            val end = parseTime(endStr) ?: return Result.success()
            val now = LocalTime.now()

            val inWindow = if (start <= end) {
                now in start..end
            } else {
                // Overnight window (e.g., 22:00 to 07:00)
                now >= start || now <= end
            }

            val isCurrentlyEnabled = prefs.isEnabled.first()
            val method = prefs.blockMethod.first()

            val shouldBeEnabled = when (mode) {
                "block" -> inWindow       // blocking active during window
                "unblock" -> !inWindow    // blocking disabled during window
                else -> return Result.success()
            }

            if (shouldBeEnabled != isCurrentlyEnabled && method != BlockMethod.DISABLED) {
                prefs.setEnabled(shouldBeEnabled)

                if (shouldBeEnabled) {
                    when (method) {
                        BlockMethod.VPN -> {
                            val intent = Intent(applicationContext, DnsVpnService::class.java)
                                .apply { action = DnsVpnService.ACTION_START }
                            applicationContext.startForegroundService(intent)
                        }
                        BlockMethod.ROOT_HOSTS -> RootDnsService.start(applicationContext)
                        else -> { }
                    }
                } else {
                    when (method) {
                        BlockMethod.VPN -> {
                            val intent = Intent(applicationContext, DnsVpnService::class.java)
                                .apply { action = DnsVpnService.ACTION_STOP }
                            applicationContext.startService(intent)
                        }
                        BlockMethod.ROOT_HOSTS -> RootDnsService.stop(applicationContext)
                        else -> { }
                    }
                }

                Log.i(TAG, "Schedule: ${if (shouldBeEnabled) "enabled" else "disabled"} blocking ($mode mode, window $startStr-$endStr)")
                HostShieldWidgetProvider.updateWidget(applicationContext, shouldBeEnabled, 0)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Schedule check failed: ${e.message}", e)
            return if (runAttemptCount < 5) Result.retry() else Result.failure()
        }
        return Result.success()
    }

    private fun parseTime(time: String): LocalTime? = try {
        LocalTime.parse(time, DateTimeFormatter.ofPattern("HH:mm"))
    } catch (_: Exception) { null }
}
