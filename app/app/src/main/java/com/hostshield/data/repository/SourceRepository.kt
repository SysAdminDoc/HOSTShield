package com.hostshield.data.repository

import com.hostshield.data.database.HostSourceDao
import com.hostshield.data.model.HostSource
import com.hostshield.data.model.SourceCategory
import com.hostshield.data.model.SourceHealth
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

internal const val SPOTIFY_ADS_SOURCE_URL =
    "https://raw.githubusercontent.com/SysAdminDoc/HostShield/main/blocklists/SpotifyAds.txt"
internal const val LEGACY_SPOTIFY_ADS_SOURCE_URL =
    "https://raw.githubusercontent.com/Mireli5656/adblock360-/refs/heads/main/lists/spotifyadlist.hosts"
private const val SPOTIFY_ADS_DESCRIPTION =
    "Aggressive Spotify ad and telemetry list maintained by HostShield. May interrupt playback or app updates. ~84 entries."

private const val HOSTSHIELD_BLOCKLIST_BASE_URL =
    "https://raw.githubusercontent.com/SysAdminDoc/HostShield/main/blocklists/"
internal const val HAGEZI_REFERRAL_ALLOWLIST_SOURCE_URL =
    "https://raw.githubusercontent.com/SysAdminDoc/HostShield/main/blocklists/HaGeZi-ReferralAllowlist.txt"

internal val HAGEZI_SOURCE_URL_MIGRATIONS = mapOf(
    "https://raw.githubusercontent.com/hagezi/dns-blocklists/main/hosts/light.txt" to
        HOSTSHIELD_BLOCKLIST_BASE_URL + "HaGeZi-Light.txt",
    "https://raw.githubusercontent.com/hagezi/dns-blocklists/main/hosts/multi.txt" to
        HOSTSHIELD_BLOCKLIST_BASE_URL + "HaGeZi-Multi.txt",
    "https://raw.githubusercontent.com/hagezi/dns-blocklists/main/hosts/pro.txt" to
        HOSTSHIELD_BLOCKLIST_BASE_URL + "HaGeZi-Pro.txt",
    "https://raw.githubusercontent.com/hagezi/dns-blocklists/main/hosts/pro.plus.txt" to
        HOSTSHIELD_BLOCKLIST_BASE_URL + "HaGeZi-ProPlus.txt",
    "https://raw.githubusercontent.com/hagezi/dns-blocklists/main/hosts/ultimate.txt" to
        HOSTSHIELD_BLOCKLIST_BASE_URL + "HaGeZi-Ultimate.txt",
    "https://raw.githubusercontent.com/hagezi/dns-blocklists/main/hosts/tif.txt" to
        HOSTSHIELD_BLOCKLIST_BASE_URL + "HaGeZi-TIF.txt",
    "https://raw.githubusercontent.com/hagezi/dns-blocklists/main/adblock/tif.mini.txt" to
        HOSTSHIELD_BLOCKLIST_BASE_URL + "HaGeZi-TIF-Mini.txt",
    "https://raw.githubusercontent.com/hagezi/dns-blocklists/main/adblock/dyndns.txt" to
        HOSTSHIELD_BLOCKLIST_BASE_URL + "HaGeZi-DynDNS.txt",
    "https://raw.githubusercontent.com/hagezi/dns-blocklists/main/adblock/spam-tlds.txt" to
        HOSTSHIELD_BLOCKLIST_BASE_URL + "HaGeZi-SpamTLDs.txt",
    "https://raw.githubusercontent.com/hagezi/dns-blocklists/main/adblock/gambling.txt" to
        HOSTSHIELD_BLOCKLIST_BASE_URL + "HaGeZi-Gambling.txt",
    "https://raw.githubusercontent.com/hagezi/dns-blocklists/main/adblock/whitelist-referral-native.txt" to
        HAGEZI_REFERRAL_ALLOWLIST_SOURCE_URL,
    "https://raw.githubusercontent.com/hagezi/dns-blocklists/main/adblock/spam-tlds-adblock-allow.txt" to
        HOSTSHIELD_BLOCKLIST_BASE_URL + "HaGeZi-SpamTLDsAllow.txt",
    "https://raw.githubusercontent.com/hagezi/dns-blocklists/main/whitelist.txt" to
        HAGEZI_REFERRAL_ALLOWLIST_SOURCE_URL,
    "https://raw.githubusercontent.com/hagezi/dns-blocklists/main/whitelist-referral.txt" to
        HAGEZI_REFERRAL_ALLOWLIST_SOURCE_URL
)

