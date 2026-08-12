package com.hostshield.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.hostshield.MainActivity

/** Builds and publishes the persistent VPN status notification. */
internal class VpnNotificationController(
    private val service: DnsVpnService,
    private val isPaused: () -> Boolean,
    private val transportLabel: () -> String,
    private val dnsTrapEnabled: () -> Boolean,
    private val publishBlockedCount: (Int) -> Unit,
) {
    fun createChannels() {
        val notificationManager = service.getSystemService(NotificationManager::class.java)
        NotificationChannel(
            DnsVpnService.CHANNEL_ID,
            "HostShield VPN",
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "VPN blocking status"
            setShowBadge(false)
        }.let { notificationManager.createNotificationChannel(it) }
        NotificationChannel(
            DnsVpnService.ALERT_CHANNEL_ID,
            "HostShield Alerts",
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply { description = "Source health and system alerts" }
            .let { notificationManager.createNotificationChannel(it) }
    }

    fun build(blocked: Int): Notification {
        val contentIntent = PendingIntent.getActivity(
            service,
            0,
            Intent(service, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val stopIntent = PendingIntent.getService(
            service,
            1,
            Intent(service, DnsVpnService::class.java).apply { action = DnsVpnService.ACTION_STOP },
            PendingIntent.FLAG_IMMUTABLE,
        )

        val paused = isPaused()
        val subtitle = buildString {
            if (paused) {
                append("Blocking paused")
            } else {
                append(if (blocked > 0) "$blocked blocked" else "DNS filtering active")
                transportLabel().takeIf { it.isNotBlank() }?.let { append(" | $it") }
                if (dnsTrapEnabled()) append(" | Trap")
            }
        }
        val builder = NotificationCompat.Builder(service, DnsVpnService.CHANNEL_ID)
            .setContentTitle(if (paused) "HostShield Paused" else "HostShield Active")
            .setContentText(subtitle)
            .setSmallIcon(com.hostshield.R.drawable.ic_shield)
            .setOngoing(true)
            .setContentIntent(contentIntent)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)

        if (paused) {
            builder.addAction(0, "Resume", makePausePendingIntent(0, 5))
        } else {
            builder.addAction(0, "Pause 5m", makePausePendingIntent(5, 2))
            builder.addAction(0, "Pause 30m", makePausePendingIntent(30, 3))
        }
        builder.addAction(0, "Stop", stopIntent)
        return builder.build()
    }

    fun update(blocked: Int) {
        publishBlockedCount(blocked)
        service.getSystemService(NotificationManager::class.java)
            .notify(DnsVpnService.NOTIFICATION_ID, build(blocked))
    }

    private fun makePausePendingIntent(minutes: Int, requestCode: Int): PendingIntent =
        PendingIntent.getService(
            service,
            requestCode,
            Intent(service, DnsVpnService::class.java).apply {
                action = DnsVpnService.ACTION_PAUSE
                putExtra("pause_minutes", minutes)
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
}
