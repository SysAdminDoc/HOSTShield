package com.hostshield.util

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.util.Base64
import javax.crypto.AEADBadTagException

class BackupCryptoTest {

    @Test
    fun `encrypted backup roundtrips with correct passphrase`() {
        val plaintext = """{"app":"HostShield","sources":[]}""".toByteArray(Charsets.UTF_8)
        val encrypted = BackupCrypto.encrypt(plaintext, "correct horse battery staple")

        assertTrue(BackupCrypto.isEncrypted(encrypted))
        assertArrayEquals(plaintext, BackupCrypto.decrypt(encrypted, "correct horse battery staple"))
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

    private companion object {
        private const val MAGIC_BYTES = 4
        private const val VERSION_BYTES = 1
        private const val SALT_BYTES = 16
        private const val IV_BYTES = 12
        private const val SALT_OFFSET = MAGIC_BYTES + VERSION_BYTES
        private const val IV_OFFSET = SALT_OFFSET + SALT_BYTES
        private const val CIPHERTEXT_OFFSET = IV_OFFSET + IV_BYTES
    }
}
