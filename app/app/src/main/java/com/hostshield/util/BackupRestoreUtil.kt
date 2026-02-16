package com.hostshield.util

import android.content.Context
import android.net.Uri
import com.hostshield.data.database.*
import com.hostshield.data.model.*
import com.hostshield.data.preferences.AppPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

// ══════════════════════════════════════════════════════════════
// HostShield v0.2.0 — Full Backup / Restore
// ══════════════════════════════════════════════════════════════

data class BackupResult(
    val sourcesCount: Int,
    val rulesCount: Int,
    val profilesCount: Int
)

@Singleton
class BackupRestoreUtil @Inject constructor(
    private val hostSourceDao: HostSourceDao,
    private val userRuleDao: UserRuleDao,
    private val profileDao: ProfileDao,
    private val prefs: AppPreferences
) {
    /**
     * Create a full JSON backup of all app data.
     */
    suspend fun createBackup(): String = withContext(Dispatchers.IO) {
        val root = JSONObject()
        root.put("app", "HostShield")
        root.put("backup_version", 1)
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
            })
        }
        root.put("profiles", profilesArr)

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
        root.put("preferences", prefsObj)

        root.toString(2)
    }

    /**
     * Write backup JSON to a URI via SAF.
     */
    suspend fun writeBackupToUri(context: Context, uri: Uri, json: String) = withContext(Dispatchers.IO) {
        context.contentResolver.openOutputStream(uri)?.use { stream ->
            stream.write(json.toByteArray(Charsets.UTF_8))
        } ?: throw Exception("Cannot open output stream")
    }

    /**
     * Restore from a backup JSON string.
     */
    suspend fun restoreBackup(json: String): BackupResult = withContext(Dispatchers.IO) {
        val root = JSONObject(json)
        var sourcesCount = 0
        var rulesCount = 0
        var profilesCount = 0

        // Restore sources
        if (root.has("sources")) {
            val arr = root.getJSONArray("sources")
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                hostSourceDao.insert(HostSource(
                    url = obj.getString("url"),
                    label = obj.getString("label"),
                    description = obj.optString("description", ""),
                    enabled = obj.optBoolean("enabled", true),
                    category = try { SourceCategory.valueOf(obj.optString("category", "CUSTOM")) }
                              catch (_: Exception) { SourceCategory.CUSTOM },
                    isBuiltin = obj.optBoolean("is_builtin", false),
                    entryCount = obj.optInt("entry_count", 0)
                ))
                sourcesCount++
            }
        }

        // Restore rules
        if (root.has("rules")) {
            val arr = root.getJSONArray("rules")
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                userRuleDao.insert(UserRule(
                    hostname = obj.getString("hostname"),
                    type = try { RuleType.valueOf(obj.optString("type", "BLOCK")) }
                           catch (_: Exception) { RuleType.BLOCK },
                    redirectIp = obj.optString("redirect_ip", ""),
                    enabled = obj.optBoolean("enabled", true),
                    comment = obj.optString("comment", ""),
                    isWildcard = obj.optBoolean("is_wildcard", false)
                ))
                rulesCount++
            }
        }

        // Restore profiles
        if (root.has("profiles")) {
            val arr = root.getJSONArray("profiles")
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                profileDao.insert(BlockingProfile(
                    name = obj.getString("name"),
                    isActive = obj.optBoolean("is_active", false),
                    sourceIds = obj.optString("source_ids", ""),
                    scheduleStart = obj.optString("schedule_start", ""),
                    scheduleEnd = obj.optString("schedule_end", ""),
                    daysOfWeek = obj.optString("days_of_week", "0,1,2,3,4,5,6")
                ))
                profilesCount++
            }
        }

        // Restore preferences
        if (root.has("preferences")) {
            val p = root.getJSONObject("preferences")
            if (p.has("block_method")) prefs.setBlockMethod(
                try { BlockMethod.valueOf(p.getString("block_method")) }
                catch (_: Exception) { BlockMethod.ROOT_HOSTS }
            )
            if (p.has("ipv4_redirect")) prefs.setIpv4Redirect(p.getString("ipv4_redirect"))
            if (p.has("ipv6_redirect")) prefs.setIpv6Redirect(p.getString("ipv6_redirect"))
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
        }

        BackupResult(sourcesCount, rulesCount, profilesCount)
    }

    /**
     * Read backup file content from SAF URI.
     */
    suspend fun readBackupFromUri(context: Context, uri: Uri): String = withContext(Dispatchers.IO) {
        context.contentResolver.openInputStream(uri)?.use { stream ->
            stream.bufferedReader().readText()
        } ?: throw Exception("Cannot open input stream")
    }
}
