package com.hostshield.ui.screens.settings

import androidx.compose.runtime.*
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hostshield.data.preferences.AppPreferences
import com.hostshield.util.BackupRestoreUtil
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
    private val backupRestoreUtil: BackupRestoreUtil,
) : ViewModel() {

    val serverUrl = prefs.webdavUrl.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")
    val username = prefs.webdavUsername.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")

    var isSyncing by mutableStateOf(false)
        private set
    var message by mutableStateOf<String?>(null)
        private set
    var messageIsError by mutableStateOf(false)
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
                setMessage("Use a complete HTTPS WebDAV URL.", isError = true)
                return@launch
            }
            prefs.setWebdavUrl(normalizedUrl)
            prefs.setWebdavUsername(user.trim())
            if (pass.isNotBlank()) {
                prefs.setWebdavPassword(pass)
                hasSavedPassword = true
                setMessage("Credentials saved", isError = false)
            } else {
                if (hasSavedPassword) {
                    setMessage("Settings saved. Existing password kept.", isError = false)
                } else {
                    setMessage("Server settings saved. Add a password before syncing.", isError = false)
                }
            }
        }
    }

    fun testConnection(url: String, user: String, pass: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val normalizedUrl = WebDavSync.normalizedServerUrlOrNull(url)
            if (normalizedUrl == null) {
                setMessage("Use a complete HTTPS WebDAV URL.", isError = true)
                return@launch
            }
            val password = resolvePassword(pass)
            if (password.isBlank()) {
                setMessage("Enter a WebDAV password or app token", isError = true)
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
                    if (files.isEmpty()) {
                        setMessage("Connected. No remote files found.", isError = false)
                    } else {
                        setMessage("Connected. ${files.size} items found.", isError = false)
                    }
                } else {
                    setMessage("Connection failed - check URL and credentials", isError = true)
                }
            } catch (e: Exception) {
                android.util.Log.w("WebDavSync", "Connection test failed", e)
                setMessage("Connection failed - check URL and credentials", isError = true)
            } finally {
                isSyncing = false
            }
        }
    }

    /**
     * Build a backup and upload it to the canonical `/HostShield/backups`
     * directory via [WebDavSync.syncBackup]. Uses the saved server settings so
     * the screen can offer a one-tap "Upload backup now" action.
     */
    fun uploadBackupNow(url: String, user: String, pass: String) {
        viewModelScope.launch(Dispatchers.IO) {
            // Use the TYPED field values, like testConnection: the button is
            // enabled from what's on screen, so executing with the saved
            // settings either failed ("enter a URL" with a valid URL visible)
            // or silently uploaded to the previously-saved server.
            val normalizedUrl = WebDavSync.normalizedServerUrlOrNull(url)
            if (normalizedUrl == null) {
                setMessage("Use a complete HTTPS WebDAV URL.", isError = true)
                return@launch
            }
            val password = resolvePassword(pass)
            if (password.isBlank()) {
                setMessage("Enter a WebDAV password or app token.", isError = true)
                return@launch
            }
            isSyncing = true
            message = null
            try {
                val data = backupRestoreUtil.createBackup().toByteArray(Charsets.UTF_8)
                val creds = WebDavSync.Credentials(user.trim(), password)
                when (val result = webDavSync.syncBackup(normalizedUrl, creds, data)) {
                    is WebDavSync.SyncResult.Success -> {
                        setMessage("Backup uploaded to /HostShield/backups", isError = false)
                        // Refresh the listing so the new backup shows.
                        webDavSync.listFiles(normalizedUrl, creds, "/")?.let {
                            remoteFiles = it
                            hasListedRemoteFiles = true
                        }
                    }
                    is WebDavSync.SyncResult.AuthError ->
                        setMessage("Upload failed - authentication rejected", isError = true)
                    is WebDavSync.SyncResult.ServerError ->
                        setMessage("Upload failed - server error ${result.code}", isError = true)
                    is WebDavSync.SyncResult.NetworkError ->
                        setMessage("Upload failed - ${result.message}", isError = true)
                    is WebDavSync.SyncResult.ParseError ->
                        setMessage("Upload failed - unexpected server response", isError = true)
                }
            } catch (e: Exception) {
                android.util.Log.w("WebDavSync", "Backup upload failed", e)
                setMessage("Upload failed - check server settings and retry", isError = true)
            } finally {
                isSyncing = false
            }
        }
    }

    fun clearMessage() { message = null }

    private fun setMessage(text: String, isError: Boolean) {
        message = text
        messageIsError = isError
    }

    private suspend fun resolvePassword(input: String): String =
        input.ifBlank { prefs.webdavPassword.first() }
}
