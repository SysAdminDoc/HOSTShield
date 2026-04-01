package com.hostshield.data.repository

import com.hostshield.data.database.HostSourceDao
import com.hostshield.data.model.HostSource
import com.hostshield.data.model.SourceCategory
import com.hostshield.data.model.SourceHealth
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SourceRepository @Inject constructor(
    private val sourceDao: HostSourceDao
) {
    fun getAllSources(): Flow<List<HostSource>> = sourceDao.getAllSources()
    fun getSourcesByCategory(cat: SourceCategory): Flow<List<HostSource>> = sourceDao.getByCategory(cat)
    fun getTotalEnabledEntries(): Flow<Int?> = sourceDao.getTotalEnabledEntries()
    fun getUnhealthySources(): Flow<List<HostSource>> = sourceDao.getUnhealthySources()
    suspend fun getEnabledSourcesList(): List<HostSource> = sourceDao.getEnabledSources()
    suspend fun getEnabledBlockSources(): List<HostSource> = sourceDao.getEnabledBlockSources()
    suspend fun getEnabledAllowlistSources(): List<HostSource> = sourceDao.getEnabledAllowlistSources()
    suspend fun addSource(source: HostSource): Long = sourceDao.insert(source)
    suspend fun updateSource(source: HostSource) = sourceDao.update(source)
    suspend fun deleteSource(source: HostSource) = sourceDao.delete(source)
    suspend fun toggleSource(id: Long, enabled: Boolean) = sourceDao.setEnabled(id, enabled)

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
            ),
            HostSource(
                url = "https://raw.githubusercontent.com/anudeepND/whitelist/master/domains/whitelist.txt",
                label = "Anudeep's Whitelist",
                description = "Curated allowlist preventing common false positives. ~400 domains.",
                category = SourceCategory.ALLOWLIST,
                isBuiltin = true,
                enabled = false
            ),
            HostSource(
                url = "https://raw.githubusercontent.com/anudeepND/whitelist/master/domains/optional-list.txt",
                label = "Anudeep's Optional Whitelist",
                description = "Extended allowlist including CDNs, analytics safe domains. ~100 domains.",
                category = SourceCategory.ALLOWLIST,
                isBuiltin = true,
                enabled = false
            ),
            HostSource(
                url = "https://raw.githubusercontent.com/hagezi/dns-blocklists/main/whitelist.txt",
                label = "HaGeZi Whitelist",
                description = "Referral allowlist preventing breakage from aggressive blocklists.",
                category = SourceCategory.ALLOWLIST,
                isBuiltin = true,
                enabled = false
            )
        )

        sourceDao.insertAll(defaults)
    }
}
