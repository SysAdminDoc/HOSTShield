package com.hostshield.service

import android.util.Log
import com.hostshield.data.preferences.AppPreferences
import com.hostshield.data.preferences.SecureStore
import kotlinx.coroutines.flow.first
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Parental controls with age-based content profiles and PIN lock (Roadmap #48).
 *
 * Three age profiles control which [ContentCategory] categories are automatically
 * blocked. A 4-digit PIN protects settings changes when parental mode is active.
 *
 * Integration: called from DnsVpnService packet loop after per-app rules but
 * before the global blocklist check. The manager queries its own enabled state
 * and age profile, then delegates domain checks to [ContentFilterManager].
 *
 * Thread safety: all public methods are safe for concurrent hot-path calls.
 * Profile data is loaded once at VPN start and cached in volatile fields.
 */
@Singleton
class ParentalControlManager @Inject constructor(
    private val prefs: AppPreferences,
    private val contentFilterManager: ContentFilterManager
) {

    companion object {
        private const val TAG = "ParentalControl"
        private const val PIN_LENGTH = 4
    }

    /**
     * Age-based profiles. Each maps to a set of [ContentCategory] values
     * that are automatically blocked when that profile is active.
     */
    enum class AgeProfile(val label: String, val minAge: Int) {
        CHILD("Child (< 10)", 0),
        TEEN("Teen (10-17)", 10),
        ADULT("Adult (18+)", 18);

        companion object {
            fun fromName(name: String): AgeProfile =
                entries.firstOrNull { it.name.equals(name, ignoreCase = true) } ?: ADULT
        }
    }

    // Cached state — loaded at VPN start, read from hot path
    @Volatile var enabled = false
        private set
    @Volatile var currentProfile = AgeProfile.ADULT
        private set

    // Category restrictions per age profile
    private val profileRestrictions = ConcurrentHashMap<AgeProfile, Set<ContentCategory>>().apply {
        put(AgeProfile.CHILD, setOf(
            ContentCategory.ADULT,
            ContentCategory.GAMBLING,
            ContentCategory.DATING,
            ContentCategory.VPN_PROXY,
            ContentCategory.MALWARE,
            ContentCategory.CRYPTO,
            ContentCategory.SOCIAL,
            ContentCategory.GAMING,
            ContentCategory.STREAMING
        ))
        put(AgeProfile.TEEN, setOf(
            ContentCategory.ADULT,
            ContentCategory.GAMBLING,
            ContentCategory.DATING,
            ContentCategory.VPN_PROXY,
            ContentCategory.MALWARE,
            ContentCategory.CRYPTO
        ))
        put(AgeProfile.ADULT, emptySet())
    }

    // ── Public API ───────────────────────────────────────────────

    /**
     * Load parental control state from preferences.
     * Call once at VPN start (in startVpn()).
     */
    suspend fun loadState() {
        enabled = prefs.parentalEnabled.first()
        currentProfile = AgeProfile.fromName(prefs.parentalAgeProfile.first())
        Log.d(TAG, "Loaded: enabled=$enabled, profile=${currentProfile.name}")
    }

    /**
     * Check if [domain] should be blocked by the current age profile.
     * Returns true if parental controls are enabled and the domain
     * falls into a restricted category.
     *
     * Hot-path safe: no allocations, O(1) lookup.
     */
    fun shouldBlock(domain: String): Boolean {
        if (!enabled) return false
        val restrictions = profileRestrictions[currentProfile] ?: return false
        if (restrictions.isEmpty()) return false
        return contentFilterManager.isBlocked(domain, restrictions)
    }

    /**
     * Get the set of blocked categories for the current profile.
     */
    fun getRestrictedCategories(): Set<ContentCategory> =
        if (enabled) profileRestrictions[currentProfile] ?: emptySet()
        else emptySet()

    /**
     * Get the restrictions for a specific profile (for UI display).
     */
    fun getRestrictionsForProfile(profile: AgeProfile): Set<ContentCategory> =
        profileRestrictions[profile] ?: emptySet()

    // ── PIN Management ───────────────────────────────────────────

    /**
     * Set a new 4-digit PIN. Stores PBKDF2 hash in EncryptedSharedPreferences.
     * @return true if PIN was valid and stored.
     */
    suspend fun setPin(pin: String): Boolean {
        if (!isValidPin(pin)) return false
        prefs.setParentalPinHash(SecureStore.hashPin(pin))
        Log.d(TAG, "PIN updated (PBKDF2)")
        return true
    }

    /**
     * Verify a PIN against the stored hash.
     * Supports both legacy SHA-256 (hex string without ':') and
     * new PBKDF2 ("salt:hash") formats for seamless migration.
     * @return true if PIN matches, or if no PIN is set.
     */
    suspend fun verifyPin(pin: String): Boolean {
        val storedHash = prefs.parentalPinHash.first()
        if (storedHash.isEmpty()) return true // no PIN set
        return if (storedHash.contains(':')) {
            // New PBKDF2 format
            SecureStore.verifyPin(pin, storedHash)
        } else {
            // Legacy SHA-256 format — verify then upgrade to PBKDF2
            val legacyMatch = MessageDigest.isEqual(hashPin(pin).toByteArray(), storedHash.toByteArray())
            if (legacyMatch) {
                // Re-hash with PBKDF2 and store the upgraded hash
                prefs.setParentalPinHash(SecureStore.hashPin(pin))
                Log.d(TAG, "PIN hash upgraded from SHA-256 to PBKDF2")
            }
            legacyMatch
        }
    }

    /**
     * Check if a PIN has been configured.
     */
    suspend fun isPinSet(): Boolean = prefs.parentalPinHash.first().isNotEmpty()

    /**
     * Remove the PIN lock.
     */
    suspend fun clearPin() {
        prefs.setParentalPinHash("")
        Log.d(TAG, "PIN cleared")
    }

    // ── Profile Management ───────────────────────────────────────

    /**
     * Change the active age profile. Requires PIN verification first
     * if parental controls are enabled and a PIN is set.
     */
    suspend fun setProfile(profile: AgeProfile) {
        prefs.setParentalAgeProfile(profile.name)
        currentProfile = profile
        Log.d(TAG, "Profile changed to ${profile.name}")
    }

    /**
     * Enable parental controls with the given profile.
     */
    suspend fun enable(profile: AgeProfile) {
        prefs.setParentalEnabled(true)
        prefs.setParentalAgeProfile(profile.name)
        enabled = true
        currentProfile = profile
        Log.i(TAG, "Enabled with profile ${profile.name}")
    }

    /**
     * Disable parental controls. Requires PIN verification if PIN is set.
     */
    suspend fun disable() {
        prefs.setParentalEnabled(false)
        enabled = false
        Log.i(TAG, "Disabled")
    }

    // ── Internals ────────────────────────────────────────────────

    private fun isValidPin(pin: String): Boolean =
        pin.length == PIN_LENGTH && pin.all { it.isDigit() }

    private fun hashPin(pin: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val bytes = digest.digest(pin.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
