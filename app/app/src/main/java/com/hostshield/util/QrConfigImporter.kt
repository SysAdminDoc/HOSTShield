package com.hostshield.util

import com.hostshield.data.model.HostSource
import com.hostshield.data.model.RuleType
import com.hostshield.data.model.SourceCategory
import com.hostshield.data.model.UserRule
import com.hostshield.data.preferences.AppPreferences
import com.hostshield.data.repository.HostShieldRepository
import kotlinx.coroutines.flow.first
import java.net.URI
import javax.inject.Inject
import javax.inject.Singleton

data class QrImportPlan(
    val rulesToAdd: List<UserRule> = emptyList(),
    val sourcesToAdd: List<HostSource> = emptyList(),
    val customDns: String? = null,
    val dohEnabled: Boolean? = null,
    val dohProvider: String? = null,
    val skippedRules: Int = 0,
    val skippedSources: Int = 0,
) {
    val changeCount: Int
        get() = rulesToAdd.size + sourcesToAdd.size +
            listOfNotNull(customDns, dohEnabled, dohProvider).size

    val hasChanges: Boolean
        get() = changeCount > 0
}

data class QrImportResult(
    val rulesAdded: Int,
    val sourcesAdded: Int,
    val settingsUpdated: Int,
)

interface QrImportSink {
    suspend fun addRule(rule: UserRule)
    suspend fun addSource(source: HostSource)
    suspend fun setCustomDns(value: String)
    suspend fun setDohEnabled(value: Boolean)
    suspend fun setDohProvider(value: String)
}

object QrConfigImportPlanner {
    private const val MAX_SETTING_LENGTH = 512

    fun buildPlan(
        config: ShareableConfig,
        existingRuleHostnames: Set<String> = emptySet(),
        existingSourceUrls: Set<String> = emptySet(),
    ): QrImportPlan {
        val seenRules = existingRuleHostnames.map { it.lowercase() }.toMutableSet()
        val seenSources = existingSourceUrls.map { it.lowercase() }.toMutableSet()
        val rules = mutableListOf<UserRule>()
        val sources = mutableListOf<HostSource>()
        var skippedRules = 0
        var skippedSources = 0

        config.userRules.forEach { entry ->
            val hostname = normalizeRuleHost(entry.domain)
            val type = when (entry.type.trim().lowercase()) {
                "allow", "whitelist" -> RuleType.ALLOW
                "block", "deny", "" -> RuleType.BLOCK
                else -> null
            }
            if (hostname == null || type == null || !seenRules.add(hostname)) {
                skippedRules++
                return@forEach
            }
            rules.add(
                UserRule(
                    hostname = hostname,
                    type = type,
                    isWildcard = hostname.startsWith("*."),
                    comment = "Imported from QR config",
                )
            )
        }

        config.sourceUrls.forEach { rawUrl ->
            val url = normalizeHttpsSourceUrl(rawUrl)
            if (url == null || !seenSources.add(url.lowercase())) {
                skippedSources++
                return@forEach
            }
            sources.add(
                HostSource(
                    url = url,
                    label = sourceLabel(url),
                    description = "Imported from QR config",
                    category = SourceCategory.CUSTOM,
                )
            )
        }

        val customDns = config.customDns.cleanSetting()
        val dohProvider = config.dohProvider.cleanSetting()
        val dohEnabled = if (config.dohEnabled) true else null

        return QrImportPlan(
            rulesToAdd = rules,
            sourcesToAdd = sources,
            customDns = customDns,
            dohEnabled = dohEnabled,
            dohProvider = dohProvider,
            skippedRules = skippedRules,
            skippedSources = skippedSources,
        )
    }

    suspend fun applyPlan(plan: QrImportPlan, sink: QrImportSink): QrImportResult {
        plan.rulesToAdd.forEach { sink.addRule(it) }
        plan.sourcesToAdd.forEach { sink.addSource(it) }

        var settingsUpdated = 0
        plan.customDns?.let {
            sink.setCustomDns(it)
            settingsUpdated++
        }
        plan.dohProvider?.let {
            sink.setDohProvider(it)
            settingsUpdated++
        }
        plan.dohEnabled?.let {
            sink.setDohEnabled(it)
            settingsUpdated++
        }

        return QrImportResult(
            rulesAdded = plan.rulesToAdd.size,
            sourcesAdded = plan.sourcesToAdd.size,
            settingsUpdated = settingsUpdated,
        )
    }

