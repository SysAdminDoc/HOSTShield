package com.hostshield.data.preferences

import com.google.common.truth.Truth.assertThat
import java.security.SecureRandom
import javax.crypto.KeyGenerator
import org.junit.Test

class SecureStoreCryptoTest {

    @Test
    fun encryptDecryptRoundTripsWithAssociatedKeyName() {
        val key = testKey()
        val envelope = SecureStoreCrypto.encryptString("wireguard_private_key", "secret-value", key)

        assertThat(SecureStoreCrypto.decryptString("wireguard_private_key", envelope, key))
            .isEqualTo("secret-value")
    }

    @Test
    fun encryptUsesFreshIvForSameValue() {
        val key = testKey()

        val first = SecureStoreCrypto.encryptString("webdav_password", "same-secret", key)
        val second = SecureStoreCrypto.encryptString("webdav_password", "same-secret", key)

        assertThat(first).isNotEqualTo(second)
        assertThat(SecureStoreCrypto.decryptString("webdav_password", first, key)).isEqualTo("same-secret")
        assertThat(SecureStoreCrypto.decryptString("webdav_password", second, key)).isEqualTo("same-secret")
    }

    @Test(expected = javax.crypto.AEADBadTagException::class)
    fun decryptRejectsWrongAssociatedKeyName() {
        val key = testKey()
        val envelope = SecureStoreCrypto.encryptString("parental_pin", "hash", key)

        SecureStoreCrypto.decryptString("other_key", envelope, key)
    }

    @Test(expected = IllegalArgumentException::class)
    fun decryptRejectsUnsupportedEnvelopeVersion() {
        val key = testKey()

        SecureStoreCrypto.decryptString("key", "v1:bad:bad", key)
    }

    @Test
    fun storageKeysAreStableAndDoNotExposeRawKeyName() {
        val storageKey = SecureStoreCrypto.storageKey("sec_webdav_password")

        assertThat(storageKey).startsWith("secret_")
        assertThat(storageKey).doesNotContain("webdav")
        assertThat(SecureStoreCrypto.storageKey("sec_webdav_password")).isEqualTo(storageKey)
    }

    private fun testKey(): javax.crypto.SecretKey {
        val generator = KeyGenerator.getInstance("AES")
        generator.init(256, SecureRandom())
        return generator.generateKey()
    }
}
