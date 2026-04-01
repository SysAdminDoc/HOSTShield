package com.hostshield.service

/**
 * Pure packet classification utilities — detects DNS packets by IP version and protocol.
 * Zero state dependencies; all methods are stateless.
 */
object PacketClassifier {

    const val DNS_PORT = 53

    /** Detect IPv4 UDP DNS packet (protocol 17, dest port 53). */
    fun isIpv4UdpDns(p: ByteArray, len: Int): Boolean {
        if (len < 28) return false
        val vih = p[0].toInt() and 0xFF
        if (vih shr 4 != 4) return false
        if (p[9].toInt() and 0xFF != 17) return false
        val ihl = (vih and 0x0F) * 4
        if (len < ihl + 8) return false
        return ((p[ihl + 2].toInt() and 0xFF) shl 8 or (p[ihl + 3].toInt() and 0xFF)) == DNS_PORT
    }

    /** Detect IPv6 UDP DNS packet (next header 17, dest port 53). */
    fun isIpv6UdpDns(p: ByteArray, len: Int): Boolean {
        if (len < 48) return false
        if (p[6].toInt() and 0xFF != 17) return false
        return ((p[42].toInt() and 0xFF) shl 8 or (p[43].toInt() and 0xFF)) == DNS_PORT
    }

    /** Detect IPv4 TCP DNS packet (protocol 6, dest port 53). */
    fun isIpv4TcpDns(p: ByteArray, len: Int): Boolean {
        if (len < 40) return false
        val vih = p[0].toInt() and 0xFF
        if (vih shr 4 != 4) return false
        if (p[9].toInt() and 0xFF != 6) return false
        val ihl = (vih and 0x0F) * 4
        if (len < ihl + 20) return false
        return ((p[ihl + 2].toInt() and 0xFF) shl 8 or (p[ihl + 3].toInt() and 0xFF)) == DNS_PORT
    }

    /** Detect IPv6 TCP DNS packet (next header 6, dest port 53). */
    fun isIpv6TcpDns(p: ByteArray, len: Int): Boolean {
        if (len < 60) return false
        if (p[6].toInt() and 0xFF != 6) return false
        return ((p[42].toInt() and 0xFF) shl 8 or (p[43].toInt() and 0xFF)) == DNS_PORT
    }
}
