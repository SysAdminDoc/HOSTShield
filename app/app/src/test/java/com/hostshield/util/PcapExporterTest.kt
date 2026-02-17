package com.hostshield.util

import org.junit.Assert.*
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class PcapExporterTest {

    @Test
    fun `PCAP magic number is correct`() {
        val magic = 0xA1B2C3D4.toInt()
        val buf = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN)
        buf.putInt(magic)
        val bytes = buf.array()
        // Little-endian: D4 C3 B2 A1
        assertEquals(0xD4.toByte(), bytes[0])
        assertEquals(0xC3.toByte(), bytes[1])
        assertEquals(0xB2.toByte(), bytes[2])
        assertEquals(0xA1.toByte(), bytes[3])
    }

    @Test
    fun `DNS query label encoding`() {
        // "ads.example.com" should encode as:
        // 3 "ads" 7 "example" 3 "com" 0
        val hostname = "ads.example.com"
        val labels = hostname.split('.')
        val nameLen = labels.sumOf { it.length + 1 } + 1
        assertEquals(17, nameLen) // 1+3 + 1+7 + 1+3 + 1 = 17

        val buf = ByteBuffer.allocate(nameLen)
        for (label in labels) {
            buf.put(label.length.toByte())
            buf.put(label.toByteArray(Charsets.US_ASCII))
        }
        buf.put(0.toByte())

        val bytes = buf.array()
        assertEquals(3.toByte(), bytes[0])   // length of "ads"
        assertEquals('a'.code.toByte(), bytes[1])
        assertEquals('d'.code.toByte(), bytes[2])
        assertEquals('s'.code.toByte(), bytes[3])
        assertEquals(7.toByte(), bytes[4])   // length of "example"
        assertEquals(0.toByte(), bytes[nameLen - 1]) // null terminator
    }

    @Test
    fun `IPv4 packet header structure`() {
        // Verify IPv4 header basics
        val ipLen = 20 + 8 // IP + UDP minimum
        val buf = ByteBuffer.allocate(ipLen)

        buf.put(0x45.toByte())           // version 4, IHL 5
        buf.put(0x00.toByte())           // DSCP
        buf.putShort(ipLen.toShort())    // total length
        buf.putShort(0.toShort())        // ID
        buf.putShort(0x4000.toShort())   // DF flag
        buf.put(64.toByte())             // TTL
        buf.put(17.toByte())             // UDP protocol
        buf.putShort(0.toShort())        // checksum
        buf.put(byteArrayOf(10, 0, 0, 1))
        buf.put(byteArrayOf(8, 8, 8, 8))

        val bytes = buf.array()
        assertEquals(0x45.toByte(), bytes[0])  // IPv4, 5 words
        assertEquals(64.toByte(), bytes[8])    // TTL
        assertEquals(17.toByte(), bytes[9])    // UDP
    }

    @Test
    fun `TCP SYN flag is set correctly`() {
        // TCP flags field at offset 13 in TCP header
        // SYN = 0x02, data offset = 5 (20 bytes)
        // Combined in bytes 12-13: 0x50 0x02
        val synFlags: Short = 0x5002
        val buf = ByteBuffer.allocate(2)
        buf.putShort(synFlags)
        val bytes = buf.array()
        assertEquals(0x50.toByte(), bytes[0]) // data offset = 5
        assertEquals(0x02.toByte(), bytes[1]) // SYN flag
    }

    @Test
    fun `PCAP record header timestamps`() {
        val timestampMs = 1708123456789L
        val sec = (timestampMs / 1000).toInt()
        val usec = ((timestampMs % 1000) * 1000).toInt()

        assertEquals(1708123456, sec)
        assertEquals(789000, usec)
    }
}
