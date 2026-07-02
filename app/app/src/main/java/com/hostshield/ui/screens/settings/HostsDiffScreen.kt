package com.hostshield.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hostshield.util.RootUtil
import com.hostshield.ui.components.HostShieldActionIconButton
import com.hostshield.ui.components.HostShieldBackHeader
import com.hostshield.ui.components.HostShieldEmptyState
import com.hostshield.ui.components.HostShieldLoadingState
import com.hostshield.ui.components.HostShieldMetricTile
import com.hostshield.ui.theme.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

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

@Composable
fun HostsDiffScreen(viewModel: HostsDiffViewModel = hiltViewModel(), onBack: () -> Unit) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Column(modifier = Modifier.fillMaxSize().background(Black)) {
        HostShieldBackHeader(
            title = "Hosts file",
            subtitle = "${state.currentLineCount} lines, ${state.addedCount} blocked entries",
            onBack = onBack,
            actions = {
                HostShieldActionIconButton(
                    icon = Icons.Filled.Refresh,
                    contentDescription = "Refresh hosts file",
                    accent = Teal,
                    enabled = !state.isLoading,
                    onClick = { viewModel.refresh() },
                )
            },
        )

        if (state.isLoading) {
            Box(Modifier.fillMaxSize().padding(20.dp), contentAlignment = Alignment.TopCenter) {
                HostShieldLoadingState(
                    title = "Reading hosts file",
                    message = "Checking the active system hosts file with root access.",
                    accent = Teal,
                )
            }
        } else if (state.error != null) {
            Box(Modifier.fillMaxSize().padding(20.dp), contentAlignment = Alignment.TopCenter) {
                HostShieldEmptyState(
                    icon = Icons.Filled.GppBad,
                    title = "Could not read hosts file",
                    message = state.error ?: "Check root access and try again.",
                    accent = Red,
                    primaryActionLabel = "Retry",
                    onPrimaryAction = { viewModel.refresh() },
                )
            }
        } else {
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                HostShieldMetricTile(
                    value = state.addedCount.toString(),
                    label = "Blocked",
                    accent = Green,
                    modifier = Modifier.weight(1f),
                )
                HostShieldMetricTile(
                    value = state.currentLineCount.toString(),
                    label = "Lines",
                    accent = Blue,
                    modifier = Modifier.weight(1f),
                )
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
