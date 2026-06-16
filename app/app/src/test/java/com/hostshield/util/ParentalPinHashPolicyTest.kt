package com.hostshield.util

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ParentalPinHashPolicyTest {
    @Test
    fun `legacy SHA-256 PIN records are detected`() {
        val legacy = ParentalPinHashPolicy.sha256Hex("1234")

        assertTrue(ParentalPinHashPolicy.isLegacySha256Record(legacy))
        assertTrue(ParentalPinHashPolicy.isLegacySha256Record(legacy.uppercase()))
        assertFalse(ParentalPinHashPolicy.isLegacySha256Record("argon2id\$v=19\$m=19456,t=2,p=1\$salt\$hash"))
        assertFalse(ParentalPinHashPolicy.isLegacySha256Record("not-a-pin-hash"))
    }

    @Test
    fun `legacy SHA-256 PIN verification is exact`() {
        val legacy = ParentalPinHashPolicy.sha256Hex("1234")

        assertTrue(ParentalPinHashPolicy.verifyLegacySha256Pin("1234", legacy))
        assertFalse(ParentalPinHashPolicy.verifyLegacySha256Pin("0000", legacy))
        assertFalse(ParentalPinHashPolicy.verifyLegacySha256Pin("1234", "not-a-pin-hash"))
    }
}
