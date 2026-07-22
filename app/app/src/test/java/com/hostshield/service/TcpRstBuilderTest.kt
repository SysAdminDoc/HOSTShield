package com.hostshield.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class TcpRstBuilderTest {

    private fun readAckNum(pkt: ByteArray, tcpStart: Int): Long {
        return ((pkt[tcpStart + 8].toLong() and 0xFF) shl 24) or
            ((pkt[tcpStart + 9].toLong() and 0xFF) shl 16) or
            ((pkt[tcpStart + 10].toLong() and 0xFF) shl 8) or
            (pkt[tcpStart + 11].toLong() and 0xFF)
    }

    private fun buildIpv4Tcp(seq: Long, flags: Int, payload: Int): ByteArray {
        val ihl = 20
        val pkt = ByteArray(ihl + 20 + payload)
        pkt[0] = ((4 shl 4) or (ihl / 4)).toByte()
        pkt[9] = 6
        // src 10.0.0.2, dst 1.2.3.4
        pkt[12] = 10; pkt[13] = 0; pkt[14] = 0; pkt[15] = 2
        pkt[16] = 1; pkt[17] = 2; pkt[18] = 3; pkt[19] = 4
        // src port 40000, dst port 53
        pkt[ihl] = 0x9C.toByte(); pkt[ihl + 1] = 0x40.toByte()
        pkt[ihl + 2] = 0; pkt[ihl + 3] = 53
        pkt[ihl + 4] = ((seq shr 24) and 0xFF).toByte()
        pkt[ihl + 5] = ((seq shr 16) and 0xFF).toByte()
        pkt[ihl + 6] = ((seq shr 8) and 0xFF).toByte()
        pkt[ihl + 7] = (seq and 0xFF).toByte()
        pkt[ihl + 12] = 0x50.toByte() // data offset 5
        pkt[ihl + 13] = flags.toByte()
        return pkt
    }

    private fun buildIpv6Tcp(seq: Long, flags: Int, payload: Int): ByteArray {
        val pkt = ByteArray(40 + 20 + payload)
        pkt[0] = 0x60.toByte()
        pkt[6] = 6
        pkt[ihl6] = 0x9C.toByte(); pkt[ihl6 + 1] = 0x40.toByte()
        pkt[ihl6 + 2] = 0; pkt[ihl6 + 3] = 53
        pkt[ihl6 + 4] = ((seq shr 24) and 0xFF).toByte()
        pkt[ihl6 + 5] = ((seq shr 16) and 0xFF).toByte()
        pkt[ihl6 + 6] = ((seq shr 8) and 0xFF).toByte()
        pkt[ihl6 + 7] = (seq and 0xFF).toByte()
        pkt[ihl6 + 12] = 0x50.toByte()
        pkt[ihl6 + 13] = flags.toByte()
        return pkt
    }

    private val ihl6 = 40

    @Test
    fun `ipv4 syn rst acks seq plus one`() {
        val syn = buildIpv4Tcp(seq = 1000, flags = 0x02, payload = 0)
        val rst = TcpRstBuilder.buildTcpRst(syn, 20)
        assertNotNull(rst)
        assertEquals(1001L, readAckNum(rst!!, 20))
        assertEquals(0x14, rst[20 + 13].toInt() and 0xFF) // RST+ACK flags
    }

    @Test
    fun `ipv4 data segment rst acks seq plus payload`() {
        val data = buildIpv4Tcp(seq = 5000, flags = 0x18, payload = 33)
        val rst = TcpRstBuilder.buildTcpRst(data, 20)
        assertNotNull(rst)
        assertEquals(5033L, readAckNum(rst!!, 20))
    }

    @Test
    fun `ipv6 syn rst acks seq plus one`() {
        val syn = buildIpv6Tcp(seq = 7000, flags = 0x02, payload = 0)
        val rst = TcpRstBuilder.buildTcpRstV6(syn)
        assertNotNull(rst)
        assertEquals(7001L, readAckNum(rst!!, 40))
    }

    @Test
    fun `untrimmed mtu buffer would inflate ack - callers must trim to captured length`() {
        // Regression guard for the DnsVpnService packet-loop contract: the shared
        // 1500-byte read buffer must be copyOf(length)-trimmed before RST building,
        // because the builder derives the payload term from orig.size.
        val syn = buildIpv4Tcp(seq = 1000, flags = 0x02, payload = 0)
        val padded = syn.copyOf(1500)
        val trimmedRst = TcpRstBuilder.buildTcpRst(syn, 20)!!
        val paddedRst = TcpRstBuilder.buildTcpRst(padded, 20)!!
        assertEquals(1001L, readAckNum(trimmedRst, 20))
        assertEquals(1000L + (1500 - 40) + 1, readAckNum(paddedRst, 20))
    }

    @Test
    fun `ports and addresses are swapped`() {
        val syn = buildIpv4Tcp(seq = 1, flags = 0x02, payload = 0)
        val rst = TcpRstBuilder.buildTcpRst(syn, 20)!!
        // RST src = original dst (1.2.3.4), RST dst = original src (10.0.0.2)
        assertEquals(1, rst[12].toInt()); assertEquals(4, rst[15].toInt())
        assertEquals(10, rst[16].toInt()); assertEquals(2, rst[19].toInt())
        // RST src port = 53, dst port = 40000
        assertEquals(53, ((rst[20].toInt() and 0xFF) shl 8) or (rst[21].toInt() and 0xFF))
        assertEquals(40000, ((rst[22].toInt() and 0xFF) shl 8) or (rst[23].toInt() and 0xFF))
    }
}
