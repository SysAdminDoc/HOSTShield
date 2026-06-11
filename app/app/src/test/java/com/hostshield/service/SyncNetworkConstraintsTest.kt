package com.hostshield.service

import androidx.work.NetworkType
import org.junit.Assert.assertEquals
import org.junit.Test

class SyncNetworkConstraintsTest {

    @Test
    fun `sync network type uses unmetered when wifi only is enabled`() {
        assertEquals(NetworkType.UNMETERED, syncNetworkType(wifiOnly = true))
    }

    @Test
    fun `sync network type uses connected when wifi only is disabled`() {
        assertEquals(NetworkType.CONNECTED, syncNetworkType(wifiOnly = false))
    }

    @Test
    fun `sync network constraints mirror selected network type`() {
        assertEquals(NetworkType.UNMETERED, syncNetworkConstraints(wifiOnly = true).requiredNetworkType)
        assertEquals(NetworkType.CONNECTED, syncNetworkConstraints(wifiOnly = false).requiredNetworkType)
    }
}
