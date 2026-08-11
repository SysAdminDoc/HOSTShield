package com.hostshield.service

/**
 * Pure decision logic for context-aware firewall rules.
 *
 * Extracted from [DnsVpnService] so the branch matrix is unit-testable without a
 * packet loop, mirroring [DnsTcpFallback] / [LocalDnsServerPolicy].
 */
object ContextFirewallPolicy {

    /**
     * True when a context-aware rule should block [packageName] right now.
     *
     * [foregroundPackage] is empty when the foreground app is unknown — usage-stats
     * access was not granted, or no sample has been taken yet. Background blocking
     * then fails OPEN: an unknown foreground must never be read as "some other app
     * is in front", which would block the target app permanently, including while
     * the user is actively using it.
     */
    fun shouldBlock(
        blockScreenOff: Boolean,
        blockBackground: Boolean,
        blockMetered: Boolean,
        packageName: String,
        isScreenOn: Boolean,
        foregroundPackage: String,
        isMetered: Boolean,
    ): Boolean {
        if (blockScreenOff && !isScreenOn) return true
        if (blockBackground &&
            foregroundPackage.isNotEmpty() &&
            foregroundPackage != packageName
        ) {
            return true
        }
        if (blockMetered && isMetered) return true
        return false
    }
}
