package com.hostshield.util

import android.content.Context
import android.net.Uri
import com.hostshield.data.source.SourceUrlPolicy
import com.hostshield.data.model.FirewallRule
import com.hostshield.data.model.RuleType
import com.hostshield.data.model.UserRule
import com.hostshield.data.model.HostSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

// ══════════════════════════════════════════════════════════════
// Import and export utilities
// ══════════════════════════════════════════════════════════════

data class ImportResult(
    val blocklist: List<UserRule> = emptyList(),
    val allowlist: List<UserRule> = emptyList(),
    val redirects: List<UserRule> = emptyList(),
    val sources: List<HostSource> = emptyList(),
    val format: String = "unknown"
)

@Singleton
class ImportExportUtil @Inject constructor() {
    companion object {
        const val MAX_IMPORT_BYTES = 25L * 1024L * 1024L
        private val HOST_LABEL_RE = Regex("""^[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?$""")
    }

    /**
     * Export all rules and sources as JSON.
     */
    fun exportJson(
        rules: List<UserRule>,
        sources: List<HostSource>
    ): String {
        val root = JSONObject()
        root.put("app", "HostShield")
        root.put("version", "1.0.0")
        root.put("exported_at", System.currentTimeMillis())

        val rulesArr = JSONArray()
        rules.forEach { rule ->
            rulesArr.put(JSONObject().apply {
                put("hostname", rule.hostname)
                put("type", rule.type.name)
                put("redirect_ip", rule.redirectIp)
                put("enabled", rule.enabled)
                put("comment", rule.comment)
                put("is_wildcard", rule.isWildcard)
            })
        }
        root.put("rules", rulesArr)

        val sourcesArr = JSONArray()
        sources.filter { !it.isBuiltin }.forEach { src ->
            sourcesArr.put(JSONObject().apply {
                put("url", src.url)
                put("label", src.label)
                put("description", src.description)
                put("category", src.category.name)
                put("enabled", src.enabled)
            })
        }
        root.put("sources", sourcesArr)

        return root.toString(2)
    }

    /**
     * Export rules as plain text hosts file format.
     */
    fun exportHostsFormat(blockRules: List<UserRule>, allowRules: List<UserRule>): String {
        val sb = StringBuilder()
        sb.appendLine("# HostShield Export")
        sb.appendLine("# ${java.time.Instant.now()}")
        sb.appendLine()

        if (allowRules.isNotEmpty()) {
            sb.appendLine("# Allowlist (prefixed with #allow#)")
            allowRules.forEach { sb.appendLine("#allow# ${it.hostname}") }
            sb.appendLine()
        }

        sb.appendLine("# Blocklist")
        blockRules.forEach { sb.appendLine("0.0.0.0 ${it.hostname}") }

        return sb.toString()
    }

    /**
     * Import from JSON (HostShield format).
     */
    suspend fun importJson(content: String): ImportResult = withContext(Dispatchers.Default) {
        val root = JSONObject(content)
        val rules = mutableListOf<UserRule>()
        val sources = mutableListOf<HostSource>()

        if (root.has("rules")) {
            val arr = root.getJSONArray("rules")
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val isWildcard = obj.optBoolean("is_wildcard", false)
                val hostname = normalizedRuleHost(obj.optString("hostname", ""), isWildcard)
                    ?: continue
                val type = try {
                    RuleType.valueOf(obj.optString("type", "BLOCK"))
                } catch (_: Exception) {
                    continue
                }
                val redirectIp = obj.optString("redirect_ip", "")
                if (type == RuleType.REDIRECT && !isIpLike(redirectIp)) continue

                rules.add(UserRule(
                    hostname = hostname,
                    type = type,
                    redirectIp = redirectIp,
                    enabled = obj.optBoolean("enabled", true),
                    comment = obj.optString("comment", ""),
                    isWildcard = isWildcard
                ))
            }
        }

