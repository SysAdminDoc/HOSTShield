package com.hostshield.data.preferences

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Encrypted key-value store backed by [EncryptedSharedPreferences].
 *
 * Uses AES256_SIV for key encryption and AES256_GCM for value encryption
 * via the Android Keystore-backed master key.
 */
@Singleton
class SecureStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val prefs: SharedPreferences by lazy {
        val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
        EncryptedSharedPreferences.create(
            "hostshield_secure_prefs",
            masterKeyAlias,
            context,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    /** Retrieve a stored secret string, or [default] if absent. */
    fun getString(key: String, default: String = ""): String =
        prefs.getString(key, default) ?: default

    /** Store a secret string value. */
    fun putString(key: String, value: String) {
        prefs.edit().putString(key, value).apply()
    }

    /** Remove a key from the secure store. */
    fun remove(key: String) {
        prefs.edit().remove(key).apply()
    }

    /** Check whether the store contains a given key. */
    fun contains(key: String): Boolean = prefs.contains(key)

    // ── PBKDF2 PIN hashing ──────────────────────────────────────

    companion object {
        private const val PBKDF2_ALGORITHM = "PBKDF2WithHmacSHA256"
        private const val ITERATIONS = 210_000
        private const val KEY_LENGTH_BITS = 256
        private const val SALT_BYTES = 16

        /**
         * Hash a raw PIN with PBKDF2-HMAC-SHA256.
         * Returns `"base64(salt):base64(hash)"`.
         */
        fun hashPin(rawPin: String): String {
            val salt = ByteArray(SALT_BYTES).also { SecureRandom().nextBytes(it) }
            val spec = PBEKeySpec(rawPin.toCharArray(), salt, ITERATIONS, KEY_LENGTH_BITS)
            val hash = SecretKeyFactory.getInstance(PBKDF2_ALGORITHM)
                .generateSecret(spec)
                .encoded
            val saltB64 = Base64.encodeToString(salt, Base64.NO_WRAP)
            val hashB64 = Base64.encodeToString(hash, Base64.NO_WRAP)
            return "$saltB64:$hashB64"
        }

        /**
         * Verify a raw PIN against a stored `"salt:hash"` string produced by [hashPin].
         */
        fun verifyPin(rawPin: String, stored: String): Boolean {
            if (!stored.contains(':')) return false
            val (saltB64, expectedB64) = stored.split(':', limit = 2)
            val salt = Base64.decode(saltB64, Base64.NO_WRAP)
            val spec = PBEKeySpec(rawPin.toCharArray(), salt, ITERATIONS, KEY_LENGTH_BITS)
            val hash = SecretKeyFactory.getInstance(PBKDF2_ALGORITHM)
                .generateSecret(spec)
                .encoded
            val hashB64 = Base64.encodeToString(hash, Base64.NO_WRAP)
            return hashB64 == expectedB64
        }
    }
}
