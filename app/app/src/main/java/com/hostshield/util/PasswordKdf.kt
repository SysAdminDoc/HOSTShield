package com.hostshield.util

import org.bouncycastle.crypto.generators.Argon2BytesGenerator
import org.bouncycastle.crypto.params.Argon2Parameters
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Arrays
import java.util.Base64
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * Password/PIN KDF helpers shared by local PIN hashes and encrypted backups.
 *
 * New records use Argon2id. PBKDF2-HMAC-SHA256 remains available only for
 * verifying legacy records that were written before the Argon2id migration.
 */
object PasswordKdf {
    data class Argon2idParams(
        val memoryKiB: Int = ARGON2_DEFAULT_MEMORY_KIB,
        val iterations: Int = ARGON2_DEFAULT_ITERATIONS,
        val parallelism: Int = ARGON2_DEFAULT_PARALLELISM
    ) {
        init {
            require(memoryKiB > 0) { "Argon2id memory must be positive" }
            require(iterations > 0) { "Argon2id iterations must be positive" }
            require(parallelism > 0) { "Argon2id parallelism must be positive" }
        }
    }

    const val PIN_SALT_BYTES = 16
    const val KEY_LENGTH_BYTES = 32
    const val KEY_LENGTH_BITS = KEY_LENGTH_BYTES * 8

    const val PIN_PBKDF2_ITERATIONS = 210_000
    const val BACKUP_PBKDF2_ITERATIONS = 600_000

    const val ARGON2_DEFAULT_MEMORY_KIB = 9 * 1024
    const val ARGON2_DEFAULT_ITERATIONS = 4
    const val ARGON2_DEFAULT_PARALLELISM = 1
    const val ARGON2_VERSION = 0x13
    const val MAX_ARGON2_MEMORY_KIB = 64 * 1024
    const val MAX_ARGON2_ITERATIONS = 10
    const val MAX_ARGON2_PARALLELISM = 4

    val DEFAULT_ARGON2ID_PARAMS = Argon2idParams()

    private const val ARGON2ID_PREFIX = "argon2id"
    private const val RECORD_SEPARATOR = '$'
    private const val PBKDF2_ALGORITHM = "PBKDF2WithHmacSHA256"
    private val BASE64_DECODER = Base64.getDecoder()
    private val BASE64_URL = Base64.getUrlEncoder().withoutPadding()
    private val BASE64_URL_DECODER = Base64.getUrlDecoder()
    private val ARGON2_PARAM_PATTERN = Regex("""m=(\d+),t=(\d+),p=(\d+)""")

    fun randomBytes(size: Int): ByteArray = ByteArray(size).also { SecureRandom().nextBytes(it) }

    fun isArgon2idRecord(stored: String): Boolean =
        stored.startsWith(ARGON2ID_PREFIX + RECORD_SEPARATOR)

    fun needsPinRehash(stored: String): Boolean = !isArgon2idRecord(stored)

    /**
     * Hash a raw PIN with Argon2id.
     *
     * Format:
     *   argon2id$v=19$m=<KiB>,t=<iterations>,p=<parallelism>$<salt-b64url>$<hash-b64url>
     */
    fun hashPin(rawPin: String): String {
        val salt = randomBytes(PIN_SALT_BYTES)
        val hash = deriveArgon2id(rawPin, salt, DEFAULT_ARGON2ID_PARAMS, KEY_LENGTH_BYTES)
        return try {
            encodeArgon2idRecord(DEFAULT_ARGON2ID_PARAMS, salt, hash)
        } finally {
            Arrays.fill(hash, 0)
        }
    }

    /**
     * Verify a raw PIN against either the new Argon2id record format or the
     * legacy `"base64(salt):base64(hash)"` PBKDF2 format.
     */
    fun verifyPin(rawPin: String, stored: String): Boolean {
        return when {
            isArgon2idRecord(stored) -> verifyArgon2idRecord(rawPin, stored)
            stored.contains(':') -> verifyLegacyPbkdf2Pin(rawPin, stored)
            else -> false
        }
    }

    fun deriveArgon2id(
        secret: String,
        salt: ByteArray,
        params: Argon2idParams = DEFAULT_ARGON2ID_PARAMS,
        outputBytes: Int = KEY_LENGTH_BYTES
    ): ByteArray {
        require(salt.isNotEmpty()) { "Argon2id salt must not be empty" }
        require(outputBytes > 0) { "Argon2id output size must be positive" }

        val passwordBytes = secret.toByteArray(Charsets.UTF_8)
        val output = ByteArray(outputBytes)
        try {
            val generator = Argon2BytesGenerator()
            generator.init(
                Argon2Parameters.Builder(Argon2Parameters.ARGON2_id)
                    .withVersion(ARGON2_VERSION)
                    .withMemoryAsKB(params.memoryKiB)
                    .withIterations(params.iterations)
                    .withParallelism(params.parallelism)
                    .withSalt(salt.copyOf())
                    .build()
            )
            generator.generateBytes(passwordBytes, output)
            return output
        } finally {
            Arrays.fill(passwordBytes, 0)
        }
    }

