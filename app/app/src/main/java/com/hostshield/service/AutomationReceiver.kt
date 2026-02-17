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

// HostShield v1.6.0 - Automation Intent API
//
// Allows Tasker, MacroDroid, Automate, and shell scripts to control
// HostShield via broadcast intents.
//
// Usage from shell:
//   am broadcast -a com.hostshield.ACTION_ENABLE
//   am broadcast -a com.hostshield.ACTION_DISABLE
//   am broadcast -a com.hostshield.ACTION_TOGGLE
//   am broadcast -a com.hostshield.ACTION_APPLY_FIREWALL
//   am broadcast -a com.hostshield.ACTION_CLEAR_FIREWALL
//   am broadcast -a com.hostshield.ACTION_STATUS
//
// From Tasker:
//   Action: Send Intent
//   Action: com.hostshield.ACTION_ENABLE
//   Target: Broadcast Receiver
//
// STATUS broadcasts a result intent:
//   com.hostshield.STATUS_RESULT
//   extras: enabled (bool), method (string), firewall_active (bool)

@AndroidEntryPoint
class AutomationReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "AutomationRcvr"
        const val ACTION_ENABLE = "com.hostshield.ACTION_ENABLE"
        const val ACTION_DISABLE = "com.hostshield.ACTION_DISABLE"
        const val ACTION_TOGGLE = "com.hostshield.ACTION_TOGGLE"
        const val ACTION_APPLY_FIREWALL = "com.hostshield.ACTION_APPLY_FIREWALL"
        const val ACTION_CLEAR_FIREWALL = "com.hostshield.ACTION_CLEAR_FIREWALL"
        const val ACTION_STATUS = "com.hostshield.ACTION_STATUS"
        const val STATUS_RESULT = "com.hostshield.STATUS_RESULT"
    }

    @Inject lateinit var prefs: AppPreferences
    @Inject lateinit var iptablesManager: IptablesManager

    override fun onReceive(context: Context, intent: Intent) {
        Log.i(TAG, "Received: ${intent.action}")
        val pendingResult = goAsync()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                when (intent.action) {
                    ACTION_ENABLE -> enable(context)
                    ACTION_DISABLE -> disable(context)
                    ACTION_TOGGLE -> {
                        if (prefs.isEnabled.first()) disable(context) else enable(context)
                    }
                    ACTION_APPLY_FIREWALL -> {
                        iptablesManager.applyRules()
                        prefs.setNetworkFirewallEnabled(true)
                        Log.i(TAG, "Firewall applied via automation")
                    }
                    ACTION_CLEAR_FIREWALL -> {
                        iptablesManager.clearRules()
                        prefs.setNetworkFirewallEnabled(false)
                        Log.i(TAG, "Firewall cleared via automation")
                    }
                    ACTION_STATUS -> {
                        val enabled = prefs.isEnabled.first()
                        val method = prefs.blockMethod.first()
                        val fwActive = iptablesManager.isActive.value
                        context.sendBroadcast(Intent(STATUS_RESULT).apply {
                            putExtra("enabled", enabled)
                            putExtra("method", method.name)
                            putExtra("firewall_active", fwActive)
                        })
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Automation action failed: ${e.message}", e)
            } finally {
                pendingResult.finish()
            }
        }
    }

    private suspend fun enable(context: Context) {
        val method = prefs.blockMethod.first()
        when (method) {
            BlockMethod.VPN -> {
                val vpnIntent = Intent(context, DnsVpnService::class.java).apply {
                    action = DnsVpnService.ACTION_START
                }
                context.startForegroundService(vpnIntent)
            }
            BlockMethod.ROOT_HOSTS -> {
                RootDnsService.start(context)
            }
            BlockMethod.DISABLED -> { }
        }
        prefs.setEnabled(true)
        Log.i(TAG, "Enabled via automation (method=$method)")
    }

    private suspend fun disable(context: Context) {
        val method = prefs.blockMethod.first()
        when (method) {
            BlockMethod.VPN -> {
                val vpnIntent = Intent(context, DnsVpnService::class.java).apply {
                    action = DnsVpnService.ACTION_STOP
                }
                context.startService(vpnIntent)
            }
            BlockMethod.ROOT_HOSTS -> {
                RootDnsService.stop(context)
            }
            BlockMethod.DISABLED -> { }
        }
        prefs.setEnabled(false)
        Log.i(TAG, "Disabled via automation")
    }
}
