package com.hostshield.service

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.hostshield.R

// Homescreen toggle widget provider

class HostShieldWidgetProvider : AppWidgetProvider() {

    companion object {
        const val ACTION_TOGGLE = "com.hostshield.WIDGET_TOGGLE"
        private const val PREFS_NAME = "hostshield_widget"
        private const val KEY_ENABLED = "widget_enabled"
        private const val KEY_COUNT = "widget_count"
        private const val KEY_MODE = "widget_mode"
        private const val KEY_BLOCKED_TODAY = "widget_blocked_today"
        private const val KEY_LAST_UPDATE = "widget_last_update"

        fun updateWidget(
            context: Context,
            isEnabled: Boolean,
            blockedCount: Int,
            mode: String? = null,
            blockedToday: Int? = null,
            lastUpdateTime: Long = 0L
        ) {
            val store = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            // Preserve the previously-stored mode/blockedToday when a caller does
            // not supply them, so the lightweight (enabled, count) callers don't
            // wipe fields that the stats/apply callers populate. Always stamp the
            // update time so the "Updated …" line reflects the latest refresh.
            val resolvedMode = mode ?: store.getString(KEY_MODE, "") ?: ""
            val resolvedBlockedToday = blockedToday ?: store.getInt(KEY_BLOCKED_TODAY, 0)
            val resolvedTime = if (lastUpdateTime > 0) lastUpdateTime else System.currentTimeMillis()
            store.edit()
                .putBoolean(KEY_ENABLED, isEnabled)
                .putInt(KEY_COUNT, blockedCount)
                .putString(KEY_MODE, resolvedMode)
                .putInt(KEY_BLOCKED_TODAY, resolvedBlockedToday)
                .putLong(KEY_LAST_UPDATE, resolvedTime)
                .apply()

            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(
                ComponentName(context, HostShieldWidgetProvider::class.java)
            )
            ids.forEach { id -> updateAppWidget(context, manager, id) }
        }

        private fun updateAppWidget(context: Context, manager: AppWidgetManager, widgetId: Int) {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val isEnabled = prefs.getBoolean(KEY_ENABLED, false)
            val count = prefs.getInt(KEY_COUNT, 0)
            val mode = prefs.getString(KEY_MODE, "") ?: ""
            val blockedToday = prefs.getInt(KEY_BLOCKED_TODAY, 0)
            val lastUpdate = prefs.getLong(KEY_LAST_UPDATE, 0L)

            val views = RemoteViews(context.packageName, R.layout.widget_hostshield)
            val nf = java.text.NumberFormat.getNumberInstance()

            // Status
            views.setTextViewText(R.id.widget_status, if (isEnabled) "Protected" else "Disabled")
            views.setTextViewText(R.id.widget_toggle_text, if (isEnabled) "Disable" else "Enable")

            // Mode badge
            views.setTextViewText(R.id.widget_mode, when {
                !isEnabled -> ""
                mode.isNotEmpty() -> mode.uppercase()
                else -> ""
            })

            // Blocklist count
            views.setTextViewText(
                R.id.widget_count,
                if (count > 0) "${nf.format(count)} domains" else ""
            )

            // Blocked today
            views.setTextViewText(
                R.id.widget_today,
                if (blockedToday > 0 && isEnabled) "${nf.format(blockedToday)} blocked today" else ""
            )

            // Last update
            views.setTextViewText(
                R.id.widget_updated,
                if (lastUpdate > 0) "Updated ${formatRelativeTime(lastUpdate)}" else ""
            )

            // Color tinting
            val tealColor = android.graphics.Color.parseColor("#7FFFEA")
            val dimColor = android.graphics.Color.parseColor("#AAB6C8")
            val activeColor = if (isEnabled) tealColor else dimColor
            views.setTextColor(R.id.widget_status, activeColor)
            views.setTextColor(R.id.widget_shield, activeColor)

            // Toggle action
            val toggleIntent = Intent(context, HostShieldWidgetProvider::class.java).apply {
                action = ACTION_TOGGLE
            }
            val pendingIntent = PendingIntent.getBroadcast(
                context, 0, toggleIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_toggle, pendingIntent)

            // Tap anywhere else opens app
            val launchIntent = Intent(context, com.hostshield.MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val launchPending = PendingIntent.getActivity(
                context, 1, launchIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_root, launchPending)

            manager.updateAppWidget(widgetId, views)
        }

        private fun formatRelativeTime(timestampMs: Long): String {
            val diff = System.currentTimeMillis() - timestampMs
            return when {
                diff < 60_000 -> "just now"
                diff < 3_600_000 -> "${diff / 60_000}m ago"
                diff < 86_400_000 -> "${diff / 3_600_000}h ago"
                else -> "${diff / 86_400_000}d ago"
            }
        }
    }

    override fun onUpdate(context: Context, manager: AppWidgetManager, widgetIds: IntArray) {
        widgetIds.forEach { id -> updateAppWidget(context, manager, id) }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_TOGGLE) {
            val launchIntent = Intent(context, com.hostshield.MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                putExtra("toggle_blocking", true)
            }
            context.startActivity(launchIntent)
        }
    }
}
