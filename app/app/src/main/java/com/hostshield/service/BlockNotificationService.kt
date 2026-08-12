package com.hostshield.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.core.app.NotificationCompat
import com.hostshield.R
import com.hostshield.data.database.DnsLogDao
import com.hostshield.data.model.DnsLogEntry
import com.hostshield.data.preferences.AppPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Monitors DNS logs for suspicious activity and sends notifications.
 *
 * Alert: high-frequency tracker — an app makes >50 blocked queries in 5 minutes.
 *
 * Notifications are rate-limited to max 1 per app per 15 minutes. Started by the
 * active protection service (VPN, root, or DNS proxy).
 */
@Singleton
class BlockNotificationService @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val dnsLogDao: DnsLogDao,
    private val prefs: AppPreferences,
    private val notificationTokens: BlockNotificationTokenStore,
) {
    companion object {
        private const val TAG = "BlockNotify"
        private const val CHANNEL_ID = "hostshield_block_alerts"
        private const val BURST_THRESHOLD = 50       // queries in window
        private const val BURST_WINDOW_MS = 300_000L // 5 minutes
        private const val NOTIFY_COOLDOWN_MS = 900_000L // 15 min per app
        private const val DOMAIN_NOTIFY_COOLDOWN_MS = 900_000L // 15 min per domain
        private const val DOMAIN_LOG_BATCH_SIZE = 50
        private const val POLL_INTERVAL_MS = 30_000L // check every 30s
    }

    private var scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var monitorJob: Job? = null

    // packageName -> last notification timestamp
    private val lastNotified = ConcurrentHashMap<String, Long>()
    // hostname -> last blocked-domain notification timestamp
    private val lastDomainNotified = ConcurrentHashMap<String, Long>()
    private var lastSeenBlockedLogId = 0L
    private var notificationId = 5000

    fun start() {
        if (monitorJob?.isActive == true) return
        createChannel()
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        monitorJob = scope.launch {
            lastSeenBlockedLogId = runCatching { dnsLogDao.getLatestBlockedLogId() ?: 0L }
                .getOrDefault(0L)
            while (isActive) {
                try { checkForBlockedDomains() } catch (e: Exception) {
                    Log.w(TAG, "Blocked-domain check failed: ${e.message}")
                }
                try { checkForBursts() } catch (e: Exception) {
                    Log.w(TAG, "Check failed: ${e.message}")
                }
                delay(POLL_INTERVAL_MS)
            }
        }
    }

    fun stop() {
        monitorJob?.cancel()
        monitorJob = null
        scope.cancel()
    }

    /** Notify only for rows inserted since the monitor started or last poll. */
    private suspend fun checkForBlockedDomains() {
        val entries = dnsLogDao.getBlockedLogsAfterId(
            afterId = lastSeenBlockedLogId,
            limit = DOMAIN_LOG_BATCH_SIZE,
        )
        if (entries.isEmpty()) return

        lastSeenBlockedLogId = maxOf(lastSeenBlockedLogId, entries.maxOf { it.id })
        if (!prefs.blockedDomainNotifications.first()) return

        val now = System.currentTimeMillis()
        entries.asSequence()
            .filter { it.hostname.isNotBlank() }
            .distinctBy { it.hostname.trim().lowercase().removeSuffix(".") }
            .forEach { entry ->
                val hostname = entry.hostname.trim().lowercase().removeSuffix(".")
                val previous = lastDomainNotified[hostname] ?: 0L
                if (now - previous < DOMAIN_NOTIFY_COOLDOWN_MS) return@forEach
                sendBlockedDomainNotification(entry)
                lastDomainNotified[hostname] = now
            }

        if (lastDomainNotified.size > 100) {
            val cutoff = now - DOMAIN_NOTIFY_COOLDOWN_MS * 2
            lastDomainNotified.entries.removeAll { it.value < cutoff }
        }
    }

    private fun sendBlockedDomainNotification(entry: DnsLogEntry) {
        val hostname = entry.hostname.trim().lowercase().removeSuffix(".")
        val source = entry.decisionSource.ifBlank { context.getString(R.string.app_name) }
        val reason = entry.decisionReason
            .takeIf { it.isNotBlank() && it != "none" }
            ?.let(::formatNotificationReason)
            ?: context.getString(R.string.notification_blocked_reason_default)
        val body = context.getString(R.string.notification_blocked_body, source, reason)
        val baseRequestCode = notificationId * 10
        val whyPending = logsPendingIntent(
            action = BlockNotificationActions.WHY,
            entry = entry,
            requestCode = baseRequestCode,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_shield)
            .setContentTitle(context.getString(R.string.notification_blocked_title, hostname))
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setAutoCancel(true)
            .setContentIntent(whyPending)
            .setGroup("hostshield_blocks")
            .addAction(
                0,
                context.getString(R.string.notification_allow_once),
                logsPendingIntent(BlockNotificationActions.ALLOW_ONCE, entry, baseRequestCode + 1),
            )
            .addAction(
                0,
                context.getString(R.string.notification_allow_10_minutes),
                logsPendingIntent(BlockNotificationActions.ALLOW_10_MINUTES, entry, baseRequestCode + 2),
            )
            .addAction(
                0,
                context.getString(R.string.notification_allow_always),
                logsPendingIntent(BlockNotificationActions.ALLOW_ALWAYS, entry, baseRequestCode + 3),
            )
            .addAction(
                0,
                context.getString(R.string.notification_why),
                whyPending,
            )
            .build()

        notify(notification)
    }

    private fun logsPendingIntent(action: String, entry: DnsLogEntry, requestCode: Int) =
        android.app.PendingIntent.getActivity(
            context,
            requestCode,
            Intent(context, com.hostshield.MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                this.action = Intent.ACTION_VIEW
                data = Uri.Builder()
                    .scheme("hostshield")
                    .authority("logs")
                    .appendQueryParameter("action", action)
                    .appendQueryParameter("hostname", entry.hostname)
                    .appendQueryParameter("source", entry.decisionSource)
                    .appendQueryParameter("reason", entry.decisionReason)
                    .appendQueryParameter(
                        "token",
                        notificationTokens.issue(
                            action = action,
                            hostname = entry.hostname,
                            source = entry.decisionSource,
                            reason = entry.decisionReason,
                        ),
                    )
                    .build()
            },
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE,
        )

    private fun notify(notification: android.app.Notification) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(notificationId++, notification)
        if (notificationId > 5100) notificationId = 5000
    }

    private fun formatNotificationReason(reason: String): String = reason
        .replace('_', ' ')
        .split(' ')
        .joinToString(" ") { word -> word.replaceFirstChar { it.uppercase() } }

    private suspend fun checkForBursts() {
        val since = System.currentTimeMillis() - BURST_WINDOW_MS
        val now = System.currentTimeMillis()

        try {
            // Get apps with high blocked query counts in the recent window only —
            // all-time counts would fire "high tracker activity" alerts forever.
            val topApps = dnsLogDao.getTopBlockedAppsSince(since = since, limit = 10).first()
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
        // "Firewall this app" action — launches firewall screen
        val firewallIntent = android.content.Intent(context, com.hostshield.MainActivity::class.java).apply {
            flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK or
                android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP
            action = android.content.Intent.ACTION_VIEW
            data = android.net.Uri.parse("hostshield://firewall")
        }
        val firewallPending = android.app.PendingIntent.getActivity(
            context, notificationId + 1000, firewallIntent,
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
        )

        // "View logs" action
        val logsIntent = android.content.Intent(context, com.hostshield.MainActivity::class.java).apply {
            flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK or
                android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP
            action = "com.hostshield.SHORTCUT_LOGS"
        }
        val logsPending = android.app.PendingIntent.getActivity(
            context, notificationId + 2000, logsIntent,
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_shield)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setAutoCancel(true)
            .setContentIntent(logsPending)
            .setGroup("hostshield_blocks")
            .addAction(0, "Firewall App", firewallPending)
            .addAction(0, "View Logs", logsPending)
            .build()

        notify(notification)
    }

    private fun createChannel() {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.notification_block_channel),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = context.getString(R.string.notification_block_channel_description)
        }
        nm.createNotificationChannel(channel)
    }
}
