package com.hostshield.util

import java.nio.ByteBuffer
import java.security.MessageDigest
import java.util.ArrayDeque
import java.util.Base64
import java.util.HashSet

/**
 * Bounded export-time guard for AES-GCM backup key/IV tuple reuse.
 *
 * HostShield v2 backups derive the AES key from the user passphrase plus the
 * per-backup Argon2id salt. The public key-id proxy for generated backups is
 * therefore the KDF parameter set plus salt; pairing it with the IV lets the app
 * refuse a repeated AES-GCM tuple if entropy generation ever regresses.
 */
internal object BackupNonceLedger {
    private const val MAX_TRACKED_EXPORTS = 1024
    private const val BACKUP_FORMAT_VERSION_ARGON2ID = 2
    private val fingerprintDomain = "HostShieldBackupNonceLedger:v1".toByteArray(Charsets.US_ASCII)
    private val orderedFingerprints = ArrayDeque<String>()
    private val fingerprints = HashSet<String>()
    private val lock = Any()

    fun rememberArgon2idExport(
        params: PasswordKdf.Argon2idParams,
        salt: ByteArray,
        iv: ByteArray
    ) {
        require(salt.size == 16) { "Backup salt must be 16 bytes" }
        require(iv.size == 12) { "AES-GCM backup IV must be 12 bytes" }
        require(params.memoryKiB <= PasswordKdf.MAX_ARGON2_MEMORY_KIB) {
            "Encrypted backup Argon2id memory parameter is too high"
        }
        require(params.iterations <= PasswordKdf.MAX_ARGON2_ITERATIONS) {
            "Encrypted backup Argon2id iteration parameter is too high"
        }
        require(params.parallelism <= PasswordKdf.MAX_ARGON2_PARALLELISM) {
            "Encrypted backup Argon2id parallelism parameter is too high"
        }

        val fingerprint = fingerprint(params, salt, iv)
        synchronized(lock) {
            check(fingerprints.add(fingerprint)) {
                "Duplicate AES-GCM backup key/IV tuple generated; refusing export"
            }
            orderedFingerprints.addLast(fingerprint)
            while (orderedFingerprints.size > MAX_TRACKED_EXPORTS) {
                fingerprints.remove(orderedFingerprints.removeFirst())
            }
        }
    }

    fun clearForTest() {
        synchronized(lock) {
            orderedFingerprints.clear()
            fingerprints.clear()
        }
    }

    private fun fingerprint(
        params: PasswordKdf.Argon2idParams,
        salt: ByteArray,
        iv: ByteArray
    ): String {
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(fingerprintDomain)
        digest.update(BACKUP_FORMAT_VERSION_ARGON2ID.toByte())
        digest.updateInt(params.memoryKiB)
        digest.updateInt(params.iterations)
        digest.updateInt(params.parallelism)
        digest.update(salt)
        digest.update(iv)
        return Base64.getEncoder().encodeToString(digest.digest())
    }

    private fun MessageDigest.updateInt(value: Int) {
        update(ByteBuffer.allocate(Int.SIZE_BYTES).putInt(value).array())
    }
}
