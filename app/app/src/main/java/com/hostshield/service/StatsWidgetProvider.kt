package com.hostshield.service

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.hostshield.R
import com.hostshield.data.database.DnsLogDao
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId

// Stats widget provider

@AndroidEntryPoint
class StatsWidgetProvider : AppWidgetProvider() {

    @Inject lateinit var dnsLogDao: DnsLogDao

    companion object {
        private const val PREFS_NAME = "hostshield_stats_widget"
        private const val KEY_BLOCKED_TODAY = "blocked_today"
        private const val KEY_QUERIES_TODAY = "queries_today"
        private const val KEY_BLOCK_RATE = "block_rate"

        fun updateWidget(context: Context, blockedToday: Int, queriesToday: Int) {
            val rate = if (queriesToday > 0) (blockedToday.toFloat() / queriesToday * 100).toInt() else 0
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
                .putInt(KEY_BLOCKED_TODAY, blockedToday)
                .putInt(KEY_QUERIES_TODAY, queriesToday)
                .putInt(KEY_BLOCK_RATE, rate)
                .apply()

            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, StatsWidgetProvider::class.java))
            ids.forEach { id -> updateAppWidget(context, manager, id) }
        }

        private fun updateAppWidget(context: Context, manager: AppWidgetManager, widgetId: Int) {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val blocked = prefs.getInt(KEY_BLOCKED_TODAY, 0)
            val queries = prefs.getInt(KEY_QUERIES_TODAY, 0)
            val rate = prefs.getInt(KEY_BLOCK_RATE, 0)
            val nf = java.text.NumberFormat.getNumberInstance()

            val views = RemoteViews(context.packageName, R.layout.widget_stats)
            views.setTextViewText(R.id.stats_widget_blocked, nf.format(blocked))
            views.setTextViewText(R.id.stats_widget_queries, nf.format(queries))
            views.setTextViewText(R.id.stats_widget_rate, "$rate%")

            val launchIntent = Intent(context, com.hostshield.MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val pending = PendingIntent.getActivity(
                context, 2, launchIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.stats_widget_root, pending)

            manager.updateAppWidget(widgetId, views)
        }
    }

    override fun onUpdate(context: Context, manager: AppWidgetManager, widgetIds: IntArray) {
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val zone = ZoneId.systemDefault()
                val todayStart = LocalDate.now(zone)
                    .atStartOfDay(zone)
                    .toInstant()
                    .toEpochMilli()
                val blockedToday = dnsLogDao.getBlockedCountSince(todayStart).first()
                val queriesToday = dnsLogDao.getTotalCountSince(todayStart).first()
                updateWidget(context, blockedToday, queriesToday)
            } catch (e: Exception) {
                android.util.Log.w("StatsWidgetProvider", "Could not refresh stats widget: ${e.message}")
                widgetIds.forEach { id -> updateAppWidget(context, manager, id) }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
