package com.hostshield.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OneShotAllowStoreTest {
    @Test
    fun `grant normalizes hostname and is consumed once`() {
        OneShotAllowStore.grant(" Ads.Example.COM. ")

        assertTrue(OneShotAllowStore.consume("ads.example.com"))
        assertFalse(OneShotAllowStore.consume("ads.example.com"))
    }
}
