package com.hostshield.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.hostshield.data.model.BlockMethod
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

// ══════════════════════════════════════════════════════════════
// HostShield v0.2.0 — App Preferences (DataStore)
// ══════════════════════════════════════════════════════════════

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "hostshield_prefs")

@Singleton
class AppPreferences @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val ds get() = context.dataStore

    // ── Keys ─────────────────────────────────────────────────
    private object Keys {
        val BLOCK_METHOD = stringPreferencesKey("block_method")
        val IS_ENABLED = booleanPreferencesKey("is_enabled")
        val IPV4_REDIRECT = stringPreferencesKey("ipv4_redirect")
        val IPV6_REDIRECT = stringPreferencesKey("ipv6_redirect")
        val INCLUDE_IPV6 = booleanPreferencesKey("include_ipv6")
        val LOCAL_WEBSERVER = booleanPreferencesKey("local_webserver")
        val AUTO_UPDATE = booleanPreferencesKey("auto_update")
        val UPDATE_INTERVAL_HOURS = intPreferencesKey("update_interval_hours")
        val WIFI_ONLY = booleanPreferencesKey("wifi_only")
        val DNS_LOGGING = booleanPreferencesKey("dns_logging")
        val LOG_RETENTION_DAYS = intPreferencesKey("log_retention_days")
        val SHOW_NOTIFICATION = booleanPreferencesKey("show_notification")
        val LAST_APPLY_TIME = longPreferencesKey("last_apply_time")
        val LAST_APPLY_COUNT = intPreferencesKey("last_apply_count")
        val FIRST_LAUNCH = booleanPreferencesKey("first_launch")
        val DOH_ENABLED = booleanPreferencesKey("doh_enabled")
        val DOH_PROVIDER = stringPreferencesKey("doh_provider")
        val EXCLUDED_APPS = stringPreferencesKey("excluded_apps")
    }

    // ── Blocking ─────────────────────────────────────────────
    val blockMethod: Flow<BlockMethod> = ds.data.map {
        try { BlockMethod.valueOf(it[Keys.BLOCK_METHOD] ?: "ROOT_HOSTS") }
        catch (_: Exception) { BlockMethod.ROOT_HOSTS }
    }
    suspend fun setBlockMethod(method: BlockMethod) = ds.edit { it[Keys.BLOCK_METHOD] = method.name }

    val isEnabled: Flow<Boolean> = ds.data.map { it[Keys.IS_ENABLED] ?: false }
    suspend fun setEnabled(enabled: Boolean) = ds.edit { it[Keys.IS_ENABLED] = enabled }

    val ipv4Redirect: Flow<String> = ds.data.map { it[Keys.IPV4_REDIRECT] ?: "0.0.0.0" }
    suspend fun setIpv4Redirect(ip: String) = ds.edit { it[Keys.IPV4_REDIRECT] = ip }

    val ipv6Redirect: Flow<String> = ds.data.map { it[Keys.IPV6_REDIRECT] ?: "::" }
    suspend fun setIpv6Redirect(ip: String) = ds.edit { it[Keys.IPV6_REDIRECT] = ip }

    val includeIpv6: Flow<Boolean> = ds.data.map { it[Keys.INCLUDE_IPV6] ?: true }
    suspend fun setIncludeIpv6(include: Boolean) = ds.edit { it[Keys.INCLUDE_IPV6] = include }

    val localWebserver: Flow<Boolean> = ds.data.map { it[Keys.LOCAL_WEBSERVER] ?: false }
    suspend fun setLocalWebserver(enabled: Boolean) = ds.edit { it[Keys.LOCAL_WEBSERVER] = enabled }

    // ── Updates ───────────────────────────────────────────────
    val autoUpdate: Flow<Boolean> = ds.data.map { it[Keys.AUTO_UPDATE] ?: true }
    suspend fun setAutoUpdate(enabled: Boolean) = ds.edit { it[Keys.AUTO_UPDATE] = enabled }

    val updateIntervalHours: Flow<Int> = ds.data.map { it[Keys.UPDATE_INTERVAL_HOURS] ?: 24 }
    suspend fun setUpdateIntervalHours(hours: Int) = ds.edit { it[Keys.UPDATE_INTERVAL_HOURS] = hours }

    val wifiOnly: Flow<Boolean> = ds.data.map { it[Keys.WIFI_ONLY] ?: true }
    suspend fun setWifiOnly(wifiOnly: Boolean) = ds.edit { it[Keys.WIFI_ONLY] = wifiOnly }

    // ── Logging ──────────────────────────────────────────────
    val dnsLogging: Flow<Boolean> = ds.data.map { it[Keys.DNS_LOGGING] ?: true }
    suspend fun setDnsLogging(enabled: Boolean) = ds.edit { it[Keys.DNS_LOGGING] = enabled }

    val logRetentionDays: Flow<Int> = ds.data.map { it[Keys.LOG_RETENTION_DAYS] ?: 7 }
    suspend fun setLogRetentionDays(days: Int) = ds.edit { it[Keys.LOG_RETENTION_DAYS] = days }

    // ── Notification ─────────────────────────────────────────
    val showNotification: Flow<Boolean> = ds.data.map { it[Keys.SHOW_NOTIFICATION] ?: true }
    suspend fun setShowNotification(show: Boolean) = ds.edit { it[Keys.SHOW_NOTIFICATION] = show }

    // ── State ────────────────────────────────────────────────
    val lastApplyTime: Flow<Long> = ds.data.map { it[Keys.LAST_APPLY_TIME] ?: 0L }
    suspend fun setLastApplyTime(time: Long) = ds.edit { it[Keys.LAST_APPLY_TIME] = time }

    val lastApplyCount: Flow<Int> = ds.data.map { it[Keys.LAST_APPLY_COUNT] ?: 0 }
    suspend fun setLastApplyCount(count: Int) = ds.edit { it[Keys.LAST_APPLY_COUNT] = count }

    val isFirstLaunch: Flow<Boolean> = ds.data.map { it[Keys.FIRST_LAUNCH] ?: true }
    suspend fun setFirstLaunch(first: Boolean) = ds.edit { it[Keys.FIRST_LAUNCH] = first }

    // ── DoH ──────────────────────────────────────────────────
    val dohEnabled: Flow<Boolean> = ds.data.map { it[Keys.DOH_ENABLED] ?: false }
    suspend fun setDohEnabled(enabled: Boolean) = ds.edit { it[Keys.DOH_ENABLED] = enabled }

    val dohProvider: Flow<String> = ds.data.map { it[Keys.DOH_PROVIDER] ?: "cloudflare" }
    suspend fun setDohProvider(provider: String) = ds.edit { it[Keys.DOH_PROVIDER] = provider }

    // ── VPN Excluded Apps ────────────────────────────────────
    val excludedApps: Flow<Set<String>> = ds.data.map {
        (it[Keys.EXCLUDED_APPS] ?: "").split(",").filter { s -> s.isNotBlank() }.toSet()
    }
    suspend fun setExcludedApps(apps: Set<String>) = ds.edit {
        it[Keys.EXCLUDED_APPS] = apps.joinToString(",")
    }
}
