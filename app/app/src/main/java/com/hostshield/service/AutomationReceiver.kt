package com.hostshield.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Binder
import android.os.Build
import android.os.Process
import android.util.Log
import com.hostshield.data.database.AutomationAuditDao
import com.hostshield.util.PrivacyLog
import com.hostshield.data.database.ProfileDao
import com.hostshield.data.model.AutomationAuditEntry
import com.hostshield.data.model.BlockMethod
import com.hostshield.data.preferences.AppPreferences
import com.hostshield.util.DnsServerInputPolicy
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
        private const val PERMISSION_AUTOMATION_SUFFIX = ".permission.AUTOMATION"

        private val TRUSTED_UIDS = setOf(0, 2000) // root, shell
        private const val RATE_LIMIT_MS = 5_000L

        // Static rate limit state — survives receiver re-creation per broadcast delivery
        private val lastExecTime = java.util.concurrent.ConcurrentHashMap<String, Long>()

        internal fun clearRateLimitsForTest() {
            lastExecTime.clear()
        }
    }

    @Inject lateinit var prefs: AppPreferences
    @Inject lateinit var iptablesManager: IptablesManager
    @Inject lateinit var auditDao: AutomationAuditDao
    @Inject lateinit var profileDao: ProfileDao

    override fun onReceive(context: Context, intent: Intent) {
        val callerUid = resolveCallerUid()
        val callerPkg = resolveCallerPackage(context, callerUid)
        val requestedAction = intent.action ?: return
        val automationPermission = automationPermission(context)
        val action = AutomationActionContract.normalizeAction(requestedAction)

        // Security: verify the caller is trusted before executing
        if (!isCallerTrusted(context, callerUid, automationPermission)) {
            PrivacyLog.w(TAG, "DENIED $requestedAction from uid=$callerUid pkg=$callerPkg " +
                "(missing $automationPermission)")
            logAudit(requestedAction, callerUid, callerPkg, "DENIED")
            return
        }

        if (action == null) {
            PrivacyLog.w(TAG, "Unknown automation action: $requestedAction from uid=$callerUid pkg=$callerPkg")
            logAudit(requestedAction, callerUid, callerPkg, "ERROR_UNKNOWN_ACTION")
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

        if (requestedAction != action) {
            PrivacyLog.i(TAG, "Received legacy action $requestedAction as $action from uid=$callerUid pkg=$callerPkg")
        } else {
            PrivacyLog.i(TAG, "Received: $action from uid=$callerUid pkg=$callerPkg")
        }
        val pendingResult = goAsync()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                when (action) {
                    AutomationActionContract.ACTION_ENABLE -> enable(context)
                    AutomationActionContract.ACTION_DISABLE -> disable(context)
                    AutomationActionContract.ACTION_TOGGLE -> {
                        if (prefs.isEnabled.first()) disable(context) else enable(context)
                    }
                    AutomationActionContract.ACTION_APPLY_FIREWALL -> {
                        iptablesManager.applyRules()
                        prefs.setNetworkFirewallEnabled(true)
                        PrivacyLog.i(TAG, "Firewall applied via automation (caller=$callerPkg)")
                    }
                    AutomationActionContract.ACTION_CLEAR_FIREWALL -> {
                        iptablesManager.clearRules()
                        prefs.setNetworkFirewallEnabled(false)
                        PrivacyLog.i(TAG, "Firewall cleared via automation (caller=$callerPkg)")
                    }
                    AutomationActionContract.ACTION_REFRESH_BLOCKLIST -> {
                        HostsUpdateWorker.runOnce(context)
                        PrivacyLog.i(TAG, "Blocklist refresh queued via automation (caller=$callerPkg)")
                    }
                    AutomationActionContract.ACTION_SET_PROFILE -> {
                        val profileName = intent.getStringExtra(AutomationActionContract.EXTRA_PROFILE_NAME)
                        if (profileName.isNullOrBlank()) {
                            Log.w(TAG, "SET_PROFILE missing '${AutomationActionContract.EXTRA_PROFILE_NAME}' extra")
                            logAudit(action, callerUid, callerPkg, "ERROR_MISSING_EXTRA")
                            return@launch
                        }
                        val profiles = profileDao.getAllProfilesList()
                        val match = profiles.firstOrNull { it.name.equals(profileName, ignoreCase = true) }
                        if (match == null) {
                            Log.w(TAG, "SET_PROFILE profile not found: $profileName")
                            logAudit(action, callerUid, callerPkg, "ERROR_NOT_FOUND")
                            return@launch
                        }
                        profileDao.activateExclusive(match.id)
                        PrivacyLog.i(TAG, "Profile activated via automation: '${match.name}' (caller=$callerPkg)")
                    }
                    AutomationActionContract.ACTION_SET_DNS -> {
                        val dnsServers = intent.getStringExtra(AutomationActionContract.EXTRA_DNS_SERVERS)
                        if (dnsServers.isNullOrBlank()) {
                            Log.w(TAG, "SET_DNS missing '${AutomationActionContract.EXTRA_DNS_SERVERS}' extra")
                            logAudit(action, callerUid, callerPkg, "ERROR_MISSING_EXTRA")
                            return@launch
                        }
                        val normalizedDnsServers = DnsServerInputPolicy.normalizeServerList(dnsServers)
                        if (normalizedDnsServers.isBlank()) {
                            Log.w(TAG, "SET_DNS invalid '${AutomationActionContract.EXTRA_DNS_SERVERS}' extra")
                            logAudit(action, callerUid, callerPkg, "ERROR_INVALID_EXTRA")
                            return@launch
                        }
                        prefs.setCustomUpstreamDns(normalizedDnsServers)
                        PrivacyLog.i(TAG, "Custom DNS set via automation: $normalizedDnsServers (caller=$callerPkg)")
                    }
                    AutomationActionContract.ACTION_PAUSE -> {
                        val durationMinutes = AutomationActionContract.pauseDurationMinutes(
                            durationMinutes = intent.optionalIntExtra(AutomationActionContract.EXTRA_DURATION_MINUTES),
                            legacyPauseMinutes = intent.optionalIntExtra(AutomationActionContract.LEGACY_EXTRA_PAUSE_MINUTES)
                        )
                        // duration_minutes == 0 resumes immediately
                        if (durationMinutes == 0) {
                            PauseResumeWorker.cancel(context)
                            enable(context)
                            PrivacyLog.i(TAG, "VPN resumed via PAUSE 0 (caller=$callerPkg)")
                        } else {
                            disable(context)
                            // BroadcastReceiver.goAsync() is killed after ~10s, so we cannot
                            // sleep here. Schedule WorkManager resume — survives Doze.
                            PauseResumeWorker.schedule(context, durationMinutes)
                            PrivacyLog.i(TAG, "VPN paused for ${durationMinutes}m via automation (caller=$callerPkg)")
                        }
                    }
                    AutomationActionContract.ACTION_STATUS -> sendStatus(context)
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

    private fun Intent.optionalIntExtra(name: String): Int? =
        if (hasExtra(name)) getIntExtra(name, 0) else null

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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            getSentFromPackage()?.let { return it }
        }
        if (uid == 0) return "root"
        if (uid == 2000) return "shell"
        return context.packageManager.getPackagesForUid(uid)?.firstOrNull() ?: "uid:$uid"
    }

    private fun resolveCallerUid(): Int {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val sentUid = getSentFromUid()
            if (sentUid != Process.INVALID_UID) return sentUid
        }
        return Binder.getCallingUid()
    }

    private fun automationPermission(context: Context): String =
        "${context.packageName}$PERMISSION_AUTOMATION_SUFFIX"

    private suspend fun sendStatus(context: Context) {
        val enabled = prefs.isEnabled.first()
        val method = prefs.blockMethod.first()
        val fwActive = iptablesManager.isActive.value
        val fwRules = iptablesManager.lastApplyCount.value
        context.sendBroadcast(
            Intent(AutomationActionContract.STATUS_RESULT).apply {
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
