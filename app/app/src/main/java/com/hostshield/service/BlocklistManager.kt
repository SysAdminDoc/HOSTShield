package com.hostshield.service

import android.util.Log
import com.hostshield.util.DiagnosticEventType

/** Serializes the VPN's blocklist rebuild side effects behind one boundary. */
internal class BlocklistManager(
    private val sourceCoordinator: BlocklistSourceCoordinator,
    private val sourceFailureNotifier: SourceFailureNotifier,
    private val recordEvent: (DiagnosticEventType, String, Map<String, Any?>) -> Unit,
) {
    suspend fun rebuild() {
        try {
            val rebuild = sourceCoordinator.rebuildBlocklistHolder()
            val failedSources = rebuild.snapshot.failedSources
            failedSources.forEach { notice ->
                recordEvent(
                    DiagnosticEventType.SOURCE_DOWNLOAD_FAILED,
                    "Source download failed during VPN blocklist rebuild",
                    mapOf(
                        "source" to notice.url,
                        "error" to notice.error,
                        "http_status" to notice.httpStatus,
                        "failures" to notice.consecutiveFailures,
                    ),
                )
            }
            sourceFailureNotifier.notifyFailures(failedSources)
            recordEvent(
                DiagnosticEventType.BLOCKLIST_SWAP,
                "Blocklist snapshot swapped",
                mapOf(
                    "domains" to rebuild.domainCount,
                    "source" to "vpn_rebuild",
                    "downloaded_sources" to rebuild.snapshot.downloadedSourceCount,
                ),
            )
        } catch (e: Exception) {
            Log.w("HostShield", "Blocklist rebuild failed: ${e.message}")
        }
    }
}
