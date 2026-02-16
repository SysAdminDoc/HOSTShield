package com.hostshield.service

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.*
import com.hostshield.data.database.DnsLogDao
import com.hostshield.data.preferences.AppPreferences
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

// HostShield v0.3.0 - Log Cleanup Worker

@HiltWorker
class LogCleanupWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val logDao: DnsLogDao,
    private val prefs: AppPreferences
) : CoroutineWorker(context, params) {

    companion object {
        const val WORK_NAME = "hostshield_log_cleanup"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<LogCleanupWorker>(
                12, TimeUnit.HOURS
            ).build()

            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(
                    WORK_NAME,
                    ExistingPeriodicWorkPolicy.KEEP,
                    request
                )
        }
    }

    override suspend fun doWork(): Result {
        return try {
            val retentionDays = prefs.logRetentionDays.first()
            val cutoff = System.currentTimeMillis() - (retentionDays.toLong() * 24 * 60 * 60 * 1000)
            logDao.deleteOlderThan(cutoff)
            Result.success()
        } catch (e: Exception) {
            Result.failure()
        }
    }
}
