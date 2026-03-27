package com.hostshield.util

import android.util.Log
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * TLS ClientHello fingerprinting — JA3 and JA4 hash computation (Roadmap #47).
 *
 * Extracts fingerprints from TLS ClientHello messages observed in the VPN
 * packet loop. These fingerprints identify the TLS library (and often the
 * specific application) making the connection, enabling protocol-level
 * visibility without decrypting traffic.
 *
 * JA3 (Salesforce, 2017):
 *   MD5(TLSVersion,Ciphers,Extensions,EllipticCurves,EllipticCurvePointFormats)
 *
 * JA4 (FoxIO, 2023):
 *   t{TLSVersion}_{SNI}_{CipherCount}{ExtCount}_{TruncatedSHA256(sortedCiphers_sortedExtensions)}
 *
 * Thread safety: all methods are stateless or use ConcurrentHashMap.
 * Safe to call from VPN packet loop hot path.
 */
@Singleton
class TlsFingerprinter @Inject constructor() {

    companion object {
        private const val TAG = "TlsFingerprinter"

        // TLS record types
        private const val TLS_HANDSHAKE: Byte = 0x16
        private const val HANDSHAKE_CLIENT_HELLO: Byte = 0x01

        // Extension types we care about
        private const val EXT_SNI = 0x0000
        private const val EXT_SUPPORTED_GROUPS = 0x000A       // elliptic_curves
        private const val EXT_EC_POINT_FORMATS = 0x000B
        private const val EXT_SUPPORTED_VERSIONS = 0x002B

        // GREASE values (RFC 8701) — must be excluded from fingerprints
        private val GREASE_VALUES = setOf(
            0x0a0a, 0x1a1a, 0x2a2a, 0x3a3a, 0x4a4a, 0x5a5a,
            0x6a6a, 0x7a7a, 0x8a8a, 0x9a9a, 0xaaaa, 0xbaba,
            0xcaca, 0xdada, 0xeaea, 0xfafa
        )

        // Known JA3 hashes for common apps/libraries
        private val KNOWN_JA3 = mapOf(
            "e7d705a3286e19ea42f587b344ee6865" to "Chrome/Chromium",
            "773906b0efdefa24a7f2b8eb6985bf37" to "Firefox",
            "b32309a26951912be7dba376398abc3b" to "Safari/WebKit",
            "a0e9f5d64349fb13191bc781f81f42e1" to "Python/requests",
            "6734f37431670b3ab4292b8f60f29984" to "curl",
            "d0ec4b50a944b182fc10ff51f883ccf7" to "OkHttp (Android)",
            "e35b4e97b36e73c3e71a7eaee2605809" to "Java/HttpURLConnection"
        )
    }

    /**
     * Parsed TLS ClientHello fields needed for fingerprinting.
     */
    data class ClientHello(
        val tlsVersion: Int,                  // Record-layer version (0x0301, 0x0303, etc.)
        val handshakeVersion: Int,            // ClientHello version field
        val cipherSuites: List<Int>,          // Non-GREASE cipher suites
        val extensions: List<Int>,            // Non-GREASE extension types
        val supportedGroups: List<Int>,       // Elliptic curves (non-GREASE)
        val ecPointFormats: List<Int>,        // EC point format list
        val sni: String?,                     // Server Name Indication
        val supportedVersions: List<Int>      // from supported_versions extension
    )

    /**
     * Complete fingerprint result.
     */
    data class TlsFingerprint(
        val ja3: String,         // MD5 hash
        val ja3Raw: String,      // Raw JA3 string (for debugging)
        val ja4: String,         // JA4 fingerprint string
        val sni: String?,        // Extracted SNI hostname
        val knownIdentity: String?  // Matched known library/app, or null
    )

    // Cache: JA3 hash -> known identity (avoids repeated map lookups)
    private val identityCache = ConcurrentHashMap<String, String?>()

    // ── Fingerprint History (v6.2) ──────────────────────────────

    /**
     * A captured TLS fingerprint with app attribution and timestamp.
     */
    data class CapturedFingerprint(
        val timestamp: Long,
        val packageName: String,
        val appLabel: String,
        val fingerprint: TlsFingerprint,
    )

    /** Ring buffer of recently captured fingerprints. */
    private val history = java.util.concurrent.ConcurrentLinkedDeque<CapturedFingerprint>()
    private val maxHistory = 500

    /**
     * Record a fingerprint observation with app attribution.
     * Called from DnsVpnService after successful fingerprinting.
     */
    fun record(packageName: String, appLabel: String, fp: TlsFingerprint) {
        val entry = CapturedFingerprint(
            timestamp = System.currentTimeMillis(),
            packageName = packageName,
            appLabel = appLabel,
            fingerprint = fp,
        )
        history.addFirst(entry)
        while (history.size > maxHistory) history.removeLast()
    }

    /** Get all captured fingerprints (newest first). */
    fun getHistory(): List<CapturedFingerprint> = history.toList()

    /** Get unique fingerprints grouped by app. */
    fun getByApp(): Map<String, List<CapturedFingerprint>> =
        history.groupBy { it.packageName }

    /** Clear all stored fingerprints. */
    fun clearHistory() = history.clear()

    // ── Public API ───────────────────────────────────────────────

    /**
     * Extract TLS fingerprint from a raw packet payload.
     * The [data] should be the TCP payload (after IP + TCP headers).
     * Returns null if the payload is not a TLS ClientHello.
     */
    fun fingerprint(data: ByteArray, offset: Int = 0, length: Int = data.size - offset): TlsFingerprint? {
        val hello = parseClientHello(data, offset, length) ?: return null
        return computeFingerprint(hello)
    }

    /**
     * Quick check: is this byte array the start of a TLS ClientHello?
     * Useful as a fast pre-filter before the full parse.
     */
    fun isClientHello(data: ByteArray, offset: Int = 0, length: Int = data.size - offset): Boolean {
        if (length < 6) return false
        return data[offset] == TLS_HANDSHAKE &&
               data[offset + 1] == 0x03.toByte() &&  // TLS major version
               data[offset + 5] == HANDSHAKE_CLIENT_HELLO
    }

    /**
     * Look up a known identity for a JA3 hash.
     */
    fun identifyJa3(ja3Hash: String): String? =
        identityCache.getOrPut(ja3Hash) { KNOWN_JA3[ja3Hash] }

    // ── Parsing ──────────────────────────────────────────────────

    /**
     * Parse a TLS ClientHello message from raw bytes.
     */
    fun parseClientHello(data: ByteArray, offset: Int, length: Int): ClientHello? {
        try {
            var pos = offset
            val end = offset + length

            // TLS record header (5 bytes)
            if (pos + 5 > end) return null
            if (data[pos] != TLS_HANDSHAKE) return null
            val tlsVersion = readUint16(data, pos + 1)
            // val recordLength = readUint16(data, pos + 3)  // not used directly
            pos += 5

            // Handshake header (4 bytes)
            if (pos + 4 > end) return null
            if (data[pos] != HANDSHAKE_CLIENT_HELLO) return null
            // val hsLength = readUint24(data, pos + 1)  // not used directly
            pos += 4

            // ClientHello body
            if (pos + 2 > end) return null
            val handshakeVersion = readUint16(data, pos)
            pos += 2

            // Random (32 bytes)
            pos += 32
            if (pos > end) return null

            // Session ID (variable)
            if (pos + 1 > end) return null
            val sessionIdLen = data[pos].toInt() and 0xFF
            pos += 1 + sessionIdLen
            if (pos > end) return null

            // Cipher suites
            if (pos + 2 > end) return null
            val cipherLen = readUint16(data, pos)
            pos += 2
            if (pos + cipherLen > end) return null
            val cipherSuites = mutableListOf<Int>()
            var csPos = pos
            while (csPos + 1 < pos + cipherLen) {
                val cs = readUint16(data, csPos)
                if (cs !in GREASE_VALUES) cipherSuites.add(cs)
                csPos += 2
            }
            pos += cipherLen

            // Compression methods
            if (pos + 1 > end) return null
            val compLen = data[pos].toInt() and 0xFF
            pos += 1 + compLen
            if (pos > end) return null

            // Extensions
            val extensions = mutableListOf<Int>()
            val supportedGroups = mutableListOf<Int>()
            val ecPointFormats = mutableListOf<Int>()
            val supportedVersions = mutableListOf<Int>()
            var sni: String? = null

            if (pos + 2 <= end) {
                val extTotalLen = readUint16(data, pos)
                pos += 2
                val extEnd = minOf(pos + extTotalLen, end)

                while (pos + 4 <= extEnd) {
                    val extType = readUint16(data, pos)
                    val extLen = readUint16(data, pos + 2)
                    pos += 4
                    if (pos + extLen > extEnd) break

                    if (extType !in GREASE_VALUES) {
                        extensions.add(extType)
                    }

                    when (extType) {
                        EXT_SNI -> sni = parseSni(data, pos, extLen)
                        EXT_SUPPORTED_GROUPS -> {
                            if (extLen >= 2) {
                                val listLen = readUint16(data, pos)
                                var gp = pos + 2
                                val gpEnd = minOf(pos + 2 + listLen, pos + extLen)
                                while (gp + 1 < gpEnd) {
                                    val group = readUint16(data, gp)
                                    if (group !in GREASE_VALUES) supportedGroups.add(group)
                                    gp += 2
                                }
                            }
                        }
                        EXT_EC_POINT_FORMATS -> {
                            if (extLen >= 1) {
                                val fmtLen = data[pos].toInt() and 0xFF
                                for (i in 0 until minOf(fmtLen, extLen - 1)) {
                                    ecPointFormats.add(data[pos + 1 + i].toInt() and 0xFF)
                                }
                            }
                        }
                        EXT_SUPPORTED_VERSIONS -> {
                            if (extLen >= 1) {
                                val svLen = data[pos].toInt() and 0xFF
                                var sv = pos + 1
                                val svEnd = minOf(pos + 1 + svLen, pos + extLen)
                                while (sv + 1 < svEnd) {
                                    val ver = readUint16(data, sv)
                                    if (ver !in GREASE_VALUES) supportedVersions.add(ver)
                                    sv += 2
                                }
                            }
                        }
                    }
                    pos += extLen
                }
            }

            return ClientHello(
                tlsVersion = tlsVersion,
                handshakeVersion = handshakeVersion,
                cipherSuites = cipherSuites,
                extensions = extensions,
                supportedGroups = supportedGroups,
                ecPointFormats = ecPointFormats,
                sni = sni,
                supportedVersions = supportedVersions
            )
        } catch (e: Exception) {
            Log.d(TAG, "ClientHello parse error: ${e.message}")
            return null
        }
    }

    // ── Fingerprint Computation ──────────────────────────────────

    private fun computeFingerprint(hello: ClientHello): TlsFingerprint {
        val ja3Raw = buildJa3Raw(hello)
        val ja3Hash = md5Hex(ja3Raw)
        val ja4 = buildJa4(hello)
        val identity = identifyJa3(ja3Hash)

        return TlsFingerprint(
            ja3 = ja3Hash,
            ja3Raw = ja3Raw,
            ja4 = ja4,
            sni = hello.sni,
            knownIdentity = identity
        )
    }

    /**
     * JA3 raw string: TLSVersion,Ciphers,Extensions,EllipticCurves,ECPointFormats
     * Each list is dash-separated.
     */
    private fun buildJa3Raw(hello: ClientHello): String {
        // Use the highest version from supported_versions if available,
        // otherwise fall back to handshake version
        val version = if (hello.supportedVersions.isNotEmpty()) {
            hello.supportedVersions.max()
        } else {
            hello.handshakeVersion
        }

        val ciphers = hello.cipherSuites.joinToString("-")
        val extensions = hello.extensions.joinToString("-")
        val curves = hello.supportedGroups.joinToString("-")
        val pointFormats = hello.ecPointFormats.joinToString("-")

        return "$version,$ciphers,$extensions,$curves,$pointFormats"
    }

    /**
     * JA4 fingerprint: t{version}_{sni}_{cipherCount}{extCount}_{hash}
     *
     * Simplified JA4 implementation:
     * - Protocol: 't' for TCP TLS
     * - Version: TLS version mapped to 10/11/12/13
     * - SNI: 'd' if present, 'i' if IP/absent
     * - Cipher/ext counts: 2-digit zero-padded
     * - Hash: first 12 chars of SHA-256 of sorted ciphers + sorted extensions
     */
    private fun buildJa4(hello: ClientHello): String {
        val version = if (hello.supportedVersions.isNotEmpty()) {
            hello.supportedVersions.max()
        } else {
            hello.handshakeVersion
        }

        val versionStr = when (version) {
            0x0304 -> "13"
            0x0303 -> "12"
            0x0302 -> "11"
            0x0301 -> "10"
            else -> "00"
        }

        val sniChar = if (hello.sni != null && !hello.sni.matches(Regex("^\\d+\\.\\d+\\.\\d+\\.\\d+$"))) 'd' else 'i'
        val cipherCount = "%02d".format(minOf(hello.cipherSuites.size, 99))
        val extCount = "%02d".format(minOf(hello.extensions.size, 99))

        // Hash: sorted ciphers, then sorted extensions
        val sortedCiphers = hello.cipherSuites.sorted().joinToString(",")
        val sortedExts = hello.extensions.sorted().joinToString(",")
        val hashInput = "${sortedCiphers}_${sortedExts}"
        val fullHash = sha256Hex(hashInput)
        val truncHash = fullHash.take(12)

        return "t${versionStr}${sniChar}_${cipherCount}${extCount}_$truncHash"
    }

    // ── Helpers ──────────────────────────────────────────────────

    private fun parseSni(data: ByteArray, offset: Int, length: Int): String? {
        if (length < 5) return null
        // SNI list length (2 bytes)
        var pos = offset + 2
        // SNI type (1 byte, 0 = hostname)
        if (data[pos].toInt() != 0) return null
        pos++
        // Hostname length (2 bytes)
        val nameLen = readUint16(data, pos)
        pos += 2
        if (pos + nameLen > offset + length) return null
        return String(data, pos, nameLen, Charsets.US_ASCII)
    }

    private fun readUint16(data: ByteArray, offset: Int): Int =
        ((data[offset].toInt() and 0xFF) shl 8) or (data[offset + 1].toInt() and 0xFF)

    private fun md5Hex(input: String): String {
        val digest = MessageDigest.getInstance("MD5")
        return digest.digest(input.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
    }

    private fun sha256Hex(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        return digest.digest(input.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
    }
}
