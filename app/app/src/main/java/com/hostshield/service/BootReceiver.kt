package com.hostshield.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.hostshield.data.model.BlockMethod
import com.hostshield.data.preferences.AppPreferences
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import javax.inject.Inject

// HostShield v1.6.0 -- Boot Receiver
//
// On BOOT_COMPLETED:
// 1. Reschedule workers (auto-update, health check, log cleanup, profiles)
// 2. Restart VPN service if VPN mode was active
// 3. Restart root DNS logger if root mode was active
// 4. Re-apply iptables firewall rules if network firewall was enabled
// 5. Restart NFLOG reader if connection logging was enabled

@AndroidEntryPoint
class BootReceiver : BroadcastReceiver() {

    @Inject lateinit var prefs: AppPreferences
    @Inject lateinit var iptablesManager: IptablesManager
    @Inject lateinit var nflogReader: NflogReader

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED &&
            intent.action != "android.intent.action.QUICKBOOT_POWERON"
        ) return

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val isEnabled = prefs.isEnabled.first()
                val autoUpdate = prefs.autoUpdate.first()
                val blockMethod = prefs.blockMethod.first()
                val networkFwEnabled = prefs.networkFirewallEnabled.first()
                val autoApplyFw = prefs.autoApplyFirewall.first()
                val connLogEnabled = prefs.connectionLogEnabled.first()

                // Reschedule all workers
                if (autoUpdate) {
                    val interval = prefs.updateIntervalHours.first()
                    val wifiOnly = prefs.wifiOnly.first()
                    HostsUpdateWorker.schedule(context, interval, wifiOnly)
                }
                SourceHealthWorker.schedule(context)
                LogCleanupWorker.schedule(context)
                ProfileScheduleWorker.schedule(context)

                if (!isEnabled) {
                    Log.i("BootReceiver", "HostShield not enabled, skipping restore")
                    return@launch
                }

                when (blockMethod) {
                    BlockMethod.VPN -> {
                        val vpnIntent = Intent(context, DnsVpnService::class.java).apply {
                            action = DnsVpnService.ACTION_START
                        }
                        context.startForegroundService(vpnIntent)
                        Log.i("BootReceiver", "VPN service restarted")
                    }
                    BlockMethod.ROOT_HOSTS -> {
                        RootDnsService.start(context)
                        Log.i("BootReceiver", "Root DNS service restarted")
                    }
                    BlockMethod.DISABLED -> { }
                }

                // Restore iptables firewall rules
                if (networkFwEnabled && autoApplyFw) {
                    iptablesManager.applyRules()
                    Log.i("BootReceiver", "iptables firewall rules re-applied")

                    // Start connection log reader
                    if (connLogEnabled) {
                        nflogReader.start()
                        Log.i("BootReceiver", "NFLOG reader restarted")
                    }
                }
            } catch (e: Exception) {
                Log.e("BootReceiver", "Boot restore failed: ${e.message}", e)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
