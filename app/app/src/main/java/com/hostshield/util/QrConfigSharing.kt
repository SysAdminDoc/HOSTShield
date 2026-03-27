package com.hostshield.util

import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import javax.inject.Inject
import javax.inject.Singleton

// ══════════════════════════════════════════════════════════════
// HostShield — QR Code Config Sharing (Roadmap #38)
//
// Encodes/decodes a lightweight subset of HostShield config
// into a compact string suitable for QR code transport.
//
// Format: "HS:" + Base64(GZIP(JSON))
//
// QR rendering requires a library (ZXing / ML Kit) at the UI
// layer — this class handles serialisation only.
// ══════════════════════════════════════════════════════════════

/** A single user rule for QR sharing (domain + block/allow type). */
data class RuleEntry(
    val domain: String,
    val type: String   // "block" or "allow"
)

/**
 * Shareable subset of HostShield configuration.
 *
 * Intentionally excludes large blocklist data — only source URLs
 * are shared so the receiving device can re-download them.
 */
data class ShareableConfig(
    val version: Int = 1,
    val userRules: List<RuleEntry> = emptyList(),
    val customDns: String = "",
    val dohEnabled: Boolean = false,
    val dohProvider: String = "",
    val sourceUrls: List<String> = emptyList(),
    val profileName: String = ""
)

@Singleton
class QrConfigSharing @Inject constructor() {

    companion object {
        /** Scheme prefix for HostShield QR payloads. */
        const val SCHEME_PREFIX = "HS:"

        /**
         * Practical ceiling for QR-encoded data.  QR version 40 can
         * hold ~4 KB of binary, but readability degrades well before
         * that limit.  2 KB keeps the code scannable on most cameras.
         */
        const val MAX_QR_BYTES = 2048
    }

    // ── Encoding ────────────────────────────────────────────────

    /**
     * Encode a [ShareableConfig] into the HostShield QR string format.
     *
     * If the full payload exceeds [MAX_QR_BYTES] the encoder
     * progressively trims optional fields (source URLs, then user
     * rules) to fit within the budget.
     *
     * @return an `"HS:..."` string ready to be rendered as a QR code.
     */
    fun encodeConfig(config: ShareableConfig): String {
        // First attempt: full config
        val full = toJson(config)
        val fullEncoded = compress(full)
        if (fullEncoded.length <= MAX_QR_BYTES) {
            return SCHEME_PREFIX + fullEncoded
        }

        // Second attempt: drop source URLs to save space
        val trimmedSources = config.copy(sourceUrls = emptyList())
        val trimmedEncoded = compress(toJson(trimmedSources))
        if (trimmedEncoded.length <= MAX_QR_BYTES) {
            return SCHEME_PREFIX + trimmedEncoded
        }

        // Third attempt: also truncate user rules to fit
        val maxRules = findMaxRules(config.copy(sourceUrls = emptyList()))
        val minimal = trimmedSources.copy(userRules = config.userRules.take(maxRules))
        return SCHEME_PREFIX + compress(toJson(minimal))
    }

    /**
     * Decode a QR string back into a [ShareableConfig].
     *
     * @return the parsed config, or `null` when the input is invalid
     *         or does not carry the `HS:` prefix.
     */
    fun decodeConfig(qrData: String): ShareableConfig? {
        if (!qrData.startsWith(SCHEME_PREFIX)) return null

        return try {
            val payload = qrData.removePrefix(SCHEME_PREFIX)
            val json = decompress(payload)
            fromJson(json)
        } catch (_: Exception) {
            null
        }
    }

    // ── JSON serialisation ──────────────────────────────────────

    private fun toJson(config: ShareableConfig): String {
        val root = JSONObject()
        root.put("v", config.version)

        if (config.userRules.isNotEmpty()) {
            val rulesArr = JSONArray()
            config.userRules.forEach { rule ->
                rulesArr.put(JSONObject().apply {
                    put("d", rule.domain)
                    put("t", rule.type)
                })
            }
            root.put("r", rulesArr)
        }

        if (config.customDns.isNotEmpty()) root.put("dns", config.customDns)
        if (config.dohEnabled) root.put("doh", true)
        if (config.dohProvider.isNotEmpty()) root.put("dohp", config.dohProvider)

        if (config.sourceUrls.isNotEmpty()) {
            root.put("src", JSONArray(config.sourceUrls))
        }

        if (config.profileName.isNotEmpty()) root.put("pn", config.profileName)

        return root.toString()
    }

    private fun fromJson(json: String): ShareableConfig {
        val root = JSONObject(json)

        val rules = mutableListOf<RuleEntry>()
        if (root.has("r")) {
            val arr = root.getJSONArray("r")
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                rules.add(RuleEntry(
                    domain = obj.getString("d"),
                    type = obj.optString("t", "block")
                ))
            }
        }

        val sourceUrls = mutableListOf<String>()
        if (root.has("src")) {
            val arr = root.getJSONArray("src")
            for (i in 0 until arr.length()) {
                sourceUrls.add(arr.getString(i))
            }
        }

        return ShareableConfig(
            version = root.optInt("v", 1),
            userRules = rules,
            customDns = root.optString("dns", ""),
            dohEnabled = root.optBoolean("doh", false),
            dohProvider = root.optString("dohp", ""),
            sourceUrls = sourceUrls,
            profileName = root.optString("pn", "")
        )
    }

    // ── Compression helpers ─────────────────────────────────────

    private fun compress(data: String): String {
        val bos = ByteArrayOutputStream()
        GZIPOutputStream(bos).use { gz ->
            gz.write(data.toByteArray(Charsets.UTF_8))
        }
        return Base64.encodeToString(bos.toByteArray(), Base64.NO_WRAP or Base64.URL_SAFE)
    }

    private fun decompress(encoded: String): String {
        val compressed = Base64.decode(encoded, Base64.NO_WRAP or Base64.URL_SAFE)
        val bis = ByteArrayInputStream(compressed)
        return GZIPInputStream(bis).use { gz ->
            gz.bufferedReader(Charsets.UTF_8).readText()
        }
    }

    // ── Size-fitting helpers ────────────────────────────────────

    /**
     * Binary-search for the maximum number of user rules that still
     * fits within [MAX_QR_BYTES] after compression.
     */
    private fun findMaxRules(config: ShareableConfig): Int {
        var lo = 0
        var hi = config.userRules.size
        var best = 0

        while (lo <= hi) {
            val mid = (lo + hi) / 2
            val candidate = config.copy(userRules = config.userRules.take(mid))
            val size = (SCHEME_PREFIX + compress(toJson(candidate))).length
            if (size <= MAX_QR_BYTES) {
                best = mid
                lo = mid + 1
            } else {
                hi = mid - 1
            }
        }
        return best
    }
}