    fun derivePbkdf2HmacSha256(
        secret: String,
        salt: ByteArray,
        iterations: Int,
        keyLengthBits: Int = KEY_LENGTH_BITS
    ): ByteArray {
        require(salt.isNotEmpty()) { "PBKDF2 salt must not be empty" }
        require(iterations > 0) { "PBKDF2 iterations must be positive" }
        require(keyLengthBits > 0) { "PBKDF2 key length must be positive" }

        val spec = PBEKeySpec(secret.toCharArray(), salt, iterations, keyLengthBits)
        return try {
            SecretKeyFactory.getInstance(PBKDF2_ALGORITHM).generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }

    private fun encodeArgon2idRecord(params: Argon2idParams, salt: ByteArray, hash: ByteArray): String =
        listOf(
            ARGON2ID_PREFIX,
            "v=$ARGON2_VERSION",
            "m=${params.memoryKiB},t=${params.iterations},p=${params.parallelism}",
            BASE64_URL.encodeToString(salt),
            BASE64_URL.encodeToString(hash)
        ).joinToString(RECORD_SEPARATOR.toString())

    private fun verifyArgon2idRecord(rawPin: String, stored: String): Boolean {
        val parsed = parseArgon2idRecord(stored) ?: return false
        val actual = deriveArgon2id(rawPin, parsed.salt, parsed.params, parsed.hash.size)
        return try {
            MessageDigest.isEqual(actual, parsed.hash)
        } finally {
            Arrays.fill(actual, 0)
            Arrays.fill(parsed.hash, 0)
            Arrays.fill(parsed.salt, 0)
        }
    }

    private fun verifyLegacyPbkdf2Pin(rawPin: String, stored: String): Boolean {
        val parts = stored.split(':', limit = 2)
        if (parts.size != 2 || parts[0].isEmpty() || parts[1].isEmpty()) return false

        val salt: ByteArray
        val expected: ByteArray
        try {
            salt = BASE64_DECODER.decode(parts[0])
            expected = BASE64_DECODER.decode(parts[1])
        } catch (_: IllegalArgumentException) {
            return false
        }
        if (salt.isEmpty() || expected.size != KEY_LENGTH_BYTES) return false

        val actual = derivePbkdf2HmacSha256(rawPin, salt, PIN_PBKDF2_ITERATIONS)
        return try {
            MessageDigest.isEqual(actual, expected)
        } finally {
            Arrays.fill(actual, 0)
            Arrays.fill(expected, 0)
            Arrays.fill(salt, 0)
        }
    }

    private fun parseArgon2idRecord(stored: String): ParsedArgon2id? {
        val parts = stored.split(RECORD_SEPARATOR)
        if (parts.size != 5 || parts[0] != ARGON2ID_PREFIX || parts[1] != "v=$ARGON2_VERSION") {
            return null
        }

        val match = ARGON2_PARAM_PATTERN.matchEntire(parts[2]) ?: return null
        val params = runCatching {
            Argon2idParams(
                memoryKiB = match.groupValues[1].toInt(),
                iterations = match.groupValues[2].toInt(),
                parallelism = match.groupValues[3].toInt()
            )
        }.getOrNull() ?: return null
        if (!params.isWithinMobileBounds()) return null

        val salt: ByteArray
        val hash: ByteArray
        try {
            salt = BASE64_URL_DECODER.decode(parts[3])
            hash = BASE64_URL_DECODER.decode(parts[4])
        } catch (_: IllegalArgumentException) {
            return null
        }
        if (salt.isEmpty() || hash.isEmpty()) return null
        return ParsedArgon2id(params, salt, hash)
    }

    private data class ParsedArgon2id(
        val params: Argon2idParams,
        val salt: ByteArray,
        val hash: ByteArray
    )

    private fun Argon2idParams.isWithinMobileBounds(): Boolean =
        memoryKiB <= MAX_ARGON2_MEMORY_KIB &&
            iterations <= MAX_ARGON2_ITERATIONS &&
            parallelism <= MAX_ARGON2_PARALLELISM
}
