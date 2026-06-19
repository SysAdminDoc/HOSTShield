package com.hostshield.ui.screens.sources

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.CompareArrows
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.hostshield.data.model.HostSource
import com.hostshield.data.model.SourceCategory
import com.hostshield.data.repository.HostShieldRepository
import com.hostshield.data.source.SourceDownloader
import com.hostshield.domain.parser.HostsParser
import com.hostshield.ui.components.HostShieldBackHeader
import com.hostshield.ui.components.HostShieldEmptyState
import com.hostshield.ui.components.HostShieldInlineAction
import com.hostshield.ui.components.HostShieldLoadingState
import com.hostshield.ui.components.HostShieldStatusBanner
import com.hostshield.ui.screens.home.GlassCard
import com.hostshield.ui.theme.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.text.NumberFormat
import javax.inject.Inject

data class OverlapPair(
    val sourceA: String,
    val sourceB: String,
    val overlapCount: Int,
    val overlapPercent: Float, // percent of smaller source that overlaps
    val sizeA: Int,
    val sizeB: Int
)

data class OverlapState(
    val isAnalyzing: Boolean = false,
    val progress: String = "",
    val pairs: List<OverlapPair> = emptyList(),
    val sourceSizes: Map<String, Int> = emptyMap(),
    val totalUnique: Int = 0,
    val totalWithDupes: Int = 0,
    val wastedEntries: Int = 0
)

@HiltViewModel
class OverlapViewModel @Inject constructor(
    private val repository: HostShieldRepository,
    private val downloader: SourceDownloader
) : ViewModel() {
    private val _state = MutableStateFlow(OverlapState())
    val state = _state.asStateFlow()

    fun analyze() {
        viewModelScope.launch(Dispatchers.IO) {
            _state.update { it.copy(isAnalyzing = true, progress = "Loading sources...") }

            val sources = repository.getEnabledBlockSources()
                .filter { it.category != SourceCategory.ALLOWLIST }
            if (sources.size < 2) {
                _state.update { it.copy(isAnalyzing = false, progress = "Need at least 2 enabled sources to analyze overlap.") }
                return@launch
            }

            // Download and parse each source
            val sourceDomains = mutableMapOf<String, Set<String>>()
            for ((idx, source) in sources.withIndex()) {
                _state.update { it.copy(progress = "Downloading ${source.label} (${idx + 1}/${sources.size})...") }
                downloader.download(source, forceDownload = true).onSuccess { dl ->
                    val domains = HostsParser.parse(dl.content).map { it.hostname }.toSet()
                    sourceDomains[source.label] = domains
                }
            }

            _state.update { it.copy(progress = "Calculating overlap...") }

            val labels = sourceDomains.keys.toList()
            val pairs = mutableListOf<OverlapPair>()

            for (i in labels.indices) {
                for (j in i + 1 until labels.size) {
                    val a = sourceDomains[labels[i]] ?: continue
                    val b = sourceDomains[labels[j]] ?: continue
                    val overlap = a.intersect(b).size
                    if (overlap > 0) {
                        val smaller = minOf(a.size, b.size).coerceAtLeast(1)
                        pairs.add(OverlapPair(
                            sourceA = labels[i],
                            sourceB = labels[j],
                            overlapCount = overlap,
                            overlapPercent = (overlap.toFloat() / smaller) * 100f,
                            sizeA = a.size,
                            sizeB = b.size
                        ))
                    }
                }
            }

            // Calculate totals
            val allDomains = mutableSetOf<String>()
            var totalWithDupes = 0
            sourceDomains.values.forEach { domains ->
                totalWithDupes += domains.size
                allDomains.addAll(domains)
            }

            _state.update {
                it.copy(
                    isAnalyzing = false,
                    progress = "",
                    pairs = pairs.sortedByDescending { p -> p.overlapPercent },
                    sourceSizes = sourceDomains.mapValues { (_, v) -> v.size },
                    totalUnique = allDomains.size,
                    totalWithDupes = totalWithDupes,
                    wastedEntries = totalWithDupes - allDomains.size
                )
            }
        }
    }
}

