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

    @Test
    fun `supports the current android 17 platform`() {
        val snapshot = Android16VpnRecoveryDetector.Snapshot(
            sdkInt = 37,
            vpnRunning = true,
            alwaysOn = true,
            lockdownEnabled = true,
            tunFdValid = true,
            hasValidatedPhysicalNetwork = true,
            elapsedSinceVpnStartMs = 180_000L,
            inboundPacketCount = 0L
        )

        assertThat(Android16VpnRecoveryDetector.shouldShowRecoveryAdvisory(snapshot)).isTrue()
    }

    @Test
    fun `suppresses advisory while observation window is still open`() {
        val snapshot = Android16VpnRecoveryDetector.Snapshot(
            sdkInt = 36,
            vpnRunning = true,
            alwaysOn = true,
            lockdownEnabled = true,
            tunFdValid = true,
            hasValidatedPhysicalNetwork = true,
            elapsedSinceVpnStartMs = Android16VpnRecoveryDetector.MIN_OBSERVATION_WINDOW_MS - 1,
            inboundPacketCount = 0L
        )

        assertThat(Android16VpnRecoveryDetector.shouldShowRecoveryAdvisory(snapshot)).isFalse()
    }

    @Test
    fun `suppresses advisory when the physical network is not validated`() {
        val snapshot = Android16VpnRecoveryDetector.Snapshot(
            sdkInt = 36,
            vpnRunning = true,
            alwaysOn = true,
            lockdownEnabled = true,
            tunFdValid = true,
            hasValidatedPhysicalNetwork = false,
            elapsedSinceVpnStartMs = 180_000L,
            inboundPacketCount = 0L
        )

        assertThat(Android16VpnRecoveryDetector.shouldShowRecoveryAdvisory(snapshot)).isFalse()
    }

    @Test
    fun `suppresses advisory when tunnel descriptor is invalid`() {
        val snapshot = Android16VpnRecoveryDetector.Snapshot(
            sdkInt = 36,
            vpnRunning = true,
            alwaysOn = true,
            lockdownEnabled = true,
            tunFdValid = false,
            hasValidatedPhysicalNetwork = true,
            elapsedSinceVpnStartMs = 180_000L,
            inboundPacketCount = 0L
        )

        assertThat(Android16VpnRecoveryDetector.shouldShowRecoveryAdvisory(snapshot)).isFalse()
    }

    @Test
    fun `suppresses advisory unless both always-on and lockdown are enabled`() {
        listOf(
            Android16VpnRecoveryDetector.Snapshot(
                sdkInt = 36,
                vpnRunning = true,
                alwaysOn = false,
                lockdownEnabled = true,
                tunFdValid = true,
                hasValidatedPhysicalNetwork = true,
                elapsedSinceVpnStartMs = 180_000L,
                inboundPacketCount = 0L
            ),
            Android16VpnRecoveryDetector.Snapshot(
                sdkInt = 36,
                vpnRunning = true,
                alwaysOn = true,
                lockdownEnabled = false,
                tunFdValid = true,
                hasValidatedPhysicalNetwork = true,
                elapsedSinceVpnStartMs = 180_000L,
                inboundPacketCount = 0L
            )
        ).forEach { snapshot ->
            assertThat(Android16VpnRecoveryDetector.shouldShowRecoveryAdvisory(snapshot)).isFalse()
        }
    }
}
