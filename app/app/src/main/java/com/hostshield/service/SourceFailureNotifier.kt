package com.hostshield.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

data class SourceFailureNotice(
    val label: String,
    val url: String,
    val error: String,
    val httpStatus: Int = 0,
    val lastSuccessfulUpdate: Long = 0L,
    val consecutiveFailures: Int = 0,
)

@Singleton
class SourceFailureNotifier @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    fun notifyFailures(notices: List<SourceFailureNotice>) {
        if (notices.isEmpty()) return

        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        NotificationChannel(
            DnsVpnService.ALERT_CHANNEL_ID,
            "HostShield Alerts",
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply { description = "Source health and system alerts" }
            .let { nm.createNotificationChannel(it) }

        val primary = notices.maxBy { it.consecutiveFailures }
        val title = if (notices.any { it.consecutiveFailures >= DEAD_FAILURE_THRESHOLD }) {
            "Source health alert"
        } else {
            "Source download failed"
        }
        val content = if (notices.size == 1) {
            "${primary.label}: ${primary.failureSummary()}"
        } else {
            "${notices.size} sources failed. ${primary.label}: ${primary.failureSummary()}"
        }
        val expanded = notices
            .sortedByDescending { it.consecutiveFailures }
            .take(5)
            .joinToString("\n") { notice ->
                "${notice.label}: ${notice.failureSummary()} (${formatLastSuccess(notice.lastSuccessfulUpdate)})"
            } + if (notices.size > 5) "\n+${notices.size - 5} more" else ""

        val notification = NotificationCompat.Builder(context, DnsVpnService.ALERT_CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(content)
            .setStyle(NotificationCompat.BigTextStyle().bigText(expanded))
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        nm.notify(NOTIFICATION_ID_HEALTH, notification)
    }

    private fun SourceFailureNotice.failureSummary(): String {
        val status = if (httpStatus > 0) "HTTP $httpStatus" else "network"
        val reason = error.ifBlank { "Unknown error" }
        return "$status - $reason"
    }

    private fun formatLastSuccess(ms: Long): String {
        if (ms <= 0L) return "last success never"
        return try {
            "last success " + Instant.ofEpochMilli(ms)
                .atZone(ZoneId.systemDefault())
                .format(DateTimeFormatter.ofPattern("MMM d, h:mm a"))
        } catch (_: Exception) {
            "last success unknown"
        }
    }

    private companion object {
        const val DEAD_FAILURE_THRESHOLD = 5
        const val NOTIFICATION_ID_HEALTH = 200
    }
}
