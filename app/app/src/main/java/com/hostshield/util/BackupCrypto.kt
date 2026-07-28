package com.hostshield.util

import java.nio.ByteBuffer
import java.util.Arrays
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

// ══════════════════════════════════════════════════════════════
// HostShield — BackupCrypto (AES-256-GCM)
// Stateless encryption/decryption for backup export/import.
// New exports use Argon2id key derivation; v1 PBKDF2 backups still decrypt.
// ══════════════════════════════════════════════════════════════

class BackupCrypto private constructor() {

    companion object {
        private val MAGIC = "HSBK".toByteArray(Charsets.US_ASCII) // 4-byte magic header
        private const val FORMAT_VERSION_PBKDF2: Byte = 1
        private const val FORMAT_VERSION_ARGON2ID: Byte = 2
        private const val SALT_LENGTH = 16
        private const val IV_LENGTH = 12
        private const val GCM_TAG_BITS = 128
        private const val GCM_TAG_BYTES = GCM_TAG_BITS / 8
        private const val ARGON2_PARAM_BYTES = 12
        // magic(4) + version(1) + salt(16) + iv(12) = 33 bytes
        private const val V1_HEADER_SIZE = 4 + 1 + SALT_LENGTH + IV_LENGTH
        // magic(4) + version(1) + memoryKiB(4) + iterations(4) + parallelism(4) + salt(16) + iv(12)
        private const val V2_HEADER_SIZE = 4 + 1 + ARGON2_PARAM_BYTES + SALT_LENGTH + IV_LENGTH
        private const val MIN_V1_PAYLOAD_SIZE = V1_HEADER_SIZE + GCM_TAG_BYTES
        private const val MIN_V2_PAYLOAD_SIZE = V2_HEADER_SIZE + GCM_TAG_BYTES
        private val ARGON2ID_PARAMS = PasswordKdf.DEFAULT_ARGON2ID_PARAMS

        /**
         * Encrypt plaintext bytes with a passphrase-derived AES-256-GCM key.
         *
         * Output layout:
         *   HSBK (4 B) | version 2 (1 B) | Argon2id params (12 B) | salt (16 B) | IV (12 B) | ciphertext + tag
         */
        fun encrypt(plaintext: ByteArray, passphrase: String): ByteArray {
            val salt = PasswordKdf.randomBytes(SALT_LENGTH)
            val iv = PasswordKdf.randomBytes(IV_LENGTH)
            BackupNonceLedger.rememberArgon2idExport(ARGON2ID_PARAMS, salt, iv)

            val key = deriveArgon2idKey(passphrase, salt, ARGON2ID_PARAMS)

            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))

            val ciphertext = cipher.doFinal(plaintext)

            val output = ByteBuffer.allocate(V2_HEADER_SIZE + ciphertext.size)
            output.put(MAGIC)
            output.put(FORMAT_VERSION_ARGON2ID)
            output.putInt(ARGON2ID_PARAMS.memoryKiB)
            output.putInt(ARGON2ID_PARAMS.iterations)
            output.putInt(ARGON2ID_PARAMS.parallelism)
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
            require(encrypted.size >= MIN_V1_PAYLOAD_SIZE) {
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
            return when (version) {
                FORMAT_VERSION_PBKDF2 -> decryptV1Pbkdf2(buf, passphrase, encrypted.size)
                FORMAT_VERSION_ARGON2ID -> decryptV2Argon2id(buf, passphrase, encrypted.size)
                else -> throw IllegalArgumentException("Unsupported encrypted backup version: $version")
            }
        }

        /**
         * Check whether raw bytes look like an encrypted HostShield backup
         * by inspecting the magic header bytes.
         */
        fun isEncrypted(data: ByteArray): Boolean {
            if (data.size < MAGIC.size) return false
            return data.copyOfRange(0, MAGIC.size).contentEquals(MAGIC)
        }

        // ── Internal ────────────────────────────────────────────

        private fun decryptV1Pbkdf2(buf: ByteBuffer, passphrase: String, totalSize: Int): ByteArray {
            require(totalSize >= MIN_V1_PAYLOAD_SIZE) {
                "Data too short to be an encrypted HostShield backup"
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

            // Try the current (600k) iteration count first, then fall back to the
            // legacy 100k count used before v6.5.0. Version-1 payloads do not
            // encode the iteration count, so a backup made on v6.3.0–v6.4.0 would
            // otherwise be permanently undecryptable with the correct passphrase.
            return try {
                decryptV1WithIterations(salt, iv, ciphertext, passphrase, PasswordKdf.BACKUP_PBKDF2_ITERATIONS)
            } catch (e: javax.crypto.AEADBadTagException) {
                decryptV1WithIterations(salt, iv, ciphertext, passphrase, PasswordKdf.BACKUP_PBKDF2_ITERATIONS_LEGACY)
            }
        }

        private fun decryptV1WithIterations(
            salt: ByteArray,
            iv: ByteArray,
            ciphertext: ByteArray,
            passphrase: String,
            iterations: Int
        ): ByteArray {
            val keyBytes = PasswordKdf.derivePbkdf2HmacSha256(
                passphrase, salt, iterations, PasswordKdf.KEY_LENGTH_BITS
            )
            val key = SecretKeySpec(keyBytes, "AES")
            Arrays.fill(keyBytes, 0)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))
            return cipher.doFinal(ciphertext)
        }

        private fun decryptV2Argon2id(buf: ByteBuffer, passphrase: String, totalSize: Int): ByteArray {
            require(totalSize >= MIN_V2_PAYLOAD_SIZE) {
                "Data too short to be an encrypted HostShield backup"
            }

            val params = PasswordKdf.Argon2idParams(
                memoryKiB = buf.int,
                iterations = buf.int,
                parallelism = buf.int
            )
            require(params.memoryKiB <= PasswordKdf.MAX_ARGON2_MEMORY_KIB) {
                "Encrypted backup Argon2id memory parameter is too high"
            }
            require(params.iterations <= PasswordKdf.MAX_ARGON2_ITERATIONS) {
                "Encrypted backup Argon2id iteration parameter is too high"
            }
            require(params.parallelism <= PasswordKdf.MAX_ARGON2_PARALLELISM) {
                "Encrypted backup Argon2id parallelism parameter is too high"
            }

            val salt = ByteArray(SALT_LENGTH)
            buf.get(salt)

            val iv = ByteArray(IV_LENGTH)
            buf.get(iv)

            val ciphertext = ByteArray(buf.remaining())
            buf.get(ciphertext)

            val key = deriveArgon2idKey(passphrase, salt, params)

            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))

            return cipher.doFinal(ciphertext)
        }

        private fun deriveArgon2idKey(
            passphrase: String,
            salt: ByteArray,
            params: PasswordKdf.Argon2idParams
        ): SecretKeySpec {
            val keyBytes = PasswordKdf.deriveArgon2id(passphrase, salt, params, PasswordKdf.KEY_LENGTH_BYTES)
            val keySpec = SecretKeySpec(keyBytes, "AES")
            Arrays.fill(keyBytes, 0)
            return keySpec
        }

    }
}
