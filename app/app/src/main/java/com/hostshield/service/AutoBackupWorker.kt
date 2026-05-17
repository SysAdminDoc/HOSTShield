package com.hostshield.service

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.*
import com.hostshield.data.preferences.AppPreferences
import com.hostshield.util.BackupRestoreUtil
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

// Automatic backup worker
// Creates JSON backup to app-private storage on configurable interval.
// Keeps last 5 backups, auto-deletes older ones.

@HiltWorker
class AutoBackupWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val prefs: AppPreferences,
    private val backupRestore: BackupRestoreUtil
) : CoroutineWorker(context, params) {

    companion object {
        const val WORK_NAME = "hostshield_auto_backup"
        private const val TAG = "AutoBackup"
        private const val MAX_BACKUPS = 5

        fun schedule(context: Context, intervalDays: Int) {
            val request = PeriodicWorkRequestBuilder<AutoBackupWorker>(
                intervalDays.toLong(), TimeUnit.DAYS
            ).setConstraints(
                Constraints.Builder()
                    .setRequiresBatteryNotLow(true)
                    .build()
            ).build()

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

        fun getBackupDir(context: Context): File {
            val dir = File(context.filesDir, "auto_backups")
            if (!dir.exists()) dir.mkdirs()
            return dir
        }
    }

    override suspend fun doWork(): Result {
        return try {
            val enabled = prefs.autoBackupEnabled.first()
            if (!enabled) return Result.success()

            val json = backupRestore.createBackup()
            val dir = getBackupDir(applicationContext)

            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val file = File(dir, "hostshield_backup_$timestamp.json")
            file.writeText(json)

            // Prune old backups, keep last MAX_BACKUPS
            val backups = dir.listFiles()?.sortedByDescending { it.lastModified() } ?: emptyList()
            if (backups.size > MAX_BACKUPS) {
                backups.drop(MAX_BACKUPS).forEach { it.delete() }
            }

            Log.i(TAG, "Auto-backup saved: ${file.name} (${file.length() / 1024}KB), ${backups.size} total")
            Result.success()
        } catch (e: Exception) {
            Log.w(TAG, "Auto-backup failed: ${e.message}")
            Result.failure()
        }
    }
}
