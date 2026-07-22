package com.hostshield.data.preferences

import android.content.Context
import androidx.datastore.preferences.core.*
import dagger.hilt.android.qualifiers.ApplicationContext
import com.hostshield.util.ParentalPinHashPolicy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SecurityPreferences @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val secureStore: SecureStore
) {
    private val ds get() = context.hostShieldDataStore

    internal object Keys {
        val WIREGUARD_ENABLED = booleanPreferencesKey("wireguard_enabled")
        val WIREGUARD_ENDPOINT = stringPreferencesKey("wireguard_endpoint")
        val WIREGUARD_PRIVATE_KEY = stringPreferencesKey("wireguard_private_key")
        val WIREGUARD_PUBLIC_KEY = stringPreferencesKey("wireguard_public_key")
        val WIREGUARD_PRESHARED_KEY = stringPreferencesKey("wireguard_preshared_key")
        val WIREGUARD_DNS_IP = stringPreferencesKey("wireguard_dns_ip")
        val THREAT_INTEL_ENABLED = booleanPreferencesKey("threat_intel_enabled")
        val SAFE_SEARCH_ENABLED = booleanPreferencesKey("safe_search_enabled")
        val CONTENT_FILTER_CATEGORIES = stringSetPreferencesKey("content_filter_categories")
        val PARENTAL_ENABLED = booleanPreferencesKey("parental_enabled")
        val PARENTAL_PIN_HASH = stringPreferencesKey("parental_pin_hash")
        val PARENTAL_PIN_REHASH_REQUIRED = booleanPreferencesKey("parental_pin_rehash_required")
        val PARENTAL_AGE_PROFILE = stringPreferencesKey("parental_age_profile")
    }

    // ── Secure-store key constants ──────────────────────────────
    private companion object {
        const val SEC_WG_PRIVATE_KEY = "sec_wireguard_private_key"
        const val SEC_WG_ENDPOINT = "sec_wireguard_endpoint"
        const val SEC_WG_PSK = "sec_wireguard_preshared_key"
        const val SEC_PARENTAL_PIN_HASH = "sec_parental_pin_hash"
    }

    // ── Migration helper ────────────────────────────────────────
    /**
     * If a plaintext value exists in DataStore for the given key, migrate it
     * into [SecureStore] and clear the DataStore entry.
     */
    private suspend fun migrateIfNeeded(
        dsKey: Preferences.Key<String>,
        secKey: String
    ) {
        if (!secureStore.contains(secKey)) {
            val plaintext = ds.data.map { it[dsKey] }.first()
            if (!plaintext.isNullOrEmpty()) {
                secureStore.putString(secKey, plaintext)
                ds.edit { it.remove(dsKey) }
            }
        }
    }

    /**
     * Run once (e.g. at app start) to migrate any plaintext secrets from
     * DataStore into SecureStore.
     */
    suspend fun migratePlaintextSecrets() {
        migrateIfNeeded(Keys.WIREGUARD_PRIVATE_KEY, SEC_WG_PRIVATE_KEY)
        migrateIfNeeded(Keys.WIREGUARD_ENDPOINT, SEC_WG_ENDPOINT)
        migrateIfNeeded(Keys.WIREGUARD_PRESHARED_KEY, SEC_WG_PSK)
        migrateIfNeeded(Keys.PARENTAL_PIN_HASH, SEC_PARENTAL_PIN_HASH)
        refreshParentalPinRehashRequired()
    }

    suspend fun refreshParentalPinRehashRequired() {
        val hash = secureStore.getString(SEC_PARENTAL_PIN_HASH)
        ds.edit { prefs ->
            if (ParentalPinHashPolicy.isLegacySha256Record(hash)) {
                prefs[Keys.PARENTAL_PIN_REHASH_REQUIRED] = true
            } else {
                prefs.remove(Keys.PARENTAL_PIN_REHASH_REQUIRED)
            }
        }
    }

    // ── WireGuard ───────────────────────────────────────────────
    val wireGuardEnabled: Flow<Boolean> = ds.data.map { it[Keys.WIREGUARD_ENABLED] ?: false }
    suspend fun setWireGuardEnabled(enabled: Boolean) = ds.edit { it[Keys.WIREGUARD_ENABLED] = enabled }

    // Secret accessors are cold flows so the Keystore decrypt + disk I/O runs on
    // collection (off the caller's thread), not eagerly at property access.

    /** Endpoint is now served from SecureStore (Flow wrapper for API compat). */
    val wireGuardEndpoint: Flow<String>
        get() = flow { emit(secureStore.getString(SEC_WG_ENDPOINT)) }.flowOn(Dispatchers.IO)
    suspend fun setWireGuardEndpoint(endpoint: String) = secureStore.putString(SEC_WG_ENDPOINT, endpoint)

    /** Private key is now served from SecureStore (Flow wrapper for API compat). */
    val wireGuardPrivateKey: Flow<String>
        get() = flow { emit(secureStore.getString(SEC_WG_PRIVATE_KEY)) }.flowOn(Dispatchers.IO)
    suspend fun setWireGuardPrivateKey(key: String) = secureStore.putString(SEC_WG_PRIVATE_KEY, key)

    val wireGuardPublicKey: Flow<String> = ds.data.map { it[Keys.WIREGUARD_PUBLIC_KEY] ?: "" }
    suspend fun setWireGuardPublicKey(key: String) = ds.edit { it[Keys.WIREGUARD_PUBLIC_KEY] = key }

    /** Pre-shared key is now served from SecureStore (Flow wrapper for API compat). */
    val wireGuardPresharedKey: Flow<String>
        get() = flow { emit(secureStore.getString(SEC_WG_PSK)) }.flowOn(Dispatchers.IO)
    suspend fun setWireGuardPresharedKey(key: String) = secureStore.putString(SEC_WG_PSK, key)

    val wireGuardDnsIp: Flow<String> = ds.data.map { it[Keys.WIREGUARD_DNS_IP] ?: "" }
    suspend fun setWireGuardDnsIp(ip: String) = ds.edit { it[Keys.WIREGUARD_DNS_IP] = ip }

    // ── Threat intelligence ─────────────────────────────────────
    val threatIntelEnabled: Flow<Boolean> = ds.data.map { it[Keys.THREAT_INTEL_ENABLED] ?: true }
    suspend fun setThreatIntelEnabled(enabled: Boolean) = ds.edit { it[Keys.THREAT_INTEL_ENABLED] = enabled }

    // ── Safe search ─────────────────────────────────────────────
    val safeSearchEnabled: Flow<Boolean> = ds.data.map { it[Keys.SAFE_SEARCH_ENABLED] ?: false }
    suspend fun setSafeSearchEnabled(enabled: Boolean) = ds.edit { it[Keys.SAFE_SEARCH_ENABLED] = enabled }

    // ── Content filter ──────────────────────────────────────────
    val contentFilterCategories: Flow<Set<String>> = ds.data.map {
        it[Keys.CONTENT_FILTER_CATEGORIES] ?: emptySet()
    }
    suspend fun setContentFilterCategories(categories: Set<String>) = ds.edit {
        it[Keys.CONTENT_FILTER_CATEGORIES] = categories
    }

    // ── Parental controls ───────────────────────────────────────
    val parentalEnabled: Flow<Boolean> = ds.data.map { it[Keys.PARENTAL_ENABLED] ?: false }
    suspend fun setParentalEnabled(enabled: Boolean) = ds.edit { it[Keys.PARENTAL_ENABLED] = enabled }

    /** PIN hash is now served from SecureStore (Flow wrapper for API compat). */
    val parentalPinHash: Flow<String>
        get() = flow { emit(secureStore.getString(SEC_PARENTAL_PIN_HASH)) }.flowOn(Dispatchers.IO)
    val parentalPinRehashRequired: Flow<Boolean> =
        ds.data.map { it[Keys.PARENTAL_PIN_REHASH_REQUIRED] ?: false }
    suspend fun setParentalPinHash(hash: String) {
        secureStore.putString(SEC_PARENTAL_PIN_HASH, hash)
        ds.edit { prefs ->
            if (ParentalPinHashPolicy.isLegacySha256Record(hash)) {
                prefs[Keys.PARENTAL_PIN_REHASH_REQUIRED] = true
            } else {
                prefs.remove(Keys.PARENTAL_PIN_REHASH_REQUIRED)
            }
        }
    }

    val parentalAgeProfile: Flow<String> = ds.data.map { it[Keys.PARENTAL_AGE_PROFILE] ?: "ADULT" }
    suspend fun setParentalAgeProfile(profile: String) = ds.edit { it[Keys.PARENTAL_AGE_PROFILE] = profile }
}
