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
import java.security.SecureRandom
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
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

        val file = prepareExportFile(context.cacheDir, System.currentTimeMillis())
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
        private const val EXPORT_DIR_NAME = "exports"
        private const val EXPORT_FILE_PREFIX = "hostshield_evidence_"
        private const val EXPORT_MAX_AGE_MS = 24L * 60L * 60L * 1000L

        /**
         * Resolve the export file inside the dedicated exports/ cache subdirectory
         * (FileProvider "exports" cache-path) and sweep stale evidence files left
         * behind by earlier exports — including legacy cache-root placement.
         */
        internal fun prepareExportFile(cacheDir: File, nowMs: Long): File {
            val exportDir = File(cacheDir, EXPORT_DIR_NAME).apply { mkdirs() }
            val cutoff = nowMs - EXPORT_MAX_AGE_MS
            sequenceOf(cacheDir, exportDir)
                .flatMap { dir -> dir.listFiles()?.asSequence().orEmpty() }
                .filter { it.isFile && it.name.startsWith(EXPORT_FILE_PREFIX) && it.lastModified() < cutoff }
                .forEach { it.delete() }
            return File(exportDir, "$EXPORT_FILE_PREFIX$nowMs.jsonl")
        }

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

            val redaction = ExportRedaction()
            val sb = StringBuilder(rows.size * 256 + 512)
            sb.appendLine(metadataJson(options, redaction, rows.size, chunkCount, truncated))
            rows.forEachIndexed { index, row ->
                val chunkIndex = index / boundedChunkRows
                val chunkRowIndex = index % boundedChunkRows
                sb.appendLine(row.toJson(index, chunkIndex, chunkRowIndex, options, redaction))
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
            redaction: ExportRedaction,
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
                    // Filter text can contain the same identifiers as redacted rows
                    .put(
                        "query",
                        options.query.trim().redact(
                            "query",
                            options.redactDomains || options.redactApps || options.redactIps,
                            redaction
                        )
                    )
                    .put("app", options.appFilter.trim().redact("app", options.redactApps, redaction))
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
            options: EvidenceJsonlExportOptions,
            redaction: ExportRedaction
        ): String {
            val base = JSONObject()
                .put("schema", SCHEMA_NAME)
                .put("schema_version", SCHEMA_VERSION)
                .put("row_index", rowIndex)
                .put("chunk_index", chunkIndex)
                .put("chunk_row_index", chunkRowIndex)
                .put("timestamp_ms", timestampMs)
            return when (this) {
                is EvidenceRow.Dns -> entry.toJson(base, options, redaction)
                is EvidenceRow.Connection -> entry.toJson(base, options, redaction)
            }.toString()
        }

        private fun DnsLogEntry.toJson(
            base: JSONObject,
            options: EvidenceJsonlExportOptions,
            redaction: ExportRedaction
        ): JSONObject =
            base
                .put("row_type", "dns")
                .put("id", id)
                .put("hostname", hostname.redact("domain", options.redactDomains, redaction))
                .put("blocked", blocked)
                .put("app_package", appPackage.redact("app", options.redactApps, redaction))
                .put("app_label", appLabel.redact("app", options.redactApps, redaction))
                .put("source_ip", sourceIp.redact("ip", options.redactIps, redaction))
                .put("query_type", queryType)
                .put("response_time_ms", responseTimeMs)
                .put("upstream_server", upstreamServer.redactEndpoint(options, redaction))
                .put("cname_chain", cnameChain.redactList("domain", options.redactDomains, redaction))
                .put("resolved_ips", resolvedIps.redactList("ip", options.redactIps, redaction))
                .put("tracker_category", trackerCategory)
                .put("tracker_owner", trackerOwner.redact("app", options.redactApps, redaction))
                .put("decision_reason", decisionReason)
                .put("decision_source", decisionSource)
                .put("matched_value", matchedValue.redactEndpoint(options, redaction))
                .put("decision_precedence", decisionPrecedence)

        private fun ConnectionLogEntry.toJson(
            base: JSONObject,
            options: EvidenceJsonlExportOptions,
            redaction: ExportRedaction
        ): JSONObject =
            base
                .put("row_type", "connection")
                .put("id", id)
                .put("uid", uid)
                .put("package_name", packageName.redact("app", options.redactApps, redaction))
                .put("app_label", appLabel.redact("app", options.redactApps, redaction))
                .put("destination", destination.redactEndpoint(options, redaction))
                .put("port", port)
                .put("protocol", protocol)
                .put("action", action)
                .put("interface_name", interfaceName)

        private fun String.redactEndpoint(
            options: EvidenceJsonlExportOptions,
            redaction: ExportRedaction
        ): String {
            if (isBlank()) return ""
            val kind = if (looksLikeIpList()) "ip" else "domain"
            val enabled = if (kind == "ip") options.redactIps else options.redactDomains
            return redact(kind, enabled, redaction)
        }

        private fun String.redactList(kind: String, enabled: Boolean, redaction: ExportRedaction): String {
            if (isBlank()) return ""
            return split(',')
                .map { it.trim().redact(kind, enabled, redaction) }
                .joinToString(",")
        }

        private fun String.redact(kind: String, enabled: Boolean, redaction: ExportRedaction): String {
            val value = trim()
            if (value.isBlank()) return ""
            if (!enabled) return value
            return "redacted-$kind-${redaction.tag(value)}"
        }

        private fun String.looksLikeIpList(): Boolean =
            split(',').map { it.trim() }.filter { it.isNotBlank() }.all { item ->
                item.all { ch -> ch.isDigit() || ch == '.' || ch == ':' || ch in 'a'..'f' || ch in 'A'..'F' }
            }

        /**
         * Per-export salted redaction. A random in-memory salt keys the hash so
         * tokens stay stable within one export (correlation survives) but cannot
         * be reversed offline by hashing candidate dictionaries. The salt is
         * never written to the export.
         */
        private class ExportRedaction {
            private val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }

            fun tag(value: String): String {
                val mac = Mac.getInstance("HmacSHA256")
                mac.init(SecretKeySpec(salt, "HmacSHA256"))
                return mac.doFinal(value.lowercase().toByteArray(Charsets.UTF_8))
                    .joinToString("") { "%02x".format(it) }
                    .take(12)
            }
        }
    }
}
