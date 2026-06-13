package com.hostshield.service

import android.util.Log
import com.hostshield.util.PrivacyLog
import com.hostshield.data.database.AppDnsRuleDao
import com.hostshield.data.model.AppDnsRule
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
        val action: RuleAction
    )

    // packageName -> list of compiled rules  (hot-path reads, rare writes)
    private val ruleCache = ConcurrentHashMap<String, List<CompiledRule>>()

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
            // Atomic swap: clear old + putAll in rapid succession to minimize
            // the window where hot-path reads see stale data
            ruleCache.clear()
            ruleCache.putAll(newCache)
            Log.d(TAG, "Loaded rules for ${ruleCache.size} apps")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load app DNS rules", e)
        }
    }

    /**
     * Check whether [domain] should be blocked or allowed for [packageName].
     *
     * @return [RuleAction.BLOCK] or [RuleAction.ALLOW] if a matching rule exists,
     *         or `null` if no per-app rule applies (use default behavior).
     */
    fun checkDomain(packageName: String, domain: String): RuleAction? {
        val rules = ruleCache[packageName] ?: return null
        val normalised = domain.lowercase()

        var hasBlock = false

        for (rule in rules) {
            if (matches(rule, normalised)) {
                // Allow takes precedence — return immediately
                if (rule.action == RuleAction.ALLOW) return RuleAction.ALLOW
                hasBlock = true
            }
        }

        return if (hasBlock) RuleAction.BLOCK else null
    }

    /**
     * Convenience: returns `true` when the domain should be blocked for this app.
     */
    fun shouldBlock(packageName: String, domain: String): Boolean =
        checkDomain(packageName, domain) == RuleAction.BLOCK

    /**
     * Returns the set of package names that have at least one cached rule.
     */
    fun getAppsWithCachedRules(): Set<String> = ruleCache.keys.toSet()

    /**
     * Invalidate cache for a single app (after rule edit).
     * Cheaper than a full [loadRules] reload.
     */
    suspend fun reloadForApp(packageName: String) {
        try {
            val rules = appDnsRuleDao.getRulesForApp(packageName).first()
            if (rules.isEmpty()) {
                ruleCache.remove(packageName)
            } else {
                ruleCache[packageName] = rules.map { compile(it) }
            }
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
                action = action
            )
        } else {
            CompiledRule(
                domainPattern = pattern,
                suffix = pattern,
                isWildcard = false,
                action = action
            )
        }
    }

    private fun matches(rule: CompiledRule, domain: String): Boolean {
        return if (rule.isWildcard) {
            // *.facebook.com matches:
            //   facebook.com          (bare domain)
            //   sub.facebook.com      (single sub)
            //   deep.sub.facebook.com (nested subs)
            domain.endsWith(rule.suffix) || domain == rule.suffix.removePrefix(".")
        } else {
            domain == rule.suffix
        }
    }
}
