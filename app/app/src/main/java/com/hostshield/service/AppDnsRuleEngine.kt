package com.hostshield.service

import android.util.Log
import com.hostshield.util.PrivacyLog
import com.hostshield.data.database.AppDnsRuleDao
import com.hostshield.data.model.AppDnsRule
import com.hostshield.domain.ScopedAppDnsRule
import kotlinx.coroutines.flow.first
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Domain-per-app DNS rule engine (Roadmap #12).
 *
 * Maintains an in-memory cache of per-app domain rules for O(1) lookup
 * from the VPN packet loop hot path. Rules support exact domain matches
 * and wildcard patterns (*.example.com matches sub.example.com AND example.com).
 *
 * Precedence: ALLOW rules override BLOCK rules (whitelist > blacklist).
 * If no rule matches, returns null — caller falls back to default behavior.
 */
@Singleton
class AppDnsRuleEngine @Inject constructor(
    private val appDnsRuleDao: AppDnsRuleDao
) {

    companion object {
        private const val TAG = "AppDnsRuleEngine"
    }

    enum class RuleAction { BLOCK, ALLOW }

    /**
     * A compiled rule ready for fast matching.
     * [isWildcard] true means the original pattern started with "*." —
     * we store the suffix (e.g., ".facebook.com") and also match the
     * bare domain (facebook.com).
     */
    private data class CompiledRule(
        val domainPattern: String,   // original pattern as entered by user
        val suffix: String,          // ".facebook.com" for wildcard, or exact domain
        val isWildcard: Boolean,
        val matchesSubdomains: Boolean,
        val action: RuleAction,
        val packageName: String? = null,
        val packageNegated: Boolean = false,
        val dnsTypes: Set<Int>? = null,
        val dnsTypesNegated: Boolean = false,
    )

    // packageName -> list of compiled rules  (hot-path reads, rare writes)
    @Volatile
    private var ruleCache: Map<String, List<CompiledRule>> = emptyMap()

    /** Source-delivered `$app=` rules, replaced atomically after each rebuild. */
    @Volatile
    private var sourceRuleCache: List<CompiledRule> = emptyList()

    // ──────────────────────────────────────────────────────────────
    // Public API
    // ──────────────────────────────────────────────────────────────

    /**
     * Warm the in-memory cache from the database.
     * Call once at VPN start and whenever rules are modified.
     */
    suspend fun loadRules() {
        try {
            val allRules: List<AppDnsRule> = appDnsRuleDao.getAllRules().first()
            val newCache = HashMap<String, List<CompiledRule>>()
            val grouped = allRules
                .filter { it.enabled }
                .groupBy { it.packageName }

            for ((pkg, rules) in grouped) {
                newCache[pkg] = rules.map { compile(it) }
            }
            ruleCache = newCache
            Log.d(TAG, "Loaded rules for ${ruleCache.size} apps")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load app DNS rules", e)
        }
    }

    /** Replace source-delivered scoped rules without disturbing user rules. */
    fun replaceSourceRules(rules: List<ScopedAppDnsRule>) {
        sourceRuleCache = rules
            .map { it.normalized() }
            .filter { it.domain.isNotBlank() && it.packageName.isNotBlank() }
            .map(::compileSource)
    }

    /**
     * Check whether [domain] should be blocked or allowed for [packageName].
     *
     * @return [RuleAction.BLOCK] or [RuleAction.ALLOW] if a matching rule exists,
     *         or `null` if no per-app rule applies (use default behavior).
     */
    fun checkDomain(packageName: String, domain: String, queryType: Int = 0): RuleAction? {
        val normalised = domain.lowercase()

        var hasBlock = false

        fun consider(rule: CompiledRule): RuleAction? {
            if (rule.packageName != null &&
                (if (rule.packageNegated) packageName == rule.packageName else packageName != rule.packageName)
            ) return null
            if (rule.dnsTypes != null) {
                if (queryType <= 0) return null
                val typeMatches = if (rule.dnsTypesNegated) {
                    queryType !in rule.dnsTypes
                } else {
                    queryType in rule.dnsTypes
                }
                if (!typeMatches) return null
            }
            if (!matches(rule, normalised)) return null
            if (rule.action == RuleAction.ALLOW) return RuleAction.ALLOW
            hasBlock = true
            return null
        }

        ruleCache[packageName].orEmpty().forEach { rule ->
            if (consider(rule) == RuleAction.ALLOW) return RuleAction.ALLOW
        }
        sourceRuleCache.forEach { rule ->
            if (consider(rule) == RuleAction.ALLOW) return RuleAction.ALLOW
        }

        return if (hasBlock) RuleAction.BLOCK else null
    }

    /** Returns the number of source rules currently active in the hot-path cache. */
    fun getSourceRuleCount(): Int = sourceRuleCache.size

    /**
     * Convenience: returns `true` when the domain should be blocked for this app.
     */
    fun shouldBlock(packageName: String, domain: String): Boolean =
        checkDomain(packageName, domain) == RuleAction.BLOCK

    /**
     * Returns the set of package names that have at least one cached rule.
     */
    fun getAppsWithCachedRules(): Set<String> = ruleCache.keys

    /**
     * Invalidate cache for a single app (after rule edit).
     * Cheaper than a full [loadRules] reload.
     */
    suspend fun reloadForApp(packageName: String) {
        try {
            val rules = appDnsRuleDao.getRulesForApp(packageName).first()
            val current = ruleCache.toMutableMap()
            if (rules.isEmpty()) {
                current.remove(packageName)
            } else {
                current[packageName] = rules.map { compile(it) }
            }
            ruleCache = current
        } catch (e: Exception) {
            PrivacyLog.e(TAG, "Failed to reload rules for $packageName", e)
        }
    }

    // ──────────────────────────────────────────────────────────────
    // Internals
    // ──────────────────────────────────────────────────────────────

    private fun compile(rule: AppDnsRule): CompiledRule {
        val pattern = rule.domain.lowercase().trim()
        val action = if (rule.action.equals("allow", ignoreCase = true))
            RuleAction.ALLOW else RuleAction.BLOCK

        return if (pattern.startsWith("*.")) {
            // Wildcard: *.facebook.com  ->  suffix = ".facebook.com"
            // Matches: sub.facebook.com, deep.sub.facebook.com, and facebook.com itself
            val suffix = pattern.removePrefix("*")
            CompiledRule(
                domainPattern = pattern,
                suffix = suffix,         // e.g. ".facebook.com"
                isWildcard = true,
                matchesSubdomains = true,
                action = action
            )
        } else {
            CompiledRule(
                domainPattern = pattern,
                suffix = pattern,
                isWildcard = false,
                matchesSubdomains = false,
                action = action
            )
        }
    }

    private fun compileSource(rule: ScopedAppDnsRule): CompiledRule {
        val pattern = rule.domain.lowercase().trim()
        val isWildcard = rule.isWildcard
        return CompiledRule(
            domainPattern = pattern,
            suffix = if (isWildcard) ".${pattern.removePrefix(".")}" else pattern,
            isWildcard = isWildcard,
            matchesSubdomains = rule.matchesSubdomains || isWildcard,
            action = if (rule.isException) RuleAction.ALLOW else RuleAction.BLOCK,
            packageName = rule.packageName,
            packageNegated = rule.packageNegated,
            dnsTypes = rule.dnsTypes,
            dnsTypesNegated = rule.dnsTypesNegated,
        )
    }

    private fun matches(rule: CompiledRule, domain: String): Boolean {
        return if (rule.matchesSubdomains) {
            // *.facebook.com matches:
            //   facebook.com          (bare domain)
            //   sub.facebook.com      (single sub)
            //   deep.sub.facebook.com (nested subs)
            val base = rule.suffix.removePrefix(".")
            domain.endsWith(".$base") || domain == base
        } else {
            domain == rule.suffix
        }
    }
}
