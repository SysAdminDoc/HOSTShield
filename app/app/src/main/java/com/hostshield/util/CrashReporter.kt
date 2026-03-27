package com.hostshield.util

import android.content.Context
import android.os.Build
import dagger.hilt.android.qualifiers.ApplicationContext
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * CrashReport — immutable snapshot of a single crash event.
 */
data class CrashReport(
    val timestamp: Long,
    val stackTrace: String,
    val deviceModel: String,
    val sdkVersion: Int,
    val appVersion: String,
    val freeMemoryMb: Long,
    val totalMemoryMb: Long,
)

/**
 * ACRA-style open-source crash reporter (Roadmap #39).
 *
 * Captures uncaught exceptions, serialises device/memory diagnostics to JSON,
 * and persists them under `filesDir/crashes/`.  At most [MAX_REPORTS] are kept;
 * the oldest are pruned automatically.
 *
 * All I/O runs on the calling thread — callers should dispatch to
 * [kotlinx.coroutines.Dispatchers.IO] when reading or clearing reports.
 */
@Singleton
class CrashReporter @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    companion object {
        private const val CRASH_DIR = "crashes"
        private const val MAX_REPORTS = 20
        private const val FILE_EXT = ".json"
    }

    private val crashDir: File
        get() = File(context.filesDir, CRASH_DIR).also { it.mkdirs() }

    private val lock = Any()

    // ── public API ──────────────────────────────────────────────

    /**
     * Installs this reporter as the default [Thread.UncaughtExceptionHandler].
     * The previous handler is chained so the process still terminates normally.
     */
    fun install() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            writeCrashReport(throwable)
            previous?.uncaughtException(thread, throwable)
        }
    }

    /**
     * Returns every persisted [CrashReport], newest first.
     */
    fun getCrashReports(): List<CrashReport> = synchronized(lock) {
        listReportFiles()
            .sortedByDescending { it.nameWithoutExtension.toLongOrNull() ?: 0L }
            .mapNotNull { parseReport(it) }
    }

    /**
     * Returns the most recent [CrashReport], or `null` if none exist.
     */
    fun getLatestCrashReport(): CrashReport? = synchronized(lock) {
        listReportFiles()
            .maxByOrNull { it.nameWithoutExtension.toLongOrNull() ?: 0L }
            ?.let { parseReport(it) }
    }

    /**
     * Deletes every stored crash report.
     */
    fun clearCrashReports(): Unit = synchronized(lock) {
        listReportFiles().forEach { it.delete() }
    }

    // ── internals ───────────────────────────────────────────────

    private fun writeCrashReport(throwable: Throwable) {
        try {
            synchronized(lock) {
                val runtime = Runtime.getRuntime()
                val timestamp = System.currentTimeMillis()

                val json = JSONObject().apply {
                    put("timestamp", timestamp)
                    put("stackTrace", throwable.stackTraceToString())
                    put("deviceModel", Build.MODEL)
                    put("manufacturer", Build.MANUFACTURER)
                    put("sdkVersion", Build.VERSION.SDK_INT)
                    put("appVersion", appVersion())
                    put("freeMemoryMb", runtime.freeMemory() / (1024L * 1024L))
                    put("totalMemoryMb", runtime.totalMemory() / (1024L * 1024L))
                }

                val file = File(crashDir, "$timestamp$FILE_EXT")
                file.writeText(json.toString(2))

                pruneOldReports()
            }
        } catch (_: Throwable) {
            // Never let reporting itself crash the crash handler.
        }
    }

    private fun pruneOldReports() {
        val files = listReportFiles()
            .sortedByDescending { it.nameWithoutExtension.toLongOrNull() ?: 0L }
        if (files.size > MAX_REPORTS) {
            files.drop(MAX_REPORTS).forEach { it.delete() }
        }
    }

    private fun listReportFiles(): List<File> =
        crashDir.listFiles { f -> f.extension == "json" }?.toList() ?: emptyList()

    private fun parseReport(file: File): CrashReport? = try {
        val json = JSONObject(file.readText())
        CrashReport(
            timestamp = json.getLong("timestamp"),
            stackTrace = json.getString("stackTrace"),
            deviceModel = json.getString("deviceModel"),
            sdkVersion = json.getInt("sdkVersion"),
            appVersion = json.optString("appVersion", "unknown"),
            freeMemoryMb = json.getLong("freeMemoryMb"),
            totalMemoryMb = json.getLong("totalMemoryMb"),
        )
    } catch (_: Exception) {
        null
    }

    private fun appVersion(): String = try {
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        info.versionName ?: "unknown"
    } catch (_: Exception) {
        "unknown"
    }
}
