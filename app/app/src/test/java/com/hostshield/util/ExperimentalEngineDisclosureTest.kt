package com.hostshield.util

import org.junit.Assert.assertTrue
import org.junit.Test

class ExperimentalEngineDisclosureTest {
    @Test
    fun `production policy says release builds force experimental dns engines off`() {
        assertTrue(
            ExperimentalEngineDisclosure.PRODUCTION_DEFAULT.contains(
                "Release builds force DoQ, DoH3, and WireGuard DNS off"
            )
        )
        assertTrue(ExperimentalEngineDisclosure.DOQ_UI.contains("Debug-only"))
        assertTrue(ExperimentalEngineDisclosure.WIREGUARD_UI.contains("Debug-only"))
        assertTrue(ExperimentalEngineDisclosure.DOQ_DIAGNOSTIC.contains("release builds force DoQ off"))
        assertTrue(
            ExperimentalEngineDisclosure.WIREGUARD_DIAGNOSTIC.contains(
                "release builds force WireGuard DNS off"
            )
        )
    }
}
