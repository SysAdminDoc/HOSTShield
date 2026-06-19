package com.hostshield.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.hostshield.ui.components.HostShieldActionIconButton
import com.hostshield.ui.components.HostShieldBackHeader
import com.hostshield.ui.components.HostShieldInlineAction
import com.hostshield.ui.components.HostShieldLoadingState
import com.hostshield.ui.components.HostShieldStatusBanner
import com.hostshield.ui.theme.*
import com.hostshield.util.RootUtil
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HostsEditorState(
    val content: String = "",
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val message: String? = null,
    val lineCount: Int = 0,
    val entryCount: Int = 0,
    val isEdited: Boolean = false
)

@HiltViewModel
class HostsEditorViewModel @Inject constructor(
    private val rootUtil: RootUtil
) : ViewModel() {
    private val _state = MutableStateFlow(HostsEditorState())
    val state = _state.asStateFlow()

    init { loadHostsFile() }

    fun loadHostsFile() {
        viewModelScope.launch(Dispatchers.IO) {
            _state.update { it.copy(isLoading = true) }
            try {
                val content = rootUtil.readHostsFile()
                val lines = content.lines()
                val entries = lines.count { l -> l.isNotBlank() && !l.trimStart().startsWith("#") }
                _state.update {
                    it.copy(
                        content = content,
                        isLoading = false,
                        lineCount = lines.size,
                        entryCount = entries,
                        isEdited = false
                    )
                }
            } catch (e: Exception) {
                android.util.Log.e("HostsEditor", "Failed to read hosts file", e)
                _state.update { it.copy(isLoading = false, message = "Read failed. Check root access and try again.") }
            }
        }
    }

    fun setContent(text: String) {
        _state.update {
            val lines = text.lines()
            val entries = lines.count { l -> l.isNotBlank() && !l.trimStart().startsWith("#") }
            it.copy(content = text, lineCount = lines.size, entryCount = entries, isEdited = true)
        }
    }

    fun save() {
        viewModelScope.launch(Dispatchers.IO) {
            _state.update { it.copy(isSaving = true) }
            try {
                rootUtil.writeHostsFile(_state.value.content)
                _state.update { it.copy(isSaving = false, isEdited = false, message = "Hosts file saved") }
            } catch (e: Exception) {
                android.util.Log.e("HostsEditor", "Failed to save hosts file", e)
                _state.update { it.copy(isSaving = false, message = "Save failed. Check root access and try again.") }
            }
        }
    }

    fun clearMessage() { _state.update { it.copy(message = null) } }
}

@Composable
fun HostsEditorScreen(
    viewModel: HostsEditorViewModel = hiltViewModel(),
    onBack: () -> Unit
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Column(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        HostShieldBackHeader(
            title = "Hosts editor",
            subtitle = "${state.lineCount} lines, ${state.entryCount} editable entries",
            onBack = onBack,
            actions = {
                if (state.isEdited) {
                    HostShieldInlineAction(
                        label = if (state.isSaving) "Saving" else "Save",
                        icon = Icons.Filled.Save,
                        accent = Teal,
                        enabled = !state.isSaving,
                        onClick = { viewModel.save() },
                    )
                }
                HostShieldActionIconButton(
                    icon = Icons.Filled.Refresh,
                    contentDescription = "Reload hosts file",
                    accent = TextDim,
                    enabled = !state.isLoading && !state.isSaving,
                    onClick = { viewModel.loadHostsFile() },
                )
            },
        )

        // Status message
        state.message?.let { msg ->
            val isError = msg.contains("fail", ignoreCase = true)
            HostShieldStatusBanner(
                icon = if (isError) Icons.Filled.Error else Icons.Filled.CheckCircle,
                title = if (isError) "Hosts update failed" else "Hosts file updated",
                message = msg,
                accent = if (isError) Red else Teal,
                onDismiss = { viewModel.clearMessage() },
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            Spacer(Modifier.height(4.dp))
        }

        if (state.isLoading) {
            Box(modifier = Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.TopCenter) {
                HostShieldLoadingState(
                    title = "Loading hosts file",
                    message = "Reading the current system hosts file with root access.",
                    accent = Teal,
                )
            }
        } else {
            // Editor
            OutlinedTextField(
                value = state.content,
                onValueChange = { viewModel.setContent(it) },
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                textStyle = LocalTextStyle.current.copy(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    lineHeight = 15.sp,
                    color = TextPrimary
                ),
                shape = RoundedCornerShape(8.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Surface3,
                    unfocusedBorderColor = Surface2,
                    cursorColor = Teal,
                    focusedContainerColor = Surface0,
                    unfocusedContainerColor = Surface0
                )
            )
        }
    }
}
