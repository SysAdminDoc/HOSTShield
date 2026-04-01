package com.hostshield.service

/**
 * Pure TCP RST packet construction and checksum computation.
 * Zero state dependencies — all methods operate solely on packet byte arrays.
 */
object TcpRstBuilder {

    /** Build IPv4 TCP RST by swapping src/dst and computing checksums. */
    fun buildTcpRst(orig: ByteArray, ihl: Int): ByteArray? {
        if (orig.size < ihl + 20) return null
        val rstLen = ihl + 20
        val rst = ByteArray(rstLen)

        // IP header
        rst[0] = ((4 shl 4) or (ihl / 4)).toByte()
        rst[1] = 0
        rst[2] = ((rstLen shr 8) and 0xFF).toByte()
        rst[3] = (rstLen and 0xFF).toByte()
        rst[4] = 0; rst[5] = 0
        rst[6] = 0x40.toByte(); rst[7] = 0
        rst[8] = 64
        rst[9] = 6
        rst[10] = 0; rst[11] = 0
        System.arraycopy(orig, 16, rst, 12, 4)
        System.arraycopy(orig, 12, rst, 16, 4)

        // TCP header
        val t = ihl
        rst[t] = orig[t + 2]; rst[t + 1] = orig[t + 3]
        rst[t + 2] = orig[t]; rst[t + 3] = orig[t + 1]
        rst[t + 4] = 0; rst[t + 5] = 0; rst[t + 6] = 0; rst[t + 7] = 0

        val origSeq = ((orig[t + 4].toLong() and 0xFF) shl 24) or
            ((orig[t + 5].toLong() and 0xFF) shl 16) or
            ((orig[t + 6].toLong() and 0xFF) shl 8) or
            (orig[t + 7].toLong() and 0xFF)
        val origDataOff = ((orig[t + 12].toInt() and 0xF0) shr 4) * 4
        val origPayload = orig.size - ihl - origDataOff
        val origFlags = orig[t + 13].toInt() and 0xFF
        val synBit = if ((origFlags and 0x02) != 0) 1 else 0
        val finBit = if ((origFlags and 0x01) != 0) 1 else 0
        val ackNum = origSeq + origPayload + synBit + finBit
        rst[t + 8] = ((ackNum shr 24) and 0xFF).toByte()
        rst[t + 9] = ((ackNum shr 16) and 0xFF).toByte()
        rst[t + 10] = ((ackNum shr 8) and 0xFF).toByte()
        rst[t + 11] = (ackNum and 0xFF).toByte()

        rst[t + 12] = 0x50.toByte()
        rst[t + 13] = 0x14.toByte()
        rst[t + 14] = 0; rst[t + 15] = 0
        rst[t + 16] = 0; rst[t + 17] = 0
        rst[t + 18] = 0; rst[t + 19] = 0

        computeTcpChecksum(rst, ihl, 20)
        computeIpChecksum(rst, ihl)

        return rst
    }

    /** Build IPv6 TCP RST by swapping src/dst and computing checksum. */
    fun buildTcpRstV6(orig: ByteArray): ByteArray? {
        if (orig.size < 60) return null
        val rstLen = 60
        val rst = ByteArray(rstLen)

        // IPv6 header
        rst[0] = 0x60.toByte()
        rst[1] = 0; rst[2] = 0; rst[3] = 0
        val tcpLen = 20
        rst[4] = ((tcpLen shr 8) and 0xFF).toByte()
        rst[5] = (tcpLen and 0xFF).toByte()
        rst[6] = 6
        rst[7] = 64
        System.arraycopy(orig, 24, rst, 8, 16)
        System.arraycopy(orig, 8, rst, 24, 16)

        // TCP header
        val t = 40
        val origT = 40
        rst[t] = orig[origT + 2]; rst[t + 1] = orig[origT + 3]
        rst[t + 2] = orig[origT]; rst[t + 3] = orig[origT + 1]
        rst[t + 4] = 0; rst[t + 5] = 0; rst[t + 6] = 0; rst[t + 7] = 0

        val origSeq = ((orig[origT + 4].toLong() and 0xFF) shl 24) or
            ((orig[origT + 5].toLong() and 0xFF) shl 16) or
            ((orig[origT + 6].toLong() and 0xFF) shl 8) or
            (orig[origT + 7].toLong() and 0xFF)
        val origDataOff = ((orig[origT + 12].toInt() and 0xF0) shr 4) * 4
        val origPayload = orig.size - 40 - origDataOff
        val origFlags = orig[origT + 13].toInt() and 0xFF
        val synBit = if ((origFlags and 0x02) != 0) 1 else 0
        val finBit = if ((origFlags and 0x01) != 0) 1 else 0
        val ackNum = origSeq + origPayload + synBit + finBit
        rst[t + 8] = ((ackNum shr 24) and 0xFF).toByte()
        rst[t + 9] = ((ackNum shr 16) and 0xFF).toByte()
        rst[t + 10] = ((ackNum shr 8) and 0xFF).toByte()
        rst[t + 11] = (ackNum and 0xFF).toByte()
        rst[t + 12] = 0x50.toByte()
        rst[t + 13] = 0x14.toByte()
        rst[t + 14] = 0; rst[t + 15] = 0
        rst[t + 16] = 0; rst[t + 17] = 0
        rst[t + 18] = 0; rst[t + 19] = 0

        computeTcpChecksumV6(rst, 40, tcpLen)

        return rst
    }

