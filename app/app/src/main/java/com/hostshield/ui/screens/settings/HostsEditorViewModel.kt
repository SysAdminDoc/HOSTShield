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
            rootUtil.readHostsFile().fold(
                onSuccess = { content ->
                    val lines = content.lines()
                    val entries = lines.count { l -> l.isNotBlank() && !l.trimStart().startsWith("#") }
                    _state.update {
                        it.copy(
                            content = content,
                            isLoading = false,
                            lineCount = lines.size,
                            entryCount = entries,
                            isEdited = false,
                            loadFailed = false,
                            message = null,
                            messageIsError = false
                        )
                    }
                },
                onFailure = { e ->
                    android.util.Log.e("HostsEditor", "Failed to read hosts file", e)
                    _state.update {
                        it.copy(
                            isLoading = false,
                            loadFailed = true,
                            message = "Read failed. Check root access and try again.",
                            messageIsError = true
                        )
                    }
                }
            )
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
        // Refuse to save when the initial read failed: the editor content would
        // be empty/partial and writing it would clobber the real hosts file.
        if (_state.value.loadFailed) {
            _state.update {
                it.copy(
                    message = "Cannot save: hosts file could not be read. Reload first.",
                    messageIsError = true
                )
            }
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            _state.update { it.copy(isSaving = true) }
            rootUtil.writeHostsFile(_state.value.content).fold(
                onSuccess = {
                    _state.update {
                        it.copy(isSaving = false, isEdited = false, message = "Hosts file saved", messageIsError = false)
                    }
                },
                onFailure = { e ->
                    android.util.Log.e("HostsEditor", "Failed to save hosts file", e)
                    _state.update {
                        it.copy(
                            isSaving = false,
                            message = "Save failed. Check root access and try again.",
                            messageIsError = true
                        )
                    }
                }
            )
        }
    }

    fun clearMessage() { _state.update { it.copy(message = null) } }
}
