package com.hostshield.data.preferences

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Small encrypted key-value store backed by Android Keystore AES-GCM.
 *
 * New writes use [SecureStoreCrypto] and `hostshield_secure_store_v2`. A
 * one-time Tink reader migrates values from the old AndroidX
 * EncryptedSharedPreferences file when the original Keystore key is still
 * available, then all reads/writes stay on the local wrapper.
 */
@Singleton
class SecureStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val prefs: SharedPreferences by lazy {
        context.getSharedPreferences(STORE_PREFS_NAME, Context.MODE_PRIVATE)
    }
    private val masterKey: SecretKey by lazy { getOrCreateMasterKey() }
    private val legacyReader by lazy { LegacyEncryptedPreferencesReader(context) }
    @Volatile private var migrationChecked = false

    /** Retrieve a stored secret string, or [default] if absent/unavailable. */
    fun getString(key: String, default: String = ""): String {
        ensureLegacyMigration()
        val envelope = prefs.getString(SecureStoreCrypto.storageKey(key), null) ?: return default
        return runCatching {
            SecureStoreCrypto.decryptString(key, envelope, masterKey)
        }.onFailure {
            Log.w(TAG, "Unable to decrypt secure value for $key: ${it.message}")
        }.getOrDefault(default)
    }

    /** Store a secret string value. */
    fun putString(key: String, value: String) {
        ensureLegacyMigration()
        val envelope = SecureStoreCrypto.encryptString(key, value, masterKey)
        prefs.edit().putString(SecureStoreCrypto.storageKey(key), envelope).apply()
    }

    /** Remove a key from the secure store. */
    fun remove(key: String) {
        ensureLegacyMigration()
        prefs.edit().remove(SecureStoreCrypto.storageKey(key)).apply()
    }

    /** Check whether the store contains a given key. */
    fun contains(key: String): Boolean {
        ensureLegacyMigration()
        return prefs.contains(SecureStoreCrypto.storageKey(key))
    }

    private fun ensureLegacyMigration() {
        if (migrationChecked || prefs.getBoolean(KEY_LEGACY_MIGRATION_DONE, false)) {
            migrationChecked = true
            return
        }
        synchronized(this) {
            if (migrationChecked || prefs.getBoolean(KEY_LEGACY_MIGRATION_DONE, false)) {
                migrationChecked = true
                return
            }
            val migrated = legacyReader.readAllStrings()
            if (migrated == null) {
                migrationChecked = true
                return
            }
            val editor = prefs.edit()
            migrated.forEach { (key, value) ->
                val storageKey = SecureStoreCrypto.storageKey(key)
                if (!prefs.contains(storageKey)) {
                    editor.putString(storageKey, SecureStoreCrypto.encryptString(key, value, masterKey))
                }
            }
            editor.putBoolean(KEY_LEGACY_MIGRATION_DONE, true).apply()
            migrationChecked = true
            if (migrated.isNotEmpty()) {
                Log.i(TAG, "Migrated ${migrated.size} legacy secure values")
            }
        }
    }

    private fun getOrCreateMasterKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getEntry(MASTER_KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.secretKey?.let {
            return it
        }

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        val spec = KeyGenParameterSpec.Builder(
            MASTER_KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setKeySize(256)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setRandomizedEncryptionRequired(true)
            .build()
        generator.init(spec)
        return generator.generateKey()
    }

    private class LegacyEncryptedPreferencesReader(private val context: Context) {
        fun readAllStrings(): Map<String, String>? {
            val legacyPrefs = context.getSharedPreferences(LEGACY_PREFS_NAME, Context.MODE_PRIVATE)
            val rawEntries = legacyPrefs.all
            if (!rawEntries.containsKey(KEY_KEYSET_ALIAS) || !rawEntries.containsKey(VALUE_KEYSET_ALIAS)) {
                return emptyMap()
            }

            return runCatching {
                com.google.crypto.tink.aead.AeadConfig.register()
                com.google.crypto.tink.daead.DeterministicAeadConfig.register()

                val keyDaead = com.google.crypto.tink.integration.android.AndroidKeysetManager.Builder()
                    .withKeyTemplate(com.google.crypto.tink.KeyTemplates.get("AES256_SIV"))
                    .withSharedPref(context, KEY_KEYSET_ALIAS, LEGACY_PREFS_NAME)
                    .withMasterKeyUri(LEGACY_MASTER_KEY_URI)
                    .build()
                    .keysetHandle
                    .getPrimitive(
                        com.google.crypto.tink.RegistryConfiguration.get(),
                        com.google.crypto.tink.DeterministicAead::class.java
                    )

                val valueAead = com.google.crypto.tink.integration.android.AndroidKeysetManager.Builder()
                    .withKeyTemplate(com.google.crypto.tink.KeyTemplates.get("AES256_GCM"))
                    .withSharedPref(context, VALUE_KEYSET_ALIAS, LEGACY_PREFS_NAME)
                    .withMasterKeyUri(LEGACY_MASTER_KEY_URI)
                    .build()
                    .keysetHandle
                    .getPrimitive(
                        com.google.crypto.tink.RegistryConfiguration.get(),
                        com.google.crypto.tink.Aead::class.java
                    )

                rawEntries.mapNotNull { (encryptedKey, encryptedValue) ->
                    if (isReservedLegacyKey(encryptedKey) || encryptedValue !is String) return@mapNotNull null
                    val key = decryptLegacyKey(encryptedKey, keyDaead) ?: return@mapNotNull null
                    val value = decryptLegacyString(encryptedKey, encryptedValue, valueAead)
                        ?: return@mapNotNull null
                    key to value
                }.toMap()
            }.onFailure {
                Log.w(TAG, "Legacy secure-store migration skipped: ${it.message}")
            }.getOrNull()
        }

        private fun decryptLegacyKey(
            encryptedKey: String,
            daead: com.google.crypto.tink.DeterministicAead
        ): String? {
            val clearText = daead.decryptDeterministically(
                com.google.crypto.tink.subtle.Base64.decode(encryptedKey, android.util.Base64.DEFAULT),
                LEGACY_PREFS_NAME.toByteArray(StandardCharsets.UTF_8)
            )
            val key = String(clearText, StandardCharsets.UTF_8)
            return if (key == LEGACY_NULL_VALUE) null else key
        }

        private fun decryptLegacyString(
            encryptedKey: String,
            encryptedValue: String,
            aead: com.google.crypto.tink.Aead
        ): String? {
            val cipherText = com.google.crypto.tink.subtle.Base64.decode(
                encryptedValue,
                android.util.Base64.DEFAULT
            )
            val value = aead.decrypt(cipherText, encryptedKey.toByteArray(StandardCharsets.UTF_8))
            val buffer = ByteBuffer.wrap(value)
            val typeId = buffer.int
            if (typeId != LEGACY_STRING_TYPE_ID) return null
            val length = buffer.int
            require(length >= 0 && length <= buffer.remaining()) { "Invalid legacy string length" }
            val bytes = ByteArray(length)
            buffer.get(bytes)
            val decoded = String(bytes, StandardCharsets.UTF_8)
            return if (decoded == LEGACY_NULL_VALUE) null else decoded
        }

        private fun isReservedLegacyKey(key: String): Boolean =
            key == KEY_KEYSET_ALIAS || key == VALUE_KEYSET_ALIAS
    }

    // PBKDF2 PIN hashing
    companion object {
        private const val TAG = "SecureStore"
        private const val STORE_PREFS_NAME = "hostshield_secure_store_v2"
        private const val KEY_LEGACY_MIGRATION_DONE = "__legacy_esp_migration_done__"
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val MASTER_KEY_ALIAS = "hostshield_secure_store_master_v2"

        private const val LEGACY_PREFS_NAME = "hostshield_secure_prefs"
        private const val LEGACY_MASTER_KEY_URI = "android-keystore://_androidx_security_master_key_"
        private const val KEY_KEYSET_ALIAS = "__androidx_security_crypto_encrypted_prefs_key_keyset__"
        private const val VALUE_KEYSET_ALIAS = "__androidx_security_crypto_encrypted_prefs_value_keyset__"
        private const val LEGACY_NULL_VALUE = "__NULL__"
        private const val LEGACY_STRING_TYPE_ID = 0

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
            val hash = try {
                SecretKeyFactory.getInstance(PBKDF2_ALGORITHM)
                    .generateSecret(spec)
                    .encoded
            } finally {
                spec.clearPassword()
            }
            val saltB64 = android.util.Base64.encodeToString(salt, android.util.Base64.NO_WRAP)
            val hashB64 = android.util.Base64.encodeToString(hash, android.util.Base64.NO_WRAP)
            return "$saltB64:$hashB64"
        }

        /**
         * Verify a raw PIN against a stored `"salt:hash"` string produced by [hashPin].
         * Constant-time comparison on decoded bytes (not Base64 strings, which can
         * leak length differences). Returns false on any decode failure.
         */
        fun verifyPin(rawPin: String, stored: String): Boolean {
            if (!stored.contains(':')) return false
            val parts = stored.split(':', limit = 2)
            if (parts.size != 2 || parts[0].isEmpty() || parts[1].isEmpty()) return false
            val salt: ByteArray
            val expected: ByteArray
            try {
                salt = android.util.Base64.decode(parts[0], android.util.Base64.NO_WRAP)
                expected = android.util.Base64.decode(parts[1], android.util.Base64.NO_WRAP)
            } catch (_: IllegalArgumentException) {
                return false
            }
            if (salt.isEmpty() || expected.size * 8 != KEY_LENGTH_BITS) return false
            val spec = PBEKeySpec(rawPin.toCharArray(), salt, ITERATIONS, KEY_LENGTH_BITS)
            val hash = try {
                SecretKeyFactory.getInstance(PBKDF2_ALGORITHM)
                    .generateSecret(spec)
                    .encoded
            } finally {
                spec.clearPassword()
            }
            return MessageDigest.isEqual(hash, expected)
        }
    }
}
