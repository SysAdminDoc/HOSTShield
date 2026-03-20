package com.hostshield.service

import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.hilt.work.HiltWorker
import androidx.work.*
import com.hostshield.data.database.HostSourceDao
import com.hostshield.data.model.SourceHealth
import com.hostshield.data.source.SourceDownloader
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit

// HostShield v4.3.0 - Source Health Monitor with notifications

@HiltWorker
class SourceHealthWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val sourceDao: HostSourceDao,
    private val downloader: SourceDownloader
) : CoroutineWorker(context, params) {

    companion object {
        const val WORK_NAME = "hostshield_health_check"
        private const val STALE_THRESHOLD_MS = 7L * 24 * 60 * 60 * 1000 // 7 days
        private const val DEAD_FAILURE_THRESHOLD = 5
        private const val NOTIFICATION_ID_HEALTH = 200

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<SourceHealthWorker>(
                12, TimeUnit.HOURS
            )
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .build()

            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(
                    WORK_NAME,
                    ExistingPeriodicWorkPolicy.KEEP,
                    request
                )
        }

        fun runNow(context: Context) {
            val request = OneTimeWorkRequestBuilder<SourceHealthWorker>()
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
            val sources = sourceDao.getAllSourcesList()
            val newlyDead = mutableListOf<String>()

            for (source in sources) {
                val wasDead = source.health == SourceHealth.DEAD
                val validationResult = downloader.validate(source.url)

                validationResult.onSuccess { lineCount ->
                    val isStale = source.lastUpdated > 0 &&
                            (System.currentTimeMillis() - source.lastUpdated) > STALE_THRESHOLD_MS

                    val health = when {
                        lineCount == 0 -> SourceHealth.ERROR
                        isStale -> SourceHealth.STALE
                        else -> SourceHealth.OK
                    }

                    sourceDao.updateHealth(
                        id = source.id,
                        health = health,
                        error = if (lineCount == 0) "Source returned 0 entries" else "",
                        failures = 0
                    )
                }.onFailure { err ->
                    val failures = source.consecutiveFailures + 1
                    val health = if (failures >= DEAD_FAILURE_THRESHOLD) SourceHealth.DEAD else SourceHealth.ERROR

                    sourceDao.updateHealth(
                        id = source.id,
                        health = health,
                        error = err.message ?: "Unknown error",
                        failures = failures
                    )

                    // Track sources that just transitioned to DEAD
                    if (!wasDead && health == SourceHealth.DEAD) {
                        newlyDead.add(source.label)
                    }
                }
            }

            // Notify user about newly dead sources
            if (newlyDead.isNotEmpty()) {
                notifyDeadSources(newlyDead)
            }

            Result.success()
        } catch (e: Exception) {
            if (runAttemptCount < 2) Result.retry() else Result.failure()
        }
    }

    private fun notifyDeadSources(labels: List<String>) {
        try {
            val nm = applicationContext.getSystemService(NotificationManager::class.java) ?: return
            val text = if (labels.size == 1) {
                "${labels[0]} is unreachable after $DEAD_FAILURE_THRESHOLD failures"
            } else {
                "${labels.size} sources unreachable: ${labels.joinToString(", ")}"
            }

            val notification = NotificationCompat.Builder(applicationContext, DnsVpnService.ALERT_CHANNEL_ID)
                .setContentTitle("Source Health Alert")
                .setContentText(text)
                .setStyle(NotificationCompat.BigTextStyle().bigText(text))
                .setSmallIcon(android.R.drawable.ic_dialog_alert)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .build()

            nm.notify(NOTIFICATION_ID_HEALTH, notification)
        } catch (_: Exception) { }
    }
}
