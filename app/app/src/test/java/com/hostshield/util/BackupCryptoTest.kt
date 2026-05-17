package com.hostshield.util

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.nio.ByteBuffer
import java.util.Arrays
import java.util.Base64
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

class BackupCryptoTest {

    @Test
    fun `encrypted backup roundtrips with correct passphrase`() {
        val plaintext = """{"app":"HostShield","sources":[]}""".toByteArray(Charsets.UTF_8)
        val encrypted = BackupCrypto.encrypt(plaintext, "correct horse battery staple")

        assertTrue(BackupCrypto.isEncrypted(encrypted))
        assertEquals(VERSION_ARGON2ID, encrypted[VERSION_OFFSET])
        assertArrayEquals(plaintext, BackupCrypto.decrypt(encrypted, "correct horse battery staple"))
    }

    @Test
    fun `new encrypted backups carry Argon2id parameters`() {
        val encrypted = BackupCrypto.encrypt("{}".toByteArray(), "passphrase")
        val header = ByteBuffer.wrap(encrypted)

        assertEquals("HSBK", String(encrypted.copyOfRange(0, MAGIC_BYTES), Charsets.US_ASCII))
        assertEquals(VERSION_ARGON2ID, encrypted[VERSION_OFFSET])
        header.position(ARGON2_PARAM_OFFSET)
        assertEquals(PasswordKdf.ARGON2_DEFAULT_MEMORY_KIB, header.int)
        assertEquals(PasswordKdf.ARGON2_DEFAULT_ITERATIONS, header.int)
        assertEquals(PasswordKdf.ARGON2_DEFAULT_PARALLELISM, header.int)
    }

    @Test
    fun `wrong passphrase fails authentication`() {
        val encrypted = BackupCrypto.encrypt("secret".toByteArray(), "right-passphrase")

        try {
            BackupCrypto.decrypt(encrypted, "wrong-passphrase")
            fail("Wrong passphrase should fail AES-GCM authentication")
        } catch (_: AEADBadTagException) {
            // Expected: wrong passphrase derives a different key and fails tag validation.
        }
    }

    @Test
    fun `short payloads are rejected before header parsing`() {
        listOf(0, 1, 4, 16, 33, 48).forEach { size ->
            try {
                BackupCrypto.decrypt(ByteArray(size), "passphrase")
                fail("Payload of $size bytes should be too short")
            } catch (e: IllegalArgumentException) {
                assertEquals("Data too short to be an encrypted HostShield backup", e.message)
            }
        }
    }

    @Test
    fun `truncated Argon2id backups are rejected after version parsing`() {
        val payload = ByteBuffer.allocate(V1_MIN_PAYLOAD_BYTES)
            .put("HSBK".toByteArray(Charsets.US_ASCII))
            .put(VERSION_ARGON2ID)
            .array()

        try {
            BackupCrypto.decrypt(payload, "passphrase")
            fail("Truncated v2 payload should be too short")
        } catch (e: IllegalArgumentException) {
            assertEquals("Data too short to be an encrypted HostShield backup", e.message)
        }
    }

    @Test
    fun `invalid encrypted header is rejected`() {
        val payload = ByteArray(49) { 0x41 }

        try {
            BackupCrypto.decrypt(payload, "passphrase")
            fail("Invalid magic header should be rejected")
        } catch (e: IllegalArgumentException) {
            assertEquals("Not an encrypted HostShield backup (invalid header)", e.message)
        }
    }

    @Test
    fun `unsupported encrypted backup version is rejected`() {
        val payload = ByteBuffer.allocate(V1_MIN_PAYLOAD_BYTES)
            .put("HSBK".toByteArray(Charsets.US_ASCII))
            .put(99.toByte())
            .array()

        try {
            BackupCrypto.decrypt(payload, "passphrase")
            fail("Unsupported version should be rejected")
        } catch (e: IllegalArgumentException) {
            assertEquals("Unsupported encrypted backup version: 99", e.message)
        }
    }

    @Test
    fun `legacy PBKDF2 backups still decrypt`() {
        val plaintext = """{"legacy":true}""".toByteArray(Charsets.UTF_8)
        val encrypted = encryptLegacyPbkdf2(plaintext, "legacy-passphrase")

        assertTrue(BackupCrypto.isEncrypted(encrypted))
        assertEquals(VERSION_PBKDF2, encrypted[VERSION_OFFSET])
        assertArrayEquals(plaintext, BackupCrypto.decrypt(encrypted, "legacy-passphrase"))
    }

