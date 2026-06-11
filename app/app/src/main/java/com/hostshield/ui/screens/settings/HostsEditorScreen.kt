package com.hostshield.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
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
                _state.update { it.copy(isLoading = false, message = "Failed to read: ${e.message}") }
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
                _state.update { it.copy(isSaving = false, message = "Save failed: ${e.message}") }
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
        // Header
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = TextPrimary) }
            Column(modifier = Modifier.weight(1f)) {
                Text("Hosts editor", style = MaterialTheme.typography.titleLarge, color = TextPrimary)
                Text("${state.lineCount} lines, ${state.entryCount} entries", color = TextDim, fontSize = 11.sp)
            }
            if (state.isEdited) {
                Button(
                    onClick = { viewModel.save() },
                    enabled = !state.isSaving,
                    colors = ButtonDefaults.buttonColors(containerColor = Teal),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    if (state.isSaving) CircularProgressIndicator(Modifier.size(14.dp), color = Color.Black, strokeWidth = 2.dp)
                    else Text("Save", fontSize = 12.sp)
                }
            }
            Spacer(Modifier.width(4.dp))
            IconButton(onClick = { viewModel.loadHostsFile() }) {
                Icon(Icons.Filled.Refresh, "Reload", tint = TextDim)
            }
        }

        // Status message
        state.message?.let { msg ->
            Surface(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                shape = RoundedCornerShape(8.dp),
                color = if (msg.contains("fail", ignoreCase = true)) Red.copy(alpha = 0.08f) else Teal.copy(alpha = 0.08f)
            ) {
                Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(msg, color = TextSecondary, fontSize = 12.sp, modifier = Modifier.weight(1f))
                    IconButton(onClick = { viewModel.clearMessage() }, modifier = Modifier.size(20.dp)) {
                        Icon(Icons.Filled.Close, "Dismiss hosts editor message", tint = TextDim, modifier = Modifier.size(12.dp))
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
        }

        if (state.isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Teal)
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
