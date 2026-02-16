package com.hostshield.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.hostshield.data.preferences.AppPreferences
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import javax.inject.Inject

// ══════════════════════════════════════════════════════════════
// HostShield v1.0.0 — Boot Receiver
// ══════════════════════════════════════════════════════════════

@AndroidEntryPoint
class BootReceiver : BroadcastReceiver() {

    @Inject lateinit var prefs: AppPreferences

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val isEnabled = prefs.isEnabled.first()
                val autoUpdate = prefs.autoUpdate.first()
                val blockMethod = prefs.blockMethod.first()

                // Reschedule auto-update worker
                if (autoUpdate) {
                    val interval = prefs.updateIntervalHours.first()
                    val wifiOnly = prefs.wifiOnly.first()
                    HostsUpdateWorker.schedule(context, interval, wifiOnly)
                }

                // Schedule background maintenance workers
                SourceHealthWorker.schedule(context)
                LogCleanupWorker.schedule(context)
                ProfileScheduleWorker.schedule(context)

                // Restart VPN service if that was the blocking method
                if (isEnabled && blockMethod == com.hostshield.data.model.BlockMethod.VPN) {
                    val vpnIntent = Intent(context, DnsVpnService::class.java).apply {
                        action = DnsVpnService.ACTION_START
                    }
                    context.startForegroundService(vpnIntent)
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
