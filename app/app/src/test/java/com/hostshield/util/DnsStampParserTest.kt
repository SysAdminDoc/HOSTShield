package com.hostshield.util

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.util.Base64

class DnsStampParserTest {

    private val parser = DnsStampParser()

    @Test
    fun `parses DNSCrypt resolver stamp with provider public key`() {
        val providerKey = ByteArray(32) { it.toByte() }
        val stamp = stampOf(
            0x01,
            properties(dnssec = true, noLog = true, noFilter = false),
            lp("9.9.9.9:8443"),
            lp(providerKey),
            lp("2.dnscrypt-cert.quad9.net")
        )

        val parsed = parser.parse(stamp)

        assertNotNull(parsed)
        requireNotNull(parsed)
        assertEquals(DnsStampParser.DnsStamp.Protocol.DNSCRYPT, parsed.protocol)
        assertEquals("9.9.9.9:8443", parsed.address)
        assertEquals("2.dnscrypt-cert.quad9.net", parsed.providerName)
        assertTrue(parsed.dnssec)
        assertTrue(parsed.noLog)
        assertFalse(parsed.noFilter)
        assertArrayEquals(providerKey, parsed.providerPublicKey)
    }

    @Test
    fun `rejects DNSCrypt resolver stamp without a 32 byte provider key`() {
        val stamp = stampOf(
            0x01,
            properties(),
            lp("9.9.9.9"),
            lp(byteArrayOf(1, 2, 3)),
            lp("2.dnscrypt-cert.quad9.net")
        )

        assertNull(parser.parse(stamp))
    }

    @Test
    fun `parses Anonymized DNSCrypt relay stamp without resolver properties`() {
        val stamp = stampOf(0x81, lp("[2001:db8::53]:443"))

        val parsed = parser.parse(stamp)

        assertNotNull(parsed)
        requireNotNull(parsed)
        assertEquals(DnsStampParser.DnsStamp.Protocol.DNSCRYPT_RELAY, parsed.protocol)
        assertEquals("[2001:db8::53]:443", parsed.address)
        assertFalse(parsed.dnssec)
        assertFalse(parsed.noLog)
        assertFalse(parsed.noFilter)
    }

    @Test
    fun `encodes DNSCrypt stamps with spec width properties`() {
        val stamp = DnsStampParser.DnsStamp(
            protocol = DnsStampParser.DnsStamp.Protocol.DNSCRYPT,
            address = "1.1.1.1",
            hostname = "",
            path = "",
            dnssec = true,
            noLog = true,
            noFilter = true,
            providerName = "2.dnscrypt-cert.example",
            providerPublicKey = ByteArray(32) { (255 - it).toByte() }
        )

        val encoded = parser.encode(stamp)
        val decoded = Base64.getUrlDecoder().decode(encoded.removePrefix("sdns://"))

        assertEquals(0x01, decoded[0].toInt() and 0xFF)
        assertEquals(0x07, decoded[1].toInt() and 0xFF)
        assertEquals(0x00, decoded[2].toInt() and 0xFF)
        assertEquals(0x00, decoded[8].toInt() and 0xFF)
        assertEquals(stamp.address, parser.parse(encoded)?.address)
    }

    @Test
    fun `keeps parsing legacy one byte HostShield property stamps`() {
        val stamp = stampOf(
            0x00,
            byteArrayOf(0x05),
            lp("1.1.1.1")
        )

        val parsed = parser.parse(stamp)

        assertNotNull(parsed)
        requireNotNull(parsed)
        assertEquals(DnsStampParser.DnsStamp.Protocol.PLAIN_DNS, parsed.protocol)
        assertEquals("1.1.1.1", parsed.address)
        assertTrue(parsed.dnssec)
        assertFalse(parsed.noLog)
        assertTrue(parsed.noFilter)
    }

    private fun stampOf(type: Int, vararg chunks: ByteArray): String {
        val out = ByteArrayOutputStream()
        out.write(type)
        chunks.forEach { out.write(it) }
        return "sdns://" + Base64.getUrlEncoder().withoutPadding().encodeToString(out.toByteArray())
    }

    private fun properties(
        dnssec: Boolean = false,
        noLog: Boolean = false,
        noFilter: Boolean = false
    ): ByteArray {
        var props = 0
        if (dnssec) props = props or 0x01
        if (noLog) props = props or 0x02
        if (noFilter) props = props or 0x04
        return ByteArray(8).also { it[0] = props.toByte() }
    }

    private fun lp(value: String): ByteArray = lp(value.toByteArray(Charsets.UTF_8))

    private fun lp(value: ByteArray): ByteArray =
        ByteArrayOutputStream().also {
            it.write(value.size)
            it.write(value)
        }.toByteArray()
}
