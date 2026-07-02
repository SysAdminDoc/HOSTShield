package com.hostshield.util

import android.content.Context
import android.util.Log
import com.hostshield.data.database.ConnectionLogDao
import com.hostshield.data.database.DnsLogDao
import com.hostshield.data.model.ConnectionLogEntry
import com.hostshield.data.model.DnsLogEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.ceil

enum class EvidenceJsonlDataset {
    DNS,
    CONNECTIONS,
    ALL
}

data class EvidenceJsonlExportOptions(
    val dataset: EvidenceJsonlDataset = EvidenceJsonlDataset.ALL,
    val sinceMs: Long = 0L,
    val untilMs: Long = Long.MAX_VALUE,
    val query: String = "",
    val appFilter: String = "",
    val queryTypes: Set<String> = emptySet(),
    val includeBlocked: Boolean = true,
    val includeAllowed: Boolean = true,
    val redactDomains: Boolean = true,
    val redactApps: Boolean = true,
    val redactIps: Boolean = true,
    val maxRows: Int = EvidenceJsonlExporter.DEFAULT_MAX_ROWS,
    val chunkRows: Int = EvidenceJsonlExporter.DEFAULT_CHUNK_ROWS
)

data class EvidenceJsonlBuildResult(
    val content: String,
    val rowCount: Int,
    val chunkCount: Int,
    val truncated: Boolean
)

data class EvidenceJsonlFileResult(
    val file: File,
    val rowCount: Int,
    val chunkCount: Int,
    val truncated: Boolean
)

