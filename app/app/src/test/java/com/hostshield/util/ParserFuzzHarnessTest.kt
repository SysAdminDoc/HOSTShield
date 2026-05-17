package com.hostshield.util

import com.hostshield.data.model.HostSource
import com.hostshield.data.model.RuleType
import com.hostshield.data.model.UserRule
import com.hostshield.data.source.SourceDownloader
import com.hostshield.data.source.sourceHttpStatus
import com.hostshield.domain.BlocklistHolder
import com.hostshield.domain.parser.AdblockRuleParser
import com.hostshield.domain.parser.HostsParser
import com.hostshield.service.DnsPacketBuilder
import com.hostshield.service.DnsPacketParser
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.util.Base64
import kotlin.random.Random

class ParserFuzzHarnessTest {

    @Test
    fun `dns parsers tolerate deterministic malformed byte corpus`() {
        val rng = Random(0xD05F00D)
        repeat(350) { i ->
            val bytes = rng.nextBytes(rng.nextInt(0, 512))
            noThrow("dns malformed corpus item $i") {
                DnsPacketParser.parseDnsQueryDomain(bytes)
                DnsPacketParser.parseDnsQueryType(bytes)
                DnsPacketBuilder.parseDomain(bytes)
                DnsPacketBuilder.parseQueryType(bytes)
                DnsPacketBuilder.buildNxdomain(bytes)
                DnsPacketBuilder.buildRefused(bytes)
                DnsPacketBuilder.buildZeroIp(bytes)

                if (bytes.isNotEmpty()) {
                    val start = rng.nextInt(bytes.size)
                    val skipped = DnsPacketParser.skipDnsName(bytes, start)
                    assertTrue("skipDnsName returned invalid offset $skipped", skipped == -1 || skipped in 0..bytes.size)
                }

                val len = rng.nextInt(0, bytes.size + 1)
                val ihl = if (len == 0) 0 else rng.nextInt(0, len)
                DnsPacketParser.extractDnsPayload(bytes, len, ihl)
                DnsPacketParser.extractDnsPayloadV6(bytes, len, ihl)
            }
        }
    }

    @Test
    fun `generated DNS queries roundtrip through parser and response builders`() {
        val rng = Random(0x515151)
        val qtypes = listOf(1, 2, 5, 6, 15, 16, 28, 33, 64, 65, 255, 65280)

        repeat(160) {
            val domain = randomDomain(rng)
            val qtype = qtypes[rng.nextInt(qtypes.size)]
            val query = buildDnsQuery(domain, qtype)

            assertEquals(domain, DnsPacketParser.parseDnsQueryDomain(query))
            assertEquals(domain, DnsPacketBuilder.parseDomain(query))
            assertEquals(qtype, DnsPacketBuilder.parseQueryType(query))
            assertEquals(DnsPacketBuilder.queryTypeLabel(qtype), DnsPacketParser.parseDnsQueryType(query))

            listOf(
                DnsPacketBuilder.buildNxdomain(query),
                DnsPacketBuilder.buildRefused(query),
                DnsPacketBuilder.buildZeroIp(query),
                DnsPacketBuilder.buildBlockResponse(query, "zero_ip")
            ).forEach { response ->
                assertTrue("response must preserve transaction id", response[0] == query[0] && response[1] == query[1])
                assertTrue("response must set QR bit", (response[2].toInt() and 0x80) != 0)
            }
        }
    }

    @Test
    fun `dns stamp parser tolerates malformed stamp corpus and roundtrips supported stamps`() {
        val parser = DnsStampParser()
        val rng = Random(0x5D05)

        repeat(300) { i ->
            val bytes = rng.nextBytes(rng.nextInt(0, 96))
            val encoded = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
            val candidate = when (i % 4) {
                0 -> "sdns://$encoded"
                1 -> "sdns://${randomAscii(rng, rng.nextInt(0, 80))}"
                2 -> encoded
                else -> "sdns://"
            }
            noThrow("dns stamp malformed corpus item $i") {
                parser.parse(candidate)
            }
        }

        val supported = listOf(
            DnsStampParser.DnsStamp(
                protocol = DnsStampParser.DnsStamp.Protocol.PLAIN_DNS,
                address = "1.1.1.1",
                hostname = "",
                path = "",
                dnssec = true,
                noLog = false,
                noFilter = true
            ),
            DnsStampParser.DnsStamp(
                protocol = DnsStampParser.DnsStamp.Protocol.DOH,
                address = "9.9.9.9",
                hostname = "dns.quad9.net",
                path = "/dns-query",
                dnssec = true,
                noLog = true,
                noFilter = false
            ),
            DnsStampParser.DnsStamp(
                protocol = DnsStampParser.DnsStamp.Protocol.DNSCRYPT,
                address = "9.9.9.9:8443",
                hostname = "",
                path = "",
                dnssec = true,
                noLog = true,
                noFilter = false,
                providerName = "2.dnscrypt-cert.quad9.net",
                providerPublicKey = ByteArray(32) { it.toByte() }
            )
        )

        supported.forEach { stamp ->
            val parsed = parser.parse(parser.encode(stamp))
            assertNotNull(parsed)
            requireNotNull(parsed)
            assertEquals(stamp.protocol, parsed.protocol)
            assertEquals(stamp.address, parsed.address)
            assertEquals(stamp.hostname, parsed.hostname)
            assertEquals(stamp.path, parsed.path)
            assertEquals(stamp.providerName, parsed.providerName)
            assertArrayEquals(stamp.providerPublicKey, parsed.providerPublicKey)
        }
    }

