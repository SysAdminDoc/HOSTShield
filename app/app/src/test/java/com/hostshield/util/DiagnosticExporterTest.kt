package com.hostshield.util

import com.hostshield.data.model.DnsLogEntry
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

class DiagnosticExporterTest {

    @get:Rule
    val tempDir = TemporaryFolder()

    private fun buildZip(
        reportText: String = "test report",
        eventsJsonl: String = "",
        manifestJson: String = JSONObject()
            .put("generated_at_ms", 1000L)
            .put("app_version", "6.9.0")
            .put("version_code", 82)
            .put("event_count", 0)
            .toString(2)
    ): java.io.File {
        val zip = tempDir.newFile("hostshield-diag-test.zip")
        ZipOutputStream(FileOutputStream(zip)).use { zos ->
            zos.putNextEntry(ZipEntry("hostshield-diagnostic.txt"))
            zos.write(reportText.toByteArray(Charsets.UTF_8))
            zos.closeEntry()

            zos.putNextEntry(ZipEntry("diagnostic-events.jsonl"))
            zos.write(eventsJsonl.toByteArray(Charsets.UTF_8))
            zos.closeEntry()

            zos.putNextEntry(ZipEntry("manifest.json"))
            zos.write(manifestJson.toByteArray(Charsets.UTF_8))
            zos.closeEntry()
        }
        return zip
    }

    @Test
    fun `ZIP contains expected entries`() {
        val zip = buildZip()
        ZipFile(zip).use { zf ->
            val names = zf.entries().asSequence().map { it.name }.toSet()
            assertTrue(names.contains("hostshield-diagnostic.txt"))
            assertTrue(names.contains("diagnostic-events.jsonl"))
            assertTrue(names.contains("manifest.json"))
            assertEquals(3, names.size)
        }
    }

    @Test
    fun `manifest contains required fields`() {
        val zip = buildZip()
        ZipFile(zip).use { zf ->
            val entry = zf.getEntry("manifest.json")
            assertNotNull(entry)
            val json = JSONObject(zf.getInputStream(entry).bufferedReader().readText())
            assertTrue(json.has("generated_at_ms"))
            assertTrue(json.has("app_version"))
            assertTrue(json.has("version_code"))
            assertTrue(json.has("event_count"))
            assertEquals("6.9.0", json.getString("app_version"))
            assertEquals(82, json.getInt("version_code"))
            assertEquals(0, json.getInt("event_count"))
        }
    }

    @Test
    fun `report text is readable from ZIP`() {
        val reportContent = "HostShield Diagnostic Report\nGenerated: 2026-06-13"
        val zip = buildZip(reportText = reportContent)
        ZipFile(zip).use { zf ->
            val entry = zf.getEntry("hostshield-diagnostic.txt")
            val text = zf.getInputStream(entry).bufferedReader().readText()
            assertEquals(reportContent, text)
        }
    }

    @Test
    fun `events JSONL roundtrips through ZIP`() {
        val events = """{"timestamp_ms":1000,"type":"VPN_START","message":"started"}
{"timestamp_ms":2000,"type":"VPN_STOP","message":"stopped"}"""
        val zip = buildZip(eventsJsonl = events)
        ZipFile(zip).use { zf ->
            val entry = zf.getEntry("diagnostic-events.jsonl")
            val text = zf.getInputStream(entry).bufferedReader().readText()
            assertEquals(events, text)
            val lines = text.lines().filter { it.isNotBlank() }
            assertEquals(2, lines.size)
            val first = JSONObject(lines[0])
            assertEquals("VPN_START", first.getString("type"))
        }
    }

    @Test
    fun `empty events JSONL produces valid ZIP entry`() {
        val zip = buildZip(eventsJsonl = "")
        ZipFile(zip).use { zf ->
            val entry = zf.getEntry("diagnostic-events.jsonl")
            assertNotNull(entry)
            val text = zf.getInputStream(entry).bufferedReader().readText()
            assertEquals("", text)
        }
    }

    @Test
    fun `manifest event count matches JSONL lines`() {
        val events = """{"type":"A"}
{"type":"B"}
{"type":"C"}"""
        val eventCount = events.lineSequence().filter { it.isNotBlank() }.count()
        val manifest = JSONObject()
            .put("generated_at_ms", 1000L)
            .put("app_version", "6.9.0")
            .put("version_code", 82)
            .put("event_count", eventCount)
            .toString(2)
        val zip = buildZip(eventsJsonl = events, manifestJson = manifest)
        ZipFile(zip).use { zf ->
            val json = JSONObject(
                zf.getInputStream(zf.getEntry("manifest.json")).bufferedReader().readText()
            )
            assertEquals(3, json.getInt("event_count"))
        }
    }

    @Test
    fun `threat intel diagnostic summary groups without raw indicators`() {
        val summary = summarizeThreatIntelReviewLogs(
            listOf(
                DnsLogEntry(
                    hostname = "bad.example",
                    blocked = true,
                    appPackage = "com.example.browser",
                    timestamp = 1000L,
                    decisionReason = "threat_intel_domain",
                    decisionSource = "URLhaus",
                    matchedValue = "bad.example"
                ),
                DnsLogEntry(
                    hostname = "ip-hit.example",
                    blocked = true,
                    appPackage = "com.example.browser",
                    timestamp = 2000L,
                    decisionReason = "threat_intel_ip",
                    decisionSource = "Spamhaus DROP",
                    matchedValue = "203.0.113.44"
                ),
                DnsLogEntry(
                    hostname = "ip-hit.example",
                    blocked = true,
                    appPackage = "com.example.mail",
                    timestamp = 3000L,
                    decisionReason = "threat_intel_ip",
                    decisionSource = "Spamhaus DROP",
                    matchedValue = "203.0.113.44"
                ),
                DnsLogEntry(
                    hostname = "ordinary.example",
                    blocked = true,
                    timestamp = 4000L,
                    decisionReason = "source_list",
                    decisionSource = "Source"
                )
            )
        )

        assertEquals(3, summary.blockCount)
        assertEquals(2, summary.domainCount)
        assertEquals(
            ThreatIntelDiagnosticReviewRow(
                feedName = "Spamhaus DROP",
                matchType = "resolved_ip",
                blockCount = 2,
                appCount = 2,
                lastMatched = 3000L
            ),
            summary.rows.first()
        )
        assertFalse(summary.rows.any { it.feedName.contains("203.0.113.44") })
    }
}
