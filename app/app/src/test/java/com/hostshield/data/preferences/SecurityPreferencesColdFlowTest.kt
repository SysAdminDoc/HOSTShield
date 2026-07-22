package com.hostshield.data.preferences

import android.content.Context
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The SecureStore-backed secret accessors must be cold flows:
 * - property access alone must not trigger the Keystore decrypt + disk I/O
 *   (the old `flowOf(secureStore.getString(...))` ran it eagerly on the
 *   caller's thread), and
 * - each collection must observe the current stored value, not a value
 *   captured when the property was first accessed.
 *
 * No DataStore access happens in these getters, so a plain mocked Context works.
 */
class SecurityPreferencesColdFlowTest {

    private val secureStore = mockk<SecureStore>()
    private val prefs = SecurityPreferences(mockk<Context>(relaxed = true), secureStore)

    private fun assertColdSecretFlow(secKey: String, flowAccessor: () -> Flow<String>) = runBlocking {
        every { secureStore.getString(secKey) } returns "v1"

        val flow = flowAccessor()
        verify(exactly = 0) { secureStore.getString(secKey) }

        assertEquals("v1", flow.first())
        verify(exactly = 1) { secureStore.getString(secKey) }

        every { secureStore.getString(secKey) } returns "v2"
        assertEquals("v2", flowAccessor().first())
    }

    @Test
    fun `wireGuardEndpoint defers SecureStore read until collection`() =
        assertColdSecretFlow("sec_wireguard_endpoint") { prefs.wireGuardEndpoint }

    @Test
    fun `wireGuardPrivateKey defers SecureStore read until collection`() =
        assertColdSecretFlow("sec_wireguard_private_key") { prefs.wireGuardPrivateKey }

    @Test
    fun `wireGuardPresharedKey defers SecureStore read until collection`() =
        assertColdSecretFlow("sec_wireguard_preshared_key") { prefs.wireGuardPresharedKey }

    @Test
    fun `parentalPinHash defers SecureStore read until collection`() =
        assertColdSecretFlow("sec_parental_pin_hash") { prefs.parentalPinHash }
}
