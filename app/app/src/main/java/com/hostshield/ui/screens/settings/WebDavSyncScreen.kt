package com.hostshield.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hostshield.data.preferences.AppPreferences
import com.hostshield.ui.screens.home.GlassCard
import com.hostshield.ui.theme.*
import com.hostshield.util.BackupRestoreUtil
import com.hostshield.util.WebDavSync
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
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

    fun saveCredentials(url: String, user: String, pass: String) {
        viewModelScope.launch {
            prefs.setWebdavUrl(url)
            prefs.setWebdavUsername(user)
            prefs.setWebdavPassword(pass)
            message = "Credentials saved"
        }
    }

    fun testConnection(url: String, user: String, pass: String) {
        viewModelScope.launch(Dispatchers.IO) {
            isSyncing = true
            val creds = WebDavSync.Credentials(user, pass)
            val files = webDavSync.listFiles(url, creds, "/")
            if (files != null) {
                remoteFiles = files
                message = if (files.isEmpty()) {
                    "Connected — no remote files found"
                } else {
                    "Connected — ${files.size} items found"
                }
            } else {
                message = "Connection failed — check URL and credentials"
            }
            isSyncing = false
        }
    }

    fun syncBackup(url: String, user: String, pass: String, data: ByteArray) {
        viewModelScope.launch(Dispatchers.IO) {
            isSyncing = true
            val creds = WebDavSync.Credentials(user, pass)
            val ts = java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.US).format(java.util.Date())
            val remotePath = "/hostshield_backup_$ts.json"
            val success = webDavSync.upload(url, creds, remotePath, data)
            message = if (success) "Backup uploaded to $remotePath" else "Upload failed"
            isSyncing = false
        }
    }

    fun clearMessage() { message = null }
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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = TextPrimary)
            }
            Text("WebDAV Sync", style = MaterialTheme.typography.headlineMedium, color = TextPrimary)
        }

        Text(
            "Sync backups to a WebDAV server (Nextcloud, ownCloud, WebDAV-compatible storage).",
            color = TextDim, fontSize = 12.sp, lineHeight = 16.sp,
            modifier = Modifier.padding(horizontal = 4.dp),
        )

        // Server configuration
        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Cloud, null, tint = Blue, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Server Configuration", color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                }
                Spacer(Modifier.height(12.dp))

                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text("Server URL", color = TextDim, fontSize = 12.sp) },
                    placeholder = { Text("https://cloud.example.com/remote.php/dav/files/user", color = TextDim, fontSize = 11.sp) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Blue, unfocusedBorderColor = Surface3,
                        cursorColor = Blue, focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary,
                    ),
                )
                Spacer(Modifier.height(8.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = user,
                        onValueChange = { user = it },
                        label = { Text("Username", color = TextDim, fontSize = 12.sp) },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
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
                        modifier = Modifier.weight(1f),
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
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Teal),
                    ) {
                        Icon(Icons.Filled.Save, null, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Save", fontSize = 12.sp)
                    }
                    OutlinedButton(
                        onClick = { viewModel.testConnection(url, user, pass) },
                        enabled = url.isNotBlank() && !viewModel.isSyncing,
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Blue),
                    ) {
                        if (viewModel.isSyncing) {
                            CircularProgressIndicator(Modifier.size(12.dp), color = Blue, strokeWidth = 1.5.dp)
                        } else {
                            Icon(Icons.Filled.Wifi, null, modifier = Modifier.size(14.dp))
                        }
                        Spacer(Modifier.width(4.dp))
                        Text("Test", fontSize = 12.sp)
                    }
                }
            }
        }

        // Remote files listing
        if (viewModel.remoteFiles.isNotEmpty()) {
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Remote Files", color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(8.dp))
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
                            Text(file.name, color = TextPrimary, fontSize = 12.sp, modifier = Modifier.weight(1f))
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
        }

        // Message
        viewModel.message?.let { msg ->
            val isError = msg.contains("fail", ignoreCase = true)
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = if (isError) Red.copy(alpha = 0.08f) else Teal.copy(alpha = 0.08f),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        if (isError) Icons.Filled.Error else Icons.Filled.CheckCircle,
                        null, tint = if (isError) Red else Teal, modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(msg, color = TextSecondary, fontSize = 12.sp, modifier = Modifier.weight(1f))
                    IconButton(onClick = { viewModel.clearMessage() }, modifier = Modifier.size(24.dp)) {
                        Icon(Icons.Filled.Close, "Dismiss WebDAV message", tint = TextDim, modifier = Modifier.size(14.dp))
                    }
                }
            }
        }

        Spacer(Modifier.height(24.dp))
    }
}

private fun formatSize(bytes: Long): String = when {
    bytes < 1024 -> "${bytes}B"
    bytes < 1024 * 1024 -> "${bytes / 1024}KB"
    else -> "${"%.1f".format(bytes / (1024.0 * 1024.0))}MB"
}
