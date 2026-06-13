package com.hostshield.data.preferences

import android.content.Context
import androidx.datastore.preferences.core.*
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DnsPreferences @Inject constructor(
    @param:ApplicationContext private val context: Context
) {
    private val ds get() = context.hostShieldDataStore

    internal object Keys {
        val DOH_ENABLED = booleanPreferencesKey("doh_enabled")
        val DOH_PROVIDER = stringPreferencesKey("doh_provider")
        val DOT_ENABLED = booleanPreferencesKey("dot_enabled")
        val DOT_PROVIDER = stringPreferencesKey("dot_provider")
        val DOQ_ENABLED = booleanPreferencesKey("doq_enabled")
        val DOQ_PROVIDER = stringPreferencesKey("doq_provider")
        val CUSTOM_UPSTREAM_DNS = stringPreferencesKey("custom_upstream_dns")
        val REMOTE_DOH_DOMAINS = stringPreferencesKey("remote_doh_domains")
        val REMOTE_DOH_WILDCARDS = stringPreferencesKey("remote_doh_wildcards")
        val REMOTE_DOH_VERSION = intPreferencesKey("remote_doh_version")
        val CNAME_CLOAK_DOMAINS = stringPreferencesKey("cname_cloak_domains")
        val DNS_LOGGING = booleanPreferencesKey("dns_logging")
        val LOG_RETENTION_DAYS = intPreferencesKey("log_retention_days")
        val DNS_ONLY_MODE = booleanPreferencesKey("dns_only_mode")
        val CAPTIVE_PORTAL_HANDLING = booleanPreferencesKey("captive_portal_handling")
        val ONLINE_GEOIP_ENABLED = booleanPreferencesKey("online_geoip_enabled")
    }

    // DoH
    val dohEnabled: Flow<Boolean> = ds.data.map { it[Keys.DOH_ENABLED] ?: false }
    suspend fun setDohEnabled(enabled: Boolean) = ds.edit { it[Keys.DOH_ENABLED] = enabled }

    val dohProvider: Flow<String> = ds.data.map { it[Keys.DOH_PROVIDER] ?: "cloudflare" }
    suspend fun setDohProvider(provider: String) = ds.edit { it[Keys.DOH_PROVIDER] = provider }

    // DoT
    val dotEnabled: Flow<Boolean> = ds.data.map { it[Keys.DOT_ENABLED] ?: false }
    suspend fun setDotEnabled(enabled: Boolean) = ds.edit { it[Keys.DOT_ENABLED] = enabled }

    val dotProvider: Flow<String> = ds.data.map { it[Keys.DOT_PROVIDER] ?: "cloudflare" }
    suspend fun setDotProvider(provider: String) = ds.edit { it[Keys.DOT_PROVIDER] = provider }

    // DoQ
    val doqEnabled: Flow<Boolean> = ds.data.map { it[Keys.DOQ_ENABLED] ?: false }
    suspend fun setDoqEnabled(enabled: Boolean) = ds.edit { it[Keys.DOQ_ENABLED] = enabled }

    val doqProvider: Flow<String> = ds.data.map { it[Keys.DOQ_PROVIDER] ?: "adguard" }
    suspend fun setDoqProvider(provider: String) = ds.edit { it[Keys.DOQ_PROVIDER] = provider }

    // Custom upstream
    val customUpstreamDns: Flow<String> = ds.data.map { it[Keys.CUSTOM_UPSTREAM_DNS] ?: "" }
    suspend fun setCustomUpstreamDns(dns: String) = ds.edit { it[Keys.CUSTOM_UPSTREAM_DNS] = dns }

    suspend fun getUpstreamDnsList(): List<String> {
        val raw = customUpstreamDns.first()
        return if (raw.isBlank()) emptyList()
        else raw.split(",").map { it.trim() }.filter { it.isNotBlank() }
    }

    // Remote DoH bypass
    suspend fun setRemoteDohBypassList(domains: String, wildcards: String, version: Int) = ds.edit {
        it[Keys.REMOTE_DOH_DOMAINS] = domains
        it[Keys.REMOTE_DOH_WILDCARDS] = wildcards
        it[Keys.REMOTE_DOH_VERSION] = version
    }
    suspend fun getRemoteDohDomains(): String = ds.data.map { it[Keys.REMOTE_DOH_DOMAINS] ?: "" }.first()
    suspend fun getRemoteDohWildcards(): String = ds.data.map { it[Keys.REMOTE_DOH_WILDCARDS] ?: "" }.first()
    suspend fun getRemoteDohVersion(): Int = ds.data.map { it[Keys.REMOTE_DOH_VERSION] ?: 0 }.first()

    // CNAME cloak
    suspend fun setCnameCloakDomains(domains: String) = ds.edit { it[Keys.CNAME_CLOAK_DOMAINS] = domains }
    suspend fun getCnameCloakDomains(): String = ds.data.map { it[Keys.CNAME_CLOAK_DOMAINS] ?: "" }.first()

    // Logging
    val dnsLogging: Flow<Boolean> = ds.data.map { it[Keys.DNS_LOGGING] ?: true }
    suspend fun setDnsLogging(enabled: Boolean) = ds.edit { it[Keys.DNS_LOGGING] = enabled }

    val logRetentionDays: Flow<Int> = ds.data.map { it[Keys.LOG_RETENTION_DAYS] ?: 7 }
    suspend fun setLogRetentionDays(days: Int) = ds.edit { it[Keys.LOG_RETENTION_DAYS] = days }

    // DNS-only mode
    val dnsOnlyMode: Flow<Boolean> = ds.data.map { it[Keys.DNS_ONLY_MODE] ?: false }
    suspend fun setDnsOnlyMode(enabled: Boolean) = ds.edit { it[Keys.DNS_ONLY_MODE] = enabled }

    // Captive portal
    val captivePortalHandling: Flow<Boolean> = ds.data.map { it[Keys.CAPTIVE_PORTAL_HANDLING] ?: true }
    suspend fun setCaptivePortalHandling(enabled: Boolean) = ds.edit { it[Keys.CAPTIVE_PORTAL_HANDLING] = enabled }

    // Online GeoIP fallback (ipapi.co) — disabled by default for privacy
    val onlineGeoIpEnabled: Flow<Boolean> = ds.data.map { it[Keys.ONLINE_GEOIP_ENABLED] ?: false }
    suspend fun setOnlineGeoIpEnabled(enabled: Boolean) = ds.edit { it[Keys.ONLINE_GEOIP_ENABLED] = enabled }
}
