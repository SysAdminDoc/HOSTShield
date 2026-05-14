package com.hostshield.util

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class Android16VpnRecoveryDetectorTest {

    @Test
    fun `shows advisory for android 16 always-on lockdown with no tunnel ingress`() {
        val snapshot = Android16VpnRecoveryDetector.Snapshot(
            sdkInt = 36,
            vpnRunning = true,
            alwaysOn = true,
            lockdownEnabled = true,
            tunFdValid = true,
            hasValidatedPhysicalNetwork = true,
            elapsedSinceVpnStartMs = 120_000L,
            inboundPacketCount = 0L
        )

        assertThat(Android16VpnRecoveryDetector.shouldShowRecoveryAdvisory(snapshot)).isTrue()
    }

    @Test
    fun `suppresses advisory once packets arrive`() {
        val snapshot = Android16VpnRecoveryDetector.Snapshot(
            sdkInt = 36,
            vpnRunning = true,
            alwaysOn = true,
            lockdownEnabled = true,
            tunFdValid = true,
            hasValidatedPhysicalNetwork = true,
            elapsedSinceVpnStartMs = 180_000L,
            inboundPacketCount = 1L
        )

        assertThat(Android16VpnRecoveryDetector.shouldShowRecoveryAdvisory(snapshot)).isFalse()
    }

    @Test
    fun `suppresses advisory before android 16`() {
        val snapshot = Android16VpnRecoveryDetector.Snapshot(
            sdkInt = 35,
            vpnRunning = true,
            alwaysOn = true,
            lockdownEnabled = true,
            tunFdValid = true,
            hasValidatedPhysicalNetwork = true,
            elapsedSinceVpnStartMs = 180_000L,
            inboundPacketCount = 0L
        )

        assertThat(Android16VpnRecoveryDetector.shouldShowRecoveryAdvisory(snapshot)).isFalse()
    }
}