@Composable
fun OverlapAnalysisScreen(
    viewModel: OverlapViewModel = hiltViewModel(),
    onBack: () -> Unit
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val nf = NumberFormat.getNumberInstance()

    Column(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        HostShieldBackHeader(
            title = "Overlap analysis",
            subtitle = "Find redundant domains across enabled sources",
            onBack = onBack,
            actions = {
                HostShieldInlineAction(
                    label = if (state.isAnalyzing) "Analyzing" else "Analyze",
                    icon = Icons.Filled.Analytics,
                    accent = Teal,
                    enabled = !state.isAnalyzing,
                    onClick = { viewModel.analyze() },
                )
            },
        )

        LazyColumn(
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (state.progress.isNotEmpty()) {
                item {
                    if (state.isAnalyzing) {
                        HostShieldLoadingState(
                            title = "Analyzing source overlap",
                            message = state.progress,
                            accent = Teal,
                        )
                    } else {
                        HostShieldStatusBanner(
                            icon = Icons.Filled.Info,
                            title = "Analysis needs more sources",
                            message = state.progress,
                            accent = Yellow,
                        )
                    }
                }
            }

            // Summary card
            if (state.totalUnique > 0) {
                item {
                    GlassCard(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(modifier = Modifier.size(28.dp).clip(RoundedCornerShape(8.dp)).background(Teal.copy(alpha = 0.1f)), contentAlignment = Alignment.Center) {
                                    Icon(Icons.Filled.Analytics, null, tint = Teal, modifier = Modifier.size(14.dp))
                                }
                                Spacer(Modifier.width(10.dp))
                                Text("Summary", color = TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                            }
                            Spacer(Modifier.height(12.dp))
                            SummaryRow("Sources analyzed", "${state.sourceSizes.size}")
                            SummaryRow("Total entries (with dupes)", nf.format(state.totalWithDupes))
                            SummaryRow("Unique domains", nf.format(state.totalUnique))
                            SummaryRow(
                                "Wasted (redundant)",
                                nf.format(state.wastedEntries),
                                valueColor = if (state.wastedEntries > 0) Yellow else Green
                            )
                            if (state.totalWithDupes > 0) {
                                val efficiency = (state.totalUnique.toFloat() / state.totalWithDupes * 100).toInt()
                                SummaryRow("Efficiency", "$efficiency%", valueColor = when {
                                    efficiency >= 90 -> Green
                                    efficiency >= 70 -> Yellow
                                    else -> Red
                                })
                            }
                        }
                    }
                }
            }

            // Source sizes
            if (state.sourceSizes.isNotEmpty()) {
                item {
                    GlassCard(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(modifier = Modifier.size(28.dp).clip(RoundedCornerShape(8.dp)).background(Blue.copy(alpha = 0.1f)), contentAlignment = Alignment.Center) {
                                    Icon(Icons.Filled.Storage, null, tint = Blue, modifier = Modifier.size(14.dp))
                                }
                                Spacer(Modifier.width(10.dp))
                                Text("Source Sizes", color = TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                            }
                            Spacer(Modifier.height(12.dp))
                            val maxSize = state.sourceSizes.values.maxOrNull() ?: 1
                            state.sourceSizes.entries.sortedByDescending { it.value }.forEach { (label, size) ->
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(label, color = TextSecondary, fontSize = 11.sp, modifier = Modifier.weight(1f), maxLines = 1)
                                    Spacer(Modifier.width(8.dp))
                                    Box(
                                        modifier = Modifier.width(60.dp).height(4.dp)
                                            .clip(RoundedCornerShape(2.dp)).background(Surface3)
                                    ) {
                                        Box(
                                            modifier = Modifier.fillMaxWidth(size.toFloat() / maxSize)
                                                .fillMaxHeight().background(Blue.copy(alpha = 0.6f))
                                        )
                                    }
                                    Spacer(Modifier.width(8.dp))
                                    Text(nf.format(size), color = TextDim, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                                }
                            }
                        }
                    }
                }
            }

            // Overlap pairs
            if (state.pairs.isNotEmpty()) {
                item {
                    Spacer(Modifier.height(2.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.AutoMirrored.Filled.CompareArrows, null, tint = Yellow, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Overlap Details", color = TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    }
                }

                items(state.pairs) { pair ->
                    GlassCard(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(pair.sourceA, color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Medium,
                                    modifier = Modifier.weight(1f), maxLines = 1)
                                Surface(shape = RoundedCornerShape(4.dp), color = Surface3) {
                                    Text(nf.format(pair.sizeA), modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp),
                                        color = TextDim, fontSize = 9.sp, fontFamily = FontFamily.Monospace)
                                }
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(Icons.Filled.SwapVert, null, tint = TextDim, modifier = Modifier.size(14.dp))
                                Spacer(Modifier.width(6.dp))
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = when {
                                        pair.overlapPercent > 80 -> Red.copy(alpha = 0.12f)
                                        pair.overlapPercent > 40 -> Yellow.copy(alpha = 0.1f)
                                        else -> Green.copy(alpha = 0.08f)
                                    }
                                ) {
                                    Text(
                                        "${nf.format(pair.overlapCount)} shared (${pair.overlapPercent.toInt()}%)",
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                        color = when {
                                            pair.overlapPercent > 80 -> Red
                                            pair.overlapPercent > 40 -> Yellow
                                            else -> Green
                                        },
                                        fontSize = 11.sp, fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(pair.sourceB, color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Medium,
                                    modifier = Modifier.weight(1f), maxLines = 1)
                                Surface(shape = RoundedCornerShape(4.dp), color = Surface3) {
                                    Text(nf.format(pair.sizeB), modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp),
                                        color = TextDim, fontSize = 9.sp, fontFamily = FontFamily.Monospace)
                                }
                            }
                        }
                    }
                }
            }

            // Empty state
            if (!state.isAnalyzing && state.pairs.isEmpty() && state.totalUnique == 0) {
                item {
                    HostShieldEmptyState(
                        icon = Icons.AutoMirrored.Filled.CompareArrows,
                        title = "No overlap analysis yet",
                        message = "Compare enabled block sources to find duplicate coverage and list redundancy.",
                        accent = Teal,
                        primaryActionLabel = "Analyze sources",
                        onPrimaryAction = { viewModel.analyze() },
                    )
                }
            }

            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun SummaryRow(label: String, value: String, valueColor: Color = TextPrimary) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, color = TextDim, fontSize = 12.sp)
        Text(value, color = valueColor, fontSize = 12.sp, fontWeight = FontWeight.Medium, fontFamily = FontFamily.Monospace)
    }
}
