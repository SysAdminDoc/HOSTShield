package com.hostshield.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hostshield.util.RootUtil
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HostsDiffViewModel @Inject constructor(private val rootUtil: RootUtil) : ViewModel() {
    private val _uiState = MutableStateFlow(DiffUiState())
    val uiState: StateFlow<DiffUiState> = _uiState.asStateFlow()

    init { loadCurrentHosts() }

    private fun loadCurrentHosts() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            rootUtil.readHostsFile().fold(
                onSuccess = { content ->
                    val lines = content.lines()
                    val diffLines = lines.map { line ->
                        val type = when {
                            line.trim().startsWith("#") -> DiffLineType.COMMENT
                            isSinkholeLine(line) -> DiffLineType.ADDED
                            else -> DiffLineType.CONTEXT
                        }
                        DiffLine(line, type)
                    }
                    val blockCount = lines.count { isSinkholeLine(it) }
                    _uiState.update {
                        it.copy(isLoading = false, currentLineCount = lines.size, diffLines = diffLines, addedCount = blockCount)
                    }
                },
                onFailure = { e ->
                    android.util.Log.e("HostsDiff", "Failed to read hosts file", e)
                    _uiState.update {
                        it.copy(isLoading = false, error = "Could not read the hosts file. Check root access and try again.")
                    }
                }
            )
        }
    }

    fun refresh() = loadCurrentHosts()

    private companion object {
        /**
         * A blocked (sinkholed) hosts line maps a domain to the unspecified
         * address `0.0.0.0` or `::`. The IPv6 loopback `::1 localhost` is stock
         * boilerplate, not a block, so match the sink token exactly rather than
         * by prefix (which would count `::1` as blocked).
         */
        fun isSinkholeLine(line: String): Boolean {
            val firstField = line.trim().substringBefore(' ').substringBefore('\t')
            return firstField == "0.0.0.0" || firstField == "::"
        }
    }
}
