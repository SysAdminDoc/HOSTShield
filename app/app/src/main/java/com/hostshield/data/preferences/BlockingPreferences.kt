package com.hostshield.data.preferences

import android.content.Context
import androidx.datastore.preferences.core.*
import com.hostshield.data.model.BlockMethod
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BlockingPreferences @Inject constructor(
    @param:ApplicationContext private val context: Context
) {
    private val ds get() = context.hostShieldDataStore

    internal object Keys {
        val BLOCK_METHOD = stringPreferencesKey("block_method")
        val IS_ENABLED = booleanPreferencesKey("is_enabled")
        val IPV4_REDIRECT = stringPreferencesKey("ipv4_redirect")
        val IPV6_REDIRECT = stringPreferencesKey("ipv6_redirect")
        val INCLUDE_IPV6 = booleanPreferencesKey("include_ipv6")
        val LOCAL_WEBSERVER = booleanPreferencesKey("local_webserver")
        val DNS_TRAP_ENABLED = booleanPreferencesKey("dns_trap_enabled")
        val BLOCK_RESPONSE_TYPE = stringPreferencesKey("block_response_type")
        val LAST_APPLY_TIME = longPreferencesKey("last_apply_time")
        val LAST_APPLY_COUNT = intPreferencesKey("last_apply_count")
        val FIRST_LAUNCH = booleanPreferencesKey("first_launch")
        val PAUSE_END_TIME = longPreferencesKey("pause_end_time")
        val EDE_ENABLED = booleanPreferencesKey("ede_enabled")
        val LAN_DNS_ENABLED = booleanPreferencesKey("lan_dns_enabled")
        val LAN_DNS_PORT = intPreferencesKey("lan_dns_port")
        val LAN_DNS_ALLOW_EXTERNAL_CLIENTS = booleanPreferencesKey("lan_dns_allow_external_clients")
    }

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

    val dnsTrapEnabled: Flow<Boolean> = ds.data.map { it[Keys.DNS_TRAP_ENABLED] ?: true }
    suspend fun setDnsTrapEnabled(enabled: Boolean) = ds.edit { it[Keys.DNS_TRAP_ENABLED] = enabled }

    val blockResponseType: Flow<String> = ds.data.map { it[Keys.BLOCK_RESPONSE_TYPE] ?: "nxdomain" }
    suspend fun setBlockResponseType(type: String) = ds.edit { it[Keys.BLOCK_RESPONSE_TYPE] = type }

    val lastApplyTime: Flow<Long> = ds.data.map { it[Keys.LAST_APPLY_TIME] ?: 0L }
    suspend fun setLastApplyTime(time: Long) = ds.edit { it[Keys.LAST_APPLY_TIME] = time }

    val lastApplyCount: Flow<Int> = ds.data.map { it[Keys.LAST_APPLY_COUNT] ?: 0 }
    suspend fun setLastApplyCount(count: Int) = ds.edit { it[Keys.LAST_APPLY_COUNT] = count }

    val isFirstLaunch: Flow<Boolean> = ds.data.map { it[Keys.FIRST_LAUNCH] ?: true }
    suspend fun setFirstLaunch(first: Boolean) = ds.edit { it[Keys.FIRST_LAUNCH] = first }

    val pauseEndTime: Flow<Long> = ds.data.map { it[Keys.PAUSE_END_TIME] ?: 0L }
    suspend fun setPauseEndTime(epochMs: Long) = ds.edit { it[Keys.PAUSE_END_TIME] = epochMs }

    val edeEnabled: Flow<Boolean> = ds.data.map { it[Keys.EDE_ENABLED] ?: false }
    suspend fun setEdeEnabled(enabled: Boolean) = ds.edit { it[Keys.EDE_ENABLED] = enabled }

    val lanDnsEnabled: Flow<Boolean> = ds.data.map { it[Keys.LAN_DNS_ENABLED] ?: false }
    suspend fun setLanDnsEnabled(enabled: Boolean) = ds.edit { it[Keys.LAN_DNS_ENABLED] = enabled }

    val lanDnsPort: Flow<Int> = ds.data.map { it[Keys.LAN_DNS_PORT] ?: 5353 }
    suspend fun setLanDnsPort(port: Int) = ds.edit { it[Keys.LAN_DNS_PORT] = port }

    val lanDnsAllowExternalClients: Flow<Boolean> =
        ds.data.map { it[Keys.LAN_DNS_ALLOW_EXTERNAL_CLIENTS] ?: false }
    suspend fun setLanDnsAllowExternalClients(enabled: Boolean) =
        ds.edit { it[Keys.LAN_DNS_ALLOW_EXTERNAL_CLIENTS] = enabled }
}
