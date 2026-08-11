package com.hostshield.service

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ContextFirewallPolicyTest {

    private fun decide(
        blockScreenOff: Boolean = false,
        blockBackground: Boolean = false,
        blockMetered: Boolean = false,
        packageName: String = "com.example.app",
        isScreenOn: Boolean = true,
        foregroundPackage: String = "",
        isMetered: Boolean = false,
    ) = ContextFirewallPolicy.shouldBlock(
        blockScreenOff = blockScreenOff,
        blockBackground = blockBackground,
        blockMetered = blockMetered,
        packageName = packageName,
        isScreenOn = isScreenOn,
        foregroundPackage = foregroundPackage,
        isMetered = isMetered,
    )

    // Regression: foregroundPackage was never populated, so "" != pkg made every
    // blockBackground rule a permanent block — including while the app was in use.
    @Test
    fun `background rule does not block when foreground app is unknown`() {
        assertFalse(decide(blockBackground = true, foregroundPackage = ""))
    }

    @Test
    fun `background rule does not block while the app itself is foreground`() {
        assertFalse(
            decide(blockBackground = true, foregroundPackage = "com.example.app")
        )
    }

    @Test
    fun `background rule blocks when another app is foreground`() {
        assertTrue(
            decide(blockBackground = true, foregroundPackage = "com.other.app")
        )
    }

    @Test
    fun `screen-off rule blocks only when the screen is off`() {
        assertTrue(decide(blockScreenOff = true, isScreenOn = false))
        assertFalse(decide(blockScreenOff = true, isScreenOn = true))
    }

    @Test
    fun `metered rule blocks only on a metered network`() {
        assertTrue(decide(blockMetered = true, isMetered = true))
        assertFalse(decide(blockMetered = true, isMetered = false))
    }

    @Test
    fun `a rule with no context flags never blocks`() {
        assertFalse(
            decide(
                isScreenOn = false,
                foregroundPackage = "com.other.app",
                isMetered = true,
            )
        )
    }

    @Test
    fun `flags are independent — an unrelated flag does not suppress another`() {
        // Screen off blocks even though the app is foreground and unmetered.
        assertTrue(
            decide(
                blockScreenOff = true,
                blockBackground = true,
                isScreenOn = false,
                foregroundPackage = "com.example.app",
            )
        )
    }
}
