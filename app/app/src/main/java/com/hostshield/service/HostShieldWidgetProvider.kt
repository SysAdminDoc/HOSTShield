package com.hostshield.service

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.util.Log
import android.widget.RemoteViews
import com.hostshield.R
import com.hostshield.data.model.BlockMethod
import com.hostshield.data.preferences.AppPreferences
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

// Homescreen toggle widget provider

@AndroidEntryPoint
class HostShieldWidgetProvider : AppWidgetProvider() {

    @Inject lateinit var prefs: AppPreferences

    companion object {
        const val ACTION_TOGGLE = "com.hostshield.WIDGET_TOGGLE"
        private const val TAG = "HostShieldWidget"
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
            views.setTextViewText(
                R.id.widget_status,
                context.getString(
                    if (isEnabled) R.string.widget_status_protected else R.string.widget_status_inactive
                )
            )
            views.setTextViewText(
                R.id.widget_toggle_text,
                context.getString(
                    if (isEnabled) R.string.widget_toggle_disable else R.string.widget_toggle_enable
                )
            )

            // Mode badge — human label, not the raw enum constant
            views.setTextViewText(R.id.widget_mode, when {
                !isEnabled -> ""
                else -> when (mode) {
                    BlockMethod.VPN.name -> context.getString(R.string.widget_mode_vpn)
                    BlockMethod.ROOT_HOSTS.name -> context.getString(R.string.widget_mode_root)
                    BlockMethod.DNS_PROXY.name -> context.getString(R.string.widget_mode_proxy)
                    else -> ""
                }.uppercase()
            })

            // Blocklist count
            views.setTextViewText(
                R.id.widget_count,
                if (count > 0) {
                    context.getString(R.string.widget_domains_count, nf.format(count))
                } else ""
            )

            // Blocked today
            views.setTextViewText(
                R.id.widget_today,
                if (blockedToday > 0 && isEnabled) {
                    context.getString(R.string.widget_blocked_today_count, nf.format(blockedToday))
                } else ""
            )

            // Last update
            views.setTextViewText(
                R.id.widget_updated,
                if (lastUpdate > 0) formatUpdatedLabel(context, lastUpdate) else ""
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

        private fun formatUpdatedLabel(context: Context, timestampMs: Long): String {
            val diff = System.currentTimeMillis() - timestampMs
            return when {
                diff < 60_000 -> context.getString(R.string.widget_updated_just_now)
                diff < 3_600_000 -> context.getString(R.string.widget_updated_ago, "${diff / 60_000}m")
                diff < 86_400_000 -> context.getString(R.string.widget_updated_ago, "${diff / 3_600_000}h")
                else -> context.getString(R.string.widget_updated_ago, "${diff / 86_400_000}d")
            }
        }
    }

    override fun onUpdate(context: Context, manager: AppWidgetManager, widgetIds: IntArray) {
        widgetIds.forEach { id -> updateAppWidget(context, manager, id) }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_TOGGLE) {
            // Perform the toggle HERE, mirroring the QS tile. The previous
            // implementation launched MainActivity with a "toggle_blocking"
            // extra that nothing reads — the Enable/Disable button just opened
            // the app (or, under Android 14+ background-activity-launch
            // restrictions, did nothing at all).
            val pendingResult = goAsync()
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    performToggle(context)
                } catch (e: Exception) {
                    Log.e(TAG, "Widget toggle failed: ${e.message}", e)
                } finally {
                    pendingResult.finish()
                }
            }
        }
    }

    private suspend fun performToggle(context: Context) {
        val isEnabled = prefs.isEnabled.first()
        val method = prefs.blockMethod.first()

        if (isEnabled) {
            try {
                when (method) {
                    BlockMethod.VPN -> {
                        val intent = Intent(context, DnsVpnService::class.java)
                            .apply { action = DnsVpnService.ACTION_STOP }
                        context.startService(intent)
                    }
                    BlockMethod.ROOT_HOSTS -> RootDnsService.stop(context)
                    BlockMethod.DNS_PROXY -> DnsProxyService.stop(context)
                    BlockMethod.DISABLED -> { }
                }
            } catch (e: IllegalStateException) {
                Log.i(TAG, "Service already stopped (${e.message})")
            }
            prefs.setEnabled(false)
            updateWidget(context, false, prefs.lastApplyCount.first())
            return
        }

        // No method configured, or VPN consent missing — the widget can't
        // resolve either, so open the app's toggle flow instead.
        val needsApp = method == BlockMethod.DISABLED ||
            (method == BlockMethod.VPN && VpnService.prepare(context) != null)
        if (needsApp) {
            launchAppForToggle(context)
            return
        }

        val started = when (method) {
            BlockMethod.VPN -> ProtectionServiceStarter.startForegroundService(
                context,
                Intent(context, DnsVpnService::class.java).apply {
                    action = DnsVpnService.ACTION_START
                },
                "HostShieldWidget"
            )
            BlockMethod.ROOT_HOSTS -> RootDnsService.start(context, "HostShieldWidget")
            BlockMethod.DNS_PROXY -> DnsProxyService.start(context, "HostShieldWidget")
            BlockMethod.DISABLED -> false
        }
        if (started) {
            prefs.setEnabled(true)
            updateWidget(
                context, true, prefs.lastApplyCount.first(), mode = method.name
            )
        } else {
            // Foreground-service start denied from the widget context —
            // fall back to the in-app flow.
            launchAppForToggle(context)
        }
    }

    private fun launchAppForToggle(context: Context) {
        try {
            context.startActivity(
                Intent(context, com.hostshield.MainActivity::class.java).apply {
                    action = "com.hostshield.SHORTCUT_TOGGLE"
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                }
            )
        } catch (e: Exception) {
            Log.w(TAG, "Could not open app for toggle: ${e.message}")
        }
    }
}
