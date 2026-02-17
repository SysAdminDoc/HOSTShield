package com.hostshield.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
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
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.hostshield.util.RootUtil
import com.hostshield.ui.theme.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class DiffLineType { HEADER, ADDED, REMOVED, CONTEXT, COMMENT }
data class DiffLine(val text: String, val type: DiffLineType)

data class DiffUiState(
    val isLoading: Boolean = true,
    val currentLineCount: Int = 0,
    val diffLines: List<DiffLine> = emptyList(),
    val addedCount: Int = 0,
    val removedCount: Int = 0,
    val error: String? = null
)

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
                _uiState.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }

    fun refresh() = loadCurrentHosts()
}

@Composable
fun HostsDiffScreen(viewModel: HostsDiffViewModel = hiltViewModel(), onBack: () -> Unit) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Column(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, "Back", tint = TextPrimary) }
            Column(modifier = Modifier.weight(1f)) {
                Text("Hosts File", style = MaterialTheme.typography.titleLarge, color = TextPrimary)
                Text("${state.currentLineCount} lines, ${state.addedCount} blocked", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
            }
            IconButton(onClick = { viewModel.refresh() }) { Icon(Icons.Filled.Refresh, "Refresh", tint = Teal) }
        }

        if (state.isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Teal) }
        } else if (state.error != null) {
            Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                Text("Error: ${state.error}", color = Red, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            }
        } else {
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Surface(shape = RoundedCornerShape(6.dp), color = Green.copy(alpha = 0.1f)) {
                    Text("+${state.addedCount} blocked", modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), color = Green, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
                Surface(shape = RoundedCornerShape(6.dp), color = Blue.copy(alpha = 0.1f)) {
                    Text("${state.currentLineCount} lines", modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), color = Blue, fontSize = 11.sp)
                }
            }

            LazyColumn(contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)) {
                itemsIndexed(state.diffLines) { index, line ->
                    val bgColor = when (line.type) {
                        DiffLineType.ADDED -> Green.copy(alpha = 0.04f)
                        DiffLineType.REMOVED -> Red.copy(alpha = 0.04f)
                        DiffLineType.COMMENT -> Mauve.copy(alpha = 0.03f)
                        else -> Color.Transparent
                    }
                    val textColor = when (line.type) {
                        DiffLineType.ADDED -> Green.copy(alpha = 0.8f)
                        DiffLineType.REMOVED -> Red.copy(alpha = 0.8f)
                        DiffLineType.COMMENT -> TextDim
                        DiffLineType.HEADER -> Teal
                        else -> TextSecondary
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth().background(bgColor).padding(horizontal = 4.dp, vertical = 1.dp).horizontalScroll(rememberScrollState())
                    ) {
                        Text("${index + 1}", modifier = Modifier.width(40.dp), color = TextDim.copy(alpha = 0.4f), fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                        Text(line.text, color = textColor, fontSize = 10.sp, fontFamily = FontFamily.Monospace, maxLines = 1)
                    }
                }
            }
        }
    }
}