    @Test
    fun `salt and iv are unique across backup exports`() {
        val outputs = (0 until 4).map {
            BackupCrypto.encrypt("same plaintext".toByteArray(), "same passphrase")
        }

        val salts = outputs.map { it.sliceArray(SALT_OFFSET until IV_OFFSET).b64() }.toSet()
        val ivs = outputs.map { it.sliceArray(IV_OFFSET until CIPHERTEXT_OFFSET).b64() }.toSet()
        val ciphertexts = outputs.map { it.sliceArray(CIPHERTEXT_OFFSET until it.size).b64() }.toSet()

        assertEquals(outputs.size, salts.size)
        assertEquals(outputs.size, ivs.size)
        assertEquals(outputs.size, ciphertexts.size)
    }

    @Test
    fun `legacy plaintext backup is detected and decoded as plaintext`() {
        val json = """{"app":"HostShield","backup_version":1}"""
        val bytes = json.toByteArray(Charsets.UTF_8)

        assertFalse(BackupCrypto.isEncrypted(bytes))
        assertEquals(json, BackupRestoreUtil.decodeBackupBytes(bytes, passphrase = "ignored"))
    }

    @Test
    fun `encrypted import without passphrase returns promptable failure`() {
        val encrypted = BackupCrypto.encrypt("{}".toByteArray(), "passphrase")

        try {
            BackupRestoreUtil.decodeBackupBytes(encrypted, passphrase = null)
            fail("Encrypted backup without passphrase should ask for a passphrase")
        } catch (e: EncryptedBackupException) {
            assertEquals("Backup is encrypted. Please provide a passphrase.", e.message)
        }
    }

    @Test
    fun `encrypted import with wrong passphrase fails authentication`() {
        val encrypted = BackupCrypto.encrypt("{}".toByteArray(), "passphrase")

        try {
            BackupRestoreUtil.decodeBackupBytes(encrypted, passphrase = "wrong")
            fail("Wrong import passphrase should fail authentication")
        } catch (_: AEADBadTagException) {
            // Expected.
        }
    }

    private fun ByteArray.b64(): String = Base64.getEncoder().encodeToString(this)

    private fun encryptLegacyPbkdf2(plaintext: ByteArray, passphrase: String): ByteArray {
        val salt = ByteArray(SALT_BYTES) { (it + 1).toByte() }
        val iv = ByteArray(IV_BYTES) { (it + 17).toByte() }
        val keyBytes = PasswordKdf.derivePbkdf2HmacSha256(
            passphrase,
            salt,
            PasswordKdf.BACKUP_PBKDF2_ITERATIONS,
            PasswordKdf.KEY_LENGTH_BITS
        )
        val key = SecretKeySpec(keyBytes, "AES")
        Arrays.fill(keyBytes, 0)

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))
        val ciphertext = cipher.doFinal(plaintext)

        return ByteBuffer.allocate(V1_HEADER_BYTES + ciphertext.size)
            .put("HSBK".toByteArray(Charsets.US_ASCII))
            .put(VERSION_PBKDF2)
            .put(salt)
            .put(iv)
            .put(ciphertext)
            .array()
    }

    private companion object {
        private const val MAGIC_BYTES = 4
        private const val VERSION_BYTES = 1
        private const val ARGON2_PARAM_BYTES = 12
        private const val SALT_BYTES = 16
        private const val IV_BYTES = 12
        private const val GCM_TAG_BITS = 128
        private const val GCM_TAG_BYTES = GCM_TAG_BITS / 8
        private const val VERSION_OFFSET = MAGIC_BYTES
        private const val ARGON2_PARAM_OFFSET = MAGIC_BYTES + VERSION_BYTES
        private const val SALT_OFFSET = ARGON2_PARAM_OFFSET + ARGON2_PARAM_BYTES
        private const val IV_OFFSET = SALT_OFFSET + SALT_BYTES
        private const val CIPHERTEXT_OFFSET = IV_OFFSET + IV_BYTES
        private const val V1_HEADER_BYTES = MAGIC_BYTES + VERSION_BYTES + SALT_BYTES + IV_BYTES
        private const val V1_MIN_PAYLOAD_BYTES = V1_HEADER_BYTES + GCM_TAG_BYTES
        private const val VERSION_PBKDF2: Byte = 1
        private const val VERSION_ARGON2ID: Byte = 2
    }
}
