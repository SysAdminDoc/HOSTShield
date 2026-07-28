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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hostshield.ui.components.HostShieldActionIconButton
import com.hostshield.ui.components.HostShieldBackHeader
import com.hostshield.ui.components.HostShieldEmptyState
import com.hostshield.ui.components.HostShieldInlineAction
import com.hostshield.ui.components.HostShieldLoadingState
import com.hostshield.ui.components.HostShieldStatusBanner
import com.hostshield.ui.theme.*
import com.hostshield.util.RootUtil

data class HostsEditorState(
    val content: String = "",
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val message: String? = null,
    val lineCount: Int = 0,
    val entryCount: Int = 0,
    val isEdited: Boolean = false,
    /** True when the last read failed; the editor must not save over the file. */
    val loadFailed: Boolean = false,
    /** Whether [message] is an error (drives banner styling explicitly instead of sniffing the text). */
    val messageIsError: Boolean = false,
    /** True when the file is too large to edit safely in a single text field (read-only preview). */
    val tooLargeToEdit: Boolean = false
)

@Composable
fun HostsEditorScreen(
    viewModel: HostsEditorViewModel = hiltViewModel(),
    onBack: () -> Unit
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Column(modifier = Modifier.fillMaxSize().background(Black)) {
        HostShieldBackHeader(
            title = "Hosts editor",
            subtitle = "${state.lineCount} lines, ${state.entryCount} editable entries",
            onBack = onBack,
            actions = {
                if (state.isEdited && !state.tooLargeToEdit) {
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
            val isError = state.messageIsError
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
        } else if (state.loadFailed) {
            Box(modifier = Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.TopCenter) {
                HostShieldEmptyState(
                    icon = Icons.Filled.Error,
                    title = "Couldn't read the hosts file",
                    message = "Root access is required to read the system hosts file. Grant root and retry — editing is disabled until the file loads.",
                    accent = Red,
                    primaryActionLabel = "Retry",
                    onPrimaryAction = { viewModel.loadHostsFile() },
                )
            }
        } else {
            // Editor (read-only above the size threshold to keep the screen responsive)
            OutlinedTextField(
                value = state.content,
                onValueChange = { if (!state.tooLargeToEdit) viewModel.setContent(it) },
                readOnly = state.tooLargeToEdit,
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
