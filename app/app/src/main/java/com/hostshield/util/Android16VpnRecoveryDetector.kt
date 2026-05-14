package com.hostshield.util

/**
 * Flags the Android 16 always-on VPN corruption pattern documented in the
 * roadmap: lockdown is active, a physical network is validated, the TUN fd is
 * alive, but the VPN receives no inbound packets after startup.
 */
object Android16VpnRecoveryDetector {
    const val ANDROID_16_SDK = 36
    const val MIN_OBSERVATION_WINDOW_MS = 120_000L

    data class Snapshot(
        val sdkInt: Int,
        val vpnRunning: Boolean,
        val alwaysOn: Boolean,
        val lockdownEnabled: Boolean,
        val tunFdValid: Boolean,
        val hasValidatedPhysicalNetwork: Boolean,
        val elapsedSinceVpnStartMs: Long,
        val inboundPacketCount: Long
    )

    fun shouldShowRecoveryAdvisory(snapshot: Snapshot): Boolean {
        return snapshot.sdkInt >= ANDROID_16_SDK &&
            snapshot.vpnRunning &&
            snapshot.alwaysOn &&
            snapshot.lockdownEnabled &&
            snapshot.tunFdValid &&
            snapshot.hasValidatedPhysicalNetwork &&
            snapshot.elapsedSinceVpnStartMs >= MIN_OBSERVATION_WINDOW_MS &&
            snapshot.inboundPacketCount == 0L
    }
}