        if (root.has("sources")) {
            val arr = root.getJSONArray("sources")
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val url = validatedSourceUrl(obj.optString("url", ""))
                    ?: continue
                sources.add(HostSource(
                    url = url,
                    label = obj.optString("label", url.substringAfterLast("/").take(40)),
                    description = obj.optString("description", ""),
                    category = try {
                        com.hostshield.data.model.SourceCategory.valueOf(obj.optString("category", "CUSTOM"))
                    } catch (_: Exception) { com.hostshield.data.model.SourceCategory.CUSTOM },
                    enabled = obj.optBoolean("enabled", true)
                ))
            }
        }

        ImportResult(
            blocklist = rules.filter { it.type == RuleType.BLOCK },
            allowlist = rules.filter { it.type == RuleType.ALLOW },
            redirects = rules.filter { it.type == RuleType.REDIRECT },
            sources = sources,
            format = "hostshield_json"
        )
    }

    /**
     * Import from AdAway-compatible hosts format.
     * Supports:
     *   0.0.0.0 domain
     *   127.0.0.1 domain
     *   domain (bare)
     *   #allow# domain (whitelist convention)
     */
    suspend fun importHostsFormat(content: String): ImportResult = withContext(Dispatchers.Default) {
        val block = mutableListOf<UserRule>()
        val allow = mutableListOf<UserRule>()
        val redirects = mutableListOf<UserRule>()

        content.lineSequence().forEach { rawLine ->
            val line = rawLine.trim()
            if (line.isEmpty()) return@forEach

            // Allowlist convention
            if (line.startsWith("#allow#") || line.startsWith("# allow ")) {
                val host = line.substringAfter("#allow#").substringAfter("# allow ").trim()
                if (host.isNotEmpty() && host.contains('.')) {
                    allow.add(UserRule(hostname = host.lowercase(), type = RuleType.ALLOW))
                }
                return@forEach
            }

            if (line.startsWith("#")) return@forEach

            val parts = line.split(Regex("\\s+"), limit = 3)
            when {
                parts.size >= 2 && isBlockingIp(parts[0]) -> {
                    val host = parts[1].lowercase()
                    if (isValidHost(host)) {
                        block.add(UserRule(hostname = host, type = RuleType.BLOCK))
                    }
                }
                parts.size >= 2 && isIpLike(parts[0]) -> {
                    // Redirect rule
                    val host = parts[1].lowercase()
                    if (isValidHost(host)) {
                        redirects.add(UserRule(
                            hostname = host,
                            type = RuleType.REDIRECT,
                            redirectIp = parts[0]
                        ))
                    }
                }
                parts.size == 1 && isValidHost(parts[0].lowercase()) -> {
                    block.add(UserRule(hostname = parts[0].lowercase(), type = RuleType.BLOCK))
                }
            }
        }

        ImportResult(
            blocklist = block,
            allowlist = allow,
            redirects = redirects,
            format = "hosts"
        )
    }

    /**
     * Read content from a URI (for SAF file picker).
     */
    suspend fun readUri(context: Context, uri: Uri): String = withContext(Dispatchers.IO) {
        val inputStream = context.contentResolver.openInputStream(uri)
            ?: throw Exception("Cannot open file")
        inputStream.use { stream ->
            BoundedInputReader.readUtf8(stream, MAX_IMPORT_BYTES, "Import file")
        }
    }

    /**
     * Auto-detect format and import.
     */
    suspend fun autoImport(content: String): ImportResult {
        val trimmed = content.trimStart()
        return when {
            trimmed.startsWith("{") && trimmed.contains("\"app\"") && trimmed.contains("HostShield") -> importJson(content)
            trimmed.startsWith("{") && trimmed.contains("\"adaway") -> importAdAwayBackup(content)
            trimmed.startsWith("{") && (trimmed.contains("\"blocklist\"") || trimmed.contains("\"whitelist\"")) -> importBlokadaBackup(content)
            trimmed.startsWith("{") && trimmed.contains("\"denylist\"") -> importNextDnsConfig(content)
            trimmed.startsWith("{") -> importJson(content)
            // Pi-hole domainlist CSV (starts with "id,type,domain")
            trimmed.startsWith("id,") || (trimmed.lines().firstOrNull()?.matches(Regex("^\\d+,\\d+,.+")) == true) -> importPiholeFormat(content)
            else -> importHostsFormat(content)
        }
    }

    /**
     * Import from AdAway backup JSON format.
     * AdAway exports: { "adaway_lists": [...], "blocked_hosts": [...], "allowed_hosts": [...], "redirect_hosts": [...] }
     */
    suspend fun importAdAwayBackup(content: String): ImportResult = withContext(Dispatchers.Default) {
        val root = JSONObject(content)
        val block = mutableListOf<UserRule>()
        val allow = mutableListOf<UserRule>()
        val redirects = mutableListOf<UserRule>()
        val sources = mutableListOf<HostSource>()

        // Blocked hosts
        root.optJSONArray("blocked_hosts")?.let { arr ->
            for (i in 0 until arr.length()) {
                val host = arr.optString(i, "").trim().lowercase()
                if (host.isNotEmpty() && isValidHost(host)) {
                    block.add(UserRule(hostname = host, type = RuleType.BLOCK))
                }
            }
        }

        // Allowed hosts
        root.optJSONArray("allowed_hosts")?.let { arr ->
            for (i in 0 until arr.length()) {
                val host = arr.optString(i, "").trim().lowercase()
                if (host.isNotEmpty() && isValidHost(host)) {
                    allow.add(UserRule(hostname = host, type = RuleType.ALLOW))
                }
            }
        }

        // Redirect hosts
        root.optJSONArray("redirect_hosts")?.let { arr ->
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                val host = obj.optString("hostname", "").trim().lowercase()
                val ip = obj.optString("ip", "")
                if (host.isNotEmpty() && isValidHost(host)) {
                    redirects.add(UserRule(hostname = host, type = RuleType.REDIRECT, redirectIp = ip))
                }
            }
        }

        // Lists/sources
        root.optJSONArray("adaway_lists")?.let { arr ->
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                val url = obj.optString("url", "")
                val label = obj.optString("label", url.substringAfterLast("/"))
                val enabled = obj.optBoolean("enabled", true)
                val safeUrl = validatedSourceUrl(url)
                if (safeUrl != null) {
                    sources.add(HostSource(
                        url = safeUrl, label = label.ifBlank { safeUrl.substringAfterLast("/").take(40) }, enabled = enabled,
                        category = com.hostshield.data.model.SourceCategory.CUSTOM
                    ))
                }
            }
        }

        ImportResult(
            blocklist = block, allowlist = allow, redirects = redirects,
            sources = sources, format = "adaway_backup"
        )
    }

    /**
     * Import from Blokada backup JSON.
     * Blokada exports: { "blocklist": [...], "whitelist": [...] }
     */
    suspend fun importBlokadaBackup(content: String): ImportResult = withContext(Dispatchers.Default) {
        val root = JSONObject(content)
        val block = mutableListOf<UserRule>()
        val allow = mutableListOf<UserRule>()

        root.optJSONArray("blocklist")?.let { arr ->
            for (i in 0 until arr.length()) {
                val host = arr.optString(i, "").trim().lowercase()
                if (host.isNotEmpty() && isValidHost(host)) {
                    block.add(UserRule(hostname = host, type = RuleType.BLOCK))
                }
            }
        }

        root.optJSONArray("whitelist")?.let { arr ->
            for (i in 0 until arr.length()) {
                val host = arr.optString(i, "").trim().lowercase()
                if (host.isNotEmpty() && isValidHost(host)) {
                    allow.add(UserRule(hostname = host, type = RuleType.ALLOW))
                }
            }
        }

        ImportResult(blocklist = block, allowlist = allow, format = "blokada_backup")
    }

    /**
     * Import from NextDNS config export.
     * NextDNS exports: { "denylist": [...], "allowlist": [...] }
     */
    suspend fun importNextDnsConfig(content: String): ImportResult = withContext(Dispatchers.Default) {
        val root = JSONObject(content)
        val block = mutableListOf<UserRule>()
        val allow = mutableListOf<UserRule>()

        root.optJSONArray("denylist")?.let { arr ->
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i)
                val host = (obj?.optString("id", "") ?: arr.optString(i, "")).trim().lowercase()
                val normalized = normalizedRuleHost(host, host.startsWith("*."))
                if (normalized != null) {
                    block.add(UserRule(
                        hostname = normalized, type = RuleType.BLOCK,
                        isWildcard = normalized.startsWith("*.")
                    ))
                }
            }
        }

        root.optJSONArray("allowlist")?.let { arr ->
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i)
                val host = (obj?.optString("id", "") ?: arr.optString(i, "")).trim().lowercase()
                val normalized = normalizedRuleHost(host, host.startsWith("*."))
                if (normalized != null) {
                    allow.add(UserRule(
                        hostname = normalized, type = RuleType.ALLOW,
                        isWildcard = normalized.startsWith("*.")
                    ))
                }
            }
        }

        ImportResult(blocklist = block, allowlist = allow, format = "nextdns_config")
    }

    /**
     * Export user block rules as a shareable hosts file.
     * Can be hosted on GitHub or served as a URL for other blockers to subscribe to.
     */
    fun exportShareableHostsFile(
        blockRules: List<UserRule>,
        allowRules: List<UserRule>,
        appName: String = "HostShield",
        author: String = ""
    ): String {
        val sb = StringBuilder()
        sb.appendLine("# Title: $appName Custom Blocklist")
        if (author.isNotEmpty()) sb.appendLine("# Author: $author")
        sb.appendLine("# Last modified: ${java.time.Instant.now()}")
        sb.appendLine("# Entries: ${blockRules.size} blocked, ${allowRules.size} allowed")
        sb.appendLine("# Homepage: https://github.com/SysAdminDoc/HostShield")
        sb.appendLine("# License: GPLv3")
        sb.appendLine("#")
        sb.appendLine("# This file was exported from $appName and can be used as a")
        sb.appendLine("# hosts source in any ad blocker (AdAway, HostShield, Pi-hole, etc).")
        sb.appendLine()

        if (allowRules.isNotEmpty()) {
            sb.appendLine("# ── Allowlist (informational, prefix #allow#) ──")
            allowRules.sortedBy { it.hostname }.forEach { sb.appendLine("#allow# ${it.hostname}") }
            sb.appendLine()
        }

        sb.appendLine("# ── Blocklist ──")
        blockRules.sortedBy { it.hostname }.forEach { rule ->
            if (rule.isWildcard) {
                sb.appendLine("# wildcard: ${rule.hostname}")
            } else {
                sb.appendLine("0.0.0.0 ${rule.hostname}")
            }
        }

        return sb.toString()
    }

    /**
     * Export firewall rules as JSON.
     */
    fun exportFirewallJson(rules: List<FirewallRule>): String {
        val root = JSONObject()
        root.put("app", "HostShield")
        root.put("type", "firewall_rules")
        root.put("version", "1.0.0")
        root.put("exported_at", System.currentTimeMillis())

        val arr = JSONArray()
        rules.forEach { rule ->
            arr.put(JSONObject().apply {
                put("package_name", rule.packageName)
                put("app_label", rule.appLabel)
                put("uid", rule.uid)
                put("wifi_allowed", rule.wifiAllowed)
                put("mobile_allowed", rule.mobileAllowed)
                put("vpn_allowed", rule.vpnAllowed)
                put("is_system", rule.isSystem)
                put("enabled", rule.enabled)
                put("block_screen_off", rule.blockScreenOff)
                put("block_background", rule.blockBackground)
                put("block_metered", rule.blockMetered)
            })
        }
        root.put("firewall_rules", arr)
        return root.toString(2)
    }

    data class FirewallImportResult(
        val rules: List<FirewallRule>,
        val count: Int
    )

    /**
     * Import firewall rules from JSON.
     * UIDs are NOT imported (they differ across devices); the caller
     * must resolve UIDs from package names on the target device.
     */
    suspend fun importFirewallJson(content: String): FirewallImportResult = withContext(Dispatchers.Default) {
        val root = JSONObject(content)
        val rules = mutableListOf<FirewallRule>()

        root.optJSONArray("firewall_rules")?.let { arr ->
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                rules.add(FirewallRule(
                    packageName = obj.getString("package_name"),
                    appLabel = obj.optString("app_label", ""),
                    uid = obj.optInt("uid", -1),
                    wifiAllowed = obj.optBoolean("wifi_allowed", true),
                    mobileAllowed = obj.optBoolean("mobile_allowed", true),
                    vpnAllowed = obj.optBoolean("vpn_allowed", true),
                    isSystem = obj.optBoolean("is_system", false),
                    enabled = obj.optBoolean("enabled", true),
                    blockScreenOff = obj.optBoolean("block_screen_off", false),
                    blockBackground = obj.optBoolean("block_background", false),
                    blockMetered = obj.optBoolean("block_metered", false)
                ))
            }
        }

        FirewallImportResult(rules = rules, count = rules.size)
    }

    private fun isBlockingIp(s: String): Boolean =
        s == "0.0.0.0" || s == "127.0.0.1" || s == "::" || s == "::1"

    private fun isIpLike(s: String): Boolean {
        val octets = s.split(".").map { it.toIntOrNull() ?: return false }
        return octets.size == 4 && octets.all { it in 0..255 }
    }

    /**
     * Import from Pi-hole teleporter backup (SQLite gravity.db export or domainlist CSV).
     * Pi-hole teleporter includes:
     *   - "domainlist" CSV: id,type,domain,enabled,comment
     *     type 0 = exact whitelist, 1 = exact blacklist, 2 = regex whitelist, 3 = regex blacklist
     *   - "adlist" CSV: address (URL),enabled
     */
    suspend fun importPiholeFormat(content: String): ImportResult = withContext(Dispatchers.Default) {
        val block = mutableListOf<UserRule>()
        val allow = mutableListOf<UserRule>()
        val sources = mutableListOf<HostSource>()

        content.lineSequence().forEach { rawLine ->
            val line = rawLine.trim()
            if (line.isEmpty() || line.startsWith("id,") || line.startsWith("#")) return@forEach

            val parts = line.split(",", limit = 5)
            if (parts.size >= 3 && parts[0].toIntOrNull() != null) {
                // domainlist CSV format: id,type,domain,enabled,comment
                val type = parts[1].toIntOrNull() ?: return@forEach
                val domain = parts[2].trim().removeSurrounding("\"")
                val enabled = parts.getOrNull(3)?.trim() != "0"
                val isRegex = type == 2 || type == 3

                if (isRegex && !isSafeRegex(domain)) return@forEach

                when (type) {
                    0 -> allow.add(UserRule(hostname = domain, type = RuleType.ALLOW, enabled = enabled, isRegex = isRegex))
                    1 -> block.add(UserRule(hostname = domain, type = RuleType.BLOCK, enabled = enabled, isRegex = isRegex))
                    2 -> allow.add(UserRule(hostname = domain, type = RuleType.ALLOW, enabled = enabled, isRegex = true))
                    3 -> block.add(UserRule(hostname = domain, type = RuleType.BLOCK, enabled = enabled, isRegex = true))
                }
            } else if (parts.size >= 2 && parts[0].startsWith("http")) {
                // adlist CSV format: address,enabled,...
                val url = parts[0].trim().removeSurrounding("\"")
                val enabled = parts.getOrNull(1)?.trim() != "0"
                val safeUrl = validatedSourceUrl(url)
                if (safeUrl != null) {
                    sources.add(HostSource(
                        url = safeUrl,
                        label = safeUrl.substringAfterLast("/").take(40),
                        enabled = enabled,
                        category = com.hostshield.data.model.SourceCategory.CUSTOM
                    ))
                }
            } else if (isValidHost(line.lowercase())) {
                // Plain domain list (gravity list export)
                block.add(UserRule(hostname = line.lowercase(), type = RuleType.BLOCK))
            }
        }

        ImportResult(
            blocklist = block, allowlist = allow, sources = sources,
            format = "pihole"
        )
    }

    private val nestedQuantifierRe = Regex("""\([^)]*[+*][^)]*\)[+*?]""")
    private fun isSafeRegex(pattern: String): Boolean {
        if (pattern.length > 500) return false
        if (nestedQuantifierRe.containsMatchIn(pattern)) return false
        return try { Regex(pattern); true } catch (_: Exception) { false }
    }

    private fun isValidHost(s: String): Boolean =
        s.length in 3..253 &&
            s.contains('.') &&
            s !in setOf("localhost", "localhost.localdomain", "local", "broadcasthost") &&
            s.split('.').all { label -> label.isNotBlank() && HOST_LABEL_RE.matches(label) }

    private fun normalizedRuleHost(raw: String, isWildcard: Boolean): String? {
        val host = raw.trim().lowercase().trimEnd('.')
        if (host.isBlank()) return null
        if (host.startsWith("*.") && !isWildcard) return null
        val bareHost = if (host.startsWith("*.")) host.removePrefix("*.") else host
        if (!isValidHost(bareHost)) return null
        if (isWildcard) {
            return if (host.startsWith("*.")) host else bareHost
        }
        return bareHost
    }

    private fun validatedSourceUrl(raw: String): String? {
        val validation = SourceUrlPolicy.validate(raw)
        return if (validation.isValid) validation.normalizedUrl else null
    }
}
