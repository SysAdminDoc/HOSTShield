package com.hostshield.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import com.hostshield.ui.components.ConfirmDestructiveDialog
import com.hostshield.ui.components.HostShieldActionIconButton
import com.hostshield.ui.components.HostShieldBackHeader
import com.hostshield.ui.components.HostShieldEmptyState
import com.hostshield.ui.components.HostShieldSegmentOption
import com.hostshield.ui.components.HostShieldSegmentedTabs
import com.hostshield.ui.screens.home.GlassCard
import com.hostshield.ui.theme.*
import com.hostshield.util.TlsFingerprinter
import dagger.hilt.android.lifecycle.HiltViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

@HiltViewModel
class TlsFingerprintViewModel @Inject constructor(
    private val fingerprinter: TlsFingerprinter,
) : ViewModel() {

    var fingerprints by mutableStateOf<List<TlsFingerprinter.CapturedFingerprint>>(emptyList())
        private set
    var groupedByApp by mutableStateOf<Map<String, List<TlsFingerprinter.CapturedFingerprint>>>(emptyMap())
        private set
    var viewMode by mutableStateOf(ViewMode.TIMELINE)
        private set

    enum class ViewMode { TIMELINE, BY_APP }

    init { refresh() }

    fun refresh() {
        fingerprints = fingerprinter.getHistory()
        groupedByApp = fingerprinter.getByApp()
    }

    fun clear() {
        fingerprinter.clearHistory()
        fingerprints = emptyList()
        groupedByApp = emptyMap()
    }

    fun setMode(mode: ViewMode) { viewMode = mode }
}

