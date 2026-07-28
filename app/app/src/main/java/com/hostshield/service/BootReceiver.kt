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

// Boot receiver
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
            intent.action != "android.intent.action.QUICKBOOT_POWERON" &&
            intent.action != "com.htc.intent.action.QUICKBOOT_POWERON"
        ) return

        val pendingResult = goAsync()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        scope.launch {
            try {
                val isEnabled = prefs.isEnabled.first()
                val autoUpdate = prefs.autoUpdate.first()
                val blockMethod = prefs.blockMethod.first()
                val networkFwEnabled = prefs.networkFirewallEnabled.first()
                val autoApplyFw = prefs.autoApplyFirewall.first()
                val connLogEnabled = prefs.connectionLogEnabled.first()
                val wifiOnly = prefs.wifiOnly.first()
                val lanDnsEnabled = prefs.lanDnsEnabled.first()

                // Reschedule all workers
                if (autoUpdate) {
                    val interval = prefs.updateIntervalHours.first()
                    HostsUpdateWorker.schedule(context, interval, wifiOnly)
                }
                SourceHealthWorker.schedule(context, wifiOnly)
                LogCleanupWorker.schedule(context)
                ProfileScheduleWorker.schedule(context)
                // Re-register the blocking schedule after a restore onto a fresh
                // install: WorkManager persistence only covers the install that
                // enqueued the worker, and restoreBackup() can land
                // schedule_enabled=true without any enqueue having happened.
                if (prefs.scheduleEnabled.first()) {
                    BlockingScheduleWorker.schedule(context)
                }

                if (lanDnsEnabled) {
                    val port = prefs.lanDnsPort.first()
                    val allowExternalClients = prefs.lanDnsAllowExternalClients.first()
                    if (LocalDnsServerService.start(context, port, allowExternalClients, "BootReceiver")) {
                        Log.i("BootReceiver", "LAN DNS service restarted")
                    }
                }

                if (!isEnabled) {
                    Log.i("BootReceiver", "HostShield not enabled, skipping restore")
                    return@launch
                }

                when (blockMethod) {
                    BlockMethod.VPN -> {
                        val vpnIntent = Intent(context, DnsVpnService::class.java).apply {
                            action = DnsVpnService.ACTION_START
                        }
                        if (ProtectionServiceStarter.startForegroundService(context, vpnIntent, "BootReceiver")) {
                            Log.i("BootReceiver", "VPN service restarted")
                        }
                    }
                    BlockMethod.ROOT_HOSTS -> {
                        if (RootDnsService.start(context, "BootReceiver")) {
                            Log.i("BootReceiver", "Root DNS service restarted")
                        }
                    }
                    BlockMethod.DNS_PROXY -> {
                        if (DnsProxyService.start(context, "BootReceiver")) {
                            Log.i("BootReceiver", "DNS proxy service restarted")
                        }
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
                scope.cancel()
            }
        }
    }
}