@Singleton
class EvidenceJsonlExporter @Inject constructor(
    private val dnsLogDao: DnsLogDao,
    private val connectionLogDao: ConnectionLogDao
) {
    suspend fun export(
        context: Context,
        options: EvidenceJsonlExportOptions = EvidenceJsonlExportOptions()
    ): EvidenceJsonlFileResult? = withContext(Dispatchers.IO) {
        val boundedMaxRows = options.maxRows.coerceIn(1, HARD_MAX_ROWS)
        val loadLimit = (boundedMaxRows + 1).coerceAtMost(HARD_MAX_ROWS + 1)
        val dnsLogs = if (options.dataset != EvidenceJsonlDataset.CONNECTIONS) {
            dnsLogDao.getLogsForEvidenceExport(options.sinceMs, options.untilMs, loadLimit)
        } else {
            emptyList()
        }
        val connectionLogs = if (options.dataset != EvidenceJsonlDataset.DNS) {
            connectionLogDao.getLogsForEvidenceExport(options.sinceMs, options.untilMs, loadLimit)
        } else {
            emptyList()
        }
        val result = buildJsonl(dnsLogs, connectionLogs, options.copy(maxRows = boundedMaxRows))
        if (result.rowCount == 0) return@withContext null

        val file = File(context.cacheDir, "hostshield_evidence_${System.currentTimeMillis()}.jsonl")
        file.writeText(result.content, Charsets.UTF_8)
        Log.i(TAG, "Evidence JSONL export: ${file.absolutePath} (${file.length()} bytes)")
        EvidenceJsonlFileResult(
            file = file,
            rowCount = result.rowCount,
            chunkCount = result.chunkCount,
            truncated = result.truncated
        )
    }

    companion object {
        const val SCHEMA_VERSION = 1
        const val SCHEMA_NAME = "hostshield.evidence_jsonl"
        const val DEFAULT_MAX_ROWS = 5_000
        const val DEFAULT_CHUNK_ROWS = 1_000
        private const val HARD_MAX_ROWS = 20_000
        private const val TAG = "EvidenceJsonlExport"

        fun buildJsonl(
            dnsLogs: List<DnsLogEntry>,
            connectionLogs: List<ConnectionLogEntry>,
            options: EvidenceJsonlExportOptions = EvidenceJsonlExportOptions()
        ): EvidenceJsonlBuildResult {
            val boundedMaxRows = options.maxRows.coerceIn(1, HARD_MAX_ROWS)
            val boundedChunkRows = options.chunkRows.coerceIn(1, boundedMaxRows)
            val filteredRows = buildList {
                if (options.dataset != EvidenceJsonlDataset.CONNECTIONS) {
                    dnsLogs.asSequence()
                        .filter { it.matches(options) }
                        .forEach { add(EvidenceRow.Dns(it)) }
                }
                if (options.dataset != EvidenceJsonlDataset.DNS) {
                    connectionLogs.asSequence()
                        .filter { it.matches(options) }
                        .forEach { add(EvidenceRow.Connection(it)) }
                }
            }.sortedByDescending { it.timestampMs }

            val truncated = filteredRows.size > boundedMaxRows
            val rows = filteredRows.take(boundedMaxRows)
            val chunkCount = if (rows.isEmpty()) {
                0
            } else {
                ceil(rows.size.toDouble() / boundedChunkRows.toDouble()).toInt()
            }

            val sb = StringBuilder(rows.size * 256 + 512)
            sb.appendLine(metadataJson(options, rows.size, chunkCount, truncated))
            rows.forEachIndexed { index, row ->
                val chunkIndex = index / boundedChunkRows
                val chunkRowIndex = index % boundedChunkRows
                sb.appendLine(row.toJson(index, chunkIndex, chunkRowIndex, options))
            }
            return EvidenceJsonlBuildResult(
                content = sb.toString(),
                rowCount = rows.size,
                chunkCount = chunkCount,
                truncated = truncated
            )
        }

        private fun metadataJson(
            options: EvidenceJsonlExportOptions,
            rowCount: Int,
            chunkCount: Int,
            truncated: Boolean
        ): String = JSONObject()
            .put("schema", SCHEMA_NAME)
            .put("schema_version", SCHEMA_VERSION)
            .put("row_type", "metadata")
            .put("created_at_ms", System.currentTimeMillis())
            .put("dataset", options.dataset.name.lowercase())
            .put("row_count", rowCount)
            .put("chunk_count", chunkCount)
            .put("truncated", truncated)
            .put("max_rows", options.maxRows)
            .put("chunk_rows", options.chunkRows)
            .put(
                "filters",
                JSONObject()
                    .put("since_ms", options.sinceMs)
                    .put("until_ms", options.untilMs)
                    .put("query", options.query.trim())
                    .put("app", options.appFilter.trim())
                    .put("query_types", options.queryTypes.sorted().joinToString(","))
                    .put("include_blocked", options.includeBlocked)
                    .put("include_allowed", options.includeAllowed)
            )
            .put(
                "redaction",
                JSONObject()
                    .put("domains", options.redactDomains)
                    .put("apps", options.redactApps)
                    .put("ips", options.redactIps)
            )
            .toString()

        private fun DnsLogEntry.matches(options: EvidenceJsonlExportOptions): Boolean {
            if (timestamp < options.sinceMs || timestamp > options.untilMs) return false
            if (blocked && !options.includeBlocked) return false
            if (!blocked && !options.includeAllowed) return false
            if (options.queryTypes.isNotEmpty() && queryType.uppercase() !in options.queryTypes.map { it.uppercase() }) {
                return false
            }
            val query = options.query.trim()
            if (query.isNotEmpty() && !listOf(
                    hostname,
                    appPackage,
                    appLabel,
                    queryType,
                    upstreamServer,
                    decisionReason,
                    decisionSource,
                    matchedValue
                ).any { it.contains(query, ignoreCase = true) }
            ) {
                return false
            }
            val app = options.appFilter.trim()
            if (app.isNotEmpty() && !appPackage.contains(app, ignoreCase = true) && !appLabel.contains(app, ignoreCase = true)) {
                return false
            }
            return true
        }

        private fun ConnectionLogEntry.matches(options: EvidenceJsonlExportOptions): Boolean {
            if (timestamp < options.sinceMs || timestamp > options.untilMs) return false
            val rejected = action.equals("REJECT", ignoreCase = true) || action.equals("BLOCK", ignoreCase = true)
            if (rejected && !options.includeBlocked) return false
            if (!rejected && !options.includeAllowed) return false
            val query = options.query.trim()
            if (query.isNotEmpty() && !listOf(
                    destination,
                    packageName,
                    appLabel,
                    protocol,
                    action,
                    interfaceName,
                    port.toString()
                ).any { it.contains(query, ignoreCase = true) }
            ) {
                return false
            }
            val app = options.appFilter.trim()
            if (app.isNotEmpty() && !packageName.contains(app, ignoreCase = true) && !appLabel.contains(app, ignoreCase = true)) {
                return false
            }
            return true
        }

        private sealed interface EvidenceRow {
            val timestampMs: Long

            data class Dns(val entry: DnsLogEntry) : EvidenceRow {
                override val timestampMs: Long = entry.timestamp
            }

            data class Connection(val entry: ConnectionLogEntry) : EvidenceRow {
                override val timestampMs: Long = entry.timestamp
            }
        }

        private fun EvidenceRow.toJson(
            rowIndex: Int,
            chunkIndex: Int,
            chunkRowIndex: Int,
            options: EvidenceJsonlExportOptions
        ): String {
            val base = JSONObject()
                .put("schema", SCHEMA_NAME)
                .put("schema_version", SCHEMA_VERSION)
                .put("row_index", rowIndex)
                .put("chunk_index", chunkIndex)
                .put("chunk_row_index", chunkRowIndex)
                .put("timestamp_ms", timestampMs)
            return when (this) {
                is EvidenceRow.Dns -> entry.toJson(base, options)
                is EvidenceRow.Connection -> entry.toJson(base, options)
            }.toString()
        }

        private fun DnsLogEntry.toJson(base: JSONObject, options: EvidenceJsonlExportOptions): JSONObject =
            base
                .put("row_type", "dns")
                .put("id", id)
                .put("hostname", hostname.redact("domain", options.redactDomains))
                .put("blocked", blocked)
                .put("app_package", appPackage.redact("app", options.redactApps))
                .put("app_label", appLabel.redact("app", options.redactApps))
                .put("source_ip", sourceIp.redact("ip", options.redactIps))
                .put("query_type", queryType)
                .put("response_time_ms", responseTimeMs)
                .put("upstream_server", upstreamServer.redactEndpoint(options))
                .put("cname_chain", cnameChain.redactList("domain", options.redactDomains))
                .put("resolved_ips", resolvedIps.redactList("ip", options.redactIps))
                .put("tracker_category", trackerCategory)
                .put("tracker_owner", trackerOwner.redact("app", options.redactApps))
                .put("decision_reason", decisionReason)
                .put("decision_source", decisionSource)
                .put("matched_value", matchedValue.redactEndpoint(options))
                .put("decision_precedence", decisionPrecedence)

        private fun ConnectionLogEntry.toJson(base: JSONObject, options: EvidenceJsonlExportOptions): JSONObject =
            base
                .put("row_type", "connection")
                .put("id", id)
                .put("uid", uid)
                .put("package_name", packageName.redact("app", options.redactApps))
                .put("app_label", appLabel.redact("app", options.redactApps))
                .put("destination", destination.redactEndpoint(options))
                .put("port", port)
                .put("protocol", protocol)
                .put("action", action)
                .put("interface_name", interfaceName)

        private fun String.redactEndpoint(options: EvidenceJsonlExportOptions): String {
            if (isBlank()) return ""
            val kind = if (looksLikeIpList()) "ip" else "domain"
            val enabled = if (kind == "ip") options.redactIps else options.redactDomains
            return redact(kind, enabled)
        }

        private fun String.redactList(kind: String, enabled: Boolean): String {
            if (isBlank()) return ""
            return split(',')
                .map { it.trim().redact(kind, enabled) }
                .joinToString(",")
        }

        private fun String.redact(kind: String, enabled: Boolean): String {
            val value = trim()
            if (value.isBlank()) return ""
            if (!enabled) return value
            return "redacted-$kind-${sha256(value).take(12)}"
        }

        private fun String.looksLikeIpList(): Boolean =
            split(',').map { it.trim() }.filter { it.isNotBlank() }.all { item ->
                item.all { ch -> ch.isDigit() || ch == '.' || ch == ':' || ch in 'a'..'f' || ch in 'A'..'F' }
            }

        private fun sha256(value: String): String {
            val digest = MessageDigest.getInstance("SHA-256")
                .digest(value.lowercase().toByteArray(Charsets.UTF_8))
            return digest.joinToString("") { "%02x".format(it) }
        }
    }
}
