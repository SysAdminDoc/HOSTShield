package com.hostshield.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hostshield.data.preferences.AppPreferences
import com.hostshield.ui.components.HostShieldBackHeader
import com.hostshield.ui.components.HostShieldEmptyState
import com.hostshield.ui.components.HostShieldPanelHeader
import com.hostshield.ui.components.HostShieldStatusBanner
import com.hostshield.ui.screens.home.GlassCard
import com.hostshield.ui.theme.*
import com.hostshield.util.WebDavSync
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class WebDavSyncViewModel @Inject constructor(
    private val prefs: AppPreferences,
    private val webDavSync: WebDavSync,
) : ViewModel() {

    val serverUrl = prefs.webdavUrl.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")
    val username = prefs.webdavUsername.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")

    var isSyncing by mutableStateOf(false)
        private set
    var message by mutableStateOf<String?>(null)
        private set
    var remoteFiles by mutableStateOf<List<WebDavSync.RemoteFile>>(emptyList())
        private set
    var hasListedRemoteFiles by mutableStateOf(false)
        private set
    var hasSavedPassword by mutableStateOf(false)
        private set

    init {
        viewModelScope.launch {
            prefs.webdavPassword.collect { hasSavedPassword = it.isNotBlank() }
        }
    }

    fun saveCredentials(url: String, user: String, pass: String) {
        viewModelScope.launch {
            prefs.setWebdavUrl(url.trim())
            prefs.setWebdavUsername(user.trim())
            if (pass.isNotBlank()) {
                prefs.setWebdavPassword(pass)
                hasSavedPassword = true
                message = "Credentials saved"
            } else {
                message = if (hasSavedPassword) {
                    "Settings saved. Existing password kept."
                } else {
                    "Server settings saved. Add a password before syncing."
                }
            }
        }
    }

    fun testConnection(url: String, user: String, pass: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val password = resolvePassword(pass)
            if (password.isBlank()) {
                message = "Enter a WebDAV password or app token"
                return@launch
            }
            isSyncing = true
            message = null
            remoteFiles = emptyList()
            hasListedRemoteFiles = false
            try {
                val creds = WebDavSync.Credentials(user.trim(), password)
                val files = webDavSync.listFiles(url.trim(), creds, "/")
                if (files != null) {
                    remoteFiles = files
                    hasListedRemoteFiles = true
                    message = if (files.isEmpty()) {
                        "Connected - no remote files found"
                    } else {
                        "Connected - ${files.size} items found"
                    }
                } else {
                    message = "Connection failed - check URL and credentials"
                }
            } catch (e: Exception) {
                android.util.Log.w("WebDavSync", "Connection test failed", e)
                message = "Connection failed - check URL and credentials"
            } finally {
                isSyncing = false
            }
        }
    }

    fun syncBackup(url: String, user: String, pass: String, data: ByteArray) {
        viewModelScope.launch(Dispatchers.IO) {
            val password = resolvePassword(pass)
            if (password.isBlank()) {
                message = "Enter a WebDAV password or app token"
                return@launch
            }
            isSyncing = true
            message = null
            try {
                val creds = WebDavSync.Credentials(user.trim(), password)
                val ts = java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.US).format(java.util.Date())
                val remotePath = "/hostshield_backup_$ts.json"
                val success = webDavSync.upload(url.trim(), creds, remotePath, data)
                message = if (success) "Backup uploaded to $remotePath" else "Upload failed"
            } catch (e: Exception) {
                android.util.Log.w("WebDavSync", "Backup upload failed", e)
                message = "Upload failed - check server settings and retry"
            } finally {
                isSyncing = false
            }
        }
    }

    fun clearMessage() { message = null }

    private suspend fun resolvePassword(input: String): String =
        input.ifBlank { prefs.webdavPassword.first() }
}