    @Test
    fun `hosts and adblock parsers tolerate malformed import corpus`() {
        val rng = Random(0xADB10C)
        repeat(250) { i ->
            val content = buildString {
                repeat(rng.nextInt(1, 40)) {
                    append(randomImportLine(rng))
                    append('\n')
                }
            }

            noThrow("import malformed corpus item $i") {
                HostsParser.isAdblockFormat(content)
                HostsParser.parse(content)
                val parsed = AdblockRuleParser.parse(content)
                assertEquals(parsed.totalLines, parsed.parsedRules + parsed.skippedLines)
            }
        }
    }

    @Test
    fun `regex guards skip dangerous patterns and keep safe regex rules`() {
        val holder = BlocklistHolder()
        val rules = listOf(
            regexRule("(a+)+$"),
            regexRule("["), // invalid regex
            regexRule("x".repeat(501)),
            regexRule("tracker\\.example"),
            regexRule("allowed-tracker\\.example", RuleType.ALLOW)
        )

        holder.update(newDomains = emptySet(), wildcards = emptyList(), regexRules = rules)

        assertTrue(holder.isBlocked("ads.tracker.example"))
        assertFalse(holder.isBlocked("allowed-tracker.example"))
        assertFalse(holder.isBlocked("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa.example"))
    }

    @Test
    fun `backup and source import boundaries reject malformed inputs without throwing outward`() = runTest {
        val rng = Random(0xBACC)
        repeat(200) { i ->
            val bytes = rng.nextBytes(rng.nextInt(0, 24))
            noThrow("backup malformed corpus item $i") {
                assertFalse(BackupCrypto.isEncrypted(bytes))
                if (bytes.size < 49) {
                    try {
                        BackupCrypto.decrypt(bytes, "passphrase")
                        fail("short backup payload should be rejected")
                    } catch (_: IllegalArgumentException) {
                        // Expected before any key derivation.
                    }
                }
            }
        }

        val downloader = SourceDownloader()
        val invalidValidation = downloader.validate("not a url")
        assertTrue(invalidValidation.isFailure)
        assertEquals(0, invalidValidation.exceptionOrNull()?.sourceHttpStatus())

        val invalidDownload = downloader.download(
            HostSource(url = "http://", label = "Malformed")
        )
        assertTrue(invalidDownload.isFailure)
        assertEquals(0, invalidDownload.exceptionOrNull()?.sourceHttpStatus())
    }

    @Test
    fun `truncated compression pointer is rejected`() {
        assertEquals(-1, DnsPacketParser.skipDnsName(byteArrayOf(0xC0.toByte()), 0))
        assertNull(DnsPacketParser.parseDnsQueryDomain(ByteArray(12) + byteArrayOf(0xC0.toByte())))
    }

    private fun regexRule(pattern: String, type: RuleType = RuleType.BLOCK): UserRule =
        UserRule(hostname = pattern, type = type, isRegex = true)

    private fun buildDnsQuery(domain: String, qtype: Int): ByteArray {
        val header = ByteArray(12).apply {
            this[0] = 0x12
            this[1] = 0x34
            this[2] = 0x01
            this[5] = 0x01
        }
        val question = mutableListOf<Byte>()
        domain.split('.').forEach { label ->
            question += label.length.toByte()
            label.forEach { question += it.code.toByte() }
        }
        question += 0
        question += (qtype ushr 8).toByte()
        question += (qtype and 0xFF).toByte()
        question += 0
        question += 1
        return header + question.toByteArray()
    }

    private fun randomDomain(rng: Random): String {
        val labels = rng.nextInt(2, 5)
        return (0 until labels)
            .joinToString(".") { randomLabel(rng, rng.nextInt(1, 12)) }
    }

    private fun randomLabel(rng: Random, length: Int): String {
        val alphabet = "abcdefghijklmnopqrstuvwxyz0123456789"
        return buildString(length) {
            repeat(length) { append(alphabet[rng.nextInt(alphabet.length)]) }
        }
    }

    private fun randomImportLine(rng: Random): String = when (rng.nextInt(8)) {
        0 -> "0.0.0.0 ${randomDomain(rng)}"
        1 -> "127.0.0.1 ${randomDomain(rng)} # inline"
        2 -> "||${randomDomain(rng)}^"
        3 -> "@@||${randomDomain(rng)}^"
        4 -> "/${randomAscii(rng, rng.nextInt(0, 520))}/"
        5 -> "${randomAscii(rng, rng.nextInt(0, 80))} ${randomAscii(rng, rng.nextInt(0, 80))}"
        6 -> "# ${randomAscii(rng, rng.nextInt(0, 80))}"
        else -> randomAscii(rng, rng.nextInt(0, 120))
    }

    private fun randomAscii(rng: Random, length: Int): String {
        val chars = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789.-_*^$[]()/\\:@ \t!#"
        return buildString(length) {
            repeat(length) { append(chars[rng.nextInt(chars.length)]) }
        }
    }

    private fun noThrow(description: String, block: () -> Unit) {
        try {
            block()
        } catch (t: Throwable) {
            fail("$description threw ${t.javaClass.simpleName}: ${t.message}")
        }
    }
}
