package com.hostshield.util

import java.nio.ByteBuffer
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
import javax.inject.Inject
import javax.inject.Singleton

// ══════════════════════════════════════════════════════════════
// HostShield — Encrypted Backup (AES-256-GCM)
// Roadmap #36: Encrypted backup format
// ══════════════════════════════════════════════════════════════

@Singleton
class EncryptedBackup @Inject constructor(
    private val backupRestoreUtil: BackupRestoreUtil
) {
    companion object {
        private val MAGIC = "HSBACKUP".toByteArray(Charsets.US_ASCII) // 8 bytes
        private const val FORMAT_VERSION: Byte = 1
        private const val SALT_LENGTH = 16
        private const val IV_LENGTH = 12
        private const val KEY_LENGTH_BITS = 256
        // OWASP 2023 PBKDF2-HMAC-SHA256 baseline is 600_000. Align with the rest
        // of the app (SecureStore PIN uses 210_000; backup is higher-value because
        // the file is exfiltratable to a desktop attacker).
        private const val PBKDF2_ITERATIONS = 600_000
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
            if (data.size < HEADER_SIZE) return false
            return data.copyOfRange(0, MAGIC.size).contentEquals(MAGIC)
        }
    }

    // ── Public API ──────────────────────────────────────────────

    /**
     * Encrypt a JSON backup string with the given password.
     *
     * Output format (binary):
     *   HSBACKUP (8 B) | version (1 B) | salt (16 B) | IV (12 B) | ciphertext+tag
     */
    fun encrypt(json: String, password: String): ByteArray {
        val salt = ByteArray(SALT_LENGTH).also { SecureRandom().nextBytes(it) }
        val iv = ByteArray(IV_LENGTH).also { SecureRandom().nextBytes(it) }

        val key = deriveKey(password, salt)

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))

        val plaintext = json.toByteArray(Charsets.UTF_8)
        val ciphertext = cipher.doFinal(plaintext)

        // Assemble: magic + version + salt + iv + ciphertext
        val output = ByteBuffer.allocate(HEADER_SIZE + ciphertext.size)
        output.put(MAGIC)
        output.put(FORMAT_VERSION)
        output.put(salt)
        output.put(iv)
        output.put(ciphertext)

        return output.array()
    }

    /**
     * Decrypt an encrypted backup to its original JSON string.
     *
     * @throws IllegalArgumentException if the data is too short or has wrong magic/version.
     * @throws javax.crypto.AEADBadTagException if the password is wrong or data is corrupt.
     */
    fun decrypt(data: ByteArray, password: String): String {
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
        require(version == FORMAT_VERSION) {
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

        val key = deriveKey(password, salt)

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))

        val plaintext = cipher.doFinal(ciphertext)
        return String(plaintext, Charsets.UTF_8)
    }

    // ── Internal ────────────────────────────────────────────────

    private fun deriveKey(password: String, salt: ByteArray): SecretKeySpec {
        val spec = PBEKeySpec(password.toCharArray(), salt, PBKDF2_ITERATIONS, KEY_LENGTH_BITS)
        val keyBytes = try {
            SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                .generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
        val keySpec = SecretKeySpec(keyBytes, "AES")
        // Wipe the raw byte array; SecretKeySpec retains its own copy.
        java.util.Arrays.fill(keyBytes, 0)
        return keySpec
    }
}
