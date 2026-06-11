package com.hostshield.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Binder
import android.util.Log
import com.hostshield.data.database.AutomationAuditDao
import com.hostshield.data.database.ProfileDao
import com.hostshield.data.model.AutomationAuditEntry
import com.hostshield.data.model.BlockMethod
import com.hostshield.data.preferences.AppPreferences
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import javax.inject.Inject

// Hardened automation intent API receiver
//
// Security:
// - Verifies caller identity before executing any action
// - Rate limiting: max 1 command of same type per 5 seconds per caller
// - Full audit logging: all commands recorded to database with timestamp + caller
//
// Shell callers (uid 0 or 2000) are always trusted.
// App callers must hold the signature-level permission
// <applicationId>.permission.AUTOMATION or be the HostShield app itself.

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
        const val ACTION_SET_PROFILE = "com.hostshield.ACTION_SET_PROFILE"
        const val ACTION_SET_DNS = "com.hostshield.ACTION_SET_DNS"
        const val ACTION_PAUSE = "com.hostshield.ACTION_PAUSE"
        const val STATUS_RESULT = "com.hostshield.STATUS_RESULT"
        private const val PERMISSION_AUTOMATION_SUFFIX = ".permission.AUTOMATION"

        private val TRUSTED_UIDS = setOf(0, 2000) // root, shell
        private const val RATE_LIMIT_MS = 5_000L

        // Static rate limit state — survives receiver re-creation per broadcast delivery
        private val lastExecTime = java.util.concurrent.ConcurrentHashMap<String, Long>()
    }

    @Inject lateinit var prefs: AppPreferences
    @Inject lateinit var iptablesManager: IptablesManager
    @Inject lateinit var auditDao: AutomationAuditDao
    @Inject lateinit var profileDao: ProfileDao

    override fun onReceive(context: Context, intent: Intent) {
        val callerUid = Binder.getCallingUid()
        val callerPkg = resolveCallerPackage(context, callerUid)
        val action = intent.action ?: return
        val automationPermission = automationPermission(context)

        // Security: verify the caller is trusted before executing
        if (!isCallerTrusted(context, callerUid, automationPermission)) {
            Log.w(TAG, "DENIED $action from uid=$callerUid pkg=$callerPkg " +
                "(missing $automationPermission)")
            logAudit(action, callerUid, callerPkg, "DENIED")
            return
        }

        // Rate limiting: prevent rapid-fire commands
        val rateKey = "$action:$callerUid"
        val now = System.currentTimeMillis()
        val lastTime = lastExecTime.get(rateKey)
        if (lastTime != null && now - lastTime < RATE_LIMIT_MS) {
            Log.w(TAG, "RATE_LIMITED $action from uid=$callerUid (${now - lastTime}ms since last)")
            logAudit(action, callerUid, callerPkg, "RATE_LIMITED")
            return
        }
        lastExecTime.put(rateKey, now)

        Log.i(TAG, "Received: $action from uid=$callerUid pkg=$callerPkg")
        val pendingResult = goAsync()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                when (action) {
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
                    ACTION_SET_PROFILE -> {
                        val profileName = intent.getStringExtra("profile_name")
                        if (profileName.isNullOrBlank()) {
                            Log.w(TAG, "SET_PROFILE missing 'profile_name' extra")
                            logAudit(action, callerUid, callerPkg, "ERROR_MISSING_EXTRA")
                            pendingResult.finish()
                            return@launch
                        }
                        val profiles = profileDao.getAllProfilesList()
                        val match = profiles.firstOrNull { it.name.equals(profileName, ignoreCase = true) }
                        if (match == null) {
                            Log.w(TAG, "SET_PROFILE profile not found: $profileName")
                            logAudit(action, callerUid, callerPkg, "ERROR_NOT_FOUND")
                            pendingResult.finish()
                            return@launch
                        }
                        profileDao.deactivateAll()
                        profileDao.activate(match.id)
                        Log.i(TAG, "Profile activated via automation: '${match.name}' (caller=$callerPkg)")
                    }
                    ACTION_SET_DNS -> {
                        val dnsServers = intent.getStringExtra("dns_servers")
                        if (dnsServers.isNullOrBlank()) {
                            Log.w(TAG, "SET_DNS missing 'dns_servers' extra")
                            logAudit(action, callerUid, callerPkg, "ERROR_MISSING_EXTRA")
                            pendingResult.finish()
                            return@launch
                        }
                        prefs.setCustomUpstreamDns(dnsServers)
                        Log.i(TAG, "Custom DNS set via automation: $dnsServers (caller=$callerPkg)")
                    }
                    ACTION_PAUSE -> {
                        val durationMinutes = intent.getIntExtra("duration_minutes", 5)
                            .coerceIn(1, 1440) // clamp: 1 min to 24 hours
                        // duration_minutes == 0 (per README) → resume immediately
                        if (intent.getIntExtra("duration_minutes", -1) == 0) {
                            PauseResumeWorker.cancel(context)
                            enable(context)
                            Log.i(TAG, "VPN resumed via PAUSE 0 (caller=$callerPkg)")
                        } else {
                            disable(context)
                            // BroadcastReceiver.goAsync() is killed after ~10s, so we cannot
                            // sleep here. Schedule WorkManager resume — survives Doze.
                            PauseResumeWorker.schedule(context, durationMinutes)
                            Log.i(TAG, "VPN paused for ${durationMinutes}m via automation (caller=$callerPkg)")
                        }
                    }
                    ACTION_STATUS -> sendStatus(context)
                }
                logAudit(action, callerUid, callerPkg, "OK")
            } catch (e: Exception) {
                Log.e(TAG, "Automation action failed: ${e.message}", e)
                logAudit(action, callerUid, callerPkg, "ERROR")
            } finally {
                pendingResult.finish()
            }
        }
    }

    private fun logAudit(action: String, uid: Int, pkg: String, result: String) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                auditDao.insert(AutomationAuditEntry(
                    action = action,
                    callerUid = uid,
                    callerPackage = pkg,
                    result = result
                ))
                // Prune audit logs older than 30 days
                val thirtyDaysAgo = System.currentTimeMillis() - (30L * 24 * 60 * 60 * 1000)
                auditDao.deleteOlderThan(thirtyDaysAgo)
            } catch (_: Exception) { }
        }
    }

    private fun isCallerTrusted(context: Context, callerUid: Int, automationPermission: String): Boolean {
        if (callerUid in TRUSTED_UIDS) return true
        if (callerUid == android.os.Process.myUid()) return true

        val packages = context.packageManager.getPackagesForUid(callerUid)
        if (packages != null) {
            for (pkg in packages) {
                if (context.packageManager.checkPermission(automationPermission, pkg) ==
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

    private fun automationPermission(context: Context): String =
        "${context.packageName}$PERMISSION_AUTOMATION_SUFFIX"

    private suspend fun sendStatus(context: Context) {
        val enabled = prefs.isEnabled.first()
        val method = prefs.blockMethod.first()
        val fwActive = iptablesManager.isActive.value
        val fwRules = iptablesManager.lastApplyCount.value
        context.sendBroadcast(
            Intent(STATUS_RESULT).apply {
                putExtra("enabled", enabled)
                putExtra("method", method.name)
                putExtra("firewall_active", fwActive)
                putExtra("firewall_rules", fwRules)
                putExtra("version", com.hostshield.BuildConfig.VERSION_NAME)
            },
            automationPermission(context)
        )
    }

    private suspend fun enable(context: Context) {
        val method = prefs.blockMethod.first()
        val started = when (method) {
            BlockMethod.VPN -> {
                ProtectionServiceStarter.startForegroundService(
                    context,
                    Intent(context, DnsVpnService::class.java).apply {
                        action = DnsVpnService.ACTION_START
                    },
                    "AutomationReceiver"
                )
            }
            BlockMethod.ROOT_HOSTS -> RootDnsService.start(context)
            BlockMethod.DNS_PROXY -> {
                DnsProxyService.start(context, "AutomationReceiver")
            }
            BlockMethod.DISABLED -> true
        }
        if (!started) {
            throw IllegalStateException("Unable to start $method foreground service")
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
            BlockMethod.DNS_PROXY -> {
                context.stopService(Intent(context, DnsProxyService::class.java))
            }
            BlockMethod.DISABLED -> { }
        }
        prefs.setEnabled(false)
        Log.i(TAG, "Disabled via automation")
    }
}
