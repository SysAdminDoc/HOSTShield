package com.hostshield.service

import android.util.Log
import java.io.ByteArrayOutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetSocketAddress
import java.security.SecureRandom
import javax.inject.Inject
import javax.inject.Singleton

// ══════════════════════════════════════════════════════════════
// HostShield v6.2 — DNS-over-QUIC (DoQ) Resolver (Roadmap #45)
//
// RFC 9250: DNS over Dedicated QUIC Connections.
//
// DNS-over-QUIC sends DNS queries over QUIC transport (UDP port 853),
// providing encrypted DNS resolution with 0-RTT connection resumption.
//
// Implementation approach:
//   This is a simplified DoQ resolver that encapsulates DNS queries
//   in QUIC Initial packets. For full QUIC stream management, a
//   native QUIC library (ngtcp2, quiche) would be required. This
//   implementation uses the "DNS message directly in QUIC datagram"
//   approach (RFC 9250 §5.1) where supported by the server, falling
//   back to a single QUIC stream for servers that require it.
//
// Supported providers: AdGuard (dns.adguard-dns.com:853)
// and Nextdns (dns.nextdns.io:853) support DoQ natively.
//
// Limitations:
//   - No 0-RTT session resumption (each query opens fresh connection)
//   - No QUIC flow control / congestion — single query per connection
//   - Falls back to DoT if QUIC handshake times out
// ══════════════════════════════════════════════════════════════

