package com.hostshield.service

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.*
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit

// Threat intelligence feed update worker
//
// Runs daily via WorkManager to refresh threat intelligence feeds.
// Downloads malicious IP ranges and domains from curated sources
// (Spamhaus DROP, abuse.ch URLhaus, Emerging Threats, Disconnect).

@HiltWorker
class ThreatIntelWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val threatIntelManager: ThreatIntelManager
) : CoroutineWorker(context, params) {

    companion object {
        const val WORK_NAME = "threat_intel_update"
        const val TAG = "threat_intel"

        fun schedule(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val request = PeriodicWorkRequestBuilder<ThreatIntelWorker>(
                24, TimeUnit.HOURS
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
                    ExistingPeriodicWorkPolicy.KEEP,
                    request
                )

            Log.i(TAG, "Threat intel daily update scheduled")
        }
    }

    override suspend fun doWork(): Result {
        Log.i(TAG, "Starting threat intel feed refresh")
        return try {
            val success = threatIntelManager.refreshFeedsAndPersist()
            if (success) {
                Log.i(TAG, "Threat intel refresh complete: ${threatIntelManager.domainCount} domains, ${threatIntelManager.ipCidrCount} IP CIDRs")
                Result.success()
            } else {
                Log.w(TAG, "Threat intel refresh partial failure, will retry")
                if (runAttemptCount < 5) Result.retry() else Result.failure()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Threat intel refresh failed: ${e.message}", e)
            if (runAttemptCount < 5) Result.retry() else Result.failure()
        }
    }
}
