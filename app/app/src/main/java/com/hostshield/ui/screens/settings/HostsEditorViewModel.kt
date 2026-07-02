package com.hostshield.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hostshield.util.RootUtil
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

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
