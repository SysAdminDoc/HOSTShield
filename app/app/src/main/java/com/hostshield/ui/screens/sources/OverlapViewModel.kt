package com.hostshield.ui.screens.sources

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hostshield.data.model.HostSource
import com.hostshield.data.model.SourceCategory
import com.hostshield.data.repository.HostShieldRepository
import com.hostshield.data.source.SourceDownloader
import com.hostshield.domain.parser.HostsParser
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.text.NumberFormat
import javax.inject.Inject

@HiltViewModel
class OverlapViewModel @Inject constructor(
    private val repository: HostShieldRepository,
    private val downloader: SourceDownloader
) : ViewModel() {
    private val _state = MutableStateFlow(OverlapState())
    val state = _state.asStateFlow()

    fun analyze() {
        if (_state.value.isAnalyzing) return
        viewModelScope.launch(Dispatchers.IO) {
            _state.update {
                it.copy(
                    isAnalyzing = true,
                    progress = "Loading sources...",
                    pairs = emptyList(),
                    sourceSizes = emptyMap(),
                    totalUnique = 0,
                    totalWithDupes = 0,
                    wastedEntries = 0,
                    message = null,
                    messageIsError = false
                )
            }

            try {
                val sources = repository.getEnabledBlockSources()
                    .filter { it.category != SourceCategory.ALLOWLIST }
                if (sources.size < 2) {
                    _state.update {
                        it.copy(
                            isAnalyzing = false,
                            progress = "",
                            message = "Enable at least two block sources to analyze overlap.",
                            messageIsError = false
                        )
                    }
                    return@launch
                }

                // Download and parse each source
                val sourceDomains = mutableMapOf<String, Set<String>>()
                val failedSources = mutableListOf<HostSource>()
                for ((idx, source) in sources.withIndex()) {
                    _state.update { it.copy(progress = "Downloading ${source.label} (${idx + 1}/${sources.size})...") }
                    downloader.download(source, forceDownload = true)
                        .onSuccess { dl ->
                            try {
                                val domains = HostsParser.parse(dl.content).map { it.hostname }.toSet()
                                sourceDomains[source.label] = domains
                            } catch (e: Exception) {
                                failedSources += source
                            }
                        }
                        .onFailure {
                            failedSources += source
                        }
                }

                if (sourceDomains.size < 2) {
                    _state.update {
                        it.copy(
                            isAnalyzing = false,
                            progress = "",
                            message = if (failedSources.isEmpty()) {
                                "At least two enabled sources need parseable domains for overlap analysis."
                            } else {
                                "Only ${sourceDomains.size} source could be analyzed. ${failedSources.size} source downloads failed."
                            },
                            messageIsError = false
                        )
                    }
                    return@launch
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
                        wastedEntries = totalWithDupes - allDomains.size,
                        message = if (failedSources.isNotEmpty()) {
                            "Analyzed ${sourceDomains.size} sources. ${failedSources.size} source failed and was skipped."
                        } else {
                            null
                        },
                        messageIsError = false
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                android.util.Log.w("OverlapAnalysis", "Overlap analysis failed", e)
                _state.update {
                    it.copy(
                        isAnalyzing = false,
                        progress = "",
                        pairs = emptyList(),
                        sourceSizes = emptyMap(),
                        totalUnique = 0,
                        totalWithDupes = 0,
                        wastedEntries = 0,
                        message = "Overlap analysis could not complete. Check source connectivity and try again.",
                        messageIsError = true
                    )
                }
            }
        }
    }

    fun clearMessage() {
        _state.update { it.copy(message = null, messageIsError = false) }
    }
}
