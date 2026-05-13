package com.hostshield.util

import java.nio.ByteBuffer
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

// ══════════════════════════════════════════════════════════════
// HostShield — BackupCrypto (AES-256-GCM)
// Stateless encryption/decryption for backup export/import.
// Uses PBKDF2WithHmacSHA256 key derivation with 100k iterations.
// ══════════════════════════════════════════════════════════════

class BackupCrypto private constructor() {

    companion object {
        private val MAGIC = "HSBK".toByteArray(Charsets.US_ASCII) // 4-byte magic header
        private const val FORMAT_VERSION: Byte = 1
        private const val SALT_LENGTH = 16
        private const val IV_LENGTH = 12
        private const val KEY_LENGTH_BITS = 256
        // OWASP 2023 PBKDF2-HMAC-SHA256 baseline.
        private const val PBKDF2_ITERATIONS = 600_000
        private const val GCM_TAG_BITS = 128
        private const val GCM_TAG_BYTES = GCM_TAG_BITS / 8

        // magic(4) + version(1) + salt(16) + iv(12) = 33 bytes
        private const val HEADER_SIZE = 4 + 1 + SALT_LENGTH + IV_LENGTH
        private const val MIN_PAYLOAD_SIZE = HEADER_SIZE + GCM_TAG_BYTES

        /**
         * Encrypt plaintext bytes with a passphrase-derived AES-256-GCM key.
         *
         * Output layout:
         *   HSBK (4 B) | version (1 B) | salt (16 B) | IV (12 B) | ciphertext + GCM tag
         */
        fun encrypt(plaintext: ByteArray, passphrase: String): ByteArray {
            val salt = ByteArray(SALT_LENGTH).also { SecureRandom().nextBytes(it) }
            val iv = ByteArray(IV_LENGTH).also { SecureRandom().nextBytes(it) }

            val key = deriveKey(passphrase, salt)

            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))

            val ciphertext = cipher.doFinal(plaintext)

            val output = ByteBuffer.allocate(HEADER_SIZE + ciphertext.size)
            output.put(MAGIC)
            output.put(FORMAT_VERSION)
            output.put(salt)
            output.put(iv)
            output.put(ciphertext)
            return output.array()
        }

        /**
         * Decrypt an encrypted backup produced by [encrypt].
         *
         * @throws IllegalArgumentException if the data is too short or has a wrong magic/version.
         * @throws javax.crypto.AEADBadTagException if the passphrase is wrong or data is corrupt.
         */
        fun decrypt(encrypted: ByteArray, passphrase: String): ByteArray {
            require(encrypted.size >= MIN_PAYLOAD_SIZE) {
                "Data too short to be an encrypted HostShield backup"
            }

            val buf = ByteBuffer.wrap(encrypted)

            // Validate magic
            val magic = ByteArray(MAGIC.size)
            buf.get(magic)
            require(magic.contentEquals(MAGIC)) {
                "Not an encrypted HostShield backup (invalid header)"
            }

            // Validate version
            val version = buf.get()
            require(version == FORMAT_VERSION) {
                "Unsupported encrypted backup version: $version"
            }

            // Extract salt
            val salt = ByteArray(SALT_LENGTH)
            buf.get(salt)

            // Extract IV
            val iv = ByteArray(IV_LENGTH)
            buf.get(iv)

            // Remaining bytes are ciphertext + GCM tag
            val ciphertext = ByteArray(buf.remaining())
            buf.get(ciphertext)

            val key = deriveKey(passphrase, salt)

            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))

            return cipher.doFinal(ciphertext)
        }

        /**
         * Check whether raw bytes look like an encrypted HostShield backup
         * by inspecting the magic header bytes.
         */
        fun isEncrypted(data: ByteArray): Boolean {
            if (data.size < HEADER_SIZE) return false
            return data.copyOfRange(0, MAGIC.size).contentEquals(MAGIC)
        }

        // ── Internal ────────────────────────────────────────────

        private fun deriveKey(passphrase: String, salt: ByteArray): SecretKeySpec {
            val spec = PBEKeySpec(passphrase.toCharArray(), salt, PBKDF2_ITERATIONS, KEY_LENGTH_BITS)
            val keyBytes = try {
                SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                    .generateSecret(spec).encoded
            } finally {
                spec.clearPassword()
            }
            val keySpec = SecretKeySpec(keyBytes, "AES")
            java.util.Arrays.fill(keyBytes, 0)
            return keySpec
        }
    }
}
