package com.hostshield.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.hostshield.data.model.BlockMethod
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

// ══════════════════════════════════════════════════════════════
// HostShield v1.6.0 — App Preferences (DataStore)
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
        val BLOCKED_APPS = stringPreferencesKey("blocked_apps")
        val DNS_TRAP_ENABLED = booleanPreferencesKey("dns_trap_enabled")
        val NETWORK_FIREWALL_ENABLED = booleanPreferencesKey("network_firewall_enabled")
        val FIREWALL_MODE = stringPreferencesKey("firewall_mode")
        val AUTO_APPLY_FIREWALL = booleanPreferencesKey("auto_apply_firewall")
        val CONNECTION_LOG_ENABLED = booleanPreferencesKey("connection_log_enabled")
        val CUSTOM_UPSTREAM_DNS = stringPreferencesKey("custom_upstream_dns")
        val BLOCK_RESPONSE_TYPE = stringPreferencesKey("block_response_type")
        val REMOTE_DOH_DOMAINS = stringPreferencesKey("remote_doh_domains")
        val REMOTE_DOH_WILDCARDS = stringPreferencesKey("remote_doh_wildcards")
        val REMOTE_DOH_VERSION = intPreferencesKey("remote_doh_version")
        val ACCENT_COLOR = stringPreferencesKey("accent_color")
        val AUTO_BACKUP_ENABLED = booleanPreferencesKey("auto_backup_enabled")
        val AUTO_BACKUP_INTERVAL_DAYS = intPreferencesKey("auto_backup_interval_days")
        val PINNED_DOMAINS = stringPreferencesKey("pinned_domains")
        val SCHEDULE_ENABLED = booleanPreferencesKey("schedule_enabled")
        val SCHEDULE_START = stringPreferencesKey("schedule_start")   // HH:mm
        val SCHEDULE_END = stringPreferencesKey("schedule_end")       // HH:mm
        val SCHEDULE_MODE = stringPreferencesKey("schedule_mode")     // "block" or "unblock"
        val RULE_SYNC_URLS = stringPreferencesKey("rule_sync_urls")   // Comma-separated remote rule list URLs
        val CNAME_CLOAK_DOMAINS = stringPreferencesKey("cname_cloak_domains") // v5.0: AdGuard + NextDNS CNAME cloak DB
        val CAPTIVE_PORTAL_HANDLING = booleanPreferencesKey("captive_portal_handling")
        val THREAT_INTEL_ENABLED = booleanPreferencesKey("threat_intel_enabled") // v6.0: Threat intelligence feeds
        val DNS_ONLY_MODE = booleanPreferencesKey("dns_only_mode") // v6.0: DNS-only VPN (port 53 only, ~0.5% battery)
        val SAFE_SEARCH_ENABLED = booleanPreferencesKey("safe_search_enabled")
        val CONTENT_FILTER_CATEGORIES = stringSetPreferencesKey("content_filter_categories")
        val PARENTAL_ENABLED = booleanPreferencesKey("parental_enabled") // v6.1: Parental controls
        val PARENTAL_PIN_HASH = stringPreferencesKey("parental_pin_hash") // v6.1: SHA-256 of PIN
        val PARENTAL_AGE_PROFILE = stringPreferencesKey("parental_age_profile") // v6.1: CHILD, TEEN, ADULT
        val WEBDAV_URL = stringPreferencesKey("webdav_url") // v6.2: WebDAV sync server
        val WEBDAV_USERNAME = stringPreferencesKey("webdav_username")
        val WEBDAV_PASSWORD = stringPreferencesKey("webdav_password")
        val DOT_ENABLED = booleanPreferencesKey("dot_enabled") // v6.2: DNS-over-TLS (RFC 7858)
        val DOT_PROVIDER = stringPreferencesKey("dot_provider") // v6.2: cloudflare, google, quad9, adguard
        val DOQ_ENABLED = booleanPreferencesKey("doq_enabled") // v6.2: DNS-over-QUIC (RFC 9250)
        val DOQ_PROVIDER = stringPreferencesKey("doq_provider") // v6.2: adguard, nextdns, mullvad
        val WIREGUARD_ENABLED = booleanPreferencesKey("wireguard_enabled") // v6.2: WireGuard DNS proxy
        val WIREGUARD_ENDPOINT = stringPreferencesKey("wireguard_endpoint") // v6.2: host:port
        val WIREGUARD_PRIVATE_KEY = stringPreferencesKey("wireguard_private_key")
        val WIREGUARD_PUBLIC_KEY = stringPreferencesKey("wireguard_public_key")
        val WIREGUARD_PRESHARED_KEY = stringPreferencesKey("wireguard_preshared_key")
        val WIREGUARD_DNS_IP = stringPreferencesKey("wireguard_dns_ip") // DNS inside the tunnel
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

    // ── Per-App Firewall (blocked apps) ─────────────────────
    val blockedApps: Flow<Set<String>> = ds.data.map {
        (it[Keys.BLOCKED_APPS] ?: "").split(",").filter { s -> s.isNotBlank() }.toSet()
    }
    suspend fun setBlockedApps(apps: Set<String>) = ds.edit {
        it[Keys.BLOCKED_APPS] = apps.joinToString(",")
    }

    // ── DNS Trap ─────────────────────────────────────────────
    val dnsTrapEnabled: Flow<Boolean> = ds.data.map { it[Keys.DNS_TRAP_ENABLED] ?: true }
    suspend fun setDnsTrapEnabled(enabled: Boolean) = ds.edit { it[Keys.DNS_TRAP_ENABLED] = enabled }

    // ── Network Firewall (iptables) ─────────────────────────
    val networkFirewallEnabled: Flow<Boolean> = ds.data.map { it[Keys.NETWORK_FIREWALL_ENABLED] ?: false }
    suspend fun setNetworkFirewallEnabled(enabled: Boolean) = ds.edit { it[Keys.NETWORK_FIREWALL_ENABLED] = enabled }

    val firewallMode: Flow<String> = ds.data.map { it[Keys.FIREWALL_MODE] ?: "BLACKLIST" }
    suspend fun setFirewallMode(mode: String) = ds.edit { it[Keys.FIREWALL_MODE] = mode }

    val autoApplyFirewall: Flow<Boolean> = ds.data.map { it[Keys.AUTO_APPLY_FIREWALL] ?: false }
    suspend fun setAutoApplyFirewall(enabled: Boolean) = ds.edit { it[Keys.AUTO_APPLY_FIREWALL] = enabled }

    val connectionLogEnabled: Flow<Boolean> = ds.data.map { it[Keys.CONNECTION_LOG_ENABLED] ?: true }
    suspend fun setConnectionLogEnabled(enabled: Boolean) = ds.edit { it[Keys.CONNECTION_LOG_ENABLED] = enabled }

    // ── Custom Upstream DNS ─────────────────────────────────
    // Supports comma-separated list for fallback: "1.1.1.1,8.8.8.8,9.9.9.9"
    val customUpstreamDns: Flow<String> = ds.data.map { it[Keys.CUSTOM_UPSTREAM_DNS] ?: "" }
    suspend fun setCustomUpstreamDns(dns: String) = ds.edit { it[Keys.CUSTOM_UPSTREAM_DNS] = dns }

    /** Parse upstream DNS list into ordered list of servers. */
    suspend fun getUpstreamDnsList(): List<String> {
        val raw = customUpstreamDns.first()
        return if (raw.isBlank()) emptyList()
        else raw.split(",").map { it.trim() }.filter { it.isNotBlank() }
    }

    // ── Block Response Type ──────────────────────────────────
    // "nxdomain" = RCODE 3, "zero_ip" = 0.0.0.0/:: A/AAAA, "refused" = RCODE 5
    val blockResponseType: Flow<String> = ds.data.map { it[Keys.BLOCK_RESPONSE_TYPE] ?: "nxdomain" }
    suspend fun setBlockResponseType(type: String) = ds.edit { it[Keys.BLOCK_RESPONSE_TYPE] = type }

    // ── Remote DoH Bypass List ──────────────────────────────
    // Supplementary DoH domains fetched from GitHub, merged at blocklist reload.
    suspend fun setRemoteDohBypassList(domains: String, wildcards: String, version: Int) = ds.edit {
        it[Keys.REMOTE_DOH_DOMAINS] = domains
        it[Keys.REMOTE_DOH_WILDCARDS] = wildcards
        it[Keys.REMOTE_DOH_VERSION] = version
    }
    suspend fun getRemoteDohDomains(): String = ds.data.map { it[Keys.REMOTE_DOH_DOMAINS] ?: "" }.first()
    suspend fun getRemoteDohWildcards(): String = ds.data.map { it[Keys.REMOTE_DOH_WILDCARDS] ?: "" }.first()
    suspend fun getRemoteDohVersion(): Int = ds.data.map { it[Keys.REMOTE_DOH_VERSION] ?: 0 }.first()

    // ── CNAME Cloak Database (v5.0) ──────────────────────────
    // Domains from AdGuard cname-trackers + NextDNS cname-cloaking-blocklist
    suspend fun setCnameCloakDomains(domains: String) = ds.edit { it[Keys.CNAME_CLOAK_DOMAINS] = domains }
    suspend fun getCnameCloakDomains(): String = ds.data.map { it[Keys.CNAME_CLOAK_DOMAINS] ?: "" }.first()

    // ── Accent Color ─────────────────────────────────────────
    // Values: "teal", "blue", "purple", "green", "pink", "peach"
    val accentColor: Flow<String> = ds.data.map { it[Keys.ACCENT_COLOR] ?: "teal" }
    suspend fun setAccentColor(color: String) = ds.edit { it[Keys.ACCENT_COLOR] = color }

    // ── Auto Backup ──────────────────────────────────────────
    val autoBackupEnabled: Flow<Boolean> = ds.data.map { it[Keys.AUTO_BACKUP_ENABLED] ?: false }
    suspend fun setAutoBackupEnabled(enabled: Boolean) = ds.edit { it[Keys.AUTO_BACKUP_ENABLED] = enabled }

    val autoBackupIntervalDays: Flow<Int> = ds.data.map { it[Keys.AUTO_BACKUP_INTERVAL_DAYS] ?: 7 }
    suspend fun setAutoBackupIntervalDays(days: Int) = ds.edit { it[Keys.AUTO_BACKUP_INTERVAL_DAYS] = days }

    // ── Pinned Domains ───────────────────────────────────────
    val pinnedDomains: Flow<Set<String>> = ds.data.map {
        (it[Keys.PINNED_DOMAINS] ?: "").split(",").filter { s -> s.isNotBlank() }.toSet()
    }
    suspend fun setPinnedDomains(domains: Set<String>) = ds.edit {
        it[Keys.PINNED_DOMAINS] = domains.joinToString(",")
    }
    suspend fun pinDomain(domain: String) {
        val current = pinnedDomains.first()
        setPinnedDomains(current + domain.lowercase())
    }
    suspend fun unpinDomain(domain: String) {
        val current = pinnedDomains.first()
        setPinnedDomains(current - domain.lowercase())
    }

    // ── Scheduled Blocking ───────────────────────────────────
    // "block" mode: blocking is ACTIVE during schedule window
    // "unblock" mode: blocking is DISABLED during schedule window (bedtime whitelist)
    val scheduleEnabled: Flow<Boolean> = ds.data.map { it[Keys.SCHEDULE_ENABLED] ?: false }
    suspend fun setScheduleEnabled(enabled: Boolean) = ds.edit { it[Keys.SCHEDULE_ENABLED] = enabled }

    val scheduleStart: Flow<String> = ds.data.map { it[Keys.SCHEDULE_START] ?: "22:00" }
    suspend fun setScheduleStart(time: String) = ds.edit { it[Keys.SCHEDULE_START] = time }

    val scheduleEnd: Flow<String> = ds.data.map { it[Keys.SCHEDULE_END] ?: "07:00" }
    suspend fun setScheduleEnd(time: String) = ds.edit { it[Keys.SCHEDULE_END] = time }

    val scheduleMode: Flow<String> = ds.data.map { it[Keys.SCHEDULE_MODE] ?: "block" }
    suspend fun setScheduleMode(mode: String) = ds.edit { it[Keys.SCHEDULE_MODE] = mode }

    // ── Remote Rule Sync ─────────────────────────────────────
    // Comma-separated URLs pointing to hosts-format rule lists.
    // Fetched during HostsUpdateWorker runs and merged as user block rules.
    val ruleSyncUrls: Flow<String> = ds.data.map { it[Keys.RULE_SYNC_URLS] ?: "" }
    suspend fun setRuleSyncUrls(urls: String) = ds.edit { it[Keys.RULE_SYNC_URLS] = urls }

    suspend fun getRuleSyncUrlList(): List<String> {
        val raw = ruleSyncUrls.first()
        return if (raw.isBlank()) emptyList()
        else raw.split(",").map { it.trim() }.filter { it.startsWith("http") }
    }

    // ── Captive Portal ────────────────────────────────────────
    val captivePortalHandling: Flow<Boolean> = ds.data.map { it[Keys.CAPTIVE_PORTAL_HANDLING] ?: true }
    suspend fun setCaptivePortalHandling(enabled: Boolean) = ds.edit { it[Keys.CAPTIVE_PORTAL_HANDLING] = enabled }

    // ── Threat Intelligence (v6.0) ─────────────────────────────
    val threatIntelEnabled: Flow<Boolean> = ds.data.map { it[Keys.THREAT_INTEL_ENABLED] ?: true }
    suspend fun setThreatIntelEnabled(enabled: Boolean) = ds.edit { it[Keys.THREAT_INTEL_ENABLED] = enabled }

    // ── DNS-Only Mode (v6.0) ────────────────────────────────
    val dnsOnlyMode: Flow<Boolean> = ds.data.map { it[Keys.DNS_ONLY_MODE] ?: false }
    suspend fun setDnsOnlyMode(enabled: Boolean) = ds.edit { it[Keys.DNS_ONLY_MODE] = enabled }

    // ── Safe Search Enforcement (#41) ────────────────────────
    val safeSearchEnabled: Flow<Boolean> = ds.data.map { it[Keys.SAFE_SEARCH_ENABLED] ?: false }
    suspend fun setSafeSearchEnabled(enabled: Boolean) = ds.edit { it[Keys.SAFE_SEARCH_ENABLED] = enabled }

    // ── Content Filter Categories (#40) ────────────────────────
    val contentFilterCategories: Flow<Set<String>> = ds.data.map {
        it[Keys.CONTENT_FILTER_CATEGORIES] ?: emptySet()
    }
    suspend fun setContentFilterCategories(categories: Set<String>) = ds.edit {
        it[Keys.CONTENT_FILTER_CATEGORIES] = categories
    }

    // ── Parental Controls (#48) ────────────────────────────────
    val parentalEnabled: Flow<Boolean> = ds.data.map { it[Keys.PARENTAL_ENABLED] ?: false }
    suspend fun setParentalEnabled(enabled: Boolean) = ds.edit { it[Keys.PARENTAL_ENABLED] = enabled }

    val parentalPinHash: Flow<String> = ds.data.map { it[Keys.PARENTAL_PIN_HASH] ?: "" }
    suspend fun setParentalPinHash(hash: String) = ds.edit { it[Keys.PARENTAL_PIN_HASH] = hash }

    val parentalAgeProfile: Flow<String> = ds.data.map { it[Keys.PARENTAL_AGE_PROFILE] ?: "ADULT" }
    suspend fun setParentalAgeProfile(profile: String) = ds.edit { it[Keys.PARENTAL_AGE_PROFILE] = profile }

    // ── WebDAV Sync (v6.2) ─────────────────────────────────────
    val webdavUrl: Flow<String> = ds.data.map { it[Keys.WEBDAV_URL] ?: "" }
    suspend fun setWebdavUrl(url: String) = ds.edit { it[Keys.WEBDAV_URL] = url }

    val webdavUsername: Flow<String> = ds.data.map { it[Keys.WEBDAV_USERNAME] ?: "" }
    suspend fun setWebdavUsername(user: String) = ds.edit { it[Keys.WEBDAV_USERNAME] = user }

    val webdavPassword: Flow<String> = ds.data.map { it[Keys.WEBDAV_PASSWORD] ?: "" }
    suspend fun setWebdavPassword(pass: String) = ds.edit { it[Keys.WEBDAV_PASSWORD] = pass }

    // ── DNS-over-TLS (v6.2, Roadmap #44) ──────────────────────
    val dotEnabled: Flow<Boolean> = ds.data.map { it[Keys.DOT_ENABLED] ?: false }
    suspend fun setDotEnabled(enabled: Boolean) = ds.edit { it[Keys.DOT_ENABLED] = enabled }

    val dotProvider: Flow<String> = ds.data.map { it[Keys.DOT_PROVIDER] ?: "cloudflare" }
    suspend fun setDotProvider(provider: String) = ds.edit { it[Keys.DOT_PROVIDER] = provider }

    // ── DNS-over-QUIC (v6.2, Roadmap #45) ─────────────────────
    val doqEnabled: Flow<Boolean> = ds.data.map { it[Keys.DOQ_ENABLED] ?: false }
    suspend fun setDoqEnabled(enabled: Boolean) = ds.edit { it[Keys.DOQ_ENABLED] = enabled }

    val doqProvider: Flow<String> = ds.data.map { it[Keys.DOQ_PROVIDER] ?: "adguard" }
    suspend fun setDoqProvider(provider: String) = ds.edit { it[Keys.DOQ_PROVIDER] = provider }

    // ── WireGuard DNS Proxy (v6.2, Roadmap #51) ──────────────
    val wireGuardEnabled: Flow<Boolean> = ds.data.map { it[Keys.WIREGUARD_ENABLED] ?: false }
    suspend fun setWireGuardEnabled(enabled: Boolean) = ds.edit { it[Keys.WIREGUARD_ENABLED] = enabled }

    val wireGuardEndpoint: Flow<String> = ds.data.map { it[Keys.WIREGUARD_ENDPOINT] ?: "" }
    suspend fun setWireGuardEndpoint(endpoint: String) = ds.edit { it[Keys.WIREGUARD_ENDPOINT] = endpoint }

    val wireGuardPrivateKey: Flow<String> = ds.data.map { it[Keys.WIREGUARD_PRIVATE_KEY] ?: "" }
    suspend fun setWireGuardPrivateKey(key: String) = ds.edit { it[Keys.WIREGUARD_PRIVATE_KEY] = key }

    val wireGuardPublicKey: Flow<String> = ds.data.map { it[Keys.WIREGUARD_PUBLIC_KEY] ?: "" }
    suspend fun setWireGuardPublicKey(key: String) = ds.edit { it[Keys.WIREGUARD_PUBLIC_KEY] = key }

    val wireGuardPresharedKey: Flow<String> = ds.data.map { it[Keys.WIREGUARD_PRESHARED_KEY] ?: "" }
    suspend fun setWireGuardPresharedKey(key: String) = ds.edit { it[Keys.WIREGUARD_PRESHARED_KEY] = key }

    val wireGuardDnsIp: Flow<String> = ds.data.map { it[Keys.WIREGUARD_DNS_IP] ?: "" }
    suspend fun setWireGuardDnsIp(ip: String) = ds.edit { it[Keys.WIREGUARD_DNS_IP] = ip }

    // ── Search History ────────────────────────────────────────
    private object SearchKeys {
        val SEARCH_HISTORY = stringPreferencesKey("search_history")
    }

    val searchHistory: Flow<List<String>> = ds.data.map {
        (it[SearchKeys.SEARCH_HISTORY] ?: "").split("\n").filter { s -> s.isNotBlank() }
    }

    suspend fun addSearchQuery(query: String) {
        val trimmed = query.trim().lowercase()
        if (trimmed.length < 2) return
        ds.edit {
            val current = (it[SearchKeys.SEARCH_HISTORY] ?: "").split("\n").filter { s -> s.isNotBlank() }
            val updated = (listOf(trimmed) + current.filter { s -> s != trimmed }).take(10)
            it[SearchKeys.SEARCH_HISTORY] = updated.joinToString("\n")
        }
    }

    suspend fun clearSearchHistory() = ds.edit { it[SearchKeys.SEARCH_HISTORY] = "" }
}
