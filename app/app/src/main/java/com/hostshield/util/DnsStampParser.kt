package com.hostshield.util

import android.util.Base64
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Parses and encodes DNS stamps (sdns://) as defined by the DNSCrypt specification.
 * See: https://dnscrypt.info/stamps-specifications/
 */
@Singleton
class DnsStampParser @Inject constructor() {

    data class DnsStamp(
        val protocol: Protocol,
        val address: String,
        val hostname: String,
        val path: String,
        val dnssec: Boolean,
        val noLog: Boolean,
        val noFilter: Boolean,
        val providerName: String = ""
    ) {
        enum class Protocol(val value: Int) {
            PLAIN_DNS(0x00),
            DNSCRYPT(0x01),
            DOH(0x02),
            DOT(0x03),
            UNKNOWN(-1);

            companion object {
                fun fromByte(b: Int): Protocol = entries.firstOrNull { it.value == b } ?: UNKNOWN
            }
        }

        /** Human-readable summary of this stamp. */
        fun toDisplayString(): String = buildString {
            append(protocol.name)
            if (hostname.isNotEmpty()) append(" | $hostname")
            if (address.isNotEmpty()) append(" ($address)")
            if (path.isNotEmpty()) append(" path=$path")
            if (providerName.isNotEmpty()) append(" provider=$providerName")
            val flags = mutableListOf<String>()
            if (dnssec) flags += "DNSSEC"
            if (noLog) flags += "No-Log"
            if (noFilter) flags += "No-Filter"
            if (flags.isNotEmpty()) append(" [${flags.joinToString(", ")}]")
        }
    }

    companion object {
        private const val SDNS_PREFIX = "sdns://"
        private const val BASE64_FLAGS = Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP
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

    /**
     * Encode a [DnsStamp] back to an `sdns://` string suitable for sharing.
     */
    fun encode(stamp: DnsStamp): String {
        val bytes = encodeToBytes(stamp)
        val b64 = Base64.encodeToString(bytes, BASE64_FLAGS)
        return "$SDNS_PREFIX$b64"
    }

    // ── Parsing internals ────────────────────────────────────────

    private fun parseSafe(stamp: String): DnsStamp? {
        if (!stamp.startsWith(SDNS_PREFIX)) return null
        val encoded = stamp.removePrefix(SDNS_PREFIX)
        if (encoded.isEmpty()) return null

        val data = Base64.decode(encoded, BASE64_FLAGS)
        if (data.size < 2) return null

        val buf = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)
        val typeByte = buf.get().toInt() and 0xFF
        val protocol = DnsStamp.Protocol.fromByte(typeByte)
        if (protocol == DnsStamp.Protocol.UNKNOWN) return null

        val props = buf.get().toInt() and 0xFF
        val dnssec = (props and 0x01) != 0
        val noLog = (props and 0x02) != 0
        val noFilter = (props and 0x04) != 0

        return when (protocol) {
            DnsStamp.Protocol.PLAIN_DNS -> parsePlainDns(buf, dnssec, noLog, noFilter)
            DnsStamp.Protocol.DNSCRYPT -> parseDnsCrypt(buf, dnssec, noLog, noFilter)
            DnsStamp.Protocol.DOH -> parseDoH(buf, dnssec, noLog, noFilter)
            DnsStamp.Protocol.DOT -> parseDoT(buf, dnssec, noLog, noFilter)
            else -> null
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
        // Provider public key: 32 bytes, length-prefixed
        skipLpBytes(buf) ?: return null
        val providerName = readLpString(buf) ?: return null
        return DnsStamp(
            protocol = DnsStamp.Protocol.DNSCRYPT,
            address = address,
            hostname = "",
            path = "",
            dnssec = dnssec,
            noLog = noLog,
            noFilter = noFilter,
            providerName = providerName
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

    // ── Encoding internals ───────────────────────────────────────

    private fun encodeToBytes(stamp: DnsStamp): ByteArray {
        val out = ByteArrayOutputStream()
        out.write(stamp.protocol.value)

        var props = 0
        if (stamp.dnssec) props = props or 0x01
        if (stamp.noLog) props = props or 0x02
        if (stamp.noFilter) props = props or 0x04
        out.write(props)

        when (stamp.protocol) {
            DnsStamp.Protocol.PLAIN_DNS -> {
                writeLpString(out, stamp.address)
            }
            DnsStamp.Protocol.DNSCRYPT -> {
                writeLpString(out, stamp.address)
                // Empty provider public key placeholder (length 0)
                writeLpBytes(out, ByteArray(0))
                writeLpString(out, stamp.providerName)
            }
            DnsStamp.Protocol.DOH -> {
                writeLpString(out, stamp.address)
                // Empty hash chain
                writeLpBytes(out, ByteArray(0))
                writeLpString(out, stamp.hostname)
                writeLpString(out, stamp.path)
            }
            DnsStamp.Protocol.DOT -> {
                writeLpString(out, stamp.address)
                // Empty hash chain
                writeLpBytes(out, ByteArray(0))
                writeLpString(out, stamp.hostname)
            }
            else -> { /* UNKNOWN — encode nothing beyond header */ }
        }

        return out.toByteArray()
    }

    // ── LP (length-prefixed) helpers ─────────────────────────────

    /** Read a length-prefixed UTF-8 string from the buffer. */
    private fun readLpString(buf: ByteBuffer): String? {
        if (buf.remaining() < 1) return null
        val len = buf.get().toInt() and 0xFF
        if (buf.remaining() < len) return null
        val bytes = ByteArray(len)
        buf.get(bytes)
        return String(bytes, Charsets.UTF_8)
    }

    /** Skip a length-prefixed byte block. */
    private fun skipLpBytes(buf: ByteBuffer): Unit? {
        if (buf.remaining() < 1) return null
        val len = buf.get().toInt() and 0xFF
        if (buf.remaining() < len) return null
        buf.position(buf.position() + len)
        return Unit
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
        out.write(bytes.size and 0xFF)
        out.write(bytes)
    }

    /** Write a length-prefixed byte block. */
    private fun writeLpBytes(out: ByteArrayOutputStream, bytes: ByteArray) {
        out.write(bytes.size and 0xFF)
        out.write(bytes)
    }
}
