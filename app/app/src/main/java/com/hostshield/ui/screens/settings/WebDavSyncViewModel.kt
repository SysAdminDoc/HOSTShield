package com.hostshield.ui.screens.settings

import androidx.compose.runtime.*
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hostshield.data.preferences.AppPreferences
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
            val normalizedUrl = WebDavSync.normalizedServerUrlOrNull(url)
            if (normalizedUrl == null) {
                message = "Use a complete HTTPS WebDAV URL."
                return@launch
            }
            prefs.setWebdavUrl(normalizedUrl)
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
            val normalizedUrl = WebDavSync.normalizedServerUrlOrNull(url)
            if (normalizedUrl == null) {
                message = "Use a complete HTTPS WebDAV URL."
                return@launch
            }
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
                val files = webDavSync.listFiles(normalizedUrl, creds, "/")
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
            val normalizedUrl = WebDavSync.normalizedServerUrlOrNull(url)
            if (normalizedUrl == null) {
                message = "Use a complete HTTPS WebDAV URL."
                return@launch
            }
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
                val success = webDavSync.upload(normalizedUrl, creds, remotePath, data)
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
