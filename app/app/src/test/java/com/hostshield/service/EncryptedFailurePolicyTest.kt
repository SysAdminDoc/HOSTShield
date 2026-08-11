package com.hostshield.service

import com.hostshield.service.EncryptedFailurePolicy.Action
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The GitHub #1 leak invariant: an encrypted-transport failure must never fall
 * back to plaintext UDP. It was previously enforced only by code review and a
 * note in CLAUDE.md, so reintroducing a forwardUdp call in the encrypted paths
 * broke nothing in the suite.
 */
class EncryptedFailurePolicyTest {

    @Test
    fun `an encrypted failure with a stale answer serves the stale answer`() {
        assertEquals(
            Action.SERVE_STALE,
            EncryptedFailurePolicy.decide(encryptedTransport = true, staleAvailable = true)
        )
    }

    @Test
    fun `an encrypted failure with no stale answer returns SERVFAIL`() {
        assertEquals(
            Action.SERVFAIL,
            EncryptedFailurePolicy.decide(encryptedTransport = true, staleAvailable = false)
        )
    }

    @Test
    fun `an encrypted failure never resolves to a plaintext retry`() {
        for (stale in listOf(true, false)) {
            val action = EncryptedFailurePolicy.decide(
                encryptedTransport = true,
                staleAvailable = stale,
            )
            assertTrue(
                "encrypted failure must not retry upstream in the clear (stale=$stale)",
                action == Action.SERVE_STALE || action == Action.SERVFAIL
            )
        }
    }

    @Test
    fun `a plaintext transport may still retry upstream`() {
        assertEquals(
            Action.RETRY_UPSTREAM,
            EncryptedFailurePolicy.decide(encryptedTransport = false, staleAvailable = false)
        )
    }

    @Test
    fun `plaintext fallback is only ever legal for a plaintext transport`() {
        assertFalse(EncryptedFailurePolicy.allowsPlaintextFallback(encryptedTransport = true))
        assertTrue(EncryptedFailurePolicy.allowsPlaintextFallback(encryptedTransport = false))
    }
}
