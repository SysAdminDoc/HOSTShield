package com.hostshield.data.preferences

import android.content.Context
import com.hostshield.data.model.BlockMethod
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

// ══════════════════════════════════════════════════════════════
// DataStore preferences facade
//
// Backward-compatible facade over domain-specific preference managers.
// New code should inject the specific manager it needs:
//   BlockingPreferences, DnsPreferences, FirewallPreferences,
//   SecurityPreferences, UiPreferences, SyncPreferences
//
// All managers share a single DataStore instance (HostShieldDataStore.kt).
// ══════════════════════════════════════════════════════════════

@Singleton
class AppPreferences @Inject constructor(
    @ApplicationContext context: Context,
    val blocking: BlockingPreferences,
    val dns: DnsPreferences,
    val firewall: FirewallPreferences,
    val security: SecurityPreferences,
    val ui: UiPreferences,
    val sync: SyncPreferences
) {
    // ── Blocking ─────────────────────────────────────────────
    val blockMethod: Flow<BlockMethod> get() = blocking.blockMethod
    suspend fun setBlockMethod(method: BlockMethod) = blocking.setBlockMethod(method)

    val isEnabled: Flow<Boolean> get() = blocking.isEnabled
    suspend fun setEnabled(enabled: Boolean) = blocking.setEnabled(enabled)

    val ipv4Redirect: Flow<String> get() = blocking.ipv4Redirect
    suspend fun setIpv4Redirect(ip: String) = blocking.setIpv4Redirect(ip)

    val ipv6Redirect: Flow<String> get() = blocking.ipv6Redirect
    suspend fun setIpv6Redirect(ip: String) = blocking.setIpv6Redirect(ip)

    val includeIpv6: Flow<Boolean> get() = blocking.includeIpv6
    suspend fun setIncludeIpv6(include: Boolean) = blocking.setIncludeIpv6(include)

    val localWebserver: Flow<Boolean> get() = blocking.localWebserver
    suspend fun setLocalWebserver(enabled: Boolean) = blocking.setLocalWebserver(enabled)

    val dnsTrapEnabled: Flow<Boolean> get() = blocking.dnsTrapEnabled
    suspend fun setDnsTrapEnabled(enabled: Boolean) = blocking.setDnsTrapEnabled(enabled)

    val blockResponseType: Flow<String> get() = blocking.blockResponseType
    suspend fun setBlockResponseType(type: String) = blocking.setBlockResponseType(type)

    val lastApplyTime: Flow<Long> get() = blocking.lastApplyTime
    suspend fun setLastApplyTime(time: Long) = blocking.setLastApplyTime(time)

    val lastApplyCount: Flow<Int> get() = blocking.lastApplyCount
    suspend fun setLastApplyCount(count: Int) = blocking.setLastApplyCount(count)

    val isFirstLaunch: Flow<Boolean> get() = blocking.isFirstLaunch
    suspend fun setFirstLaunch(first: Boolean) = blocking.setFirstLaunch(first)

    val pauseEndTime: Flow<Long> get() = blocking.pauseEndTime
    suspend fun setPauseEndTime(epochMs: Long) = blocking.setPauseEndTime(epochMs)

    val edeEnabled: Flow<Boolean> get() = blocking.edeEnabled
    suspend fun setEdeEnabled(enabled: Boolean) = blocking.setEdeEnabled(enabled)

    val lanDnsEnabled: Flow<Boolean> get() = blocking.lanDnsEnabled
    suspend fun setLanDnsEnabled(enabled: Boolean) = blocking.setLanDnsEnabled(enabled)

    val lanDnsPort: Flow<Int> get() = blocking.lanDnsPort
    suspend fun setLanDnsPort(port: Int) = blocking.setLanDnsPort(port)

    val lanDnsAllowExternalClients: Flow<Boolean> get() = blocking.lanDnsAllowExternalClients
    suspend fun setLanDnsAllowExternalClients(enabled: Boolean) =
        blocking.setLanDnsAllowExternalClients(enabled)

    // ── Updates ───────────────────────────────────────────────
    val autoUpdate: Flow<Boolean> get() = sync.autoUpdate
    suspend fun setAutoUpdate(enabled: Boolean) = sync.setAutoUpdate(enabled)

    val updateIntervalHours: Flow<Int> get() = sync.updateIntervalHours
    suspend fun setUpdateIntervalHours(hours: Int) = sync.setUpdateIntervalHours(hours)

    val wifiOnly: Flow<Boolean> get() = sync.wifiOnly
    suspend fun setWifiOnly(wifiOnly: Boolean) = sync.setWifiOnly(wifiOnly)

    // ── Logging ──────────────────────────────────────────────
    val dnsLogging: Flow<Boolean> get() = dns.dnsLogging
    suspend fun setDnsLogging(enabled: Boolean) = dns.setDnsLogging(enabled)

    val logRetentionDays: Flow<Int> get() = dns.logRetentionDays
    suspend fun setLogRetentionDays(days: Int) = dns.setLogRetentionDays(days)

    // ── Notification ─────────────────────────────────────────
    val showNotification: Flow<Boolean> get() = ui.showNotification
    suspend fun setShowNotification(show: Boolean) = ui.setShowNotification(show)

    // ── DoH ──────────────────────────────────────────────────
    val dohEnabled: Flow<Boolean> get() = dns.dohEnabled
    suspend fun setDohEnabled(enabled: Boolean) = dns.setDohEnabled(enabled)

    val dohProvider: Flow<String> get() = dns.dohProvider
    suspend fun setDohProvider(provider: String) = dns.setDohProvider(provider)

    // ── VPN Excluded Apps ────────────────────────────────────
    val excludedApps: Flow<Set<String>> get() = firewall.excludedApps
    suspend fun setExcludedApps(apps: Set<String>) = firewall.setExcludedApps(apps)
    suspend fun toggleExcludedApp(pkg: String, excluded: Boolean) = firewall.toggleExcludedApp(pkg, excluded)

    // ── Per-App Firewall (blocked apps) ─────────────────────
    val blockedApps: Flow<Set<String>> get() = firewall.blockedApps
    suspend fun setBlockedApps(apps: Set<String>) = firewall.setBlockedApps(apps)
    suspend fun toggleBlockedApp(pkg: String, blocked: Boolean) = firewall.toggleBlockedApp(pkg, blocked)

    // ── Network Firewall (iptables) ─────────────────────────
    val networkFirewallEnabled: Flow<Boolean> get() = firewall.networkFirewallEnabled
    suspend fun setNetworkFirewallEnabled(enabled: Boolean) = firewall.setNetworkFirewallEnabled(enabled)

    val firewallMode: Flow<String> get() = firewall.firewallMode
    suspend fun setFirewallMode(mode: String) = firewall.setFirewallMode(mode)

    val autoApplyFirewall: Flow<Boolean> get() = firewall.autoApplyFirewall
    suspend fun setAutoApplyFirewall(enabled: Boolean) = firewall.setAutoApplyFirewall(enabled)

    val connectionLogEnabled: Flow<Boolean> get() = firewall.connectionLogEnabled
    suspend fun setConnectionLogEnabled(enabled: Boolean) = firewall.setConnectionLogEnabled(enabled)

    // ── Custom Upstream DNS ─────────────────────────────────
    val customUpstreamDns: Flow<String> get() = dns.customUpstreamDns
    suspend fun setCustomUpstreamDns(dns_val: String) = dns.setCustomUpstreamDns(dns_val)

    suspend fun getUpstreamDnsList(): List<String> = dns.getUpstreamDnsList()

    // ── Block Response Type ──────────────────────────────────
    // (already delegated above)

    // ── Remote DoH Bypass List ──────────────────────────────
    suspend fun setRemoteDohBypassList(domains: String, wildcards: String, version: Int) =
        dns.setRemoteDohBypassList(domains, wildcards, version)
    suspend fun getRemoteDohDomains(): String = dns.getRemoteDohDomains()
    suspend fun getRemoteDohWildcards(): String = dns.getRemoteDohWildcards()
    suspend fun getRemoteDohVersion(): Int = dns.getRemoteDohVersion()

    // ── CNAME Cloak Database ──────────────────────────────────
    suspend fun setCnameCloakDomains(domains: String) = dns.setCnameCloakDomains(domains)
    suspend fun getCnameCloakDomains(): String = dns.getCnameCloakDomains()

    // ── Accent Color ─────────────────────────────────────────
    val accentColor: Flow<String> get() = ui.accentColor
    suspend fun setAccentColor(color: String) = ui.setAccentColor(color)

    // ── High Contrast AMOLED ─────────────────────────────────
    val highContrastAmoled: Flow<Boolean> get() = ui.highContrastAmoled
    suspend fun setHighContrastAmoled(enabled: Boolean) = ui.setHighContrastAmoled(enabled)

    // ── Dynamic Color (Material You) ────────────────────────
    val dynamicColor: Flow<Boolean> get() = ui.dynamicColor
    suspend fun setDynamicColor(enabled: Boolean) = ui.setDynamicColor(enabled)

    // ── Theme Mode (dark/light/system) ──────────────────────
    val themeMode: Flow<String> get() = ui.themeMode
    suspend fun setThemeMode(mode: String) = ui.setThemeMode(mode)

    // ── Auto Backup ──────────────────────────────────────────
    val autoBackupEnabled: Flow<Boolean> get() = sync.autoBackupEnabled
    suspend fun setAutoBackupEnabled(enabled: Boolean) = sync.setAutoBackupEnabled(enabled)

    val autoBackupIntervalDays: Flow<Int> get() = sync.autoBackupIntervalDays
    suspend fun setAutoBackupIntervalDays(days: Int) = sync.setAutoBackupIntervalDays(days)

    // ── Pinned Domains ───────────────────────────────────────
    val pinnedDomains: Flow<Set<String>> get() = ui.pinnedDomains
    suspend fun setPinnedDomains(domains: Set<String>) = ui.setPinnedDomains(domains)
    suspend fun pinDomain(domain: String) = ui.pinDomain(domain)
    suspend fun unpinDomain(domain: String) = ui.unpinDomain(domain)

    // ── Scheduled Blocking ───────────────────────────────────
    val scheduleEnabled: Flow<Boolean> get() = sync.scheduleEnabled
    suspend fun setScheduleEnabled(enabled: Boolean) = sync.setScheduleEnabled(enabled)

    val scheduleStart: Flow<String> get() = sync.scheduleStart
    suspend fun setScheduleStart(time: String) = sync.setScheduleStart(time)

    val scheduleEnd: Flow<String> get() = sync.scheduleEnd
    suspend fun setScheduleEnd(time: String) = sync.setScheduleEnd(time)

    val scheduleMode: Flow<String> get() = sync.scheduleMode
    suspend fun setScheduleMode(mode: String) = sync.setScheduleMode(mode)

    // ── Remote Rule Sync ─────────────────────────────────────
    val ruleSyncUrls: Flow<String> get() = sync.ruleSyncUrls
    suspend fun setRuleSyncUrls(urls: String) = sync.setRuleSyncUrls(urls)
    suspend fun getRuleSyncUrlList(): List<String> = sync.getRuleSyncUrlList()

    // ── Sync URL Hashes ────────────────────────────────────────
    suspend fun getSyncUrlHash(url: String): String? = sync.getSyncUrlHash(url)
    suspend fun setSyncUrlHash(url: String, hash: String) = sync.setSyncUrlHash(url, hash)

    // ── Captive Portal ────────────────────────────────────────
    val captivePortalHandling: Flow<Boolean> get() = dns.captivePortalHandling
    suspend fun setCaptivePortalHandling(enabled: Boolean) = dns.setCaptivePortalHandling(enabled)

    // ── Online GeoIP Fallback ───────────────────────────────
    val onlineGeoIpEnabled: Flow<Boolean> get() = dns.onlineGeoIpEnabled
    suspend fun setOnlineGeoIpEnabled(enabled: Boolean) = dns.setOnlineGeoIpEnabled(enabled)

    // ── Threat Intelligence ─────────────────────────────────────
    val threatIntelEnabled: Flow<Boolean> get() = security.threatIntelEnabled
    suspend fun setThreatIntelEnabled(enabled: Boolean) = security.setThreatIntelEnabled(enabled)

    // ── DNS-Only Mode ────────────────────────────────────────
    val dnsOnlyMode: Flow<Boolean> get() = dns.dnsOnlyMode
    suspend fun setDnsOnlyMode(enabled: Boolean) = dns.setDnsOnlyMode(enabled)

    // ── Safe Search ────────────────────────────────────────
    val safeSearchEnabled: Flow<Boolean> get() = security.safeSearchEnabled
    suspend fun setSafeSearchEnabled(enabled: Boolean) = security.setSafeSearchEnabled(enabled)

    // ── Content Filter Categories ────────────────────────────
    val contentFilterCategories: Flow<Set<String>> get() = security.contentFilterCategories
    suspend fun setContentFilterCategories(categories: Set<String>) = security.setContentFilterCategories(categories)
    suspend fun toggleContentFilterCategory(name: String, enabled: Boolean) = security.toggleContentFilterCategory(name, enabled)

    // ── Parental Controls ────────────────────────────────────
    val parentalEnabled: Flow<Boolean> get() = security.parentalEnabled
    suspend fun setParentalEnabled(enabled: Boolean) = security.setParentalEnabled(enabled)

    val parentalPinHash: Flow<String> get() = security.parentalPinHash
    suspend fun setParentalPinHash(hash: String) = security.setParentalPinHash(hash)
    suspend fun isParentalPinUndecryptable(): Boolean = security.isParentalPinUndecryptable()

    val parentalPinRehashRequired: Flow<Boolean> get() = security.parentalPinRehashRequired
    suspend fun refreshParentalPinRehashRequired() = security.refreshParentalPinRehashRequired()

    val parentalAgeProfile: Flow<String> get() = security.parentalAgeProfile
    suspend fun setParentalAgeProfile(profile: String) = security.setParentalAgeProfile(profile)

    // ── WebDAV Sync ─────────────────────────────────────────
    val webdavUrl: Flow<String> get() = sync.webdavUrl
    suspend fun setWebdavUrl(url: String) = sync.setWebdavUrl(url)

    val webdavUsername: Flow<String> get() = sync.webdavUsername
    suspend fun setWebdavUsername(user: String) = sync.setWebdavUsername(user)

    val webdavPassword: Flow<String> get() = sync.webdavPassword
    suspend fun setWebdavPassword(pass: String) = sync.setWebdavPassword(pass)

    // ── DNS-over-TLS ──────────────────────────────────────────
    val dotEnabled: Flow<Boolean> get() = dns.dotEnabled
    suspend fun setDotEnabled(enabled: Boolean) = dns.setDotEnabled(enabled)

    val dotProvider: Flow<String> get() = dns.dotProvider
    suspend fun setDotProvider(provider: String) = dns.setDotProvider(provider)

    // ── DNS-over-QUIC ─────────────────────────────────────────
    val doqEnabled: Flow<Boolean> get() = dns.doqEnabled
    suspend fun setDoqEnabled(enabled: Boolean) = dns.setDoqEnabled(enabled)

    val doqProvider: Flow<String> get() = dns.doqProvider
    suspend fun setDoqProvider(provider: String) = dns.setDoqProvider(provider)

    // ── WireGuard DNS Proxy ──────────────────────────────────
    val wireGuardEnabled: Flow<Boolean> get() = security.wireGuardEnabled
    suspend fun setWireGuardEnabled(enabled: Boolean) = security.setWireGuardEnabled(enabled)

    val wireGuardEndpoint: Flow<String> get() = security.wireGuardEndpoint
    suspend fun setWireGuardEndpoint(endpoint: String) = security.setWireGuardEndpoint(endpoint)

    val wireGuardPrivateKey: Flow<String> get() = security.wireGuardPrivateKey
    suspend fun setWireGuardPrivateKey(key: String) = security.setWireGuardPrivateKey(key)

    val wireGuardPublicKey: Flow<String> get() = security.wireGuardPublicKey
    suspend fun setWireGuardPublicKey(key: String) = security.setWireGuardPublicKey(key)

    val wireGuardPresharedKey: Flow<String> get() = security.wireGuardPresharedKey
    suspend fun setWireGuardPresharedKey(key: String) = security.setWireGuardPresharedKey(key)

    val wireGuardDnsIp: Flow<String> get() = security.wireGuardDnsIp
    suspend fun setWireGuardDnsIp(ip: String) = security.setWireGuardDnsIp(ip)

    // ── Search History ────────────────────────────────────────
    val searchHistory: Flow<List<String>> get() = ui.searchHistory
    suspend fun setSearchHistory(queries: List<String>) = ui.setSearchHistory(queries)
    suspend fun addSearchQuery(query: String) = ui.addSearchQuery(query)
    suspend fun clearSearchHistory() = ui.clearSearchHistory()
}
