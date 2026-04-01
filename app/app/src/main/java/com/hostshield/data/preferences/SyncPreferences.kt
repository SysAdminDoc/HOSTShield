package com.hostshield.data.preferences

import android.content.Context
import androidx.datastore.preferences.core.*
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SyncPreferences @Inject constructor(
    @ApplicationContext private val context: Context,
    private val secureStore: SecureStore
) {
    private val ds get() = context.hostShieldDataStore

    private companion object {
        const val SEC_WEBDAV_PASSWORD = "sec_webdav_password"
    }

    internal object Keys {
        val AUTO_UPDATE = booleanPreferencesKey("auto_update")
        val UPDATE_INTERVAL_HOURS = intPreferencesKey("update_interval_hours")
        val WIFI_ONLY = booleanPreferencesKey("wifi_only")
        val AUTO_BACKUP_ENABLED = booleanPreferencesKey("auto_backup_enabled")
        val AUTO_BACKUP_INTERVAL_DAYS = intPreferencesKey("auto_backup_interval_days")
        val WEBDAV_URL = stringPreferencesKey("webdav_url")
        val WEBDAV_USERNAME = stringPreferencesKey("webdav_username")
        val WEBDAV_PASSWORD = stringPreferencesKey("webdav_password")
        val RULE_SYNC_URLS = stringPreferencesKey("rule_sync_urls")
        val SCHEDULE_ENABLED = booleanPreferencesKey("schedule_enabled")
        val SCHEDULE_START = stringPreferencesKey("schedule_start")
        val SCHEDULE_END = stringPreferencesKey("schedule_end")
        val SCHEDULE_MODE = stringPreferencesKey("schedule_mode")
    }

    // Auto-update
    val autoUpdate: Flow<Boolean> = ds.data.map { it[Keys.AUTO_UPDATE] ?: true }
    suspend fun setAutoUpdate(enabled: Boolean) = ds.edit { it[Keys.AUTO_UPDATE] = enabled }

    val updateIntervalHours: Flow<Int> = ds.data.map { it[Keys.UPDATE_INTERVAL_HOURS] ?: 24 }
    suspend fun setUpdateIntervalHours(hours: Int) = ds.edit { it[Keys.UPDATE_INTERVAL_HOURS] = hours }

    val wifiOnly: Flow<Boolean> = ds.data.map { it[Keys.WIFI_ONLY] ?: true }
    suspend fun setWifiOnly(wifiOnly: Boolean) = ds.edit { it[Keys.WIFI_ONLY] = wifiOnly }

    // Auto backup
    val autoBackupEnabled: Flow<Boolean> = ds.data.map { it[Keys.AUTO_BACKUP_ENABLED] ?: false }
    suspend fun setAutoBackupEnabled(enabled: Boolean) = ds.edit { it[Keys.AUTO_BACKUP_ENABLED] = enabled }

    val autoBackupIntervalDays: Flow<Int> = ds.data.map { it[Keys.AUTO_BACKUP_INTERVAL_DAYS] ?: 7 }
    suspend fun setAutoBackupIntervalDays(days: Int) = ds.edit { it[Keys.AUTO_BACKUP_INTERVAL_DAYS] = days }

    /**
     * Migrate plaintext WebDAV password from DataStore into SecureStore.
     * Call once at app startup.
     */
    suspend fun migratePlaintextSecrets() {
        if (!secureStore.contains(SEC_WEBDAV_PASSWORD)) {
            val plaintext = ds.data.map { it[Keys.WEBDAV_PASSWORD] }.first()
            if (!plaintext.isNullOrEmpty()) {
                secureStore.putString(SEC_WEBDAV_PASSWORD, plaintext)
                ds.edit { it.remove(Keys.WEBDAV_PASSWORD) }
            }
        }
    }

    // WebDAV
    val webdavUrl: Flow<String> = ds.data.map { it[Keys.WEBDAV_URL] ?: "" }
    suspend fun setWebdavUrl(url: String) = ds.edit { it[Keys.WEBDAV_URL] = url }

    val webdavUsername: Flow<String> = ds.data.map { it[Keys.WEBDAV_USERNAME] ?: "" }
    suspend fun setWebdavUsername(user: String) = ds.edit { it[Keys.WEBDAV_USERNAME] = user }

    /** WebDAV password is now served from SecureStore (Flow wrapper for API compat). */
    val webdavPassword: Flow<String> get() = flowOf(secureStore.getString(SEC_WEBDAV_PASSWORD))
    suspend fun setWebdavPassword(pass: String) = secureStore.putString(SEC_WEBDAV_PASSWORD, pass)

    // Rule sync
    val ruleSyncUrls: Flow<String> = ds.data.map { it[Keys.RULE_SYNC_URLS] ?: "" }
    suspend fun setRuleSyncUrls(urls: String) = ds.edit { it[Keys.RULE_SYNC_URLS] = urls }

    suspend fun getRuleSyncUrlList(): List<String> {
        val raw = ruleSyncUrls.first()
        return if (raw.isBlank()) emptyList()
        else raw.split(",").map { it.trim() }.filter { it.startsWith("http") }
    }

    // Sync URL content hashes — for integrity tracking
    suspend fun getSyncUrlHash(url: String): String? {
        val key = stringPreferencesKey("sync_url_hash_${url.hashCode()}")
        return ds.data.map { it[key] }.first()
    }

    suspend fun setSyncUrlHash(url: String, hash: String) {
        val key = stringPreferencesKey("sync_url_hash_${url.hashCode()}")
        ds.edit { it[key] = hash }
    }

    // Schedule
    val scheduleEnabled: Flow<Boolean> = ds.data.map { it[Keys.SCHEDULE_ENABLED] ?: false }
    suspend fun setScheduleEnabled(enabled: Boolean) = ds.edit { it[Keys.SCHEDULE_ENABLED] = enabled }

    val scheduleStart: Flow<String> = ds.data.map { it[Keys.SCHEDULE_START] ?: "22:00" }
    suspend fun setScheduleStart(time: String) = ds.edit { it[Keys.SCHEDULE_START] = time }

    val scheduleEnd: Flow<String> = ds.data.map { it[Keys.SCHEDULE_END] ?: "07:00" }
    suspend fun setScheduleEnd(time: String) = ds.edit { it[Keys.SCHEDULE_END] = time }

    val scheduleMode: Flow<String> = ds.data.map { it[Keys.SCHEDULE_MODE] ?: "block" }
    suspend fun setScheduleMode(mode: String) = ds.edit { it[Keys.SCHEDULE_MODE] = mode }
}
