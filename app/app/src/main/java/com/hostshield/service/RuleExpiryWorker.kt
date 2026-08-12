package com.hostshield.service

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.BackoffPolicy
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.WorkRequest
import com.hostshield.data.database.UserRuleDao
import com.hostshield.data.preferences.AppPreferences
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

/**
 * Disables expired user rules even when the activity and app process are not
 * running. WorkManager persists this periodic reconciliation across reboot and
 * the database query is the source of truth for rules created by imports or
 * backup restores.
 */
@HiltWorker
class RuleExpiryWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val userRuleDao: UserRuleDao,
    private val prefs: AppPreferences,
    private val sourceCoordinator: BlocklistSourceCoordinator,
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        return try {
            val disabled = userRuleDao.disableExpired(System.currentTimeMillis())
            if (disabled > 0 && prefs.isEnabled.first()) {
                // Rebuild from the same coordinator used by VPN/root/proxy
                // startup so an expired wildcard, regex, block, or allow rule
                // is removed from the live snapshot as well as the database.
                sourceCoordinator.rebuildBlocklistHolder()
                Log.i(TAG, "Disabled $disabled expired user rule(s)")
            }
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Rule expiry reconciliation failed", e)
            if (runAttemptCount < 5) Result.retry() else Result.failure()
        }
    }

    companion object {
        const val WORK_NAME = "hostshield_rule_expiry"
        private const val TAG = "RuleExpiryWorker"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<RuleExpiryWorker>(
                15,
                TimeUnit.MINUTES,
            )
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    WorkRequest.MIN_BACKOFF_MILLIS,
                    TimeUnit.MILLISECONDS,
                )
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                request,
            )
        }
    }
}
