package com.hostshield.util

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import com.hostshield.data.database.*
import com.hostshield.data.model.*
import com.hostshield.data.preferences.AppPreferences
import com.hostshield.data.source.SourceUrlPolicy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.Inet6Address
import java.net.InetAddress
import javax.inject.Inject
import javax.inject.Singleton

// ══════════════════════════════════════════════════════════════
// Backup and restore utilities
// ══════════════════════════════════════════════════════════════

data class BackupResult(
    val sourcesCount: Int,
    val rulesCount: Int,
    val profilesCount: Int,
    val firewallRulesCount: Int = 0
)

data class BackupPayload(
    val json: String,
    val encrypted: Boolean
)

@Singleton
class BackupRestoreUtil @Inject constructor(
    private val database: com.hostshield.data.database.HostShieldDatabase,
    private val hostSourceDao: HostSourceDao,
    private val userRuleDao: UserRuleDao,
    private val profileDao: ProfileDao,
    private val firewallRuleDao: FirewallRuleDao,
    private val appDnsRuleDao: AppDnsRuleDao,
    private val prefs: AppPreferences
) {
    companion object {
        const val BACKUP_SCHEMA_VERSION = 2
        const val MAX_BACKUP_BYTES = 25L * 1024L * 1024L
        private val HOST_LABEL_RE = Regex("""^[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?$""")
        private val PACKAGE_NAME_RE = Regex("""^[A-Za-z][A-Za-z0-9_]*(?:\.[A-Za-z][A-Za-z0-9_]*)+$""")
        private val IPV4_RE = Regex("""^(?:\d{1,3}\.){3}\d{1,3}$""")
        private val IPV6_LITERAL_RE = Regex("""^[0-9a-fA-F:]{2,45}$""")

        fun decodeBackupPayload(rawBytes: ByteArray, passphrase: String? = null): BackupPayload {
            val encrypted = BackupCrypto.isEncrypted(rawBytes)
            val json = if (encrypted) {
                if (passphrase.isNullOrEmpty()) {
                    throw EncryptedBackupException("Backup is encrypted. Please provide a passphrase.")
                }
                val decrypted = BackupCrypto.decrypt(rawBytes, passphrase)
                String(decrypted, Charsets.UTF_8)
            } else {
                String(rawBytes, Charsets.UTF_8)
            }
            return BackupPayload(json = json, encrypted = encrypted)
        }

        fun decodeBackupBytes(rawBytes: ByteArray, passphrase: String? = null): String =
            decodeBackupPayload(rawBytes, passphrase).json

        internal fun normalizeRestoredSourceUrl(rawUrl: String): String? {
            val validation = SourceUrlPolicy.validate(rawUrl)
            return if (validation.isValid) validation.normalizedUrl else null
        }

        internal fun normalizeRestoredHostname(rawHostname: String, isWildcard: Boolean = false): String? {
            val hostname = rawHostname.trim().lowercase().trimEnd('.')
            if (hostname.isBlank()) return null
            if (hostname.startsWith("*.") && !isWildcard) return null
            val bareHostname = hostname.removePrefix("*.")
            if (!isValidHostname(bareHostname)) return null
            // The canonical stored form for wildcard rules keeps the "*." prefix
            // (RulesViewModel, QR import, HostsParser.matchesWildcard all dispatch
            // on it) — stripping it here silently turned wildcards into exact rules.
            return if (isWildcard) "*.$bareHostname" else bareHostname
        }

        internal fun normalizeRestoredRegex(rawPattern: String): String? {
            val pattern = rawPattern.trim()
            if (pattern.isEmpty() || pattern.length > 500) return null
            return if (runCatching { Regex(pattern) }.isSuccess) pattern else null
        }

        internal fun normalizePackageName(rawPackageName: String): String? {
            val packageName = rawPackageName.trim()
            return if (PACKAGE_NAME_RE.matches(packageName)) packageName else null
        }

        internal fun isValidRedirectIp(rawIp: String): Boolean {
            val ip = rawIp.trim()
            return isValidIpv4(ip) || isValidIpv6(ip)
        }

        private fun isValidHostname(hostname: String): Boolean =
            hostname.length in 3..253 &&
                hostname.contains('.') &&
                hostname !in setOf("localhost", "localhost.localdomain", "local", "broadcasthost") &&
                hostname.split('.').all { label -> label.isNotBlank() && HOST_LABEL_RE.matches(label) }

        private fun isValidIpv4(ip: String): Boolean {
            if (!IPV4_RE.matches(ip)) return false
            val octets = ip.split(".").map { it.toIntOrNull() ?: return false }
            return octets.size == 4 && octets.all { it in 0..255 }
        }

        private fun isValidIpv6(ip: String): Boolean {
            if (!ip.contains(":") || !IPV6_LITERAL_RE.matches(ip)) return false
            return runCatching { InetAddress.getByName(ip) is Inet6Address }.getOrDefault(false)
        }

        private fun boundedText(value: String, maxLength: Int): String =
            value.trim().take(maxLength)
    }

    /**
     * Create a full JSON backup of all app data.
     */
    suspend fun createBackup(includeSecrets: Boolean = false): String = withContext(Dispatchers.IO) {
        val root = JSONObject()
        root.put("app", "HostShield")
        root.put("backup_version", BACKUP_SCHEMA_VERSION)
        root.put("created_at", System.currentTimeMillis())

        // Sources (all, not just enabled)
        val sourcesArr = JSONArray()
        val sources = hostSourceDao.getAllSourcesList()
        sources.forEach { src ->
            sourcesArr.put(JSONObject().apply {
                put("url", src.url)
                put("label", src.label)
                put("description", src.description)
                put("enabled", src.enabled)
                put("category", src.category.name)
                put("is_builtin", src.isBuiltin)
                put("entry_count", src.entryCount)
            })
        }
        root.put("sources", sourcesArr)

        // User rules (all, not just enabled)
        val rulesArr = JSONArray()
        val allRules = userRuleDao.getAllRulesList()
        allRules.forEach { rule ->
            rulesArr.put(JSONObject().apply {
                put("hostname", rule.hostname)
                put("type", rule.type.name)
                put("redirect_ip", rule.redirectIp)
                put("enabled", rule.enabled)
                put("comment", rule.comment)
                put("is_wildcard", rule.isWildcard)
                put("is_regex", rule.isRegex)
            })
        }
        root.put("rules", rulesArr)

        // Profiles (all, not just active)
        val profilesArr = JSONArray()
        val allProfiles = profileDao.getAllProfilesList()
        allProfiles.forEach { profile ->
            profilesArr.put(JSONObject().apply {
                put("name", profile.name)
                put("is_active", profile.isActive)
                put("source_ids", profile.sourceIds)
                put("schedule_start", profile.scheduleStart)
                put("schedule_end", profile.scheduleEnd)
                put("days_of_week", profile.daysOfWeek)
                put("wifi_ssids", profile.wifiSsids)
            })
        }
        root.put("profiles", profilesArr)

        // Firewall rules
        val fwArr = JSONArray()
        val fwRules = firewallRuleDao.getAllRulesList()
        fwRules.forEach { fw ->
            fwArr.put(JSONObject().apply {
                put("uid", fw.uid)
                put("package_name", fw.packageName)
                put("app_label", fw.appLabel)
                put("wifi_allowed", fw.wifiAllowed)
                put("mobile_allowed", fw.mobileAllowed)
                put("vpn_allowed", fw.vpnAllowed)
                put("is_system", fw.isSystem)
                put("enabled", fw.enabled)
                put("block_screen_off", fw.blockScreenOff)
                put("block_background", fw.blockBackground)
                put("block_metered", fw.blockMetered)
                put("blocked_countries", fw.blockedCountries)
                put("lan_allowed", fw.lanAllowed)
            })
        }
        root.put("firewall_rules", fwArr)

        // Per-app DNS rules
        val appDnsArr = JSONArray()
        appDnsRuleDao.getAllRulesList().forEach { rule ->
            appDnsArr.put(JSONObject().apply {
                put("package_name", rule.packageName)
                put("domain", rule.domain)
                put("action", rule.action)
                put("enabled", rule.enabled)
            })
        }
        root.put("app_dns_rules", appDnsArr)

        // Preferences
        val prefsObj = JSONObject()
        prefsObj.put("block_method", prefs.blockMethod.first().name)
        prefsObj.put("ipv4_redirect", prefs.ipv4Redirect.first())
        prefsObj.put("ipv6_redirect", prefs.ipv6Redirect.first())
        prefsObj.put("include_ipv6", prefs.includeIpv6.first())
        prefsObj.put("auto_update", prefs.autoUpdate.first())
        prefsObj.put("update_interval", prefs.updateIntervalHours.first())
        prefsObj.put("wifi_only", prefs.wifiOnly.first())
        prefsObj.put("dns_logging", prefs.dnsLogging.first())
        prefsObj.put("log_retention_days", prefs.logRetentionDays.first())
        prefsObj.put("doh_enabled", prefs.dohEnabled.first())
        prefsObj.put("doh_provider", prefs.dohProvider.first())
        prefsObj.put("excluded_apps", JSONArray(prefs.excludedApps.first().toList()))
        prefsObj.put("network_firewall_enabled", prefs.networkFirewallEnabled.first())
        prefsObj.put("firewall_mode", prefs.firewallMode.first())
        prefsObj.put("auto_apply_firewall", prefs.autoApplyFirewall.first())
        prefsObj.put("connection_log_enabled", prefs.connectionLogEnabled.first())

        // v2 blocking
        prefsObj.put("dns_trap_enabled", prefs.dnsTrapEnabled.first())
        prefsObj.put("block_response_type", prefs.blockResponseType.first())
        prefsObj.put("ede_enabled", prefs.edeEnabled.first())
        prefsObj.put("local_webserver", prefs.localWebserver.first())

        // v2 DNS
        prefsObj.put("dot_enabled", prefs.dotEnabled.first())
        prefsObj.put("dot_provider", prefs.dotProvider.first())
        prefsObj.put("doq_enabled", prefs.doqEnabled.first())
        prefsObj.put("doq_provider", prefs.doqProvider.first())
        prefsObj.put("custom_upstream_dns", prefs.customUpstreamDns.first())
        prefsObj.put("dns_only_mode", prefs.dnsOnlyMode.first())
        prefsObj.put("captive_portal_handling", prefs.captivePortalHandling.first())
        prefsObj.put("online_geoip_enabled", prefs.onlineGeoIpEnabled.first())

        // v2 security
        prefsObj.put("threat_intel_enabled", prefs.threatIntelEnabled.first())
        prefsObj.put("safe_search_enabled", prefs.safeSearchEnabled.first())
        prefsObj.put("content_filter_categories", JSONArray(prefs.contentFilterCategories.first().toList()))
        prefsObj.put("parental_enabled", prefs.parentalEnabled.first())
        prefsObj.put("parental_age_profile", prefs.parentalAgeProfile.first())
        prefsObj.put("wireguard_enabled", prefs.wireGuardEnabled.first())
        prefsObj.put("wireguard_dns_ip", prefs.wireGuardDnsIp.first())

        // v2 UI
        prefsObj.put("accent_color", prefs.accentColor.first())
        prefsObj.put("high_contrast_amoled", prefs.highContrastAmoled.first())
        prefsObj.put("dynamic_color", prefs.dynamicColor.first())
        prefsObj.put("theme_mode", prefs.themeMode.first())
        prefsObj.put("show_notification", prefs.showNotification.first())
        prefsObj.put("pinned_domains", JSONArray(prefs.pinnedDomains.first().toList()))
        prefsObj.put("search_history", JSONArray(prefs.searchHistory.first()))

        // v2 sync/schedule
        prefsObj.put("schedule_enabled", prefs.scheduleEnabled.first())
        prefsObj.put("schedule_start", prefs.scheduleStart.first())
        prefsObj.put("schedule_end", prefs.scheduleEnd.first())
        prefsObj.put("schedule_mode", prefs.scheduleMode.first())
        prefsObj.put("auto_backup_enabled", prefs.autoBackupEnabled.first())
        prefsObj.put("auto_backup_interval_days", prefs.autoBackupIntervalDays.first())
        prefsObj.put("webdav_url", prefs.webdavUrl.first())
        prefsObj.put("webdav_username", prefs.webdavUsername.first())
        prefsObj.put("rule_sync_urls", prefs.ruleSyncUrls.first())

        // v2 firewall
        prefsObj.put("blocked_apps", JSONArray(prefs.blockedApps.first().toList()))

        // v2 LAN DNS
        prefsObj.put("lan_dns_enabled", prefs.lanDnsEnabled.first())
        prefsObj.put("lan_dns_port", prefs.lanDnsPort.first())
        prefsObj.put("lan_dns_allow_external_clients", prefs.lanDnsAllowExternalClients.first())

        // The peer public key and tunnel DNS address are not credentials. The
        // endpoint, private key, optional PSK, WebDAV password, and parental
        // PIN hash are Keystore-backed secrets and are intentionally omitted
        // from plaintext/automatic backups. They are included only when the
        // caller is producing an encrypted export.
        prefsObj.put("wireguard_public_key", prefs.wireGuardPublicKey.first())

        root.put("preferences", prefsObj)

        if (includeSecrets) {
            root.put("encrypted_secrets", JSONObject().apply {
                put("wireguard_endpoint", prefs.wireGuardEndpoint.first())
                put("wireguard_private_key", prefs.wireGuardPrivateKey.first())
                put("wireguard_preshared_key", prefs.wireGuardPresharedKey.first())
                put("webdav_password", prefs.webdavPassword.first())
                put("parental_pin_hash", prefs.parentalPinHash.first())
            })
        }

        root.toString(2)
    }

    /**
     * Write backup JSON to a URI via SAF.
     * If [passphrase] is non-null and non-empty, the backup is AES-256-GCM encrypted.
     * Otherwise, plaintext JSON is written (backward-compatible).
     */
    suspend fun writeBackupToUri(
        context: Context,
        uri: Uri,
        json: String,
        passphrase: String? = null
    ) = withContext(Dispatchers.IO) {
        val bytes = if (!passphrase.isNullOrEmpty()) {
            BackupCrypto.encrypt(json.toByteArray(Charsets.UTF_8), passphrase)
        } else {
            json.toByteArray(Charsets.UTF_8)
        }
        // "wt": plain "w" does not truncate on many DocumentsProviders, so a
        // smaller backup written over a larger existing file keeps the old tail
        // bytes and becomes unrestorable.
        context.contentResolver.openOutputStream(uri, "wt")?.use { stream ->
            stream.write(bytes)
        } ?: throw Exception("Cannot open output stream")
    }

    /**
     * Restore from a backup JSON string.
     */
    suspend fun restoreBackup(json: String, allowSecrets: Boolean = false): BackupResult = withContext(Dispatchers.IO) {
        // Parse the whole document up front so a JSON error aborts before any
        // write, and run every entity insert inside a single Room transaction so
        // a mid-restore failure rolls back rather than leaving half-applied state.
        val root = JSONObject(json)
        var sourcesCount = 0
        var rulesCount = 0
        var profilesCount = 0
        var firewallRulesCount = 0

        database.withTransaction {
        // Restore sources — dedupe by URL: host_sources has no unique index on
        // url, so blind inserts would duplicate seeded/built-in sources on every
        // restore (each duplicate then downloads twice per rebuild).
        val existingSourceUrls = hostSourceDao.getAllSourcesList().map { it.url }.toMutableSet()
        if (root.has("sources")) {
            val arr = root.getJSONArray("sources")
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val url = normalizeRestoredSourceUrl(obj.optString("url", ""))
                    ?: continue
                if (!existingSourceUrls.add(url)) continue
                val label = boundedText(obj.optString("label", ""), 120)
                    .ifBlank { url.substringAfterLast('/').take(40).ifBlank { "Imported source" } }
                hostSourceDao.insert(HostSource(
                    url = url,
                    label = label,
                    description = boundedText(obj.optString("description", ""), 500),
                    enabled = obj.optBoolean("enabled", true),
                    category = try { SourceCategory.valueOf(obj.optString("category", "CUSTOM")) }
                              catch (_: Exception) { SourceCategory.CUSTOM },
                    isBuiltin = obj.optBoolean("is_builtin", false),
                    entryCount = obj.optInt("entry_count", 0).coerceAtLeast(0)
                ))
                sourcesCount++
            }
        }

        // Restore rules
        if (root.has("rules")) {
            val arr = root.getJSONArray("rules")
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val isWildcard = obj.optBoolean("is_wildcard", false)
                val isRegex = obj.optBoolean("is_regex", false)
                // Regex patterns must bypass hostname normalization (metacharacters
                // fail HOST_LABEL_RE) or every regex rule is silently dropped.
                val hostname = if (isRegex) {
                    normalizeRestoredRegex(obj.optString("hostname", "")) ?: continue
                } else {
                    normalizeRestoredHostname(obj.optString("hostname", ""), isWildcard) ?: continue
                }
                val type = try { RuleType.valueOf(obj.optString("type", "BLOCK")) }
                    catch (_: Exception) { RuleType.BLOCK }
                val redirectIp = obj.optString("redirect_ip", "").trim()
                if (type == RuleType.REDIRECT && !isValidRedirectIp(redirectIp)) continue
                userRuleDao.insert(UserRule(
                    hostname = hostname,
                    type = type,
                    redirectIp = if (type == RuleType.REDIRECT) redirectIp else "",
                    enabled = obj.optBoolean("enabled", true),
                    comment = boundedText(obj.optString("comment", ""), 500),
                    isWildcard = isWildcard && !isRegex,
                    isRegex = isRegex
                ))
                rulesCount++
            }
        }

        // Restore profiles — dedupe by name (no unique index; duplicate rows can
        // carry multiple is_active=1 flags and make getActiveProfile() nondeterministic).
        val existingProfilesByName = profileDao.getAllProfilesList().associateBy { it.name }
        val seenProfileNames = existingProfilesByName.keys.toMutableSet()
        if (root.has("profiles")) {
            val arr = root.getJSONArray("profiles")
            // Resolve the activation target before touching any flags. Deduping by
            // name used to skip the insert AND the activation, so restoring your own
            // backup — where the active profile's name already exists — cleared every
            // is_active flag and left the device with no active profile, silently
            // dropping per-profile source narrowing.
            var activationTarget: Long? = null
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val name = boundedText(obj.optString("name", ""), 80)
                if (name.isBlank()) continue
                // Only the first active profile in the backup wins.
                val wantsActive = obj.optBoolean("is_active", false) && activationTarget == null
                if (!seenProfileNames.add(name)) {
                    // Already present: adopt the existing row as the activation target.
                    if (wantsActive) activationTarget = existingProfilesByName[name]?.id
                    continue
                }
                val insertedId = profileDao.insert(BlockingProfile(
                    name = name,
                    isActive = false,
                    sourceIds = boundedText(obj.optString("source_ids", ""), 500),
                    scheduleStart = boundedText(obj.optString("schedule_start", ""), 16),
                    scheduleEnd = boundedText(obj.optString("schedule_end", ""), 16),
                    daysOfWeek = boundedText(obj.optString("days_of_week", "0,1,2,3,4,5,6"), 32),
                    wifiSsids = boundedText(obj.optString("wifi_ssids", ""), 500)
                ))
                if (wantsActive && insertedId > 0) activationTarget = insertedId
                profilesCount++
            }
            // Only disturb existing activation when the backup actually names one.
            activationTarget?.let { profileDao.activateExclusive(it) }
        }

        // Restore firewall rules. The unique uid index means a plain REPLACE
        // insert would delete an existing row and drop its context columns
        // (screen-off/background/metered/countries/LAN), so merge onto the
        // existing row when present and round-trip the context columns from the
        // backup.
        if (root.has("firewall_rules")) {
            val arr = root.getJSONArray("firewall_rules")
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val uid = obj.optInt("uid", -1)
                val packageName = normalizePackageName(obj.optString("package_name", ""))
                    ?: continue
                if (uid < 0) continue
                val existing = firewallRuleDao.getByUid(uid)
                firewallRuleDao.insert(FirewallRule(
                    uid = uid,
                    packageName = packageName,
                    appLabel = boundedText(obj.optString("app_label", ""), 120),
                    wifiAllowed = obj.optBoolean("wifi_allowed", true),
                    mobileAllowed = obj.optBoolean("mobile_allowed", true),
                    vpnAllowed = obj.optBoolean("vpn_allowed", true),
                    isSystem = obj.optBoolean("is_system", false),
                    enabled = obj.optBoolean("enabled", true),
                    blockScreenOff = obj.optBoolean("block_screen_off", existing?.blockScreenOff ?: false),
                    blockBackground = obj.optBoolean("block_background", existing?.blockBackground ?: false),
                    blockMetered = obj.optBoolean("block_metered", existing?.blockMetered ?: false),
                    blockedCountries = boundedText(obj.optString("blocked_countries", existing?.blockedCountries ?: ""), 500),
                    lanAllowed = obj.optBoolean("lan_allowed", existing?.lanAllowed ?: true)
                ))
                firewallRulesCount++
            }
        }

        // Restore per-app DNS rules
        if (root.has("app_dns_rules")) {
            val arr = root.getJSONArray("app_dns_rules")
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val packageName = normalizePackageName(obj.optString("package_name", "")) ?: continue
                val isWildcard = obj.optString("domain", "").startsWith("*.")
                val domain = normalizeRestoredHostname(obj.optString("domain", ""), isWildcard) ?: continue
                val action = obj.optString("action", "block").lowercase()
                if (action != "block" && action != "allow") continue
                appDnsRuleDao.insert(
                    AppDnsRule(
                        packageName = packageName,
                        domain = domain,
                        action = action,
                        enabled = obj.optBoolean("enabled", true),
                    )
                )
            }
        }
        } // end database.withTransaction

        // Restore preferences
        if (root.has("preferences")) {
            val p = root.getJSONObject("preferences")
            if (p.has("block_method")) prefs.setBlockMethod(
                try { BlockMethod.valueOf(p.getString("block_method")) }
                catch (_: Exception) { BlockMethod.ROOT_HOSTS }
            )
            // Redirect targets are interpolated into the root-written hosts file
            // ("<ip> <host>" lines) — restore only single IP literals so a crafted
            // backup cannot inject extra host mappings via embedded whitespace/newlines.
            if (p.has("ipv4_redirect")) {
                val v4 = p.getString("ipv4_redirect").trim()
                if (isValidRedirectIp(v4)) prefs.setIpv4Redirect(v4)
            }
            if (p.has("ipv6_redirect")) {
                val v6 = p.getString("ipv6_redirect").trim()
                if (isValidRedirectIp(v6)) prefs.setIpv6Redirect(v6)
            }
            if (p.has("include_ipv6")) prefs.setIncludeIpv6(p.getBoolean("include_ipv6"))
            if (p.has("auto_update")) prefs.setAutoUpdate(p.getBoolean("auto_update"))
            if (p.has("update_interval")) prefs.setUpdateIntervalHours(p.getInt("update_interval"))
            if (p.has("wifi_only")) prefs.setWifiOnly(p.getBoolean("wifi_only"))
            if (p.has("dns_logging")) prefs.setDnsLogging(p.getBoolean("dns_logging"))
            if (p.has("log_retention_days")) prefs.setLogRetentionDays(p.getInt("log_retention_days"))
            if (p.has("doh_enabled")) prefs.setDohEnabled(p.getBoolean("doh_enabled"))
            if (p.has("doh_provider")) prefs.setDohProvider(p.getString("doh_provider"))
            if (p.has("excluded_apps")) {
                val appsArr = p.getJSONArray("excluded_apps")
                val apps = mutableSetOf<String>()
                for (i in 0 until appsArr.length()) apps.add(appsArr.getString(i))
                prefs.setExcludedApps(apps)
            }
            if (p.has("network_firewall_enabled")) prefs.setNetworkFirewallEnabled(p.getBoolean("network_firewall_enabled"))
            if (p.has("firewall_mode")) prefs.setFirewallMode(p.getString("firewall_mode"))
            if (p.has("auto_apply_firewall")) prefs.setAutoApplyFirewall(p.getBoolean("auto_apply_firewall"))
            if (p.has("connection_log_enabled")) prefs.setConnectionLogEnabled(p.getBoolean("connection_log_enabled"))

            // v2 blocking
            if (p.has("dns_trap_enabled")) prefs.setDnsTrapEnabled(p.getBoolean("dns_trap_enabled"))
            if (p.has("block_response_type")) prefs.setBlockResponseType(p.getString("block_response_type"))
            if (p.has("ede_enabled")) prefs.setEdeEnabled(p.getBoolean("ede_enabled"))
            if (p.has("local_webserver")) prefs.setLocalWebserver(p.getBoolean("local_webserver"))

            // v2 DNS
            if (p.has("dot_enabled")) prefs.setDotEnabled(p.getBoolean("dot_enabled"))
            if (p.has("dot_provider")) prefs.setDotProvider(p.getString("dot_provider"))
            if (p.has("doq_enabled")) prefs.setDoqEnabled(p.getBoolean("doq_enabled"))
            if (p.has("doq_provider")) prefs.setDoqProvider(p.getString("doq_provider"))
            if (p.has("custom_upstream_dns")) prefs.setCustomUpstreamDns(p.getString("custom_upstream_dns"))
            if (p.has("dns_only_mode")) prefs.setDnsOnlyMode(p.getBoolean("dns_only_mode"))
            if (p.has("captive_portal_handling")) prefs.setCaptivePortalHandling(p.getBoolean("captive_portal_handling"))
            if (p.has("online_geoip_enabled")) prefs.setOnlineGeoIpEnabled(p.getBoolean("online_geoip_enabled"))

            // v2 security
            if (p.has("threat_intel_enabled")) prefs.setThreatIntelEnabled(p.getBoolean("threat_intel_enabled"))
            if (p.has("safe_search_enabled")) prefs.setSafeSearchEnabled(p.getBoolean("safe_search_enabled"))
            if (p.has("content_filter_categories")) {
                val catArr = p.getJSONArray("content_filter_categories")
                val cats = mutableSetOf<String>()
                for (i in 0 until catArr.length()) cats.add(catArr.getString(i))
                prefs.setContentFilterCategories(cats)
            }
            if (p.has("parental_enabled")) prefs.setParentalEnabled(p.getBoolean("parental_enabled"))
            if (p.has("parental_age_profile")) prefs.setParentalAgeProfile(p.getString("parental_age_profile"))
            if (p.has("wireguard_enabled")) prefs.setWireGuardEnabled(p.getBoolean("wireguard_enabled"))
            if (p.has("wireguard_dns_ip")) prefs.setWireGuardDnsIp(p.getString("wireguard_dns_ip"))
            if (p.has("wireguard_public_key")) prefs.setWireGuardPublicKey(p.getString("wireguard_public_key"))

            // v2 UI
            if (p.has("accent_color")) prefs.setAccentColor(p.getString("accent_color"))
            if (p.has("high_contrast_amoled")) prefs.setHighContrastAmoled(p.getBoolean("high_contrast_amoled"))
            if (p.has("dynamic_color")) prefs.setDynamicColor(p.getBoolean("dynamic_color"))
            if (p.has("theme_mode")) prefs.setThemeMode(p.getString("theme_mode"))
            if (p.has("show_notification")) prefs.setShowNotification(p.getBoolean("show_notification"))
            if (p.has("pinned_domains")) {
                val pinArr = p.getJSONArray("pinned_domains")
                val pins = mutableSetOf<String>()
                for (i in 0 until pinArr.length()) pins.add(pinArr.getString(i))
                prefs.setPinnedDomains(pins)
            }
            if (p.has("search_history")) {
                val historyArr = p.getJSONArray("search_history")
                val queries = mutableListOf<String>()
                for (i in 0 until historyArr.length()) queries.add(historyArr.getString(i))
                prefs.setSearchHistory(queries)
            }

            // v2 sync/schedule
            if (p.has("schedule_enabled")) prefs.setScheduleEnabled(p.getBoolean("schedule_enabled"))
            if (p.has("schedule_start")) prefs.setScheduleStart(p.getString("schedule_start"))
            if (p.has("schedule_end")) prefs.setScheduleEnd(p.getString("schedule_end"))
            if (p.has("schedule_mode")) prefs.setScheduleMode(p.getString("schedule_mode"))
            if (p.has("auto_backup_enabled")) prefs.setAutoBackupEnabled(p.getBoolean("auto_backup_enabled"))
            if (p.has("auto_backup_interval_days")) prefs.setAutoBackupIntervalDays(p.getInt("auto_backup_interval_days"))
            if (p.has("webdav_url")) prefs.setWebdavUrl(p.getString("webdav_url"))
            if (p.has("webdav_username")) prefs.setWebdavUsername(p.getString("webdav_username"))
            if (p.has("rule_sync_urls")) prefs.setRuleSyncUrls(p.getString("rule_sync_urls"))

            // v2 firewall
            if (p.has("blocked_apps")) {
                val blockedArr = p.getJSONArray("blocked_apps")
                val blocked = mutableSetOf<String>()
                for (i in 0 until blockedArr.length()) blocked.add(blockedArr.getString(i))
                prefs.setBlockedApps(blocked)
            }

            // LAN DNS is preference-only state; the service observer owns the
            // actual foreground-service lifecycle after restore.
            if (p.has("lan_dns_enabled")) prefs.setLanDnsEnabled(p.getBoolean("lan_dns_enabled"))
            if (p.has("lan_dns_port")) {
                val port = p.getInt("lan_dns_port")
                if (port in 1024..65535) prefs.setLanDnsPort(port)
            }
            if (p.has("lan_dns_allow_external_clients")) {
                prefs.setLanDnsAllowExternalClients(p.getBoolean("lan_dns_allow_external_clients"))
            }

            if (allowSecrets) {
                root.optJSONObject("encrypted_secrets")?.let { secrets ->
                    if (secrets.has("wireguard_endpoint")) {
                        prefs.setWireGuardEndpoint(secrets.getString("wireguard_endpoint"))
                    }
                    if (secrets.has("wireguard_private_key")) {
                        prefs.setWireGuardPrivateKey(secrets.getString("wireguard_private_key"))
                    }
                    if (secrets.has("wireguard_preshared_key")) {
                        prefs.setWireGuardPresharedKey(secrets.getString("wireguard_preshared_key"))
                    }
                    if (secrets.has("webdav_password")) {
                        prefs.setWebdavPassword(secrets.getString("webdav_password"))
                    }
                    if (secrets.has("parental_pin_hash")) {
                        prefs.setParentalPinHash(secrets.getString("parental_pin_hash"))
                    }
                }
            }
        }

        BackupResult(sourcesCount, rulesCount, profilesCount, firewallRulesCount)
    }

    /**
     * Read backup file content from SAF URI.
     * Automatically detects whether the file is encrypted.
     * If encrypted and [passphrase] is provided, decrypts and returns the JSON.
     * If encrypted but no passphrase is given, throws an exception
     * so the caller can prompt the user for a passphrase.
     * Plaintext JSON files are returned as-is regardless of passphrase.
     */
    suspend fun readBackupPayloadFromUri(
        context: Context,
        uri: Uri,
        passphrase: String? = null
    ): BackupPayload = withContext(Dispatchers.IO) {
        val rawBytes = context.contentResolver.openInputStream(uri)?.use { stream ->
            BoundedInputReader.readBytes(stream, MAX_BACKUP_BYTES, "Backup file")
        } ?: throw Exception("Cannot open input stream")

        decodeBackupPayload(rawBytes, passphrase)
    }

    suspend fun readBackupFromUri(
        context: Context,
        uri: Uri,
        passphrase: String? = null
    ): String = readBackupPayloadFromUri(context, uri, passphrase).json
}

/**
 * Thrown when an encrypted backup is encountered but no passphrase was provided.
 * The UI layer should catch this to prompt the user for a passphrase.
 */
class EncryptedBackupException(message: String) : Exception(message)
