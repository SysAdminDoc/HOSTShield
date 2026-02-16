package com.hostshield.util

import android.content.Context
import android.net.Uri
import com.hostshield.data.model.RuleType
import com.hostshield.data.model.UserRule
import com.hostshield.data.model.HostSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import javax.inject.Inject
import javax.inject.Singleton

// ══════════════════════════════════════════════════════════════
// HostShield v1.0.0 — Import / Export
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
                rules.add(UserRule(
                    hostname = obj.getString("hostname"),
                    type = RuleType.valueOf(obj.optString("type", "BLOCK")),
                    redirectIp = obj.optString("redirect_ip", ""),
                    enabled = obj.optBoolean("enabled", true),
                    comment = obj.optString("comment", ""),
                    isWildcard = obj.optBoolean("is_wildcard", false)
                ))
            }
        }

        if (root.has("sources")) {
            val arr = root.getJSONArray("sources")
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                sources.add(HostSource(
                    url = obj.getString("url"),
                    label = obj.getString("label"),
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
        BufferedReader(InputStreamReader(inputStream)).use { it.readText() }
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
                if (url.startsWith("http")) {
                    sources.add(HostSource(
                        url = url, label = label, enabled = enabled,
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
                if (host.isNotEmpty() && (isValidHost(host) || host.startsWith("*."))) {
                    block.add(UserRule(
                        hostname = host, type = RuleType.BLOCK,
                        isWildcard = host.startsWith("*.")
                    ))
                }
            }
        }

        root.optJSONArray("allowlist")?.let { arr ->
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i)
                val host = (obj?.optString("id", "") ?: arr.optString(i, "")).trim().lowercase()
                if (host.isNotEmpty() && (isValidHost(host) || host.startsWith("*."))) {
                    allow.add(UserRule(
                        hostname = host, type = RuleType.ALLOW,
                        isWildcard = host.startsWith("*.")
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

    private fun isBlockingIp(s: String): Boolean =
        s == "0.0.0.0" || s == "127.0.0.1" || s == "::" || s == "::1"

    private fun isIpLike(s: String): Boolean =
        s.matches(Regex("""^\d{1,3}\.\d{1,3}\.\d{1,3}\.\d{1,3}$"""))

    private fun isValidHost(s: String): Boolean =
        s.length in 3..253 && s.contains('.') &&
        s !in setOf("localhost", "localhost.localdomain", "local", "broadcasthost")
}