@Composable
fun WebDavSyncScreen(
    onBack: () -> Unit,
    viewModel: WebDavSyncViewModel = hiltViewModel(),
) {
    val savedUrl by viewModel.serverUrl.collectAsStateWithLifecycle()
    val savedUser by viewModel.username.collectAsStateWithLifecycle()

    var url by remember(savedUrl) { mutableStateOf(savedUrl) }
    var user by remember(savedUser) { mutableStateOf(savedUser) }
    var pass by remember { mutableStateOf("") }
    val urlIsValid = isValidWebDavUrl(url)
    val hasUsablePassword = pass.isNotBlank() || viewModel.hasSavedPassword

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .verticalScroll(rememberScrollState())
            .imePadding()
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        HostShieldBackHeader(
            title = "WebDAV sync",
            subtitle = "Keep encrypted backups on your own WebDAV storage",
            onBack = onBack,
            horizontalPadding = 0.dp,
            verticalPadding = 0.dp,
        )

        // Server configuration
        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                HostShieldPanelHeader(
                    icon = Icons.Filled.Cloud,
                    title = "Server configuration",
                    subtitle = "Credentials stay on this device",
                    accent = Blue,
                )
                Spacer(Modifier.height(12.dp))

                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text("Server URL", color = TextDim, fontSize = 12.sp) },
                    placeholder = { Text("https://cloud.example.com/remote.php/dav/files/user", color = TextDim, fontSize = 11.sp) },
                    singleLine = true,
                    isError = url.isNotBlank() && !urlIsValid,
                    supportingText = if (url.isNotBlank() && !urlIsValid) {
                        { Text("Use a complete http:// or https:// WebDAV URL.", color = Red, fontSize = 11.sp) }
                    } else null,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Uri,
                        imeAction = ImeAction.Next,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Blue, unfocusedBorderColor = Surface3,
                        errorBorderColor = Red, errorLabelColor = Red, errorCursorColor = Red,
                        cursorColor = Blue, focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary,
                    ),
                )
                Spacer(Modifier.height(8.dp))

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = user,
                        onValueChange = { user = it },
                        label = { Text("Username", color = TextDim, fontSize = 12.sp) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Blue, unfocusedBorderColor = Surface3,
                            cursorColor = Blue, focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary,
                        ),
                    )
                    OutlinedTextField(
                        value = pass,
                        onValueChange = { pass = it },
                        label = { Text("Password", color = TextDim, fontSize = 12.sp) },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        supportingText = if (viewModel.hasSavedPassword && pass.isBlank()) {
                            { Text("Leave blank to keep the saved password.", color = TextDim, fontSize = 11.sp) }
                        } else null,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Password,
                            imeAction = ImeAction.Done,
                        ),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Blue, unfocusedBorderColor = Surface3,
                            cursorColor = Blue, focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary,
                        ),
                    )
                }
                Spacer(Modifier.height(12.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { viewModel.saveCredentials(url, user, pass) },
                        enabled = urlIsValid && user.isNotBlank(),
                        modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Teal),
                    ) {
                        Icon(Icons.Filled.Save, null, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Save settings", fontSize = 12.sp)
                    }
                    OutlinedButton(
                        onClick = { viewModel.testConnection(url, user, pass) },
                        enabled = urlIsValid && user.isNotBlank() && hasUsablePassword && !viewModel.isSyncing,
                        modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Blue),
                    ) {
                        if (viewModel.isSyncing) {
                            CircularProgressIndicator(Modifier.size(12.dp), color = Blue, strokeWidth = 1.5.dp)
                        } else {
                            Icon(Icons.Filled.Wifi, null, modifier = Modifier.size(14.dp))
                        }
                        Spacer(Modifier.width(4.dp))
                        Text("Test connection", fontSize = 12.sp)
                    }
                }
            }
        }

        // Remote files listing
        if (viewModel.remoteFiles.isNotEmpty()) {
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    HostShieldPanelHeader(
                        icon = Icons.Filled.Folder,
                        title = "Remote files",
                        subtitle = "${viewModel.remoteFiles.size} items returned from the server",
                        accent = Blue,
                    )
                    Spacer(Modifier.height(10.dp))
                    viewModel.remoteFiles.take(20).forEach { file ->
                        Row(
                            modifier = Modifier.padding(vertical = 4.dp).fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                if (file.isDirectory) Icons.Filled.Folder else Icons.AutoMirrored.Filled.InsertDriveFile,
                                null, tint = if (file.isDirectory) Yellow else TextSecondary,
                                modifier = Modifier.size(16.dp),
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                file.name,
                                color = TextPrimary,
                                fontSize = 12.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                            if (!file.isDirectory && file.size > 0) {
                                Text(
                                    formatSize(file.size),
                                    color = TextDim, fontSize = 10.sp,
                                )
                            }
                        }
                    }
                }
            }
        } else if (viewModel.hasListedRemoteFiles && !viewModel.isSyncing) {
            HostShieldEmptyState(
                icon = Icons.Filled.CloudDone,
                title = "Connected, no remote files found",
                message = "The server accepted the credentials. HostShield backups will appear here after the first upload.",
                accent = Blue,
            )
        }

        // Message
        viewModel.message?.let { msg ->
            val isError = msg.contains("fail", ignoreCase = true)
            HostShieldStatusBanner(
                icon = if (isError) Icons.Filled.Error else Icons.Filled.CheckCircle,
                title = if (isError) "WebDAV action failed" else "WebDAV action complete",
                message = msg,
                accent = if (isError) Red else Teal,
                onDismiss = { viewModel.clearMessage() },
            )
        }

        Spacer(Modifier.height(24.dp))
    }
}

private fun formatSize(bytes: Long): String = when {
    bytes < 1024 -> "${bytes}B"
    bytes < 1024 * 1024 -> "${bytes / 1024}KB"
    else -> "${"%.1f".format(bytes / (1024.0 * 1024.0))}MB"
}

private fun isValidWebDavUrl(value: String): Boolean {
    val parsed = value.trim().toUri()
    val scheme = parsed.scheme?.lowercase()
    return (scheme == "https" || scheme == "http") && !parsed.host.isNullOrBlank()
}
