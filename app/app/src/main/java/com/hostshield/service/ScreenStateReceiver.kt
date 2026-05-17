package com.hostshield.service

import android.app.ActivityManager
import android.app.usage.UsageStatsManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.PowerManager
import android.util.Log

// Context-aware firewall state receiver
// Tracks screen state, foreground app, and network metered status.
// Used by DnsVpnService to enforce context-dependent blocking rules.

object ContextState {
    @Volatile var isScreenOn: Boolean = true
    @Volatile var foregroundPackage: String = ""
    @Volatile var isMetered: Boolean = false

    /** Register screen on/off receiver. Call from DnsVpnService.startVpn(). */
    fun register(context: Context) {
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        isScreenOn = pm.isInteractive

        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
        }
        try {
            context.registerReceiver(screenReceiver, filter)
        } catch (_: Exception) { /* already registered */ }

        updateMeteredState(context)
    }

    /** Unregister receiver. Call from DnsVpnService.stopVpn(). */
    fun unregister(context: Context) {
        try { context.unregisterReceiver(screenReceiver) } catch (_: Exception) { }
    }

    /** Update foreground app. Call periodically from VPN service. */
    fun updateForegroundApp(context: Context) {
        try {
            val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
            if (usm != null) {
                val now = System.currentTimeMillis()
                val stats = usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, now - 60_000, now)
                foregroundPackage = stats
                    ?.maxByOrNull { it.lastTimeUsed }
                    ?.packageName ?: ""
            }
        } catch (e: Exception) {
            Log.d("ContextState", "Usage stats unavailable: ${e.message}")
        }
    }

    fun updateMeteredState(context: Context) {
        try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val net = cm.activeNetwork
            val caps = cm.getNetworkCapabilities(net)
            isMetered = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED) != true
        } catch (_: Exception) { isMetered = false }
    }

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_SCREEN_ON -> isScreenOn = true
                Intent.ACTION_SCREEN_OFF -> isScreenOn = false
            }
        }
    }
}
