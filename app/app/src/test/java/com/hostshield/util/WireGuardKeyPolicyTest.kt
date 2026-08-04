package com.hostshield.util

import java.util.Base64
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WireGuardKeyPolicyTest {
    private val validKey = Base64.getEncoder().encodeToString(ByteArray(32) { it.toByte() })

    @Test
    fun `accepts standard base64 encoded 32 byte keys`() {
        assertEquals(validKey, WireGuardKeyPolicy.normalize(" $validKey "))
    }

    @Test
    fun `rejects empty malformed and wrong length keys`() {
        assertNull(WireGuardKeyPolicy.normalize(""))
        assertNull(WireGuardKeyPolicy.normalize("not base64"))
        assertNull(WireGuardKeyPolicy.normalize(Base64.getEncoder().encodeToString(ByteArray(31))))
        assertNull(WireGuardKeyPolicy.normalize(Base64.getEncoder().encodeToString(ByteArray(33))))
    }
}
