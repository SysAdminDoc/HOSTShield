package com.hostshield.data.preferences

import com.hostshield.util.PasswordKdf
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Base64

class SecureStorePinKdfTest {

    @Test
    fun `new PIN hashes use Argon2id and verify`() {
        val hash = SecureStore.hashPin("1234")

        assertTrue(hash.startsWith("argon2id${'$'}v=19${'$'}"))
        assertTrue(SecureStore.verifyPin("1234", hash))
        assertFalse(SecureStore.verifyPin("0000", hash))
        assertFalse(SecureStore.needsPinRehash(hash))
    }

    @Test
    fun `legacy PBKDF2 PIN records still verify and request rehash`() {
        val legacy = legacyPbkdf2Record("1234", ByteArray(PasswordKdf.PIN_SALT_BYTES) { it.toByte() })

        assertTrue(SecureStore.verifyPin("1234", legacy))
        assertFalse(SecureStore.verifyPin("0000", legacy))
        assertTrue(SecureStore.needsPinRehash(legacy))
    }

    @Test
    fun `malformed PIN records do not verify`() {
        assertFalse(SecureStore.verifyPin("1234", "argon2id${'$'}v=19${'$'}bad"))
        assertFalse(SecureStore.verifyPin("1234", "argon2id${'$'}v=19${'$'}m=1048576,t=1,p=1${'$'}c2FsdA${'$'}aGFzaA"))
        assertFalse(SecureStore.verifyPin("1234", "not-base64:not-base64"))
        assertFalse(SecureStore.verifyPin("1234", ""))
    }

    private fun legacyPbkdf2Record(pin: String, salt: ByteArray): String {
        val hash = PasswordKdf.derivePbkdf2HmacSha256(
            pin,
            salt,
            PasswordKdf.PIN_PBKDF2_ITERATIONS,
            PasswordKdf.KEY_LENGTH_BITS
        )
        return Base64.getEncoder().encodeToString(salt) + ":" + Base64.getEncoder().encodeToString(hash)
    }
}
