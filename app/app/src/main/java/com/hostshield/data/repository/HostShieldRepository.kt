package com.hostshield.data.repository

import com.hostshield.data.database.*
import com.hostshield.data.model.*
import com.hostshield.data.source.SourceDownloader
import com.hostshield.domain.parser.HostsParser
import com.hostshield.domain.parser.ParsedHost
import com.hostshield.util.RootUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

// ══════════════════════════════════════════════════════════════
// HostShield v0.1.0 — Repository
// ══════════════════════════════════════════════════════════════

@Singleton
class HostShieldRepository @Inject constructor(
    private val sourceDao: HostSourceDao,
    private val ruleDao: UserRuleDao,
    private val logDao: DnsLogDao,
    private val statsDao: BlockStatsDao,
    private val profileDao: ProfileDao,
    private val downloader: SourceDownloader,
    private val rootUtil: RootUtil
) {
    // ── Sources ──────────────────────────────────────────────
    fun getAllSources(): Flow<List<HostSource>> = sourceDao.getAllSources()
    fun getSourcesByCategory(cat: SourceCategory): Flow<List<HostSource>> = sourceDao.getByCategory(cat)
    fun getTotalEnabledEntries(): Flow<Int?> = sourceDao.getTotalEnabledEntries()
    fun getUnhealthySources(): Flow<List<HostSource>> = sourceDao.getUnhealthySources()
    suspend fun getEnabledSourcesList(): List<HostSource> = sourceDao.getEnabledSources()
    suspend fun addSource(source: HostSource): Long = sourceDao.insert(source)
    suspend fun updateSource(source: HostSource) = sourceDao.update(source)
    suspend fun deleteSource(source: HostSource) = sourceDao.delete(source)
    suspend fun toggleSource(id: Long, enabled: Boolean) = sourceDao.setEnabled(id, enabled)

    // ── User Rules ───────────────────────────────────────────
    fun getAllRules(): Flow<List<UserRule>> = ruleDao.getAllRules()
    fun getRulesByType(type: RuleType): Flow<List<UserRule>> = ruleDao.getByType(type)
    fun searchRules(query: String): Flow<List<UserRule>> = ruleDao.search(query)
    fun getRuleCount(type: RuleType): Flow<Int> = ruleDao.countByType(type)
    suspend fun addRule(rule: UserRule): Long = ruleDao.insert(rule)
    suspend fun updateRule(rule: UserRule) = ruleDao.update(rule)
    suspend fun deleteRule(rule: UserRule) = ruleDao.delete(rule)
    suspend fun toggleRule(id: Long, enabled: Boolean) = ruleDao.setEnabled(id, enabled)
    suspend fun ruleExists(hostname: String): Boolean = ruleDao.exists(hostname)
    suspend fun getEnabledWildcards(): List<UserRule> = ruleDao.getEnabledWildcards()
    suspend fun getEnabledRulesByType(type: RuleType): List<UserRule> = ruleDao.getEnabledByType(type)

    // ── DNS Logs ─────────────────────────────────────────────
    fun getRecentLogs(limit: Int = 500): Flow<List<DnsLogEntry>> = logDao.getRecentLogs(limit)
    fun getBlockedLogs(limit: Int = 500): Flow<List<DnsLogEntry>> = logDao.getBlockedLogs(limit)
    fun searchLogs(query: String, limit: Int = 200): Flow<List<DnsLogEntry>> = logDao.searchLogs(query, limit)
    fun getTopBlocked(limit: Int = 20): Flow<List<TopHostname>> = logDao.getTopBlocked(limit)
    fun getTopBlockedApps(limit: Int = 20): Flow<List<TopApp>> = logDao.getTopBlockedApps(limit)
    fun getBlockedCountSince(since: Long): Flow<Int> = logDao.getBlockedCountSince(since)
    fun getTotalCountSince(since: Long): Flow<Int> = logDao.getTotalCountSince(since)
    fun getHourlyBlocked(since: Long): Flow<List<HourlyStat>> = logDao.getHourlyBlocked(since)
    fun getHourlyTotal(since: Long): Flow<List<HourlyStat>> = logDao.getHourlyTotal(since)
    fun getAllAppsWithCounts(): Flow<List<AppQueryStat>> = logDao.getAllAppsWithCounts()
    fun getDomainsForApp(pkg: String, limit: Int = 200): Flow<List<AppDomainStat>> = logDao.getDomainsForApp(pkg, limit)
    fun getMostQueriedDomains(since: Long, limit: Int = 30): Flow<List<TopHostname>> = logDao.getMostQueriedDomains(since, limit)
    suspend fun logDnsQuery(entry: DnsLogEntry) = logDao.insert(entry)
    suspend fun clearOldLogs(olderThanMs: Long) = logDao.deleteOlderThan(System.currentTimeMillis() - olderThanMs)
    suspend fun clearAllLogs() = logDao.deleteAll()

    // ── Stats ────────────────────────────────────────────────
    fun getRecentStats(days: Int = 30): Flow<List<BlockStats>> = statsDao.getRecentStats(days)
    fun getTotalBlocked(): Flow<Int?> = statsDao.getTotalBlocked()
    suspend fun upsertStats(stats: BlockStats) = statsDao.upsert(stats)

    // ── Profiles ─────────────────────────────────────────────
    fun getAllProfiles(): Flow<List<BlockingProfile>> = profileDao.getAllProfiles()
    suspend fun getAllProfilesList(): List<BlockingProfile> = profileDao.getAllProfilesList()
    suspend fun getActiveProfile(): BlockingProfile? = profileDao.getActiveProfile()
    suspend fun addProfile(profile: BlockingProfile): Long = profileDao.insert(profile)
    suspend fun updateProfile(profile: BlockingProfile) = profileDao.update(profile)
    suspend fun deleteProfile(profile: BlockingProfile) = profileDao.delete(profile)
    suspend fun deactivateAllProfiles() = profileDao.deactivateAll()
    suspend fun activateProfile(id: Long) {
        profileDao.deactivateAll()
        profileDao.activate(id)
    }

    // ── Core Operations ──────────────────────────────────────

    /**
     * Download all enabled sources, parse, merge with user rules,
     * and write the resulting hosts file.
     */
    suspend fun applyBlocking(
        redirectIp4: String = "0.0.0.0",
        redirectIp6: String = "::",
        includeIpv6: Boolean = true,
        onProgress: suspend (String) -> Unit = {}
    ): Result<Int> = withContext(Dispatchers.IO) {
        try {
            val sources = sourceDao.getEnabledSources()
            val parsedSets = mutableListOf<Set<ParsedHost>>()

            onProgress("Downloading ${sources.size} sources...")

            for ((index, source) in sources.withIndex()) {
                onProgress("Downloading ${source.label} (${index + 1}/${sources.size})...")
                val result = downloader.download(source)
                result.onSuccess { dl ->
                    if (!dl.notModified) {
                        val parsed = HostsParser.parse(dl.content)
                        parsedSets.add(parsed)
                        sourceDao.updateSourceMeta(
                            id = source.id,
                            count = parsed.size,
                            timestamp = System.currentTimeMillis(),
                            etag = dl.etag,
                            size = dl.sizeBytes
                        )
                    } else {
                        val fresh = downloader.download(source.copy(etag = "", lastModifiedOnline = ""))
                        fresh.onSuccess { f -> parsedSets.add(HostsParser.parse(f.content)) }
                    }
                    // Mark healthy
                    sourceDao.updateHealth(source.id, SourceHealth.OK, "", 0)
                }.onFailure { err ->
                    val failures = source.consecutiveFailures + 1
                    val health = if (failures >= 5) SourceHealth.DEAD else SourceHealth.ERROR
                    sourceDao.updateHealth(source.id, health, err.message ?: "Unknown", failures)
                    onProgress("Failed: ${source.label} — ${err.message}")
                }
            }

            onProgress("Merging and deduplicating...")
            val userRules = ruleDao.getEnabledByType(RuleType.BLOCK) +
                    ruleDao.getEnabledByType(RuleType.ALLOW) +
                    ruleDao.getEnabledByType(RuleType.REDIRECT) +
                    ruleDao.getEnabledWildcards()

            val hostsContent = HostsParser.buildHostsFile(
                parsedSets, userRules, redirectIp4, redirectIp6, includeIpv6
            )

            val totalDomains = HostsParser.countUniqueDomains(parsedSets)

            onProgress("Writing hosts file ($totalDomains domains)...")
            val writeResult = rootUtil.writeHostsFile(hostsContent)
            writeResult.onFailure { return@withContext Result.failure(it) }

            onProgress("Done! $totalDomains domains blocked.")
            Result.success(totalDomains)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** Disable blocking by restoring original hosts file. */
    suspend fun disableBlocking(): Result<Unit> = rootUtil.restoreHostsFile()

    /** Check root access status. */
    fun isRootAvailable(): Boolean = rootUtil.isRootAvailable()

    /** Seed default built-in sources on first launch. */
    suspend fun seedDefaultSources() {
        val existing = sourceDao.getAllSourcesList()
        if (existing.isNotEmpty()) return

        val defaults = listOf(
            HostSource(
                url = "https://raw.githubusercontent.com/StevenBlack/hosts/master/hosts",
                label = "StevenBlack Unified",
                description = "Consolidated hosts from multiple curated sources. ~79k entries.",
                category = SourceCategory.ADS,
                isBuiltin = true
            ),
            HostSource(
                url = "https://adaway.org/hosts.txt",
                label = "AdAway Default",
                description = "Conservative, minimal ad-blocking list. ~400 entries.",
                category = SourceCategory.ADS,
                isBuiltin = true
            ),
            HostSource(
                url = "https://pgl.yoyo.org/adservers/serverlist.php?hostformat=hosts&showintro=0&mimetype=plaintext",
                label = "Peter Lowe's List",
                description = "Lightweight, zero false positives. ~3k entries.",
                category = SourceCategory.ADS,
                isBuiltin = true
            ),
            HostSource(
                url = "https://small.oisd.nl/",
                label = "OISD Small",
                description = "Well-curated aggregate with minimal false positives. ~70k entries.",
                category = SourceCategory.ADS,
                isBuiltin = true,
                enabled = false
            ),
            HostSource(
                url = "https://big.oisd.nl/",
                label = "OISD Big",
                description = "Comprehensive aggregate blocklist. ~200k+ entries.",
                category = SourceCategory.ADS,
                isBuiltin = true,
                enabled = false
            ),
            HostSource(
                url = "https://raw.githubusercontent.com/jerryn70/GoodbyeAds/master/Hosts/GoodbyeAds.txt",
                label = "GoodbyeAds",
                description = "Aggressive list including streaming/YouTube ad domains.",
                category = SourceCategory.ADS,
                isBuiltin = true,
                enabled = false
            ),
            HostSource(
                url = "https://raw.githubusercontent.com/StevenBlack/hosts/master/alternates/fakenews-gambling-porn/hosts",
                label = "StevenBlack + Fakenews/Gambling/Porn",
                description = "Extended list blocking adult, gambling, and fake news domains.",
                category = SourceCategory.ADULT,
                isBuiltin = true,
                enabled = false
            ),
            HostSource(
                url = "https://malware-filter.gitlab.io/malware-filter/urlhaus-filter-hosts.txt",
                label = "URLHaus Malware Filter",
                description = "Known malware distribution domains from abuse.ch.",
                category = SourceCategory.MALWARE,
                isBuiltin = true
            )
        )

        sourceDao.insertAll(defaults)
    }
}
