package com.hostshield.data.preferences

import android.content.Context
import androidx.datastore.preferences.core.*
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FirewallPreferences @Inject constructor(
    @param:ApplicationContext private val context: Context
) {
    private val ds get() = context.hostShieldDataStore

    internal object Keys {
        val NETWORK_FIREWALL_ENABLED = booleanPreferencesKey("network_firewall_enabled")
        val FIREWALL_MODE = stringPreferencesKey("firewall_mode")
        val AUTO_APPLY_FIREWALL = booleanPreferencesKey("auto_apply_firewall")
        val CONNECTION_LOG_ENABLED = booleanPreferencesKey("connection_log_enabled")
        val EXCLUDED_APPS = stringPreferencesKey("excluded_apps")
        val BLOCKED_APPS = stringPreferencesKey("blocked_apps")
    }

    val networkFirewallEnabled: Flow<Boolean> = ds.data.map { it[Keys.NETWORK_FIREWALL_ENABLED] ?: false }
    suspend fun setNetworkFirewallEnabled(enabled: Boolean) = ds.edit { it[Keys.NETWORK_FIREWALL_ENABLED] = enabled }

    val firewallMode: Flow<String> = ds.data.map { it[Keys.FIREWALL_MODE] ?: "BLACKLIST" }
    suspend fun setFirewallMode(mode: String) = ds.edit { it[Keys.FIREWALL_MODE] = mode }

    val autoApplyFirewall: Flow<Boolean> = ds.data.map { it[Keys.AUTO_APPLY_FIREWALL] ?: false }
    suspend fun setAutoApplyFirewall(enabled: Boolean) = ds.edit { it[Keys.AUTO_APPLY_FIREWALL] = enabled }

    val connectionLogEnabled: Flow<Boolean> = ds.data.map { it[Keys.CONNECTION_LOG_ENABLED] ?: true }
    suspend fun setConnectionLogEnabled(enabled: Boolean) = ds.edit { it[Keys.CONNECTION_LOG_ENABLED] = enabled }

    val excludedApps: Flow<Set<String>> = ds.data.map {
        (it[Keys.EXCLUDED_APPS] ?: "").split(",").filter { s -> s.isNotBlank() }.toSet()
    }
    suspend fun setExcludedApps(apps: Set<String>) = ds.edit {
        it[Keys.EXCLUDED_APPS] = apps.joinToString(",")
    }

    val blockedApps: Flow<Set<String>> = ds.data.map {
        (it[Keys.BLOCKED_APPS] ?: "").split(",").filter { s -> s.isNotBlank() }.toSet()
    }
    suspend fun setBlockedApps(apps: Set<String>) = ds.edit {
        it[Keys.BLOCKED_APPS] = apps.joinToString(",")
    }
}