internal fun spotifyAdsDefaultSource() = HostSource(
    url = SPOTIFY_ADS_SOURCE_URL,
    label = "Spotify Ads",
    description = SPOTIFY_ADS_DESCRIPTION,
    category = SourceCategory.ADS,
    isBuiltin = true,
    enabled = false
)

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
        repairKnownSourceUrls(existing)
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
            spotifyAdsDefaultSource(),
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
                description = "Curated allowlist preventing common false positives. ~191 domains.",
                category = SourceCategory.ALLOWLIST,
                isBuiltin = true,
                enabled = false
            ),
            HostSource(
                url = "https://raw.githubusercontent.com/anudeepND/whitelist/master/domains/optional-list.txt",
                label = "Anudeep's Optional Whitelist",
                description = "Extended allowlist including service-specific domains. ~144 domains.",
                category = SourceCategory.ALLOWLIST,
                isBuiltin = true,
                enabled = false
            ),
            HostSource(
                url = HAGEZI_REFERRAL_ALLOWLIST_SOURCE_URL,
                label = "HaGeZi Referral Allowlist",
                description = "Adblock-syntax referral and native tracker unbreak list for aggressive HaGeZi packs. ~1.6k entries.",
                category = SourceCategory.ALLOWLIST,
                isBuiltin = true,
                enabled = false
            )
        )

        sourceDao.insertAll(defaults)
    }

    private suspend fun repairKnownSourceUrls(existing: List<HostSource>) {
        val alreadyHasHostedSpotify = existing.any { it.url == SPOTIFY_ADS_SOURCE_URL }
        existing.forEach { source ->
            when {
                source.url == LEGACY_SPOTIFY_ADS_SOURCE_URL -> {
                    if (alreadyHasHostedSpotify) {
                        sourceDao.delete(source)
                    } else {
                        sourceDao.update(
                            source.copy(
                                url = SPOTIFY_ADS_SOURCE_URL,
                                label = "Spotify Ads",
                                description = SPOTIFY_ADS_DESCRIPTION,
                                category = SourceCategory.ADS,
                                isBuiltin = true,
                                entryCount = 0,
                                lastUpdated = 0L,
                                lastModifiedOnline = "",
                                etag = "",
                                sizeBytes = 0L,
                                health = SourceHealth.UNKNOWN,
                                lastError = "",
                                lastHttpStatus = 0,
                                consecutiveFailures = 0,
                                prevEntryCount = 0,
                                domainsAdded = 0,
                                domainsRemoved = 0
                            )
                        )
                    }
                }

                source.url in HAGEZI_SOURCE_URL_MIGRATIONS -> {
                    val migrated = source.copy(
                        url = HAGEZI_SOURCE_URL_MIGRATIONS.getValue(source.url),
                        entryCount = 0,
                        lastUpdated = 0L,
                        lastModifiedOnline = "",
                        etag = "",
                        sizeBytes = 0L,
                        health = SourceHealth.UNKNOWN,
                        lastError = "",
                        lastHttpStatus = 0,
                        consecutiveFailures = 0,
                        prevEntryCount = 0,
                        domainsAdded = 0,
                        domainsRemoved = 0
                    )
                    sourceDao.update(
                        if (migrated.url == HAGEZI_REFERRAL_ALLOWLIST_SOURCE_URL) {
                            migrated.copy(
                                label = "HaGeZi Referral Allowlist",
                                description = "Adblock-syntax referral and native tracker unbreak list for aggressive HaGeZi packs. ~1.6k entries.",
                                category = SourceCategory.ALLOWLIST
                            )
                        } else {
                            migrated
                        }
                    )
                }
            }
        }
    }
}
