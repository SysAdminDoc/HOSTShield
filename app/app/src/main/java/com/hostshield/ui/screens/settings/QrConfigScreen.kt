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
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import com.hostshield.data.preferences.AppPreferences
import com.hostshield.util.QrConfigSharing
import com.hostshield.util.RuleEntry
import com.hostshield.util.ShareableConfig
import com.hostshield.ui.screens.home.GlassCard
import com.hostshield.ui.theme.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class QrConfigViewModel @Inject constructor(
    private val prefs: AppPreferences,
    private val qrSharing: QrConfigSharing,
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

    fun generateQr() {
        if (isGenerating) return
        viewModelScope.launch {
            isGenerating = true
            val config = withContext(Dispatchers.IO) { buildCurrentConfig() }
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
            isGenerating = false
        }
    }

    fun importFromString(input: String) {
        viewModelScope.launch {
            val config = qrSharing.decodeConfig(input.trim())
            if (config == null) {
                importResult = "Invalid QR data — must start with HS:"
                return@launch
            }
            importResult = "Decoded: ${config.userRules.size} rules, ${config.sourceUrls.size} sources, " +
                "DNS=${config.customDns.ifEmpty { "default" }}, DoH=${config.dohEnabled}"
        }
    }

    fun clearImportResult() { importResult = null }

    private suspend fun buildCurrentConfig(): ShareableConfig {
        val customDns = prefs.customUpstreamDns.first()
        val dohEnabled = prefs.dohEnabled.first()
        val dohProvider = prefs.dohProvider.first()
        // Get user rules from repository (simplified — uses custom DNS as proxy)
        return ShareableConfig(
            version = 1,
            customDns = customDns,
            dohEnabled = dohEnabled,
            dohProvider = dohProvider,
            profileName = "HostShield Config",
        )
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
                Icon(Icons.Filled.ArrowBack, null, tint = TextPrimary)
            }
            Text("QR Config Sharing", style = MaterialTheme.typography.headlineMedium, color = TextPrimary)
        }

        Text(
            "Share your HostShield configuration via QR code. The recipient can scan it to import your DNS settings, custom rules, and sources.",
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
                    Text("Export Configuration", color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
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
                        Text("Generating...", fontWeight = FontWeight.SemiBold)
                    } else {
                        Icon(Icons.Filled.QrCode, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Generate QR Code", fontWeight = FontWeight.SemiBold)
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
                            contentDescription = "QR Code",
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
                    Text("Encoded String", color = TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
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
                    Text("Import Configuration", color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    "Paste a HostShield config string (HS:...) to preview before importing.",
                    color = TextDim, fontSize = 11.sp,
                )
                Spacer(Modifier.height(8.dp))

                OutlinedTextField(
                    value = importInput,
                    onValueChange = { importInput = it },
                    placeholder = { Text("HS:...", color = TextDim, fontSize = 12.sp) },
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
                    enabled = importInput.isNotBlank(),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Blue),
                ) {
                    Icon(Icons.Filled.Download, null, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Decode & Preview", fontSize = 12.sp)
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
                        Icon(Icons.Filled.Close, null, tint = TextDim, modifier = Modifier.size(14.dp))
                    }
                }
            }
        }

        Spacer(Modifier.height(24.dp))
    }
}
