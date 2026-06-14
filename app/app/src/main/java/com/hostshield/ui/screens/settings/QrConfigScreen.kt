package com.hostshield.ui.screens.settings

import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import com.hostshield.R
import com.hostshield.util.QrConfigImporter
import com.hostshield.util.QrImportPlan
import com.hostshield.util.QrConfigSharing
import com.hostshield.ui.screens.home.GlassCard
import com.hostshield.ui.theme.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

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

                val ruleCount = config.userRules.size
                val sourceCount = config.sourceUrls.size
                configSummary = buildString {
                    append("$ruleCount rules, $sourceCount sources")
                    if (config.dohEnabled) append(", DoH: ${config.dohProvider}")
                    if (config.customDns.isNotEmpty()) append(", DNS: ${config.customDns}")
                    append(" (${encoded.length} bytes)")
                }

                qrBitmap = withContext(Dispatchers.Default) { renderQr(encoded) }
            } catch (e: Exception) {
                importResult = "QR export failed: ${e.message ?: e.javaClass.simpleName}"
            } finally {
                isGenerating = false
            }
        }
    }

    fun importFromString(input: String) {
        if (isApplyingImport) return
        viewModelScope.launch {
            val config = qrSharing.decodeConfig(input.trim())
            if (config == null) {
                importResult = "Invalid QR data — must start with HS:"
                pendingImportPlan = null
                return@launch
            }
            val plan = withContext(Dispatchers.IO) { importer.preview(config) }
            pendingImportPlan = if (plan.hasChanges) plan else null
            importResult = importPreviewMessage(plan)
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
            } catch (e: Exception) {
                importResult = "Import failed: ${e.message ?: e.javaClass.simpleName}"
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
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            for (x in 0 until width) {
                for (y in 0 until height) {
                    bitmap.setPixel(x, y, if (matrix.get(x, y)) AndroidColor.BLACK else AndroidColor.WHITE)
                }
            }
            bitmap
        } catch (e: Exception) {
            null
        }
    }
}

@Composable
fun QrConfigScreen(
    onBack: () -> Unit,
    viewModel: QrConfigViewModel = hiltViewModel(),
) {
    var importInput by remember { mutableStateOf("") }
    val hasPendingImport = viewModel.pendingImportPlan != null

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back), tint = TextPrimary)
            }
            Text(stringResource(R.string.qr_screen_title), style = MaterialTheme.typography.headlineMedium, color = TextPrimary)
        }

        Text(
            stringResource(R.string.qr_screen_description),
            color = TextDim, fontSize = 12.sp, lineHeight = 16.sp,
            modifier = Modifier.padding(horizontal = 4.dp),
        )

        // Generate QR section
        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.QrCode2, null, tint = Teal, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.qr_export_configuration), color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                }
                Spacer(Modifier.height(12.dp))

                Button(
                    onClick = { viewModel.generateQr() },
                    enabled = !viewModel.isGenerating,
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Teal, contentColor = Color.Black),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (viewModel.isGenerating) {
                        CircularProgressIndicator(Modifier.size(16.dp), color = Color.Black, strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.qr_generating), fontWeight = FontWeight.SemiBold)
                    } else {
                        Icon(Icons.Filled.QrCode, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.qr_generate_code), fontWeight = FontWeight.SemiBold)
                    }
                }

                // QR code display
                viewModel.qrBitmap?.let { bitmap ->
                    Spacer(Modifier.height(16.dp))
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color.White,
                        modifier = Modifier.size(260.dp),
                    ) {
                        Image(
                            bitmap = bitmap.asImageBitmap(),
                            contentDescription = stringResource(R.string.qr_code_content_description),
                            modifier = Modifier.padding(16.dp),
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        viewModel.configSummary,
                        color = TextDim, fontSize = 11.sp,
                    )
                }
            }
        }

        // Raw string display
        if (viewModel.encodedString.isNotEmpty()) {
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(stringResource(R.string.qr_encoded_string), color = TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(6.dp))
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Surface0,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            viewModel.encodedString.take(200) +
                                if (viewModel.encodedString.length > 200) "..." else "",
                            color = Teal.copy(alpha = 0.8f),
                            fontSize = 9.sp, lineHeight = 13.sp,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.padding(10.dp),
                        )
                    }
                }
            }
        }

        // Import section
        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.QrCodeScanner, null, tint = Blue, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.qr_import_configuration), color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    stringResource(R.string.qr_import_hint),
                    color = TextDim, fontSize = 11.sp,
                )
                Spacer(Modifier.height(8.dp))

                OutlinedTextField(
                    value = importInput,
                    onValueChange = {
                        importInput = it
                        viewModel.clearPendingImport()
                    },
                    placeholder = { Text(stringResource(R.string.qr_placeholder), color = TextDim, fontSize = 12.sp) },
                    singleLine = false,
                    maxLines = 4,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Blue, unfocusedBorderColor = Surface3,
                        cursorColor = Blue, focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary,
                    ),
                )
                Spacer(Modifier.height(8.dp))

                OutlinedButton(
                    onClick = { viewModel.importFromString(importInput) },
                    enabled = importInput.isNotBlank() && !viewModel.isApplyingImport,
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Blue),
                ) {
                    Icon(Icons.Filled.Download, null, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(stringResource(R.string.qr_decode_preview), fontSize = 12.sp)
                }

                if (hasPendingImport) {
                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = { viewModel.applyPendingImport() },
                        enabled = !viewModel.isApplyingImport,
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Blue, contentColor = Color.Black),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        if (viewModel.isApplyingImport) {
                            CircularProgressIndicator(Modifier.size(14.dp), color = Color.Black, strokeWidth = 2.dp)
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.qr_importing), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        } else {
                            Icon(Icons.Filled.Check, null, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.qr_apply_import), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }

        // Import result
        viewModel.importResult?.let { msg ->
            val isError = msg.contains("Invalid")
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = if (isError) Red.copy(alpha = 0.08f) else Teal.copy(alpha = 0.08f),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        if (isError) Icons.Filled.Error else Icons.Filled.CheckCircle,
                        null, tint = if (isError) Red else Teal, modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(msg, color = TextSecondary, fontSize = 12.sp, modifier = Modifier.weight(1f))
                    IconButton(onClick = { viewModel.clearImportResult() }, modifier = Modifier.size(24.dp)) {
                        Icon(Icons.Filled.Close, stringResource(R.string.qr_dismiss_import_message), tint = TextDim, modifier = Modifier.size(14.dp))
                    }
                }
            }
        }

        Spacer(Modifier.height(24.dp))
    }
}
