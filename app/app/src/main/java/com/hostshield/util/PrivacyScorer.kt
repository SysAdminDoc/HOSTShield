package com.hostshield.util

import com.hostshield.data.preferences.AppPreferences
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

// Privacy score calculator
// Rates device protection 0-100 based on current configuration.

@Singleton
class PrivacyScorer @Inject constructor(
    private val prefs: AppPreferences,
    private val batteryUtil: BatteryOptimizationUtil
) {
    data class ScoreBreakdown(
        val total: Int,
        val items: List<ScoreItem>
    )

    data class ScoreItem(
        val label: String,
        val points: Int,
        val maxPoints: Int,
        val passed: Boolean,
        val hint: String = ""
    )

    suspend fun calculate(): ScoreBreakdown {
        val items = mutableListOf<ScoreItem>()

        // Blocking enabled (25 pts). IPv6 blocking is unconditional when
        // blocking is on (dual-stack VPN + AAAA decisions), so its weight is
        // folded in here rather than a separate always-on dimension.
        val enabled = prefs.isEnabled.first()
        items.add(ScoreItem("Blocking enabled", if (enabled) 25 else 0, 25, enabled,
            if (!enabled) "Enable blocking for protection" else ""))

        // DNS-over-HTTPS (15 pts)
        val doh = prefs.dohEnabled.first()
        items.add(ScoreItem("DNS-over-HTTPS", if (doh) 15 else 0, 15, doh,
            if (!doh) "Enable DoH to encrypt DNS queries" else ""))

        // DNS Trap (10 pts)
        val trap = prefs.dnsTrapEnabled.first()
        items.add(ScoreItem("DNS Trap", if (trap) 10 else 0, 10, trap,
            if (!trap) "Catches hardcoded DNS bypasses" else ""))

        // Auto-update enabled (10 pts)
        val autoUpdate = prefs.autoUpdate.first()
        items.add(ScoreItem("Auto-update lists", if (autoUpdate) 10 else 0, 10, autoUpdate,
            if (!autoUpdate) "Keep blocklists fresh automatically" else ""))

        // DNS logging (5 pts)
        val logging = prefs.dnsLogging.first()
        items.add(ScoreItem("DNS logging", if (logging) 5 else 0, 5, logging,
            if (!logging) "Track what's being blocked" else ""))

        // Battery optimization exempt (10 pts)
        val batteryOk = !batteryUtil.check().isOptimized
        items.add(ScoreItem("Battery exempt", if (batteryOk) 10 else 0, 10, batteryOk,
            if (!batteryOk) "Prevents Android from killing HostShield" else ""))

        // Network firewall (10 pts)
        val firewall = prefs.networkFirewallEnabled.first()
        items.add(ScoreItem("Network firewall", if (firewall) 10 else 0, 10, firewall,
            if (!firewall) "Block per-app network access" else ""))

        // Custom upstream DNS (5 pts)
        val customDns = prefs.customUpstreamDns.first().isNotBlank()
        items.add(ScoreItem("Custom DNS servers", if (customDns) 5 else 0, 5, customDns,
            if (!customDns) "Use privacy-focused DNS servers" else ""))

        // Auto backup (5 pts)
        val backup = prefs.autoBackupEnabled.first()
        items.add(ScoreItem("Auto backup", if (backup) 5 else 0, 5, backup,
            if (!backup) "Protect your config with backups" else ""))

        // Connection logging (5 pts)
        val connLog = prefs.connectionLogEnabled.first()
        items.add(ScoreItem("Connection logging", if (connLog) 5 else 0, 5, connLog,
            if (!connLog) "Monitor firewall-blocked connections" else ""))

        val total = items.sumOf { it.points }
        return ScoreBreakdown(total, items)
    }
}
