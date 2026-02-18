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

// HostShield v2.0.0 - Log Cleanup Worker
// Cleans dns_logs and connection_log tables based on retention settings.
// DNS logs: user-configurable (default 7 days)
// Connection logs: 3 days (shorter — these are higher volume from iptables)

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
        private const val CONNECTION_LOG_RETENTION_DAYS = 3

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
            val dnsCutoff = System.currentTimeMillis() - (retentionDays.toLong() * 24 * 60 * 60 * 1000)

            // Iterative batch deletion -- avoids ANR on large tables.
            // Deletes 1000 rows at a time with 50ms yield between batches.
            var totalDeleted = 0
            var deleted: Int
            do {
                deleted = logDao.deleteOldestBatch(dnsCutoff, 1000)
                totalDeleted += deleted
                if (deleted > 0) kotlinx.coroutines.delay(50) // yield to other DB ops
            } while (deleted >= 1000)

            // Clean connection (firewall) logs — shorter retention (higher volume)
            val connCutoff = System.currentTimeMillis() -
                (CONNECTION_LOG_RETENTION_DAYS.toLong() * 24 * 60 * 60 * 1000)
            connectionLogDao.deleteOlderThan(connCutoff)

            Log.i("LogCleanup", "Cleaned $totalDeleted DNS logs (retention: ${retentionDays}d), " +
                "connection logs (retention: ${CONNECTION_LOG_RETENTION_DAYS}d)")

            Result.success()
        } catch (e: Exception) {
            Log.w("LogCleanup", "Cleanup failed: ${e.message}")
            Result.failure()
        }
    }
}
