package com.hostshield.ui.screens.settings

import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import android.util.Log
import androidx.compose.runtime.*
import androidx.core.graphics.createBitmap
import androidx.core.graphics.set
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import com.hostshield.R
import com.hostshield.util.QrConfigImporter
import com.hostshield.util.QrImportPlan
import com.hostshield.util.QrConfigSharing
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

private const val QR_CONFIG_TAG = "QrConfig"

@HiltViewModel
class QrConfigViewModel @Inject constructor(
    private val qrSharing: QrConfigSharing,
    private val importer: QrConfigImporter,
) : ViewModel() {

    var qrBitmap by mutableStateOf<Bitmap?>(null)
        private set
    var encodedString by mutableStateOf("")
        private set
    var configSummary by mutableStateOf("")
        private set
    var isGenerating by mutableStateOf(false)
        private set
    var importResult by mutableStateOf<String?>(null)
        private set
    var importResultIsError by mutableStateOf(false)
        private set
    var pendingImportPlan by mutableStateOf<QrImportPlan?>(null)
        private set
    var isApplyingImport by mutableStateOf(false)
        private set

    fun generateQr() {
        if (isGenerating) return
        viewModelScope.launch {
            isGenerating = true
            try {
                val config = withContext(Dispatchers.IO) { importer.buildCurrentConfig() }
                val encoded = qrSharing.encodeConfig(config)
                encodedString = encoded

                // Summarize what the QR ACTUALLY contains, not the pre-trim
                // config: encodeConfig drops source URLs and truncates rules to
                // fit the QR byte budget, so the input counts can overstate the
                // shared payload.
                val actual = qrSharing.decodeConfig(encoded)
                val ruleCount = actual?.userRules?.size ?: config.userRules.size
                val sourceCount = actual?.sourceUrls?.size ?: config.sourceUrls.size
                val droppedRules = (config.userRules.size - ruleCount).coerceAtLeast(0)
                val droppedSources = (config.sourceUrls.size - sourceCount).coerceAtLeast(0)
                configSummary = buildString {
                    append("$ruleCount rules, $sourceCount sources")
                    if (config.dohEnabled) append(", DoH: ${config.dohProvider}")
                    if (config.customDns.isNotEmpty()) append(", DNS: ${config.customDns}")
                    append(" (${encoded.length} bytes)")
                    if (droppedRules > 0 || droppedSources > 0) {
                        append(" — omitted $droppedRules rules / $droppedSources sources to fit the QR")
                    }
                }

                val bitmap = withContext(Dispatchers.Default) { renderQr(encoded) }
                qrBitmap = bitmap
                if (bitmap == null) {
                    importResult = "Config is too large to render as a QR code. Share the text code instead."
                    importResultIsError = true
                }
            } catch (e: Exception) {
                Log.e(QR_CONFIG_TAG, "QR export failed", e)
                importResult = "QR export failed. Try again after reopening this screen."
                importResultIsError = true
            } finally {
                isGenerating = false
            }
        }
    }

    fun importFromString(input: String) {
        if (isApplyingImport) return
        viewModelScope.launch {
            try {
                pendingImportPlan = null
                val config = qrSharing.decodeConfig(input.trim())
                if (config == null) {
                    importResult = "Invalid QR data - must start with HS:"
                    importResultIsError = true
                    return@launch
                }
                val plan = withContext(Dispatchers.IO) { importer.preview(config) }
                pendingImportPlan = if (plan.hasChanges) plan else null
                importResult = importPreviewMessage(plan)
                importResultIsError = false
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(QR_CONFIG_TAG, "QR import preview failed", e)
                pendingImportPlan = null
                importResult = "Import preview failed. Check the code and try again."
                importResultIsError = true
            }
        }
    }

    fun applyPendingImport() {
        val plan = pendingImportPlan ?: return
        if (isApplyingImport) return
        viewModelScope.launch {
            isApplyingImport = true
            try {
                val result = withContext(Dispatchers.IO) { importer.apply(plan) }
                pendingImportPlan = null
                importResult = "Imported ${result.rulesAdded} rules, ${result.sourcesAdded} sources, " +
                    "${result.settingsUpdated} DNS setting updates"
                importResultIsError = false
            } catch (e: Exception) {
                Log.e(QR_CONFIG_TAG, "QR import failed", e)
                importResult = "Import failed. Review the preview and try again."
                importResultIsError = true
            } finally {
                isApplyingImport = false
            }
        }
    }

    fun clearImportResult() {
        importResult = null
    }

    fun clearPendingImport() {
        pendingImportPlan = null
    }

    private fun importPreviewMessage(plan: QrImportPlan): String {
        val skipped = buildString {
            if (plan.skippedRules > 0) append(", skipped ${plan.skippedRules} rules")
            if (plan.skippedSources > 0) append(", skipped ${plan.skippedSources} sources")
        }
        return if (plan.hasChanges) {
            "Preview: add ${plan.rulesToAdd.size} rules, ${plan.sourcesToAdd.size} HTTPS sources, " +
                "${listOfNotNull(plan.customDns, plan.dohEnabled, plan.dohProvider).size} DNS setting updates$skipped"
        } else {
            "No new importable changes found$skipped"
        }
    }

    private fun renderQr(data: String): Bitmap? {
        return try {
            val writer = QRCodeWriter()
            val matrix = writer.encode(data, BarcodeFormat.QR_CODE, 512, 512)
            val width = matrix.width
            val height = matrix.height
            val bitmap = createBitmap(width, height)
            for (x in 0 until width) {
                for (y in 0 until height) {
                    bitmap[x, y] = if (matrix.get(x, y)) AndroidColor.BLACK else AndroidColor.WHITE
                }
            }
            bitmap
        } catch (e: Exception) {
            null
        }
    }
}
