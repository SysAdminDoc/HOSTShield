package com.hostshield.ui.screens.settings

import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import com.hostshield.R
import com.hostshield.ui.components.HostShieldBackHeader
import com.hostshield.ui.components.HostShieldCompactState
import com.hostshield.ui.components.HostShieldPanelHeader
import com.hostshield.ui.components.HostShieldStatusBanner
import com.hostshield.util.QrConfigImporter
import com.hostshield.util.QrImportPlan
import com.hostshield.util.QrConfigSharing
import com.hostshield.ui.screens.home.GlassCard
import com.hostshield.ui.theme.*
import kotlinx.coroutines.withContext

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
            .background(Black)
            .verticalScroll(rememberScrollState())
            .imePadding()
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        HostShieldBackHeader(
            title = stringResource(R.string.qr_screen_title),
            subtitle = stringResource(R.string.qr_screen_description),
            onBack = onBack,
            horizontalPadding = 0.dp,
            verticalPadding = 0.dp,
        )

        // Generate QR section
        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                HostShieldPanelHeader(
                    icon = Icons.Filled.QrCode2,
                    title = stringResource(R.string.qr_export_configuration),
                    subtitle = stringResource(R.string.qr_export_subtitle),
                    accent = Teal,
                )
                Spacer(Modifier.height(12.dp))

                Button(
                    onClick = { viewModel.generateQr() },
                    enabled = !viewModel.isGenerating,
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Teal, contentColor = Color.Black),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
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
                    BoxWithConstraints(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.Center,
                    ) {
                        val qrSize = if (maxWidth < 260.dp) maxWidth else 260.dp
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = Color.White,
                            modifier = Modifier.size(qrSize),
                        ) {
                            Image(
                                bitmap = bitmap.asImageBitmap(),
                                contentDescription = stringResource(R.string.qr_code_content_description),
                                modifier = Modifier.padding(16.dp),
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        viewModel.configSummary,
                        color = TextDim, fontSize = 11.sp,
                    )
                } ?: run {
                    if (!viewModel.isGenerating) {
                        Spacer(Modifier.height(12.dp))
                        HostShieldCompactState(
                            icon = Icons.Filled.PrivacyTip,
                            title = stringResource(R.string.qr_empty_export_title),
                            message = stringResource(R.string.qr_empty_export_message),
                            accent = Teal,
                        )
                    }
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
                            fontSize = 10.sp, lineHeight = 14.sp,
                            fontFamily = FontFamily.Monospace,
                            maxLines = 4,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(10.dp),
                        )
                    }
                }
            }
        }

        // Import section
        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                HostShieldPanelHeader(
                    icon = Icons.Filled.QrCodeScanner,
                    title = stringResource(R.string.qr_import_configuration),
                    subtitle = stringResource(R.string.qr_import_subtitle),
                    accent = Blue,
                )
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
                    minLines = 3,
                    maxLines = 4,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Text,
                        imeAction = ImeAction.Done,
                    ),
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
                    modifier = Modifier.heightIn(min = 44.dp),
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
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
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
            val isError = msg.contains("invalid", ignoreCase = true) ||
                msg.contains("failed", ignoreCase = true)
            HostShieldStatusBanner(
                icon = if (isError) Icons.Filled.Error else Icons.Filled.CheckCircle,
                title = if (isError) stringResource(R.string.qr_action_failed) else stringResource(R.string.qr_action_ready),
                message = msg,
                accent = if (isError) Red else Teal,
                onDismiss = { viewModel.clearImportResult() },
            )
        }

        Spacer(Modifier.height(24.dp))
    }
}
