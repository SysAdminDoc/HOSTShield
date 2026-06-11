package com.hostshield.util

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

enum class DiagnosticEventType(val wireName: String) {
    VPN_START("vpn_start"),
    VPN_STOP("vpn_stop"),
    TUN_FD_INVALID("tun_fd_invalid"),
    PRIVATE_DNS_CONFLICT("private_dns_conflict"),
    BLOCKLIST_SWAP("blocklist_swap"),
    SOURCE_DOWNLOAD_FAILED("source_download_failed"),
    CERT_PIN_FAILURE("cert_pin_failure"),
    RESOLVER_FAILOVER("resolver_failover"),
    DOZE_RESUME("doze_resume"),
    ROOT_COMMAND_FAILED("root_command_failed"),
    BACKUP_IMPORT_FAILED("backup_import_failed"),
    FOREGROUND_SERVICE_TIMEOUT("foreground_service_timeout"),
    FOREGROUND_SERVICE_START_FAILED("foreground_service_start_failed")
}

data class DiagnosticEventSummary(
    val timestampMs: Long,
    val type: String,
    val message: String,
    val fields: Map<String, String>
)

@Singleton
class DiagnosticEventStore @Inject constructor(
    @param:ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "DiagnosticEvents"
        private const val MAX_EVENTS = 500
        private const val MAX_MESSAGE_LENGTH = 500
        private const val MAX_FIELD_LENGTH = 500
        private const val EVENT_DIR = "diagnostics"
        private const val EVENT_FILE = "diagnostic-events.jsonl"
    }

    private val lock = Any()
    private val eventFile: File
        get() = File(File(context.filesDir, EVENT_DIR), EVENT_FILE)

    suspend fun record(
        type: DiagnosticEventType,
        message: String = "",
        fields: Map<String, Any?> = emptyMap()
    ) = withContext(Dispatchers.IO) {
        recordBlocking(type, message, fields)
    }

    fun recordAsync(
        scope: CoroutineScope,
        type: DiagnosticEventType,
        message: String = "",
        fields: Map<String, Any?> = emptyMap()
    ) {
        scope.launch(Dispatchers.IO) {
            record(type, message, fields)
        }
    }

    fun recordBlocking(
        type: DiagnosticEventType,
        message: String = "",
        fields: Map<String, Any?> = emptyMap()
    ) {
        try {
            synchronized(lock) {
                val file = eventFile
                file.parentFile?.mkdirs()
                file.appendText(toJsonLine(type, message, fields) + "\n")
                trimIfNeeded(file)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to persist diagnostic event ${type.wireName}: ${e.message}")
        }
    }

    suspend fun readRecent(limit: Int = 50): List<DiagnosticEventSummary> = withContext(Dispatchers.IO) {
        readRecentBlocking(limit)
    }

    fun readRecentBlocking(limit: Int = 50): List<DiagnosticEventSummary> {
        return try {
            synchronized(lock) {
                val file = eventFile
                if (!file.exists()) return@synchronized emptyList()
                file.readLines()
                    .takeLast(limit.coerceAtLeast(1))
                    .mapNotNull { parseSummary(it) }
                    .asReversed()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to read diagnostic events: ${e.message}")
            emptyList()
        }
    }

    suspend fun readJsonlSnapshot(): String = withContext(Dispatchers.IO) {
        readJsonlSnapshotBlocking()
    }

    fun readJsonlSnapshotBlocking(): String {
        return try {
            synchronized(lock) {
                val file = eventFile
                if (!file.exists()) "" else file.readText()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to snapshot diagnostic events: ${e.message}")
            ""
        }
    }

    private fun toJsonLine(
        type: DiagnosticEventType,
        message: String,
        fields: Map<String, Any?>
    ): String {
        val meta = JSONObject()
        fields.toSortedMap().forEach { (key, value) ->
            meta.put(key, sanitizeField(value))
        }

        return JSONObject()
            .put("timestamp_ms", System.currentTimeMillis())
            .put("type", type.wireName)
            .put("message", message.take(MAX_MESSAGE_LENGTH))
            .put("fields", meta)
            .toString()
    }

    private fun sanitizeField(value: Any?): Any {
        return when (value) {
            null -> JSONObject.NULL
            is Number, is Boolean -> value
            else -> value.toString().take(MAX_FIELD_LENGTH)
        }
    }

    private fun trimIfNeeded(file: File) {
        val lines = file.readLines()
        if (lines.size <= MAX_EVENTS) return
        file.writeText(lines.takeLast(MAX_EVENTS).joinToString(separator = "\n", postfix = "\n"))
    }

    private fun parseSummary(line: String): DiagnosticEventSummary? {
        return try {
            val obj = JSONObject(line)
            val fieldsObj = obj.optJSONObject("fields")
            val fields = buildMap {
                if (fieldsObj != null) {
                    val keys = fieldsObj.keys()
                    while (keys.hasNext()) {
                        val key = keys.next()
                        put(key, fieldsObj.optString(key))
                    }
                }
            }
            DiagnosticEventSummary(
                timestampMs = obj.optLong("timestamp_ms"),
                type = obj.optString("type"),
                message = obj.optString("message"),
                fields = fields
            )
        } catch (_: Exception) {
            null
        }
    }
}
