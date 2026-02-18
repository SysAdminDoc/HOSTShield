package com.hostshield.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Binder
import android.util.Log
import com.hostshield.data.model.BlockMethod
import com.hostshield.data.preferences.AppPreferences
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import javax.inject.Inject

// HostShield v2.1.0 - Hardened Automation Intent API
//
// Security: Verifies caller identity before executing any action.
// - Shell callers (uid 0 or 2000) are always trusted
// - App callers must hold the signature-level permission
//   com.hostshield.permission.AUTOMATION or be the HostShield app itself
// - All actions are logged with caller package for audit trail
//
// Usage from shell (always allowed):
//   am broadcast -a com.hostshield.ACTION_ENABLE
//   am broadcast -a com.hostshield.ACTION_DISABLE
//   am broadcast -a com.hostshield.ACTION_TOGGLE
//   am broadcast -a com.hostshield.ACTION_APPLY_FIREWALL
//   am broadcast -a com.hostshield.ACTION_CLEAR_FIREWALL
//   am broadcast -a com.hostshield.ACTION_STATUS
//   am broadcast -a com.hostshield.ACTION_REFRESH_BLOCKLIST
//
// From Tasker (requires signature permission or root):
//   Action: Send Intent
//   Action: com.hostshield.ACTION_ENABLE
//   Target: Broadcast Receiver
//
// STATUS broadcasts a result intent:
//   com.hostshield.STATUS_RESULT
//   extras: enabled (bool), method (string), firewall_active (bool),
//           firewall_rules (int), version (string)

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
        const val ACTION_REFRESH_BLOCKLIST = "com.hostshield.ACTION_REFRESH_BLOCKLIST"
        const val STATUS_RESULT = "com.hostshield.STATUS_RESULT"
        const val PERMISSION_AUTOMATION = "com.hostshield.permission.AUTOMATION"

        // Well-known root/shell UIDs that don't need permission checks
        private val TRUSTED_UIDS = setOf(0, 2000) // root, shell
    }

    @Inject lateinit var prefs: AppPreferences
    @Inject lateinit var iptablesManager: IptablesManager

    override fun onReceive(context: Context, intent: Intent) {
        val callerUid = Binder.getCallingUid()
        val callerPkg = resolveCallerPackage(context, callerUid)

        // Security: verify the caller is trusted before executing
        if (!isCallerTrusted(context, callerUid)) {
            Log.w(TAG, "DENIED ${intent.action} from uid=$callerUid pkg=$callerPkg " +
                "(missing $PERMISSION_AUTOMATION)")
            return
        }

        Log.i(TAG, "Received: ${intent.action} from uid=$callerUid pkg=$callerPkg")
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
                        Log.i(TAG, "Firewall applied via automation (caller=$callerPkg)")
                    }
                    ACTION_CLEAR_FIREWALL -> {
                        iptablesManager.clearRules()
                        prefs.setNetworkFirewallEnabled(false)
                        Log.i(TAG, "Firewall cleared via automation (caller=$callerPkg)")
                    }
                    ACTION_REFRESH_BLOCKLIST -> {
                        HostsUpdateWorker.runOnce(context)
                        Log.i(TAG, "Blocklist refresh queued via automation (caller=$callerPkg)")
                    }
                    ACTION_STATUS -> sendStatus(context)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Automation action failed: ${e.message}", e)
            } finally {
                pendingResult.finish()
            }
        }
    }

    /**
     * Verify the calling app/process is trusted.
     *
     * Trusted callers:
     * 1. Root (uid 0) or shell (uid 2000) — always allowed
     * 2. Our own app (same uid) — always allowed
     * 3. Apps holding com.hostshield.permission.AUTOMATION — allowed
     *    (signature-level: only apps signed with same key or granted via ADB)
     */
    private fun isCallerTrusted(context: Context, callerUid: Int): Boolean {
        if (callerUid in TRUSTED_UIDS) return true
        if (callerUid == android.os.Process.myUid()) return true

        val packages = context.packageManager.getPackagesForUid(callerUid)
        if (packages != null) {
            for (pkg in packages) {
                if (context.packageManager.checkPermission(PERMISSION_AUTOMATION, pkg) ==
                    PackageManager.PERMISSION_GRANTED) {
                    return true
                }
            }
        }
        return false
    }

    private fun resolveCallerPackage(context: Context, uid: Int): String {
        if (uid == 0) return "root"
        if (uid == 2000) return "shell"
        return context.packageManager.getPackagesForUid(uid)?.firstOrNull() ?: "uid:$uid"
    }

    private suspend fun sendStatus(context: Context) {
        val enabled = prefs.isEnabled.first()
        val method = prefs.blockMethod.first()
        val fwActive = iptablesManager.isActive.value
        val fwRules = iptablesManager.lastApplyCount.value
        context.sendBroadcast(Intent(STATUS_RESULT).apply {
            putExtra("enabled", enabled)
            putExtra("method", method.name)
            putExtra("firewall_active", fwActive)
            putExtra("firewall_rules", fwRules)
            putExtra("version", com.hostshield.BuildConfig.VERSION_NAME)
        })
    }

    private suspend fun enable(context: Context) {
        val method = prefs.blockMethod.first()
        when (method) {
            BlockMethod.VPN -> {
                context.startForegroundService(
                    Intent(context, DnsVpnService::class.java).apply {
                        action = DnsVpnService.ACTION_START
                    }
                )
            }
            BlockMethod.ROOT_HOSTS -> RootDnsService.start(context)
            BlockMethod.DISABLED -> { }
        }
        prefs.setEnabled(true)
        Log.i(TAG, "Enabled via automation (method=$method)")
    }

    private suspend fun disable(context: Context) {
        val method = prefs.blockMethod.first()
        when (method) {
            BlockMethod.VPN -> {
                context.startService(
                    Intent(context, DnsVpnService::class.java).apply {
                        action = DnsVpnService.ACTION_STOP
                    }
                )
            }
            BlockMethod.ROOT_HOSTS -> RootDnsService.stop(context)
            BlockMethod.DISABLED -> { }
        }
        prefs.setEnabled(false)
        Log.i(TAG, "Disabled via automation")
    }
}
