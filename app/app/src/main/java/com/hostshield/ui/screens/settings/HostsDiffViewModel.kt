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
            _uiState.update { it.copy(isLoading = true) }
            try {
                val content = rootUtil.readHostsFile()
                val lines = content.lines()
                val diffLines = lines.map { line ->
                    val trimmed = line.trim()
                    val type = when {
                        trimmed.startsWith("#") -> DiffLineType.COMMENT
                        trimmed.startsWith("0.0.0.0") || trimmed.startsWith("::") -> DiffLineType.ADDED
                        trimmed.startsWith("127.0.0.1") -> DiffLineType.CONTEXT
                        else -> DiffLineType.CONTEXT
                    }
                    DiffLine(line, type)
                }
                val blockCount = lines.count { it.trim().startsWith("0.0.0.0") || it.trim().startsWith("::") }
                _uiState.update { it.copy(isLoading = false, currentLineCount = lines.size, diffLines = diffLines, addedCount = blockCount) }
            } catch (e: Exception) {
                android.util.Log.e("HostsDiff", "Failed to read hosts file", e)
                _uiState.update { it.copy(isLoading = false, error = "Could not read the hosts file. Check root access and try again.") }
            }
        }
    }

    fun refresh() = loadCurrentHosts()
}
