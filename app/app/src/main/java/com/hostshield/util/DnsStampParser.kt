package com.hostshield.util

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Base64
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Parses and encodes DNS stamps (sdns://) as defined by the DNSCrypt specification.
 * See: https://dnscrypt.info/stamps-specifications/
 */
@Singleton
class DnsStampParser @Inject constructor() {

    enum class CapabilityStatus {
        SUPPORTED,
        PARSED_BUT_DISABLED,
        UNSUPPORTED
    }

    data class DnsStampCapability(
        val status: CapabilityStatus,
        val canActivateAsResolver: Boolean,
        val productionTransport: String,
        val diagnostic: String
    )

    data class DnsStampImportDiagnostic(
        val stamp: DnsStamp?,
        val status: CapabilityStatus,
        val protocol: DnsStamp.Protocol?,
        val canActivateAsResolver: Boolean,
        val productionTransport: String,
        val diagnostic: String
    )

    data class DnsStamp(
        val protocol: Protocol,
        val address: String,
        val hostname: String,
        val path: String,
        val dnssec: Boolean,
        val noLog: Boolean,
        val noFilter: Boolean,
        val providerName: String = "",
        val providerPublicKey: ByteArray = ByteArray(0)
    ) {
        enum class Protocol(val value: Int) {
            PLAIN_DNS(0x00),
            DNSCRYPT(0x01),
            DOH(0x02),
            DOT(0x03),
            DOQ(0x04),
            ODOH_TARGET(0x05),
            DNSCRYPT_RELAY(0x81),
            ODOH_RELAY(0x85),
            UNKNOWN(-1);

            companion object {
                fun fromByte(b: Int): Protocol = entries.firstOrNull { it.value == b } ?: UNKNOWN
            }

            val hasProperties: Boolean
                get() = this != DNSCRYPT_RELAY && this != UNKNOWN

            val isRelay: Boolean
                get() = this == DNSCRYPT_RELAY || this == ODOH_RELAY
        }

        /** Human-readable summary of this stamp. */
        fun toDisplayString(): String = buildString {
            append(protocol.name.lowercase(Locale.US).replace('_', ' '))
            if (hostname.isNotEmpty()) append(" | $hostname")
            if (address.isNotEmpty()) append(" ($address)")
            if (path.isNotEmpty()) append(" path=$path")
            if (providerName.isNotEmpty()) append(" provider=$providerName")
            if (providerPublicKey.isNotEmpty()) append(" key=${providerPublicKey.size}B")
            val flags = mutableListOf<String>()
            if (dnssec) flags += "DNSSEC"
            if (noLog) flags += "No-Log"
            if (noFilter) flags += "No-Filter"
            if (flags.isNotEmpty()) append(" [${flags.joinToString(", ")}]")
        }
    }

    companion object {
        private const val SDNS_PREFIX = "sdns://"
        private const val DNSCRYPT_PROVIDER_PUBLIC_KEY_BYTES = 32
        private val BASE64_ENCODER = Base64.getUrlEncoder().withoutPadding()
        private val BASE64_DECODER = Base64.getUrlDecoder()

        private val CAPABILITY_BY_PROTOCOL = mapOf(
            DnsStamp.Protocol.PLAIN_DNS to DnsStampCapability(
                status = CapabilityStatus.SUPPORTED,
                canActivateAsResolver = true,
                productionTransport = "Plain DNS custom upstream",
                diagnostic = "Plain DNS stamps can be imported as custom upstream resolvers."
            ),
            DnsStamp.Protocol.DOH to DnsStampCapability(
                status = CapabilityStatus.SUPPORTED,
                canActivateAsResolver = true,
                productionTransport = "Pinned DoH provider",
                diagnostic = "DoH is production-supported when the stamp maps to a built-in pinned provider."
            ),
            DnsStamp.Protocol.DOT to DnsStampCapability(
                status = CapabilityStatus.SUPPORTED,
                canActivateAsResolver = true,
                productionTransport = "Pinned DoT provider",
                diagnostic = "DoT is production-supported when the stamp maps to a built-in pinned provider."
            ),
            DnsStamp.Protocol.DOQ to DnsStampCapability(
                status = CapabilityStatus.PARSED_BUT_DISABLED,
                canActivateAsResolver = false,
                productionTransport = "Debug-only experimental DoQ",
                diagnostic = "DoQ stamps parse, but release builds keep DoQ disabled and never activate them as resolvers."
            ),
            DnsStamp.Protocol.DNSCRYPT to DnsStampCapability(
                status = CapabilityStatus.PARSED_BUT_DISABLED,
                canActivateAsResolver = false,
                productionTransport = "No production DNSCrypt transport",
                diagnostic = "DNSCrypt stamps parse for validation and relay planning, but no production DNSCrypt resolver is wired."
            ),
            DnsStamp.Protocol.ODOH_TARGET to DnsStampCapability(
                status = CapabilityStatus.PARSED_BUT_DISABLED,
                canActivateAsResolver = false,
                productionTransport = "No production ODoH transport",
                diagnostic = "ODoH target stamps parse, but no production ODoH resolver is wired."
            ),
            DnsStamp.Protocol.DNSCRYPT_RELAY to DnsStampCapability(
                status = CapabilityStatus.UNSUPPORTED,
                canActivateAsResolver = false,
                productionTransport = "Relay metadata only",
                diagnostic = "Anonymized DNSCrypt relay stamps are relay metadata and cannot be selected as resolvers."
            ),
            DnsStamp.Protocol.ODOH_RELAY to DnsStampCapability(
                status = CapabilityStatus.UNSUPPORTED,
                canActivateAsResolver = false,
                productionTransport = "Relay metadata only",
                diagnostic = "ODoH relay stamps are relay metadata, and no production ODoH resolver is wired."
            ),
            DnsStamp.Protocol.UNKNOWN to DnsStampCapability(
                status = CapabilityStatus.UNSUPPORTED,
                canActivateAsResolver = false,
                productionTransport = "Unsupported",
                diagnostic = "Unknown DNS stamp protocols are ignored and cannot change active resolver settings."
            )
        )

        fun diagnosticSummaryLines(): List<String> =
            DnsStamp.Protocol.entries
                .filter { it != DnsStamp.Protocol.UNKNOWN }
                .map { protocol ->
                    val capability = capabilityFor(protocol)
                    "DNS stamp ${protocol.name}: ${capability.status.name.lowercase(Locale.US).replace('_', '-')} - ${capability.diagnostic}"
                }

        private fun capabilityFor(protocol: DnsStamp.Protocol): DnsStampCapability =
            CAPABILITY_BY_PROTOCOL[protocol] ?: CAPABILITY_BY_PROTOCOL.getValue(DnsStamp.Protocol.UNKNOWN)
    }

    // ── Public API ───────────────────────────────────────────────

    /**
     * Parse an `sdns://` stamp string into a [DnsStamp].
     * Returns `null` on any malformed or unsupported input.
     */
    fun parse(stamp: String): DnsStamp? = try {
        parseSafe(stamp)
    } catch (_: Exception) {
        null
    }

    fun classify(stamp: DnsStamp): DnsStampCapability = capabilityFor(stamp.protocol)

    fun diagnose(stamp: String): DnsStampImportDiagnostic {
        val parsed = parse(stamp)
        if (parsed == null) {
            val unsupported = capabilityFor(DnsStamp.Protocol.UNKNOWN)
            return DnsStampImportDiagnostic(
                stamp = null,
                status = unsupported.status,
                protocol = null,
                canActivateAsResolver = false,
                productionTransport = unsupported.productionTransport,
                diagnostic = "Malformed or unsupported DNS stamp; resolver settings were not changed."
            )
        }

        val capability = classify(parsed)
        return DnsStampImportDiagnostic(
            stamp = parsed,
            status = capability.status,
            protocol = parsed.protocol,
            canActivateAsResolver = capability.canActivateAsResolver,
            productionTransport = capability.productionTransport,
            diagnostic = capability.diagnostic
        )
    }

    /**
     * Encode a [DnsStamp] back to an `sdns://` string suitable for sharing.
     */
    fun encode(stamp: DnsStamp): String {
        val bytes = encodeToBytes(stamp)
        val b64 = BASE64_ENCODER.encodeToString(bytes)
        return "$SDNS_PREFIX$b64"
    }

    // ── Parsing internals ────────────────────────────────────────

    private fun parseSafe(stamp: String): DnsStamp? {
        if (!stamp.startsWith(SDNS_PREFIX)) return null
        val encoded = stamp.removePrefix(SDNS_PREFIX)
        if (encoded.isEmpty()) return null

        val data = BASE64_DECODER.decode(encoded)
        if (data.isEmpty()) return null

        return parseDecoded(data, propsBytes = 8) ?: parseDecoded(data, propsBytes = 1)
    }

    private fun parseDecoded(data: ByteArray, propsBytes: Int): DnsStamp? {
        val buf = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)
        val typeByte = buf.get().toInt() and 0xFF
        val protocol = DnsStamp.Protocol.fromByte(typeByte)
        if (protocol == DnsStamp.Protocol.UNKNOWN) return null

        val props = if (protocol.hasProperties) readProperties(buf, propsBytes) ?: return null else 0L
        val dnssec = (props and 0x01L) != 0L
        val noLog = (props and 0x02L) != 0L
        val noFilter = (props and 0x04L) != 0L

        return when (protocol) {
            DnsStamp.Protocol.PLAIN_DNS -> parsePlainDns(buf, dnssec, noLog, noFilter)
            DnsStamp.Protocol.DNSCRYPT -> parseDnsCrypt(buf, dnssec, noLog, noFilter)
            DnsStamp.Protocol.DOH -> parseDoH(buf, dnssec, noLog, noFilter)
            DnsStamp.Protocol.DOT -> parseDoT(buf, dnssec, noLog, noFilter)
            DnsStamp.Protocol.DOQ -> parseDoQ(buf, dnssec, noLog, noFilter)
            DnsStamp.Protocol.ODOH_TARGET -> parseOdoHTarget(buf, dnssec, noLog, noFilter)
            DnsStamp.Protocol.DNSCRYPT_RELAY -> parseDnsCryptRelay(buf)
            DnsStamp.Protocol.ODOH_RELAY -> parseOdoHRelay(buf, dnssec, noLog, noFilter)
        }
    }

    private fun parsePlainDns(
        buf: ByteBuffer, dnssec: Boolean, noLog: Boolean, noFilter: Boolean
    ): DnsStamp? {
        val address = readLpString(buf) ?: return null
        return DnsStamp(
            protocol = DnsStamp.Protocol.PLAIN_DNS,
            address = address,
            hostname = "",
            path = "",
            dnssec = dnssec,
            noLog = noLog,
            noFilter = noFilter
        )
    }

    private fun parseDnsCrypt(
        buf: ByteBuffer, dnssec: Boolean, noLog: Boolean, noFilter: Boolean
    ): DnsStamp? {
        val address = readLpString(buf) ?: return null
        val providerPublicKey = readLpBytes(buf) ?: return null
        if (providerPublicKey.size != DNSCRYPT_PROVIDER_PUBLIC_KEY_BYTES) return null
        val providerName = readLpString(buf) ?: return null
        return DnsStamp(
            protocol = DnsStamp.Protocol.DNSCRYPT,
            address = address,
            hostname = "",
            path = "",
            dnssec = dnssec,
            noLog = noLog,
            noFilter = noFilter,
            providerName = providerName,
            providerPublicKey = providerPublicKey
        )
    }

    private fun parseDoH(
        buf: ByteBuffer, dnssec: Boolean, noLog: Boolean, noFilter: Boolean
    ): DnsStamp? {
        val address = readLpString(buf) ?: return null
        // Skip hashes (variable-length LP blocks)
        skipHashChain(buf) ?: return null
        val hostname = readLpString(buf) ?: return null
        val path = readLpString(buf) ?: return null
        return DnsStamp(
            protocol = DnsStamp.Protocol.DOH,
            address = address,
            hostname = hostname,
            path = path,
            dnssec = dnssec,
            noLog = noLog,
            noFilter = noFilter
        )
    }

    private fun parseDoT(
        buf: ByteBuffer, dnssec: Boolean, noLog: Boolean, noFilter: Boolean
    ): DnsStamp? {
        val address = readLpString(buf) ?: return null
        skipHashChain(buf) ?: return null
        val hostname = readLpString(buf) ?: return null
        return DnsStamp(
            protocol = DnsStamp.Protocol.DOT,
            address = address,
            hostname = hostname,
            path = "",
            dnssec = dnssec,
            noLog = noLog,
            noFilter = noFilter
        )
    }

    private fun parseDoQ(
        buf: ByteBuffer, dnssec: Boolean, noLog: Boolean, noFilter: Boolean
    ): DnsStamp? {
        val address = readLpString(buf) ?: return null
        skipHashChain(buf) ?: return null
        val hostname = readLpString(buf) ?: return null
        return DnsStamp(
            protocol = DnsStamp.Protocol.DOQ,
            address = address,
            hostname = hostname,
            path = "",
            dnssec = dnssec,
            noLog = noLog,
            noFilter = noFilter
        )
    }

    private fun parseOdoHTarget(
        buf: ByteBuffer, dnssec: Boolean, noLog: Boolean, noFilter: Boolean
    ): DnsStamp? {
        val hostname = readLpString(buf) ?: return null
        val path = readLpString(buf) ?: return null
        return DnsStamp(
            protocol = DnsStamp.Protocol.ODOH_TARGET,
            address = "",
            hostname = hostname,
            path = path,
            dnssec = dnssec,
            noLog = noLog,
            noFilter = noFilter
        )
    }

    private fun parseDnsCryptRelay(buf: ByteBuffer): DnsStamp? {
        val address = readLpString(buf) ?: return null
        return DnsStamp(
            protocol = DnsStamp.Protocol.DNSCRYPT_RELAY,
            address = address,
            hostname = "",
            path = "",
            dnssec = false,
            noLog = false,
            noFilter = false
        )
    }

    private fun parseOdoHRelay(
        buf: ByteBuffer, dnssec: Boolean, noLog: Boolean, noFilter: Boolean
    ): DnsStamp? {
        val address = readLpString(buf) ?: return null
        skipHashChain(buf) ?: return null
        val hostname = readLpString(buf) ?: return null
        val path = readLpString(buf) ?: return null
        return DnsStamp(
            protocol = DnsStamp.Protocol.ODOH_RELAY,
            address = address,
            hostname = hostname,
            path = path,
            dnssec = dnssec,
            noLog = noLog,
            noFilter = noFilter
        )
    }

    // ── Encoding internals ───────────────────────────────────────

    private fun encodeToBytes(stamp: DnsStamp): ByteArray {
        val out = ByteArrayOutputStream()
        out.write(stamp.protocol.value)

        if (stamp.protocol.hasProperties) {
            writeProperties(out, stamp)
        }

        when (stamp.protocol) {
            DnsStamp.Protocol.PLAIN_DNS -> {
                writeLpString(out, stamp.address)
            }
            DnsStamp.Protocol.DNSCRYPT -> {
                writeLpString(out, stamp.address)
                writeLpBytes(out, stamp.providerPublicKey)
                writeLpString(out, stamp.providerName)
            }
            DnsStamp.Protocol.DOH -> {
                writeLpString(out, stamp.address)
                // Empty hash chain
                writeLpBytes(out, ByteArray(0))
                writeLpString(out, stamp.hostname)
                writeLpString(out, stamp.path)
            }
            DnsStamp.Protocol.DOT, DnsStamp.Protocol.DOQ -> {
                writeLpString(out, stamp.address)
                // Empty hash chain
                writeLpBytes(out, ByteArray(0))
                writeLpString(out, stamp.hostname)
            }
            DnsStamp.Protocol.ODOH_TARGET -> {
                writeLpString(out, stamp.hostname)
                writeLpString(out, stamp.path)
            }
            DnsStamp.Protocol.DNSCRYPT_RELAY -> {
                writeLpString(out, stamp.address)
            }
            DnsStamp.Protocol.ODOH_RELAY -> {
                writeLpString(out, stamp.address)
                // Empty hash chain
                writeLpBytes(out, ByteArray(0))
                writeLpString(out, stamp.hostname)
                writeLpString(out, stamp.path)
            }
            else -> { /* UNKNOWN — encode nothing beyond header */ }
        }

        return out.toByteArray()
    }

    // ── LP (length-prefixed) helpers ─────────────────────────────

    /** Read a length-prefixed UTF-8 string from the buffer. */
    private fun readLpString(buf: ByteBuffer): String? {
        val bytes = readLpBytes(buf) ?: return null
        return String(bytes, Charsets.UTF_8)
    }

    /** Read a length-prefixed byte block. */
    private fun readLpBytes(buf: ByteBuffer): ByteArray? {
        if (buf.remaining() < 1) return null
        val len = buf.get().toInt() and 0xFF
        if (buf.remaining() < len) return null
        val bytes = ByteArray(len)
        buf.get(bytes)
        return bytes
    }

    /**
     * Skip a chain of TLS pin hashes.
     * The spec encodes hashes as consecutive LP blocks where the last one
     * has a length that does NOT set the high bit. Each intermediate block
     * has 0x80 OR'd into its length byte to signal "more hashes follow".
     */
    private fun skipHashChain(buf: ByteBuffer): Unit? {
        while (true) {
            if (buf.remaining() < 1) return null
            val lenByte = buf.get().toInt() and 0xFF
            val more = (lenByte and 0x80) != 0
            val len = lenByte and 0x7F
            if (len > 0 && buf.remaining() < len) return null
            if (len > 0) buf.position(buf.position() + len)
            if (!more) return Unit
        }
    }

    /** Write a length-prefixed UTF-8 string. */
    private fun writeLpString(out: ByteArrayOutputStream, s: String) {
        val bytes = s.toByteArray(Charsets.UTF_8)
        require(bytes.size <= 255) { "DNS stamp field exceeds 255 bytes" }
        out.write(bytes.size and 0xFF)
        out.write(bytes)
    }

    /** Write a length-prefixed byte block. */
    private fun writeLpBytes(out: ByteArrayOutputStream, bytes: ByteArray) {
        require(bytes.size <= 255) { "DNS stamp field exceeds 255 bytes" }
        out.write(bytes.size and 0xFF)
        out.write(bytes)
    }

    private fun readProperties(buf: ByteBuffer, propsBytes: Int): Long? {
        if (propsBytes == 8) {
            if (buf.remaining() < 8) return null
            return buf.long
        }
        if (propsBytes == 1) {
            if (buf.remaining() < 1) return null
            return (buf.get().toInt() and 0xFF).toLong()
        }
        return null
    }

    private fun writeProperties(out: ByteArrayOutputStream, stamp: DnsStamp) {
        var props = 0L
        if (stamp.dnssec) props = props or 0x01L
        if (stamp.noLog) props = props or 0x02L
        if (stamp.noFilter) props = props or 0x04L
        repeat(8) { shift ->
            out.write(((props ushr (shift * 8)) and 0xFF).toInt())
        }
    }

}