    private fun String.cleanSetting(): String? {
        val value = trim()
        if (value.isEmpty() || value.length > MAX_SETTING_LENGTH) return null
        if (value.any { it.isISOControl() }) return null
        return value
    }

    private fun normalizeRuleHost(value: String): String? {
        val host = value.trim().lowercase().removeSuffix(".")
        val base = if (host.startsWith("*.")) host.removePrefix("*.") else host
        if (host.length !in 3..253 || !base.contains('.')) return null
        if (base.contains("..") || base.any { it.isWhitespace() || it == '/' || it == ':' }) return null
        val labels = base.split('.')
        if (labels.any { label ->
                label.isEmpty() ||
                    label.length > 63 ||
                    label.startsWith("-") ||
                    label.endsWith("-") ||
                    label.any { !it.isLetterOrDigit() && it != '-' }
            }
        ) return null
        return if (host.startsWith("*.")) "*.$base" else base
    }

    private fun normalizeHttpsSourceUrl(value: String): String? {
        val raw = value.trim()
        if (raw.length !in 12..2048 || raw.any { it.isWhitespace() || it.isISOControl() }) return null
        val uri = try {
            URI(raw)
        } catch (_: Exception) {
            return null
        }
        if (!"https".equals(uri.scheme, ignoreCase = true)) return null
        val host = uri.host?.trim()?.lowercase().orEmpty()
        if (host.isEmpty() || uri.userInfo != null) return null
        val authorityHost = if (host.contains(':')) "[$host]" else host
        val authority = if (uri.port >= 0) "$authorityHost:${uri.port}" else authorityHost
        val path = uri.rawPath?.ifEmpty { "/" } ?: "/"
        val query = uri.rawQuery?.let { "?$it" } ?: ""
        return "https://$authority$path$query"
    }

    private fun sourceLabel(url: String): String {
        val pathName = url.substringBefore('?').trimEnd('/').substringAfterLast('/').trim()
        val hostName = try {
            URI(url).host?.substringBefore('.') ?: ""
        } catch (_: Exception) {
            ""
        }
        return when {
            pathName.isNotEmpty() -> pathName.take(40)
            hostName.isNotEmpty() -> hostName.take(40)
            else -> "Imported QR source"
        }
    }
}

@Singleton
class QrConfigImporter @Inject constructor(
    private val repository: HostShieldRepository,
    private val prefs: AppPreferences,
) {
    suspend fun buildCurrentConfig(): ShareableConfig {
        val rules = repository.getAllRules().first()
            .filter { it.enabled && !it.isRegex && it.type != RuleType.REDIRECT }
            .map {
                RuleEntry(
                    domain = it.hostname,
                    type = if (it.type == RuleType.ALLOW) "allow" else "block",
                )
            }
        val sources = repository.getAllSources().first()
            .filter { !it.isBuiltin }
            .map { it.url }

        return ShareableConfig(
            version = 1,
            userRules = rules,
            customDns = prefs.customUpstreamDns.first(),
            dohEnabled = prefs.dohEnabled.first(),
            dohProvider = prefs.dohProvider.first(),
            sourceUrls = sources,
            profileName = "HostShield Config",
        )
    }

    suspend fun preview(config: ShareableConfig): QrImportPlan {
        val existingRules = repository.getAllRules().first().map { it.hostname.lowercase() }.toSet()
        val existingSources = repository.getAllSources().first().map { it.url.lowercase() }.toSet()
        return QrConfigImportPlanner.buildPlan(
            config = config,
            existingRuleHostnames = existingRules,
            existingSourceUrls = existingSources,
        )
    }

    suspend fun apply(plan: QrImportPlan): QrImportResult {
        return QrConfigImportPlanner.applyPlan(
            plan,
            object : QrImportSink {
                override suspend fun addRule(rule: UserRule) {
                    repository.addRule(rule)
                }

                override suspend fun addSource(source: HostSource) {
                    repository.addSource(source)
                }

                override suspend fun setCustomDns(value: String) {
                    prefs.setCustomUpstreamDns(value)
                }

                override suspend fun setDohEnabled(value: Boolean) {
                    prefs.setDohEnabled(value)
                }

                override suspend fun setDohProvider(value: String) {
                    prefs.setDohProvider(value)
                }
            }
        )
    }
}