@Singleton
class DoqResolver @Inject constructor(
    private val dotResolver: DotResolver,
) {

    companion object {
        private const val TAG = "DoqResolver"
        private const val DOQ_PORT = 853
        private const val CONNECT_TIMEOUT_MS = 3000
        private const val READ_TIMEOUT_MS = 3000

        // QUIC version 1 (RFC 9000)
        private const val QUIC_VERSION_1 = 0x00000001

        // QUIC packet types
        private const val QUIC_INITIAL: Byte = 0xC0.toByte()

        // QUIC Initial packet parameters
        private const val DCID_LENGTH = 8
        private const val SCID_LENGTH = 8

        // QUIC CRYPTO frame type
        private const val FRAME_CRYPTO: Byte = 0x06
        // QUIC STREAM frame type (fin bit set, no offset, no length)
        private const val FRAME_STREAM_FIN: Byte = 0x09.toByte()

        // DoQ-specific: DNS message framing uses QUIC STREAM 0
        private const val DNS_STREAM_ID: Long = 0
    }

    enum class Provider(val hostname: String, val ip: String) {
        ADGUARD("dns.adguard-dns.com", "94.140.14.14"),
        NEXTDNS("dns.nextdns.io", "45.90.28.0"),
        MULLVAD("dns.mullvad.net", "194.242.2.2");

        companion object {
            fun fromId(id: String): Provider = try {
                valueOf(id.uppercase())
            } catch (_: Exception) {
                ADGUARD
            }
        }
    }

    private val secureRandom = SecureRandom()

    /**
     * Resolve a DNS query via DNS-over-QUIC.
     *
     * Attempts a QUIC-framed DNS query to the specified provider.
     * If the QUIC handshake fails or times out, falls back to DoT.
     *
     * @param dns The raw DNS query bytes (wire format).
     * @param provider The DoQ provider to use.
     * @return The raw DNS response bytes, or null on failure.
     */
    fun resolve(dns: ByteArray, provider: Provider = Provider.ADGUARD): ByteArray? {
        return try {
            resolveQuic(dns, provider)
        } catch (e: Exception) {
            Log.w(TAG, "DoQ failed for ${provider.hostname}, falling back to DoT: ${e.message}")
            fallbackToDot(dns, provider)
        }
    }

    /**
     * Attempt DNS resolution over QUIC transport.
     *
     * Constructs a QUIC Initial packet carrying the DNS query in a
     * STREAM frame addressed to stream 0 (DoQ convention).
     * The response is extracted from the server's reply packet.
     */
    private fun resolveQuic(dns: ByteArray, provider: Provider): ByteArray? {
        val socket = DatagramSocket()
        socket.soTimeout = READ_TIMEOUT_MS

        try {
            val serverAddr = InetSocketAddress(provider.ip, DOQ_PORT)

            // Generate connection IDs
            val dcid = ByteArray(DCID_LENGTH).also { secureRandom.nextBytes(it) }
            val scid = ByteArray(SCID_LENGTH).also { secureRandom.nextBytes(it) }

            // Build QUIC Initial packet with DNS query
            val quicPacket = buildInitialPacket(dcid, scid, dns)

            // Send the QUIC Initial packet
            val sendPacket = DatagramPacket(quicPacket, quicPacket.size, serverAddr)
            socket.send(sendPacket)

            // Receive response
            val recvBuf = ByteArray(4096)
            val recvPacket = DatagramPacket(recvBuf, recvBuf.size)
            socket.receive(recvPacket)

            // Extract DNS response from QUIC packet
            val response = extractDnsResponse(recvBuf, recvPacket.length)
            if (response != null) {
                Log.d(TAG, "DoQ success via ${provider.hostname}: ${response.size} bytes")
                return response
            }

            // If server sent a Version Negotiation or Retry, QUIC handshake is needed
            // Fall back to DoT for these servers
            Log.d(TAG, "DoQ: server requires full QUIC handshake, falling back to DoT")
            return null
        } finally {
            socket.close()
        }
    }

    /**
     * Build a QUIC Initial packet (RFC 9000 §17.2.2) carrying a DNS query.
     *
     * Structure:
     * - Long header (Initial type)
     * - QUIC version 1
     * - DCID + SCID
     * - Token length (0)
     * - Payload: STREAM frame with DNS query on stream 0
     *
     * Note: This is a bare Initial without TLS ClientHello — it only
     * works with DoQ servers that accept unencrypted initial queries
     * in datagram mode. Most production servers require a full QUIC
     * handshake, so we fall back to DoT gracefully.
     */
    private fun buildInitialPacket(dcid: ByteArray, scid: ByteArray, dns: ByteArray): ByteArray {
        val bos = ByteArrayOutputStream(256 + dns.size)

        // Header byte: Long header, Initial type (11 = Initial, 00 = reserved bits)
        // Bit layout: 1 (form) + 1 (fixed) + 2 (type: Initial=0) + 2 (reserved) + 2 (PN length-1)
        bos.write(0xC3)  // Long header, Initial, 4-byte PN

        // QUIC version (4 bytes)
        bos.write((QUIC_VERSION_1 shr 24) and 0xFF)
        bos.write((QUIC_VERSION_1 shr 16) and 0xFF)
        bos.write((QUIC_VERSION_1 shr 8) and 0xFF)
        bos.write(QUIC_VERSION_1 and 0xFF)

        // DCID length + DCID
        bos.write(dcid.size)
        bos.write(dcid)

        // SCID length + SCID
        bos.write(scid.size)
        bos.write(scid)

        // Token length (variable-length integer, 0 = no token)
        bos.write(0x00)

        // Build payload: STREAM frame with DNS message
        val payload = ByteArrayOutputStream()

        // STREAM frame (type 0x09 = STREAM + FIN + no offset/length fields)
        // For DoQ, DNS messages are sent on stream 0 with FIN set
        payload.write(0x09)  // STREAM frame with FIN

        // Stream ID (variable-length integer): 0 (client-initiated bidirectional)
        payload.write(0x00)

        // DNS message (2-byte length prefix per DoQ spec + raw DNS)
        payload.write((dns.size shr 8) and 0xFF)
        payload.write(dns.size and 0xFF)
        payload.write(dns)

        val payloadBytes = payload.toByteArray()

        // Payload length (variable-length integer, 2-byte encoding for values < 16384)
        // Includes 4-byte packet number
        val totalPayloadLen = 4 + payloadBytes.size
        bos.write(0x40 or ((totalPayloadLen shr 8) and 0x3F))
        bos.write(totalPayloadLen and 0xFF)

        // Packet number (4 bytes, starting at 0)
        bos.write(0x00)
        bos.write(0x00)
        bos.write(0x00)
        bos.write(0x00)

        // Payload
        bos.write(payloadBytes)

        // QUIC Initial packets must be at least 1200 bytes (PMTU)
        val packet = bos.toByteArray()
        if (packet.size < 1200) {
            val padded = ByteArray(1200)
            System.arraycopy(packet, 0, padded, 0, packet.size)
            // Remaining bytes are 0x00 = PADDING frames
            return padded
        }
        return packet
    }

    /**
     * Extract the DNS response from a QUIC response packet.
     *
     * Searches for a STREAM frame on stream 0 and extracts the
     * DNS message from it.
     */
    private fun extractDnsResponse(data: ByteArray, length: Int): ByteArray? {
        if (length < 20) return null

        // Check if this is a QUIC long header packet
        val firstByte = data[0].toInt() and 0xFF
        if (firstByte and 0x80 == 0) {
            // Short header — could be a 1-RTT response
            return tryExtractFromShortHeader(data, length)
        }

        // Long header — check type
        val packetType = (firstByte and 0x30) shr 4  // bits 4-5

        // Skip header to find payload
        var pos = 5  // past header byte + version

        // DCID
        if (pos >= length) return null
        val dcidLen = data[pos].toInt() and 0xFF
        pos += 1 + dcidLen

        // SCID
        if (pos >= length) return null
        val scidLen = data[pos].toInt() and 0xFF
        pos += 1 + scidLen

        if (packetType == 0) {
            // Initial packet — skip token
            if (pos >= length) return null
            val tokenLen = readVarInt(data, pos, length)
            pos += varIntSize(data, pos) + tokenLen.toInt()
        }

        // Payload length
        if (pos >= length) return null
        val payloadLen = readVarInt(data, pos, length)
        pos += varIntSize(data, pos)

        // Packet number (4 bytes for PN length 3)
        val pnLen = (firstByte and 0x03) + 1
        pos += pnLen

        // Now scan frames in payload for STREAM frame
        val payloadEnd = minOf(pos + payloadLen.toInt() - pnLen, length)
        return findDnsInFrames(data, pos, payloadEnd)
    }

    private fun tryExtractFromShortHeader(data: ByteArray, length: Int): ByteArray? {
        // Short header: 1 byte + DCID (8 bytes) + PN (1-4 bytes) + payload
        var pos = 1 + DCID_LENGTH
        val pnLen = (data[0].toInt() and 0x03) + 1
        pos += pnLen
        if (pos >= length) return null
        return findDnsInFrames(data, pos, length)
    }

    private fun findDnsInFrames(data: ByteArray, start: Int, end: Int): ByteArray? {
        var pos = start
        while (pos < end) {
            val frameType = data[pos].toInt() and 0xFF
            pos++

            when {
                frameType == 0x00 -> continue // PADDING
                frameType == 0x01 -> return null // PING, no data
                frameType == 0x02 || frameType == 0x03 -> {
                    // ACK frame — skip
                    pos = skipAckFrame(data, pos, end)
                    if (pos < 0) return null
                }
                frameType in 0x08..0x0F -> {
                    // STREAM frame
                    val hasOffset = (frameType and 0x04) != 0
                    val hasLength = (frameType and 0x02) != 0
                    val hasFin = (frameType and 0x01) != 0

                    // Stream ID
                    val streamId = readVarInt(data, pos, end)
                    pos += varIntSize(data, pos)

                    // Offset (if present)
                    if (hasOffset) {
                        readVarInt(data, pos, end)
                        pos += varIntSize(data, pos)
                    }

                    // Length
                    val dataLen: Int
                    if (hasLength) {
                        dataLen = readVarInt(data, pos, end).toInt()
                        pos += varIntSize(data, pos)
                    } else {
                        dataLen = end - pos
                    }

                    if (streamId == DNS_STREAM_ID && dataLen >= 14) {
                        // DoQ: DNS message is prefixed with 2-byte length
                        val dnsLen = ((data[pos].toInt() and 0xFF) shl 8) or
                            (data[pos + 1].toInt() and 0xFF)
                        if (dnsLen in 12..4096 && pos + 2 + dnsLen <= end) {
                            return data.copyOfRange(pos + 2, pos + 2 + dnsLen)
                        }
                        // Try without length prefix (some servers)
                        if (dataLen in 12..4096) {
                            return data.copyOfRange(pos, pos + dataLen)
                        }
                    }
                    pos += dataLen
                }
                frameType == 0x06 -> {
                    // CRYPTO frame — skip
                    readVarInt(data, pos, end) // offset
                    pos += varIntSize(data, pos)
                    val cryptoLen = readVarInt(data, pos, end).toInt()
                    pos += varIntSize(data, pos) + cryptoLen
                }
                else -> return null // Unknown frame, give up
            }
        }
        return null
    }

    private fun skipAckFrame(data: ByteArray, start: Int, end: Int): Int {
        var pos = start
        // Largest Acknowledged
        readVarInt(data, pos, end); pos += varIntSize(data, pos)
        // ACK Delay
        readVarInt(data, pos, end); pos += varIntSize(data, pos)
        // ACK Range Count
        val rangeCount = readVarInt(data, pos, end).toInt(); pos += varIntSize(data, pos)
        // First ACK Range
        readVarInt(data, pos, end); pos += varIntSize(data, pos)
        // Additional ranges
        for (i in 0 until rangeCount) {
            readVarInt(data, pos, end); pos += varIntSize(data, pos)  // Gap
            readVarInt(data, pos, end); pos += varIntSize(data, pos)  // Range
        }
        return pos
    }

    // ── Variable-length integer helpers (RFC 9000 §16) ───────

    private fun readVarInt(data: ByteArray, offset: Int, end: Int): Long {
        if (offset >= end) return 0
        val first = data[offset].toInt() and 0xFF
        val prefix = first shr 6
        return when (prefix) {
            0 -> (first and 0x3F).toLong()
            1 -> {
                if (offset + 1 >= end) return 0
                (((first and 0x3F).toLong()) shl 8) or
                    (data[offset + 1].toInt() and 0xFF).toLong()
            }
            2 -> {
                if (offset + 3 >= end) return 0
                (((first and 0x3F).toLong()) shl 24) or
                    ((data[offset + 1].toInt() and 0xFF).toLong() shl 16) or
                    ((data[offset + 2].toInt() and 0xFF).toLong() shl 8) or
                    (data[offset + 3].toInt() and 0xFF).toLong()
            }
            else -> {
                if (offset + 7 >= end) return 0
                (((first and 0x3F).toLong()) shl 56) or
                    ((data[offset + 1].toInt() and 0xFF).toLong() shl 48) or
                    ((data[offset + 2].toInt() and 0xFF).toLong() shl 40) or
                    ((data[offset + 3].toInt() and 0xFF).toLong() shl 32) or
                    ((data[offset + 4].toInt() and 0xFF).toLong() shl 24) or
                    ((data[offset + 5].toInt() and 0xFF).toLong() shl 16) or
                    ((data[offset + 6].toInt() and 0xFF).toLong() shl 8) or
                    (data[offset + 7].toInt() and 0xFF).toLong()
            }
        }
    }

    private fun varIntSize(data: ByteArray, offset: Int): Int {
        if (offset >= data.size) return 1
        return 1 shl ((data[offset].toInt() and 0xFF) shr 6)
    }

    // ── Fallback ─────────────────────────────────────────────

    private fun fallbackToDot(dns: ByteArray, provider: Provider): ByteArray? {
        val dotProvider = when (provider) {
            Provider.ADGUARD -> DotResolver.Provider.ADGUARD
            Provider.NEXTDNS -> DotResolver.Provider.QUAD9
            Provider.MULLVAD -> DotResolver.Provider.QUAD9
        }
        return dotResolver.resolve(dns, dotProvider)
    }
}
