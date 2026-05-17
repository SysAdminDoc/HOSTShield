package com.hostshield.data.preferences

import java.nio.charset.StandardCharsets
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

internal object SecureStoreCrypto {
    private const val VERSION = "v2"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val GCM_TAG_BITS = 128
    private const val IV_BYTES = 12
    private const val STORAGE_KEY_PREFIX = "secret_"
    private const val AAD_PREFIX = "HostShieldSecureStore:v2:"

    fun storageKey(key: String): String =
        STORAGE_KEY_PREFIX + Base64.getUrlEncoder().withoutPadding()
            .encodeToString(key.toByteArray(StandardCharsets.UTF_8))

    fun encryptString(
        keyName: String,
        value: String,
        secretKey: SecretKey,
        random: SecureRandom = SecureRandom()
    ): String {
        val iv = ByteArray(IV_BYTES).also(random::nextBytes)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, GCMParameterSpec(GCM_TAG_BITS, iv))
        cipher.updateAAD(aad(keyName))
        val ciphertext = cipher.doFinal(value.toByteArray(StandardCharsets.UTF_8))
        return listOf(
            VERSION,
            Base64.getEncoder().encodeToString(iv),
            Base64.getEncoder().encodeToString(ciphertext)
        ).joinToString(":")
    }

    fun decryptString(keyName: String, envelope: String, secretKey: SecretKey): String {
        val parts = envelope.split(':', limit = 3)
        require(parts.size == 3 && parts[0] == VERSION) { "Unsupported secure-store envelope" }
        val iv = Base64.getDecoder().decode(parts[1])
        val ciphertext = Base64.getDecoder().decode(parts[2])
        require(iv.size == IV_BYTES) { "Invalid secure-store IV length" }

        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(GCM_TAG_BITS, iv))
        cipher.updateAAD(aad(keyName))
        return String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8)
    }

    private fun aad(keyName: String): ByteArray =
        (AAD_PREFIX + keyName).toByteArray(StandardCharsets.UTF_8)
}
