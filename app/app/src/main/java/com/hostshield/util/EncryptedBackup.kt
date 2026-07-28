package com.hostshield.util

import java.nio.ByteBuffer
import java.util.Arrays
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import javax.inject.Inject
import javax.inject.Singleton

// ══════════════════════════════════════════════════════════════
// HostShield — Encrypted Backup (AES-256-GCM)
// Legacy HSBACKUP reader retained for pre-BackupCrypto imports.
// ══════════════════════════════════════════════════════════════

@Singleton
class EncryptedBackup @Inject constructor() {
    companion object {
        private val MAGIC = "HSBACKUP".toByteArray(Charsets.US_ASCII) // 8 bytes
        private const val FORMAT_VERSION_PBKDF2: Byte = 1
        private const val SALT_LENGTH = 16
        private const val IV_LENGTH = 12
        private const val GCM_TAG_BITS = 128
        private const val GCM_TAG_BYTES = GCM_TAG_BITS / 8
        private const val HEADER_SIZE = 8 + 1 + SALT_LENGTH + IV_LENGTH // 37 bytes
        // Smallest legal ciphertext encrypts empty plaintext → just the GCM tag.
        private const val MIN_PAYLOAD_SIZE = HEADER_SIZE + GCM_TAG_BYTES

        /**
         * Check whether raw bytes look like an encrypted HostShield backup
         * by inspecting the magic header.
         */
        fun isEncryptedBackup(data: ByteArray): Boolean {
            if (BackupCrypto.isEncrypted(data)) return true
            if (data.size < MAGIC.size) return false
            return data.copyOfRange(0, MAGIC.size).contentEquals(MAGIC)
        }
    }

    // ── Public API ──────────────────────────────────────────────

    /**
     * Encrypt a JSON backup string with the given password.
     */
    fun encrypt(json: String, password: String): ByteArray =
        BackupCrypto.encrypt(json.toByteArray(Charsets.UTF_8), password)

    /**
     * Decrypt an encrypted backup to its original JSON string.
     *
     * @throws IllegalArgumentException if the data is too short or has wrong magic/version.
     * @throws javax.crypto.AEADBadTagException if the password is wrong or data is corrupt.
     */
    fun decrypt(data: ByteArray, password: String): String {
        if (BackupCrypto.isEncrypted(data)) {
            return String(BackupCrypto.decrypt(data, password), Charsets.UTF_8)
        }

        require(data.size >= MIN_PAYLOAD_SIZE) {
            "Data too short to be an encrypted HostShield backup"
        }

        val buf = ByteBuffer.wrap(data)

        // Validate magic
        val magic = ByteArray(MAGIC.size)
        buf.get(magic)
        require(magic.contentEquals(MAGIC)) {
            "Invalid backup: missing HSBACKUP magic header"
        }

        // Validate version
        val version = buf.get()
        require(version == FORMAT_VERSION_PBKDF2) {
            "Unsupported encrypted backup version: $version"
        }

        // Read salt and IV
        val salt = ByteArray(SALT_LENGTH)
        buf.get(salt)

        val iv = ByteArray(IV_LENGTH)
        buf.get(iv)

        // Read ciphertext (everything remaining)
        val ciphertext = ByteArray(buf.remaining())
        buf.get(ciphertext)

        // Try the current (600k) iteration count, then fall back to the legacy
        // 100k count used before v6.5.0 — the HSBACKUP format does not encode the
        // iteration count, so pre-v6.5.0 backups would otherwise be permanently
        // undecryptable with the correct password.
        return try {
            decryptWithIterations(salt, iv, ciphertext, password, PasswordKdf.BACKUP_PBKDF2_ITERATIONS)
        } catch (e: javax.crypto.AEADBadTagException) {
            decryptWithIterations(salt, iv, ciphertext, password, PasswordKdf.BACKUP_PBKDF2_ITERATIONS_LEGACY)
        }
    }

    // ── Internal ────────────────────────────────────────────────

    private fun decryptWithIterations(
        salt: ByteArray,
        iv: ByteArray,
        ciphertext: ByteArray,
        password: String,
        iterations: Int
    ): String {
        val keyBytes = PasswordKdf.derivePbkdf2HmacSha256(
            password,
            salt,
            iterations,
            PasswordKdf.KEY_LENGTH_BITS
        )
        val key = SecretKeySpec(keyBytes, "AES")
        // Wipe the raw byte array; SecretKeySpec retains its own copy.
        Arrays.fill(keyBytes, 0)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))
        val plaintext = cipher.doFinal(ciphertext)
        return String(plaintext, Charsets.UTF_8)
    }
}
