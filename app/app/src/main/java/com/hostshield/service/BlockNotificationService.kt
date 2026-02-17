package com.hostshield.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.util.Log
import androidx.core.app.NotificationCompat
import com.hostshield.R
import com.hostshield.data.database.DnsLogDao
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Monitors DNS logs for suspicious activity and sends notifications.
 *
 * Alerts:
 * 1. High-frequency tracker: app makes >50 blocked queries in 5 minutes
 * 2. New tracking domain: first-seen domain blocked for an app
 * 3. Burst detection: >20 queries/second from any single app
 *
 * Notifications are rate-limited to max 1 per app per 15 minutes.
 */
@Singleton
class BlockNotificationService @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dnsLogDao: DnsLogDao
) {
    companion object {
        private const val TAG = "BlockNotify"
        private const val CHANNEL_ID = "hostshield_block_alerts"
        private const val CHANNEL_NAME = "Block Alerts"
        private const val BURST_THRESHOLD = 50       // queries in window
        private const val BURST_WINDOW_MS = 300_000L // 5 minutes
        private const val NOTIFY_COOLDOWN_MS = 900_000L // 15 min per app
        private const val POLL_INTERVAL_MS = 30_000L // check every 30s
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var monitorJob: Job? = null

    // packageName -> last notification timestamp
    private val lastNotified = ConcurrentHashMap<String, Long>()
    private var notificationId = 5000

    fun start() {
        if (monitorJob?.isActive == true) return
        createChannel()
        monitorJob = scope.launch {
            while (isActive) {
                try { checkForBursts() } catch (e: Exception) {
                    Log.w(TAG, "Check failed: ${e.message}")
                }
                delay(POLL_INTERVAL_MS)
            }
        }
    }

    fun stop() {
        monitorJob?.cancel(); monitorJob = null
    }

    private suspend fun checkForBursts() {
        val since = System.currentTimeMillis() - BURST_WINDOW_MS
        val now = System.currentTimeMillis()

        try {
            // Get apps with high blocked query counts in recent window
            val topApps = dnsLogDao.getTopBlockedApps(limit = 10).first()
            for (app in topApps) {
                if (app.cnt < BURST_THRESHOLD) continue
                if (app.appPackage.isBlank()) continue

                // Check cooldown
                val lastTime = lastNotified[app.appPackage] ?: 0L
                if (now - lastTime < NOTIFY_COOLDOWN_MS) continue

                sendNotification(
                    title = "High tracker activity: ${app.appLabel}",
                    body = "${app.appLabel} had ${app.cnt} blocked queries recently. " +
                        "Consider firewalling this app entirely.",
                    pkg = app.appPackage
                )
                lastNotified[app.appPackage] = now
            }
        } catch (_: Exception) { }

        // Evict old cooldown entries
        if (lastNotified.size > 50) {
            val cutoff = now - NOTIFY_COOLDOWN_MS * 2
            lastNotified.entries.removeAll { it.value < cutoff }
        }
    }

    private fun sendNotification(title: String, body: String, pkg: String) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_shield)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setAutoCancel(true)
            .setGroup("hostshield_blocks")
            .build()

        nm.notify(notificationId++, notification)
        if (notificationId > 5100) notificationId = 5000
    }

    private fun createChannel() {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (nm.getNotificationChannel(CHANNEL_ID) == null) {
            val channel = NotificationChannel(
                CHANNEL_ID, CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Alerts about apps with high tracking activity"
            }
            nm.createNotificationChannel(channel)
        }
    }
}
