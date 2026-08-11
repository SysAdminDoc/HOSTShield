package com.hostshield.util

import org.junit.Assert.*
import org.junit.Test
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import io.mockk.mockk
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

class PcapExporterTest {

    @get:Rule
    val tempDir = TemporaryFolder()

    // These five previously built ByteBuffers inside the test and asserted on them,
    // so they passed with PcapExporter deleted. They now write through the real
    // writer and parse the emitted bytes back.
    private fun exporter() = PcapExporter(mockk(relaxed = true), mockk(relaxed = true))

    private fun u32(b: ByteArray, off: Int): Long =
        ByteBuffer.wrap(b, off, 4).order(ByteOrder.LITTLE_ENDIAN).int.toLong() and 0xFFFFFFFFL

    @Test
    fun `written PCAP global header is a valid little-endian libpcap header`() {
        val out = ByteArrayOutputStream()
        exporter().writePcapHeader(out)
        val bytes = out.toByteArray()

        assertEquals("global header is 24 bytes", 24, bytes.size)
        assertEquals(0xA1B2C3D4L, u32(bytes, 0))
        assertEquals(2L, ByteBuffer.wrap(bytes, 4, 2).order(ByteOrder.LITTLE_ENDIAN).short.toLong())
        assertEquals(4L, ByteBuffer.wrap(bytes, 6, 2).order(ByteOrder.LITTLE_ENDIAN).short.toLong())
        assertEquals("LINKTYPE_RAW", 101L, u32(bytes, 20))
    }

    @Test
    fun `written PCAP record frames the packet and carries the supplied timestamp`() {
        val out = ByteArrayOutputStream()
        val packet = ByteArray(42) { it.toByte() }
        exporter().writePcapRecord(out, packet, 1_708_123_456_789L)
        val bytes = out.toByteArray()

        assertEquals("16-byte record header plus payload", 16 + packet.size, bytes.size)
        assertEquals(1_708_123_456L, u32(bytes, 0))
        assertEquals(789_000L, u32(bytes, 4))
        assertEquals("incl_len", packet.size.toLong(), u32(bytes, 8))
        assertEquals("orig_len", packet.size.toLong(), u32(bytes, 12))
        assertArrayEquals(packet, bytes.copyOfRange(16, bytes.size))
    }

    @Test
    fun `a written PCAP stream parses back record by record`() {
        val out = ByteArrayOutputStream()
        val exporter = exporter()
        val first = ByteArray(20) { 1 }
        val second = ByteArray(35) { 2 }
        exporter.writePcapHeader(out)
        exporter.writePcapRecord(out, first, 1_000L)
        exporter.writePcapRecord(out, second, 2_500L)
        val bytes = out.toByteArray()

        var off = 24
        val firstLen = u32(bytes, off + 8).toInt()
        assertEquals(first.size, firstLen)
        off += 16 + firstLen
        val secondLen = u32(bytes, off + 8).toInt()
        assertEquals(second.size, secondLen)
        assertEquals("stream is fully consumed", bytes.size, off + 16 + secondLen)
        assertEquals(2L, u32(bytes, off + 0))
        assertEquals(500_000L, u32(bytes, off + 4))
    }

    @Test
    fun `prepareExportFile sweeps stale PCAP files and uses exports directory`() {
        val cacheDir = tempDir.newFolder("cache")
        val exportsDir = File(cacheDir, "exports").apply { mkdirs() }
        val now = System.currentTimeMillis()
        val staleMs = now - 25L * 60L * 60L * 1000L
        val staleRoot = File(cacheDir, "hostshield_dns_1.pcap").apply {
            writeText("stale")
            setLastModified(staleMs)
        }
        val staleExport = File(exportsDir, "hostshield_dns_2.pcap").apply {
            writeText("stale")
            setLastModified(staleMs)
        }
        val fresh = File(exportsDir, "hostshield_dns_3.pcap").apply { writeText("fresh") }
        val unrelated = File(cacheDir, "hostshield_dns_4.txt").apply {
            writeText("keep")
            setLastModified(staleMs)
        }

        // A stale capture from a DIFFERENT export mode must also be swept: it holds
        // the same DNS metadata and previously survived until that mode ran again.
        val staleOtherMode = File(exportsDir, "hostshield_all_9.pcap").apply {
            writeText("stale")
            setLastModified(staleMs)
        }

        val target = PcapExporter.prepareExportFile(cacheDir, "hostshield_dns_", now)

        assertFalse(staleOtherMode.exists())
        assertEquals(exportsDir, target.parentFile)
        assertTrue(target.name.startsWith("hostshield_dns_"))
        assertFalse(staleRoot.exists())
        assertFalse(staleExport.exists())
        assertTrue(fresh.exists())
        assertTrue(unrelated.exists())
    }

    @Test
    fun `parseIpv4OrNull accepts valid dotted quads`() {
        assertArrayEquals(byteArrayOf(8, 8, 8, 8), PcapExporter.parseIpv4OrNull("8.8.8.8"))
        assertArrayEquals(
            byteArrayOf(255.toByte(), 0, 10, 1),
            PcapExporter.parseIpv4OrNull("255.0.10.1")
        )
    }

    @Test
    fun `parseIpv4OrNull rejects out of range octets`() {
        // toInt().toByte() used to silently truncate these into wrong addresses
        assertNull(PcapExporter.parseIpv4OrNull("8.8.8.999"))
        assertNull(PcapExporter.parseIpv4OrNull("256.1.1.1"))
        assertNull(PcapExporter.parseIpv4OrNull("1.1.1.1000"))
    }

    @Test
    fun `parseIpv4OrNull rejects malformed input`() {
        assertNull(PcapExporter.parseIpv4OrNull("a.b.c.d"))
        assertNull(PcapExporter.parseIpv4OrNull("1.2.3"))
        assertNull(PcapExporter.parseIpv4OrNull("1.2.3.4.5"))
        assertNull(PcapExporter.parseIpv4OrNull("1.2.3."))
        assertNull(PcapExporter.parseIpv4OrNull("+1.2.3.4"))
        assertNull(PcapExporter.parseIpv4OrNull("2001:db8::1"))
    }

    @Test
    fun `parseIpv6OrNull accepts literals and rejects non-ipv6`() {
        // IPv6 connection-log rows are no longer silently dropped.
        assertEquals(16, PcapExporter.parseIpv6OrNull("2001:db8::1")?.size)
        assertEquals(16, PcapExporter.parseIpv6OrNull("[2606:4700:4700::1111]")?.size)
        assertEquals(16, PcapExporter.parseIpv6OrNull("::1")?.size)
        assertNull(PcapExporter.parseIpv6OrNull("8.8.8.8"))
        assertNull(PcapExporter.parseIpv6OrNull("example.com"))
        assertNull(PcapExporter.parseIpv6OrNull("nothex::zz"))
    }
}