@Composable
fun TlsFingerprintScreen(
    onBack: () -> Unit,
    viewModel: TlsFingerprintViewModel = hiltViewModel(),
) {
    val dateFormat = remember { SimpleDateFormat("HH:mm:ss", Locale.US) }
    var expandedIndex by remember { mutableIntStateOf(-1) }
    var showClearFingerprintsDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Black)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        HostShieldBackHeader(
            title = "TLS Fingerprints",
            subtitle = "${viewModel.fingerprints.size} captures · ${viewModel.groupedByApp.size} apps",
            onBack = onBack,
            horizontalPadding = 0.dp,
            verticalPadding = 0.dp,
            actions = {
                HostShieldActionIconButton(
                    icon = Icons.Filled.Refresh,
                    contentDescription = "Refresh fingerprints",
                    onClick = { viewModel.refresh() },
                    accent = Teal,
                )
                if (viewModel.fingerprints.isNotEmpty()) {
                    HostShieldActionIconButton(
                        icon = Icons.Filled.DeleteSweep,
                        contentDescription = "Clear fingerprints",
                        onClick = { showClearFingerprintsDialog = true },
                        accent = Red,
                    )
                }
            }
        )

        // Summary
        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier.size(40.dp)
                        .background(Sky.copy(alpha = 0.1f), RoundedCornerShape(10.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Filled.Fingerprint, null, tint = Sky, modifier = Modifier.size(20.dp))
                }
                Spacer(Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "${viewModel.fingerprints.size} fingerprints captured",
                        color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                    )
                    val uniqueJa3 = viewModel.fingerprints.map { it.fingerprint.ja3 }.toSet().size
                    val appCount = viewModel.groupedByApp.size
                    Text(
                        "$uniqueJa3 unique JA3 hashes across $appCount apps",
                        color = TextDim, fontSize = 11.sp,
                    )
                }
            }
        }

        HostShieldSegmentedTabs(
            options = listOf(
                HostShieldSegmentOption(TlsFingerprintViewModel.ViewMode.TIMELINE, "Timeline", Sky, Icons.Filled.Timeline),
                HostShieldSegmentOption(TlsFingerprintViewModel.ViewMode.BY_APP, "By App", Teal, Icons.Filled.Apps),
            ),
            selected = viewModel.viewMode,
            onSelected = { viewModel.setMode(it) },
            semanticsLabel = "TLS fingerprint view",
        )

        if (viewModel.fingerprints.isEmpty()) {
            HostShieldEmptyState(
                icon = Icons.Filled.Fingerprint,
                title = "No TLS fingerprints yet",
                message = "Enable HostShield in VPN mode and browse normally. JA3/JA4 identities will appear here for local inspection.",
                accent = Sky,
            )
        }

        when (viewModel.viewMode) {
            TlsFingerprintViewModel.ViewMode.TIMELINE -> {
                viewModel.fingerprints.take(100).forEachIndexed { index, captured ->
                    val isExpanded = expandedIndex == index
                    FingerprintCard(
                        captured = captured,
                        dateFormat = dateFormat,
                        expanded = isExpanded,
                        onToggle = { expandedIndex = if (isExpanded) -1 else index },
                    )
                }
                if (viewModel.fingerprints.size > 100) {
                    Text(
                        "${viewModel.fingerprints.size - 100} more not shown",
                        color = TextDim, fontSize = 11.sp,
                        modifier = Modifier.padding(start = 4.dp),
                    )
                }
            }

            TlsFingerprintViewModel.ViewMode.BY_APP -> {
                viewModel.groupedByApp.entries.sortedByDescending { it.value.size }.forEach { (pkg, fps) ->
                    val uniqueJa3 = fps.map { it.fingerprint.ja3 }.toSet()
                    GlassCard(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        fps.first().appLabel.ifEmpty { pkg },
                                        color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium,
                                    )
                                    Text(pkg, color = TextDim, fontSize = 10.sp)
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text("${fps.size}x", color = Sky, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                    Text("${uniqueJa3.size} unique", color = TextDim, fontSize = 10.sp)
                                }
                            }
                            Spacer(Modifier.height(8.dp))
                            uniqueJa3.take(5).forEach { ja3 ->
                                val sample = fps.first { it.fingerprint.ja3 == ja3 }
                                Row(
                                    modifier = Modifier.padding(vertical = 3.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        ja3.take(16) + "...",
                                        color = Teal, fontSize = 10.sp, fontFamily = FontFamily.Monospace,
                                        modifier = Modifier.weight(1f),
                                    )
                                    sample.fingerprint.knownIdentity?.let { id ->
                                        Surface(
                                            shape = RoundedCornerShape(4.dp),
                                            color = Green.copy(alpha = 0.1f),
                                        ) {
                                            Text(id, color = Green, fontSize = 9.sp, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                                        }
                                    }
                                    sample.fingerprint.sni?.let { sni ->
                                        Spacer(Modifier.width(6.dp))
                                        Text(sni, color = TextDim, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(24.dp))
    }

    if (showClearFingerprintsDialog) {
        ConfirmDestructiveDialog(
            title = "Clear TLS fingerprints?",
            body = "This removes the captured fingerprint history used for local inspection. Live capture and protection settings stay unchanged.",
            confirmLabel = "Clear fingerprints",
            onConfirm = {
                viewModel.clear()
                expandedIndex = -1
            },
            onDismiss = { showClearFingerprintsDialog = false },
        )
    }
}

@Composable
private fun FingerprintCard(
    captured: TlsFingerprinter.CapturedFingerprint,
    dateFormat: SimpleDateFormat,
    expanded: Boolean,
    onToggle: () -> Unit,
) {
    val fp = captured.fingerprint

    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(14.dp)) {
            Surface(onClick = onToggle, color = Color.Transparent) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                captured.appLabel.ifEmpty { captured.packageName },
                                color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Medium,
                            )
                            fp.knownIdentity?.let { id ->
                                Spacer(Modifier.width(8.dp))
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = Green.copy(alpha = 0.1f),
                                ) {
                                    Text(
                                        id, color = Green, fontSize = 9.sp,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    )
                                }
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                dateFormat.format(Date(captured.timestamp)),
                                color = TextDim, fontSize = 10.sp,
                            )
                            fp.sni?.let { sni ->
                                Text(sni, color = Sky, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                    Icon(
                        if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                        null, tint = TextDim, modifier = Modifier.size(20.dp),
                    )
                }
            }

            if (expanded) {
                Spacer(Modifier.height(8.dp))
                HorizontalDivider(color = Surface3, thickness = 0.5.dp)
                Spacer(Modifier.height(8.dp))

                FingerprintField("JA3", fp.ja3, Teal)
                FingerprintField("JA4", fp.ja4, Blue)
                FingerprintField("JA3 Raw", fp.ja3Raw, TextDim)
                FingerprintField("SNI", fp.sni ?: "—", Sky)
                FingerprintField("Package", captured.packageName, TextSecondary)
            }
        }
    }
}

@Composable
private fun FingerprintField(label: String, value: String, valueColor: Color) {
    Row(
        modifier = Modifier.padding(vertical = 2.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            "$label:", color = TextDim, fontSize = 10.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.width(60.dp),
        )
        Text(
            value, color = valueColor, fontSize = 10.sp,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.weight(1f),
        )
    }
}
