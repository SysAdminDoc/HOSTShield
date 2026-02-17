package com.hostshield.service

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.util.Log
import com.hostshield.data.database.FirewallRuleDao
import com.hostshield.data.model.FirewallRule
import com.topjohnwu.superuser.Shell
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * AFWall+-style iptables firewall manager.
 *
 * Manages per-app network access using iptables OUTPUT chain rules
 * with `-m owner --uid-owner <uid>`. Creates a hierarchy of custom
 * chains to separate WiFi, mobile data, and VPN traffic.
 *
 * Chain hierarchy:
 *   OUTPUT -> hs-main
 *     |-- hs-wifi    (matched by -o wlan+, -o eth+)
 *     |-- hs-mobile  (matched by -o rmnet+, -o ccmni+, -o pdp+)
 *     |-- hs-vpn     (matched by -o tun+, -o ppp+)
 *     `-- hs-lan     (matched by -d 10.0.0.0/8, 192.168.0.0/16, 172.16.0.0/12)
 *
 * Modes:
 *   BLACKLIST (default): Allow all, block selected apps
 *   WHITELIST: Block all, allow selected apps
 *
 * Logging: Uses NFLOG group 40 for blocked connection logging.
 */
@Singleton
class IptablesManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val firewallRuleDao: FirewallRuleDao
) {
    companion object {
        private const val TAG = "IptablesManager"
        private const val CHAIN_MAIN = "hs-main"
        private const val CHAIN_WIFI = "hs-wifi"
        private const val CHAIN_MOBILE = "hs-mobile"
        private const val CHAIN_VPN = "hs-vpn"
        private const val CHAIN_LAN = "hs-lan"
        private const val CHAIN_REJECT = "hs-reject"
        private const val NFLOG_GROUP = 40
        private const val NFLOG_PREFIX = "HSBlock"

        // WiFi interfaces
        private val WIFI_IFACES = arrayOf("wlan+", "eth+", "ap+")
        // Mobile data interfaces (varies by OEM)
        private val MOBILE_IFACES = arrayOf(
            "rmnet+", "rmnet_data+", "ccmni+", "pdp+",
            "ppp+", "uwbr+", "wimax+", "vsnet+",
            "rmnet_ipa+", "rev_rmnet+"
        )
        // VPN interfaces
        private val VPN_IFACES = arrayOf("tun+", "pptp+", "l2tp+", "ipsec+")
        // LAN ranges
        private val LAN_RANGES = arrayOf("10.0.0.0/8", "172.16.0.0/12", "192.168.0.0/16")

        // Special UIDs
        const val UID_ROOT = 0
        const val UID_SYSTEM = 1000
        const val UID_WIFI = 1010
        const val UID_DNS = 1051      // netd/dns
        const val UID_MEDIA = 1013
        const val UID_DRM = 1019
        const val UID_NFC = 1027
        const val UID_SHELL = 2000
    }

    enum class FirewallMode { BLACKLIST, WHITELIST }

    private val _isActive = MutableStateFlow(false)
    val isActive: StateFlow<Boolean> = _isActive.asStateFlow()

    private val _lastApplyCount = MutableStateFlow(0)
    val lastApplyCount: StateFlow<Int> = _lastApplyCount.asStateFlow()

    private val _lastError = MutableStateFlow("")
    val lastError: StateFlow<String> = _lastError.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var networkCallback: ConnectivityManager.NetworkCallback? = null

    /**
     * Register a NetworkCallback that re-applies iptables rules when
     * the active network changes (WiFi <-> mobile, VPN up/down).
     */
    private fun registerNetworkCallback() {
        if (networkCallback != null) return
        try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val request = NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build()
            val cb = object : ConnectivityManager.NetworkCallback() {
                @Volatile private var lastReapply = 0L
                override fun onAvailable(network: Network) {
                    val now = System.currentTimeMillis()
                    if (now - lastReapply < 5000) return // debounce 5s
                    lastReapply = now
                    if (_isActive.value) {
                        scope.launch {
                            delay(1500) // small delay for interface to stabilize
                            Log.i(TAG, "Network changed, re-applying rules")
                            applyRules()
                        }
                    }
                }
            }
            cm.registerNetworkCallback(request, cb)
            networkCallback = cb
        } catch (e: Exception) {
            Log.w(TAG, "Failed to register network callback: ${e.message}")
        }
    }

    private fun unregisterNetworkCallback() {
        networkCallback?.let { cb ->
            try {
                val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
                cm.unregisterNetworkCallback(cb)
            } catch (_: Exception) { }
        }
        networkCallback = null
    }

    /**
     * Apply all firewall rules. Rebuilds the entire chain hierarchy.
     * Safe to call multiple times -- clears old chains first.
     */
    suspend fun applyRules(mode: FirewallMode = FirewallMode.BLACKLIST) {
        _lastError.value = ""

        // Verify root access
        if (!Shell.getShell().isRoot) {
            _lastError.value = "Root access not available. Grant su permission and retry."
            Log.e(TAG, "No root access for iptables")
            return
        }

        val rules = firewallRuleDao.getAllRulesList()
        val script = buildScript(rules, mode)
        val result = Shell.cmd(*script.toTypedArray()).exec()

        if (result.isSuccess) {
            _isActive.value = true
            _lastApplyCount.value = rules.size
            _lastError.value = ""
            registerNetworkCallback()
            Log.i(TAG, "Firewall applied: ${rules.size} rules, mode=$mode")
        } else {
            val err = result.err.joinToString("\n").take(500)
            _lastError.value = "iptables failed: $err"
            Log.e(TAG, "Firewall apply failed: $err")
        }
    }

    /**
     * Remove all HostShield iptables chains and rules.
     */
    suspend fun clearRules() {
        val script = buildClearScript()
        Shell.cmd(*script.toTypedArray()).exec()
        _isActive.value = false
        unregisterNetworkCallback()
        Log.i(TAG, "Firewall rules cleared")
    }

    /**
     * Quick-toggle a single app's network access without full rebuild.
     */
    suspend fun toggleApp(uid: Int, wifiAllowed: Boolean, mobileAllowed: Boolean) {
        val cmds = mutableListOf<String>()
        val hexUid = uid.toString()

        // Remove existing rules for this UID
        for (chain in arrayOf(CHAIN_WIFI, CHAIN_MOBILE)) {
            cmds.add("iptables -D $chain -m owner --uid-owner $hexUid -j RETURN 2>/dev/null")
            cmds.add("iptables -D $chain -m owner --uid-owner $hexUid -j $CHAIN_REJECT 2>/dev/null")
            cmds.add("ip6tables -D $chain -m owner --uid-owner $hexUid -j RETURN 2>/dev/null")
            cmds.add("ip6tables -D $chain -m owner --uid-owner $hexUid -j $CHAIN_REJECT 2>/dev/null")
        }

        // Add new rules
        if (!wifiAllowed) {
            cmds.add("iptables -A $CHAIN_WIFI -m owner --uid-owner $hexUid -j $CHAIN_REJECT")
            cmds.add("ip6tables -A $CHAIN_WIFI -m owner --uid-owner $hexUid -j $CHAIN_REJECT")
        }
        if (!mobileAllowed) {
            cmds.add("iptables -A $CHAIN_MOBILE -m owner --uid-owner $hexUid -j $CHAIN_REJECT")
            cmds.add("ip6tables -A $CHAIN_MOBILE -m owner --uid-owner $hexUid -j $CHAIN_REJECT")
        }

        Shell.cmd(*cmds.toTypedArray()).exec()
    }

    /**
     * Get all installed apps with their UIDs for the firewall UI.
     */
    fun getInstalledApps(showSystem: Boolean = false): List<AppInfo> {
        val pm = context.packageManager
        val apps = pm.getInstalledApplications(PackageManager.GET_META_DATA)
        return apps.mapNotNull { appInfo ->
            val isSystem = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
            if (!showSystem && isSystem && appInfo.uid < 10000) return@mapNotNull null

            AppInfo(
                uid = appInfo.uid,
                packageName = appInfo.packageName,
                label = pm.getApplicationLabel(appInfo).toString(),
                isSystem = isSystem
            )
        }.distinctBy { it.uid }.sortedBy { it.label.lowercase() }
    }

    /**
     * Populate the firewall_rules table with all installed apps.
     * Only inserts apps not already in the database.
     */
    suspend fun syncInstalledApps(showSystem: Boolean = true) {
        val apps = getInstalledApps(showSystem)
        val existing = firewallRuleDao.getAllRulesList().map { it.uid }.toSet()
        val newRules = apps.filter { it.uid !in existing }.map { app ->
            FirewallRule(
                uid = app.uid,
                packageName = app.packageName,
                appLabel = app.label,
                isSystem = app.isSystem
            )
        }
        if (newRules.isNotEmpty()) {
            firewallRuleDao.insertAll(newRules)
            Log.i(TAG, "Synced ${newRules.size} new apps to firewall rules")
        }
    }

    // ---- Script Generation (AFWall+ pattern) --------------------

    private fun buildScript(rules: List<FirewallRule>, mode: FirewallMode): List<String> {
        val cmds = mutableListOf<String>()

        // Clear existing chains
        cmds.addAll(buildClearScript())

        // Create chains
        for (chain in arrayOf(CHAIN_MAIN, CHAIN_WIFI, CHAIN_MOBILE, CHAIN_VPN, CHAIN_LAN, CHAIN_REJECT)) {
            cmds.add("iptables -N $chain 2>/dev/null")
            cmds.add("ip6tables -N $chain 2>/dev/null")
        }

        // Reject chain: log + reject
        cmds.add("iptables -A $CHAIN_REJECT -j NFLOG --nflog-prefix \"$NFLOG_PREFIX\" --nflog-group $NFLOG_GROUP 2>/dev/null || true")
        cmds.add("iptables -A $CHAIN_REJECT -j REJECT --reject-with icmp-port-unreachable")
        cmds.add("ip6tables -A $CHAIN_REJECT -j REJECT --reject-with icmp6-port-unreachable")

        // Main chain: route to interface-specific chains
        // Allow loopback
        cmds.add("iptables -A $CHAIN_MAIN -o lo -j RETURN")
        cmds.add("ip6tables -A $CHAIN_MAIN -o lo -j RETURN")

        // Route to interface chains
        for (iface in WIFI_IFACES) {
            cmds.add("iptables -A $CHAIN_MAIN -o $iface -j $CHAIN_WIFI")
            cmds.add("ip6tables -A $CHAIN_MAIN -o $iface -j $CHAIN_WIFI")
        }
        for (iface in MOBILE_IFACES) {
            cmds.add("iptables -A $CHAIN_MAIN -o $iface -j $CHAIN_MOBILE")
            cmds.add("ip6tables -A $CHAIN_MAIN -o $iface -j $CHAIN_MOBILE")
        }
        for (iface in VPN_IFACES) {
            cmds.add("iptables -A $CHAIN_MAIN -o $iface -j $CHAIN_VPN")
            cmds.add("ip6tables -A $CHAIN_MAIN -o $iface -j $CHAIN_VPN")
        }

        // LAN chain (always allow local traffic)
        for (range in LAN_RANGES) {
            cmds.add("iptables -A $CHAIN_LAN -d $range -j RETURN")
        }

        // Always allow critical system UIDs
        for (uid in arrayOf(UID_ROOT, UID_DNS, UID_SYSTEM)) {
            for (chain in arrayOf(CHAIN_WIFI, CHAIN_MOBILE, CHAIN_VPN)) {
                cmds.add("iptables -A $chain -m owner --uid-owner $uid -j RETURN")
                cmds.add("ip6tables -A $chain -m owner --uid-owner $uid -j RETURN")
            }
        }

        // Per-app rules
        when (mode) {
            FirewallMode.BLACKLIST -> {
                // Default: allow. Blocked apps get reject rules.
                for (rule in rules) {
                    if (!rule.enabled) continue
                    if (!rule.wifiAllowed) {
                        cmds.add("iptables -A $CHAIN_WIFI -m owner --uid-owner ${rule.uid} -j $CHAIN_REJECT")
                        cmds.add("ip6tables -A $CHAIN_WIFI -m owner --uid-owner ${rule.uid} -j $CHAIN_REJECT")
                    }
                    if (!rule.mobileAllowed) {
                        cmds.add("iptables -A $CHAIN_MOBILE -m owner --uid-owner ${rule.uid} -j $CHAIN_REJECT")
                        cmds.add("ip6tables -A $CHAIN_MOBILE -m owner --uid-owner ${rule.uid} -j $CHAIN_REJECT")
                    }
                    if (!rule.vpnAllowed) {
                        cmds.add("iptables -A $CHAIN_VPN -m owner --uid-owner ${rule.uid} -j $CHAIN_REJECT")
                        cmds.add("ip6tables -A $CHAIN_VPN -m owner --uid-owner ${rule.uid} -j $CHAIN_REJECT")
                    }
                }
            }
            FirewallMode.WHITELIST -> {
                // Default: block. Allowed apps get RETURN rules.
                for (rule in rules) {
                    if (!rule.enabled) continue
                    if (rule.wifiAllowed) {
                        cmds.add("iptables -A $CHAIN_WIFI -m owner --uid-owner ${rule.uid} -j RETURN")
                        cmds.add("ip6tables -A $CHAIN_WIFI -m owner --uid-owner ${rule.uid} -j RETURN")
                    }
                    if (rule.mobileAllowed) {
                        cmds.add("iptables -A $CHAIN_MOBILE -m owner --uid-owner ${rule.uid} -j RETURN")
                        cmds.add("ip6tables -A $CHAIN_MOBILE -m owner --uid-owner ${rule.uid} -j RETURN")
                    }
                    if (rule.vpnAllowed) {
                        cmds.add("iptables -A $CHAIN_VPN -m owner --uid-owner ${rule.uid} -j RETURN")
                        cmds.add("ip6tables -A $CHAIN_VPN -m owner --uid-owner ${rule.uid} -j RETURN")
                    }
                }
                // Default reject at end of each chain
                for (chain in arrayOf(CHAIN_WIFI, CHAIN_MOBILE, CHAIN_VPN)) {
                    cmds.add("iptables -A $chain -j $CHAIN_REJECT")
                    cmds.add("ip6tables -A $chain -j $CHAIN_REJECT")
                }
            }
        }

        // Hook into OUTPUT chain
        cmds.add("iptables -I OUTPUT -j $CHAIN_MAIN")
        cmds.add("ip6tables -I OUTPUT -j $CHAIN_MAIN")

        return cmds
    }

    private fun buildClearScript(): List<String> {
        val cmds = mutableListOf<String>()

        // Remove jump from OUTPUT
        for (i in 0..3) {
            cmds.add("iptables -D OUTPUT -j $CHAIN_MAIN 2>/dev/null || true")
            cmds.add("ip6tables -D OUTPUT -j $CHAIN_MAIN 2>/dev/null || true")
        }

        // Flush and delete chains
        for (chain in arrayOf(CHAIN_MAIN, CHAIN_WIFI, CHAIN_MOBILE, CHAIN_VPN, CHAIN_LAN, CHAIN_REJECT)) {
            cmds.add("iptables -F $chain 2>/dev/null || true")
            cmds.add("ip6tables -F $chain 2>/dev/null || true")
            cmds.add("iptables -X $chain 2>/dev/null || true")
            cmds.add("ip6tables -X $chain 2>/dev/null || true")
        }

        return cmds
    }

    /**
     * Generate a human-readable dump of current iptables rules.
     */
    suspend fun dumpCurrentRules(): String {
        val result = Shell.cmd(
            "echo '=== IPv4 ===' && iptables -L OUTPUT -n -v --line-numbers 2>/dev/null",
            "echo '' && echo '=== hs-main ===' && iptables -L $CHAIN_MAIN -n -v --line-numbers 2>/dev/null",
            "echo '' && echo '=== hs-wifi ===' && iptables -L $CHAIN_WIFI -n -v --line-numbers 2>/dev/null",
            "echo '' && echo '=== hs-mobile ===' && iptables -L $CHAIN_MOBILE -n -v --line-numbers 2>/dev/null",
            "echo '' && echo '=== hs-reject ===' && iptables -L $CHAIN_REJECT -n -v --line-numbers 2>/dev/null"
        ).exec()
        return result.out.joinToString("\n")
    }

    data class AppInfo(
        val uid: Int,
        val packageName: String,
        val label: String,
        val isSystem: Boolean
    )

    /**
     * Full diagnostic dump including iptables rules, NAT table, and interface info.
     */
    suspend fun dumpFullDiagnostic(): String {
        val parts = mutableListOf<String>()
        parts.add("=== HostShield Firewall Diagnostic ===")
        parts.add("Active: ${_isActive.value}, Rules: ${_lastApplyCount.value}")
        parts.add("")

        val rules = Shell.cmd(
            "echo '--- iptables OUTPUT ---'",
            "iptables -L OUTPUT -n -v --line-numbers 2>/dev/null | head -30",
            "echo '' && echo '--- hs-main ---'",
            "iptables -L $CHAIN_MAIN -n -v --line-numbers 2>/dev/null",
            "echo '' && echo '--- hs-wifi ---'",
            "iptables -L $CHAIN_WIFI -n -v --line-numbers 2>/dev/null",
            "echo '' && echo '--- hs-mobile ---'",
            "iptables -L $CHAIN_MOBILE -n -v --line-numbers 2>/dev/null",
            "echo '' && echo '--- hs-vpn ---'",
            "iptables -L $CHAIN_VPN -n -v --line-numbers 2>/dev/null",
            "echo '' && echo '--- hs-reject ---'",
            "iptables -L $CHAIN_REJECT -n -v --line-numbers 2>/dev/null",
            "echo '' && echo '--- NAT ---'",
            "iptables -t nat -L OUTPUT -n -v --line-numbers 2>/dev/null | head -20",
            "echo '' && echo '--- Interfaces ---'",
            "ip link show 2>/dev/null | grep -E 'state|mtu'",
            "echo '' && echo '--- Active DNS ---'",
            "getprop net.dns1 2>/dev/null", "getprop net.dns2 2>/dev/null",
            "echo '' && echo '--- Kernel iptables modules ---'",
            "cat /proc/net/ip_tables_targets 2>/dev/null",
            "cat /proc/net/ip_tables_matches 2>/dev/null | head -20"
        ).exec()

        parts.addAll(rules.out)
        return parts.joinToString("\n")
    }

    /**
     * Export firewall rules as a shell script that can be applied standalone.
     */
    suspend fun exportAsScript(): String {
        val rules = firewallRuleDao.getAllRulesList()
        val script = buildScript(rules, FirewallMode.BLACKLIST)
        val sb = StringBuilder()
        sb.appendLine("#!/system/bin/sh")
        sb.appendLine("# HostShield Firewall Rules Export")
        sb.appendLine("# Generated: ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US).format(java.util.Date())}")
        sb.appendLine("# Rules: ${rules.size}")
        sb.appendLine("")
        for (cmd in script) {
            sb.appendLine(cmd)
        }
        return sb.toString()
    }
}
