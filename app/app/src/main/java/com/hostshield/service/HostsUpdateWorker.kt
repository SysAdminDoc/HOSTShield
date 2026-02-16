package com.hostshield.service

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.*
import com.hostshield.data.preferences.AppPreferences
import com.hostshield.data.repository.HostShieldRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

// ══════════════════════════════════════════════════════════════
// HostShield v0.2.0 — Auto-Update Worker
// ══════════════════════════════════════════════════════════════

@HiltWorker
class HostsUpdateWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val repository: HostShieldRepository,
    private val prefs: AppPreferences
) : CoroutineWorker(context, workerParams) {

    companion object {
        const val WORK_NAME = "hostshield_update"
        const val TAG = "hosts_update"

        fun schedule(context: Context, intervalHours: Int, wifiOnly: Boolean) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(
                    if (wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED
                )
                .build()

            val request = PeriodicWorkRequestBuilder<HostsUpdateWorker>(
                intervalHours.toLong(), TimeUnit.HOURS
            )
                .setConstraints(constraints)
                .addTag(TAG)
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    WorkRequest.MIN_BACKOFF_MILLIS,
                    TimeUnit.MILLISECONDS
                )
                .build()

            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(
                    WORK_NAME,
                    ExistingPeriodicWorkPolicy.UPDATE,
                    request
                )
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        }

        /** Run an immediate one-shot update. */
        fun runNow(context: Context) {
            val request = OneTimeWorkRequestBuilder<HostsUpdateWorker>()
                .addTag(TAG)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .build()

            WorkManager.getInstance(context).enqueue(request)
        }
    }

    override suspend fun doWork(): Result {
        return try {
            val isEnabled = prefs.isEnabled.first()
            if (!isEnabled) return Result.success()

            val ipv4 = prefs.ipv4Redirect.first()
            val ipv6 = prefs.ipv6Redirect.first()
            val includeV6 = prefs.includeIpv6.first()

            val result = repository.applyBlocking(
                redirectIp4 = ipv4,
                redirectIp6 = ipv6,
                includeIpv6 = includeV6
            )

            result.fold(
                onSuccess = { count ->
                    prefs.setLastApplyTime(System.currentTimeMillis())
                    prefs.setLastApplyCount(count)
                    Result.success()
                },
                onFailure = {
                    if (runAttemptCount < 3) Result.retry() else Result.failure()
                }
            )
        } catch (e: Exception) {
            if (runAttemptCount < 3) Result.retry() else Result.failure()
        }
    }
}
