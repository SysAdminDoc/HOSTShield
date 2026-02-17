package com.hostshield.service

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.*
import com.hostshield.data.database.ConnectionLogDao
import com.hostshield.data.database.DnsLogDao
import com.hostshield.data.preferences.AppPreferences
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

// HostShield v1.6.0 - Log Cleanup Worker
// Cleans dns_logs and connection_log tables based on retention setting.

@HiltWorker
class LogCleanupWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val logDao: DnsLogDao,
    private val connectionLogDao: ConnectionLogDao,
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

            // Iterative batch deletion -- avoids ANR on large tables.
            // Deletes 1000 rows at a time with 50ms yield between batches.
            var totalDeleted = 0
            var deleted: Int
            do {
                deleted = logDao.deleteOldestBatch(cutoff, 1000)
                totalDeleted += deleted
                if (deleted > 0) kotlinx.coroutines.delay(50) // yield to other DB ops
            } while (deleted >= 1000)

            // Clean connection (firewall) logs -- typically much smaller
            connectionLogDao.deleteOlderThan(cutoff)

            Log.i("LogCleanup", "Cleaned $totalDeleted DNS logs (retention: ${retentionDays}d)")

            Result.success()
        } catch (e: Exception) {
            Log.w("LogCleanup", "Cleanup failed: ${e.message}")
            Result.failure()
        }
    }
}
