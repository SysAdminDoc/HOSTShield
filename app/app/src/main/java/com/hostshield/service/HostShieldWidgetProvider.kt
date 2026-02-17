package com.hostshield.service

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.hostshield.R

// ══════════════════════════════════════════════════════════════
// HostShield v1.6.0 — Homescreen Widget
// ══════════════════════════════════════════════════════════════

class HostShieldWidgetProvider : AppWidgetProvider() {

    companion object {
        const val ACTION_TOGGLE = "com.hostshield.WIDGET_TOGGLE"
        private const val PREFS_NAME = "hostshield_widget"
        private const val KEY_ENABLED = "widget_enabled"
        private const val KEY_COUNT = "widget_count"

        fun updateWidget(context: Context, isEnabled: Boolean, blockedCount: Int) {
            // Persist state
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
                .putBoolean(KEY_ENABLED, isEnabled)
                .putInt(KEY_COUNT, blockedCount)
                .apply()

            // Update all widget instances
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

            val views = RemoteViews(context.packageName, R.layout.widget_hostshield)

            // Update text
            views.setTextViewText(R.id.widget_status, if (isEnabled) "Protected" else "Disabled")
            views.setTextViewText(
                R.id.widget_count,
                if (count > 0) "${java.text.NumberFormat.getNumberInstance().format(count)} blocked" else ""
            )
            views.setTextViewText(R.id.widget_toggle_text, if (isEnabled) "Disable" else "Enable")

            // Color tinting via text color
            val tealColor = android.graphics.Color.parseColor("#94E2D5")
            val dimColor = android.graphics.Color.parseColor("#585B70")
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
    }

    override fun onUpdate(context: Context, manager: AppWidgetManager, widgetIds: IntArray) {
        widgetIds.forEach { id -> updateAppWidget(context, manager, id) }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_TOGGLE) {
            // Launch activity to handle toggle (needs root/VPN permission context)
            val launchIntent = Intent(context, com.hostshield.MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                putExtra("toggle_blocking", true)
            }
            context.startActivity(launchIntent)
        }
    }
}