    /** Compute and write TCP checksum for IPv4 into pkt[ihl+16..ihl+17]. */
    fun computeTcpChecksum(pkt: ByteArray, ihl: Int, tcpLen: Int) {
        var sum = 0L
        for (i in 12 until 20 step 2) {
            sum += ((pkt[i].toInt() and 0xFF) shl 8) or (pkt[i + 1].toInt() and 0xFF)
        }
        sum += 6
        sum += tcpLen
        pkt[ihl + 16] = 0; pkt[ihl + 17] = 0
        for (i in ihl until ihl + tcpLen step 2) {
            val hi = pkt[i].toInt() and 0xFF
            val lo = if (i + 1 < pkt.size) pkt[i + 1].toInt() and 0xFF else 0
            sum += (hi shl 8) or lo
        }
        while (sum shr 16 != 0L) sum = (sum and 0xFFFF) + (sum shr 16)
        val cksum = sum.toInt().inv() and 0xFFFF
        pkt[ihl + 16] = ((cksum shr 8) and 0xFF).toByte()
        pkt[ihl + 17] = (cksum and 0xFF).toByte()
    }

    /** TCP checksum for IPv6 (pseudo-header uses src/dst IPv6 addresses). */
    fun computeTcpChecksumV6(pkt: ByteArray, tcpStart: Int, tcpLen: Int) {
        var sum = 0L
        for (i in 8 until 40 step 2) {
            sum += ((pkt[i].toInt() and 0xFF) shl 8) or (pkt[i + 1].toInt() and 0xFF)
        }
        sum += 6
        sum += tcpLen
        pkt[tcpStart + 16] = 0; pkt[tcpStart + 17] = 0
        for (i in tcpStart until tcpStart + tcpLen step 2) {
            val hi = pkt[i].toInt() and 0xFF
            val lo = if (i + 1 < pkt.size) pkt[i + 1].toInt() and 0xFF else 0
            sum += (hi shl 8) or lo
        }
        while (sum shr 16 != 0L) sum = (sum and 0xFFFF) + (sum shr 16)
        val cksum = sum.toInt().inv() and 0xFFFF
        pkt[tcpStart + 16] = ((cksum shr 8) and 0xFF).toByte()
        pkt[tcpStart + 17] = (cksum and 0xFF).toByte()
    }

    /** Compute and write IP header checksum into pkt[10..11]. */
    fun computeIpChecksum(pkt: ByteArray, ihl: Int) {
        pkt[10] = 0; pkt[11] = 0
        var sum = 0L
        for (i in 0 until ihl step 2) {
            sum += ((pkt[i].toInt() and 0xFF) shl 8) or (pkt[i + 1].toInt() and 0xFF)
        }
        while (sum shr 16 != 0L) sum = (sum and 0xFFFF) + (sum shr 16)
        val cksum = sum.toInt().inv() and 0xFFFF
        pkt[10] = ((cksum shr 8) and 0xFF).toByte()
        pkt[11] = (cksum and 0xFF).toByte()
    }
}
