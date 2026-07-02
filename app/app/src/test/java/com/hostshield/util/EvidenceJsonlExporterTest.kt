package com.hostshield.util

import com.hostshield.data.model.ConnectionLogEntry
import com.hostshield.data.model.DnsLogEntry
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EvidenceJsonlExporterTest {

    @Test
    fun `buildJsonl redacts bounded rows and reports chunks`() {
        val dns = DnsLogEntry(
            id = 1,
            hostname = "ads.example.com",
            blocked = true,
            appPackage = "com.example.browser",
            appLabel = "Browser",
            timestamp = 2_000L,
            sourceIp = "10.0.0.2",
            queryType = "A",
            upstreamServer = "1.1.1.1",
            cnameChain = "tracker.example.com,cdn.example.com",
            resolvedIps = "203.0.113.10,203.0.113.11",
            trackerOwner = "Example Ads",
            matchedValue = "ads.example.com"
        )
        val connection = ConnectionLogEntry(
            id = 2,
            uid = 10042,
            packageName = "com.example.browser",
            appLabel = "Browser",
            destination = "198.51.100.4",
            port = 443,
            action = "REJECT",
            timestamp = 1_500L
        )
        val olderDns = dns.copy(id = 3, hostname = "older.example.com", timestamp = 1_000L)

        val result = EvidenceJsonlExporter.buildJsonl(
            dnsLogs = listOf(dns, olderDns),
            connectionLogs = listOf(connection),
            options = EvidenceJsonlExportOptions(
                dataset = EvidenceJsonlDataset.ALL,
                maxRows = 2,
                chunkRows = 1,
                redactDomains = true,
                redactApps = true,
                redactIps = true
            )
        )

        val lines = result.content.trim().lines()
        assertEquals(3, lines.size)
        assertEquals(2, result.rowCount)
        assertEquals(2, result.chunkCount)
        assertTrue(result.truncated)

        val metadata = JSONObject(lines[0])
        assertEquals("hostshield.evidence_jsonl", metadata.getString("schema"))
        assertEquals(1, metadata.getInt("schema_version"))
        assertEquals("metadata", metadata.getString("row_type"))
        assertEquals(2, metadata.getInt("row_count"))
        assertEquals(2, metadata.getInt("chunk_count"))
        assertTrue(metadata.getBoolean("truncated"))

        val dnsRow = JSONObject(lines[1])
        assertEquals("dns", dnsRow.getString("row_type"))
        assertEquals(0, dnsRow.getInt("chunk_index"))
        assertTrue(dnsRow.getString("hostname").startsWith("redacted-domain-"))
        assertTrue(dnsRow.getString("app_package").startsWith("redacted-app-"))
        assertTrue(dnsRow.getString("source_ip").startsWith("redacted-ip-"))
        assertTrue(dnsRow.getString("resolved_ips").contains("redacted-ip-"))
        assertFalse(lines.joinToString("\n").contains("ads.example.com"))
        assertFalse(lines.joinToString("\n").contains("com.example.browser"))
        assertFalse(lines.joinToString("\n").contains("198.51.100.4"))

        val connectionRow = JSONObject(lines[2])
        assertEquals("connection", connectionRow.getString("row_type"))
        assertEquals(1, connectionRow.getInt("chunk_index"))
        assertEquals(443, connectionRow.getInt("port"))
        assertTrue(connectionRow.getString("destination").startsWith("redacted-ip-"))
    }

    @Test
    fun `buildJsonl applies time query app and query type filters`() {
        val matching = DnsLogEntry(
            id = 10,
            hostname = "ads.example.com",
            blocked = true,
            appPackage = "com.example.browser",
            appLabel = "Browser",
            timestamp = 5_000L,
            queryType = "AAAA"
        )
        val wrongType = matching.copy(id = 11, queryType = "A")
        val wrongApp = matching.copy(id = 12, appPackage = "com.other", appLabel = "Other")
        val wrongTime = matching.copy(id = 13, timestamp = 500L)

        val result = EvidenceJsonlExporter.buildJsonl(
            dnsLogs = listOf(matching, wrongType, wrongApp, wrongTime),
            connectionLogs = emptyList(),
            options = EvidenceJsonlExportOptions(
                dataset = EvidenceJsonlDataset.DNS,
                sinceMs = 1_000L,
                untilMs = 6_000L,
                query = "ads",
                appFilter = "browser",
                queryTypes = setOf("AAAA"),
                redactDomains = false,
                redactApps = false,
                redactIps = false
            )
        )

        val lines = result.content.trim().lines()
        assertEquals(2, lines.size)
        assertEquals(1, result.rowCount)
        val row = JSONObject(lines[1])
        assertEquals("dns", row.getString("row_type"))
        assertEquals("ads.example.com", row.getString("hostname"))
        assertEquals("com.example.browser", row.getString("app_package"))
        assertEquals("AAAA", row.getString("query_type"))
    }

    @Test
    fun `buildJsonl handles blank malformed rows with stable schema fields`() {
        val blankDns = DnsLogEntry(
            id = 20,
            hostname = "",
            blocked = false,
            timestamp = 2_000L,
            queryType = ""
        )
        val malformedConnection = ConnectionLogEntry(
            id = 21,
            uid = -1,
            packageName = "",
            appLabel = "",
            destination = "not a normal endpoint",
            port = 0,
            protocol = "",
            action = "ALLOW",
            timestamp = 1_000L
        )

        val result = EvidenceJsonlExporter.buildJsonl(
            dnsLogs = listOf(blankDns),
            connectionLogs = listOf(malformedConnection),
            options = EvidenceJsonlExportOptions(
                dataset = EvidenceJsonlDataset.ALL,
                redactDomains = true,
                redactApps = true,
                redactIps = true
            )
        )

        val lines = result.content.trim().lines()
        assertEquals(3, lines.size)
        val dnsRow = JSONObject(lines[1])
        val connectionRow = JSONObject(lines[2])
        assertEquals(1, dnsRow.getInt("schema_version"))
        assertEquals("", dnsRow.getString("hostname"))
        assertEquals("", dnsRow.getString("query_type"))
        assertEquals(1, connectionRow.getInt("schema_version"))
        assertEquals("redacted-domain-", connectionRow.getString("destination").take("redacted-domain-".length))
        assertEquals("ALLOW", connectionRow.getString("action"))
    }
}
