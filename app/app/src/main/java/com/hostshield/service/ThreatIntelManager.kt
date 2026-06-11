package com.hostshield.service

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

// Threat intelligence feed engine
//
// Downloads and indexes malicious IPs (CIDR prefixes) and domains from
// curated threat intelligence feeds:
//   - abuse.ch URLhaus (malware distribution domains)
//   - Spamhaus DROP / EDROP (hijacked IP ranges)
//   - Emerging Threats (compromised IPs)
//   - Disconnect Malware (malware domains)
//
// IPs are stored in a radix trie for O(1) CIDR prefix lookup.
// Domains are stored in a ConcurrentHashMap for O(1) lookup.
// Thread-safe for concurrent access from VPN packet loop.

@Singleton
class ThreatIntelManager @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val okHttpClient: OkHttpClient
) {
    companion object {
        private const val TAG = "ThreatIntel"
        private const val CACHE_FILE = "threat_intel_cache.json"
        private const val CACHE_KEY_DOMAINS = "domains"
        private const val CACHE_KEY_IP_CIDRS = "ip_cidrs"
        private const val CACHE_KEY_FEEDS = "feeds"
        private const val CACHE_KEY_LAST_UPDATED = "last_updated"
    }

    // ── Data classes ──────────────────────────────────────────

    data class ThreatMatch(
        val feedName: String,
        val matchType: String,   // "ip" or "domain"
        val matchedValue: String
    )

    enum class FeedType { DOMAIN, IP_CIDR, IP_LIST }

    private data class FeedConfig(
        val name: String,
        val url: String,
        val type: FeedType,
        val parser: (String, String) -> ParseResult
    )

    private data class ParseResult(
        val domains: List<Pair<String, String>> = emptyList(),   // domain -> source
        val cidrs: List<Pair<String, String>> = emptyList()      // cidr -> source
    )

    // ── Radix Trie for IPv4 CIDR matching ─────────────────────

    class IpRadixTrie {
        private class Node {
            var isMalicious = false
            var feedSource: String = ""
            val children = arrayOfNulls<Node>(2)
        }

        private val root = Node()
        var size = 0; private set

        fun insert(cidr: String, source: String) {
            val parts = cidr.split("/")
            val ip = ipToInt(parts[0])
            val prefixLen = parts.getOrNull(1)?.toIntOrNull() ?: 32
            if (prefixLen < 8 || prefixLen > 32) return // skip overly broad or invalid
            var node = root
            for (i in 31 downTo (32 - prefixLen)) {
                val bit = (ip shr i) and 1
                if (node.children[bit] == null) node.children[bit] = Node()
                node = node.children[bit]!!
            }
            if (!node.isMalicious) size++
            node.isMalicious = true
            node.feedSource = source
        }

        fun lookup(ipStr: String): String? {
            val ip = ipToInt(ipStr)
            if (ip == 0) return null
            var node = root
            for (i in 31 downTo 0) {
                if (node.isMalicious) return node.feedSource
                val bit = (ip shr i) and 1
                node = node.children[bit] ?: return null
            }
            return if (node.isMalicious) node.feedSource else null
        }

        private fun ipToInt(ip: String): Int {
            val parts = ip.split(".")
            if (parts.size != 4) return 0
            return try {
                val octets = parts.map { it.toInt() }
                if (octets.any { it !in 0..255 }) return 0
                (octets[0] shl 24) or (octets[1] shl 16) or
                        (octets[2] shl 8) or octets[3]
            } catch (_: NumberFormatException) { 0 }
        }

        fun clear() {
            root.children[0] = null
            root.children[1] = null
            root.isMalicious = false
            size = 0
        }
    }

    // ── State ─────────────────────────────────────────────────

    @Volatile private var ipTrie = IpRadixTrie()
    private val domainThreats = ConcurrentHashMap<String, String>()  // domain -> feedName

    @Volatile var feedCount = 0; private set
    @Volatile var domainCount = 0; private set
    @Volatile var ipCidrCount = 0; private set
    @Volatile var lastUpdated = 0L; private set

    // ── Feed Configuration ────────────────────────────────────

    private val feeds = listOf(
        FeedConfig(
            name = "URLhaus",
            url = "https://urlhaus.abuse.ch/downloads/hostfile/",
            type = FeedType.DOMAIN,
            parser = ::parseHostsFile
        ),
        FeedConfig(
            name = "Spamhaus DROP",
            url = "https://www.spamhaus.org/drop/drop.txt",
            type = FeedType.IP_CIDR,
            parser = ::parseSpamhausDrop
        ),
        FeedConfig(
            name = "Spamhaus EDROP",
            url = "https://www.spamhaus.org/drop/edrop.txt",
            type = FeedType.IP_CIDR,
            parser = ::parseSpamhausDrop
        ),
        FeedConfig(
            name = "Emerging Threats",
            url = "https://rules.emergingthreats.net/blockrules/compromised-ips.txt",
            type = FeedType.IP_LIST,
            parser = ::parseIpList
        ),
        FeedConfig(
            name = "Disconnect Malware",
            url = "https://s3.amazonaws.com/lists.disconnect.me/simple_malware.txt",
            type = FeedType.DOMAIN,
            parser = ::parseDomainList
        )
    )

    // ── Public API ────────────────────────────────────────────

    /** Load cached threat intel from disk (call at VPN start). */
    fun loadCached() {
        try {
            val file = File(context.filesDir, CACHE_FILE)
            if (!file.exists()) return
            val json = JSONObject(file.readText())

            val newTrie = IpRadixTrie()
            val newDomains = ConcurrentHashMap<String, String>()

            // Load IP CIDRs
            val cidrs = json.optJSONArray(CACHE_KEY_IP_CIDRS)
            if (cidrs != null) {
                for (i in 0 until cidrs.length()) {
                    val entry = cidrs.getJSONObject(i)
                    newTrie.insert(entry.getString("cidr"), entry.getString("source"))
                }
            }

            // Load domains
            val domains = json.optJSONArray(CACHE_KEY_DOMAINS)
            if (domains != null) {
                for (i in 0 until domains.length()) {
                    val entry = domains.getJSONObject(i)
                    newDomains[entry.getString("domain")] = entry.getString("source")
                }
            }

            // Atomic swap
            synchronized(this) {
                ipTrie = newTrie
                domainThreats.clear()
                domainThreats.putAll(newDomains)
                feedCount = json.optInt(CACHE_KEY_FEEDS, 0)
                lastUpdated = json.optLong(CACHE_KEY_LAST_UPDATED, 0L)
                domainCount = domainThreats.size
                ipCidrCount = ipTrie.size
            }
            Log.i(TAG, "Loaded cached threat intel: $domainCount domains, $ipCidrCount IP CIDRs")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to load threat intel cache: ${e.message}")
        }
    }

    /** Download and parse all threat feeds (call from worker). */
    suspend fun refreshFeeds(): Boolean {
        return refreshFeedsAndPersist()
    }

    /** Check if an IP address is malicious. Thread-safe. */
    fun isIpMalicious(ip: String): ThreatMatch? {
        val source = ipTrie.lookup(ip) ?: return null
        return ThreatMatch(feedName = source, matchType = "ip", matchedValue = ip)
    }

    /** Check if a domain is malicious. Thread-safe. */
    fun isDomainMalicious(domain: String): ThreatMatch? {
        val lower = domain.lowercase()
        // Check exact match
        val source = domainThreats[lower]
        if (source != null) return ThreatMatch(feedName = source, matchType = "domain", matchedValue = lower)
        // Check parent domains (e.g., sub.evil.com -> evil.com)
        val parts = lower.split(".")
        for (i in 1 until parts.size - 1) {
            val parent = parts.subList(i, parts.size).joinToString(".")
            val parentSource = domainThreats[parent]
            if (parentSource != null) return ThreatMatch(feedName = parentSource, matchType = "domain", matchedValue = parent)
        }
        return null
    }

    // ── Feed Parsers ──────────────────────────────────────────

    private fun parseHostsFile(body: String, source: String): ParseResult {
        val domains = mutableListOf<Pair<String, String>>()
        for (line in body.lineSequence()) {
            val trimmed = line.trim()
            if (trimmed.isEmpty() || trimmed.startsWith("#")) continue
            // Format: "127.0.0.1 malware.example.com" or "0.0.0.0 malware.example.com"
            val parts = trimmed.split("\\s+".toRegex())
            if (parts.size >= 2 && (parts[0] == "127.0.0.1" || parts[0] == "0.0.0.0")) {
                val domain = parts[1].lowercase()
                if (domain != "localhost" && domain.contains(".")) {
                    domains.add(domain to source)
                }
            }
        }
        return ParseResult(domains = domains)
    }

    private fun parseSpamhausDrop(body: String, source: String): ParseResult {
        val cidrs = mutableListOf<Pair<String, String>>()
        for (line in body.lineSequence()) {
            val trimmed = line.trim()
            if (trimmed.isEmpty() || trimmed.startsWith(";")) continue
            // Format: "1.10.16.0/20 ; SBxxx"
            val cidr = trimmed.split(";", " ").first().trim()
            if (cidr.contains("/") && cidr.split(".").size == 4) {
                cidrs.add(cidr to source)
            }
        }
        return ParseResult(cidrs = cidrs)
    }

    private fun parseIpList(body: String, source: String): ParseResult {
        val cidrs = mutableListOf<Pair<String, String>>()
        for (line in body.lineSequence()) {
            val trimmed = line.trim()
            if (trimmed.isEmpty() || trimmed.startsWith("#")) continue
            // Plain IP addresses — treat as /32
            if (trimmed.matches(Regex("\\d+\\.\\d+\\.\\d+\\.\\d+"))) {
                cidrs.add("$trimmed/32" to source)
            }
        }
        return ParseResult(cidrs = cidrs)
    }

    private fun parseDomainList(body: String, source: String): ParseResult {
        val domains = mutableListOf<Pair<String, String>>()
        for (line in body.lineSequence()) {
            val trimmed = line.trim()
            if (trimmed.isEmpty() || trimmed.startsWith("#")) continue
            val domain = trimmed.lowercase()
            if (domain.contains(".") && !domain.contains(" ")) {
                domains.add(domain to source)
            }
        }
        return ParseResult(domains = domains)
    }

    // ── Network ───────────────────────────────────────────────

    private fun downloadFeed(url: String): String? {
        return try {
            val request = Request.Builder().url(url).build()
            okHttpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    response.body.string()
                } else {
                    Log.w(TAG, "HTTP ${response.code} for $url")
                    null
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Download failed for $url: ${e.message}")
            null
        }
    }

    // ── Persistence ───────────────────────────────────────────

    /** Full refresh: download all feeds, swap state atomically, persist to disk. */
    suspend fun refreshFeedsAndPersist(): Boolean {
        return try {
            val newTrie = IpRadixTrie()
            val newDomains = ConcurrentHashMap<String, String>()
            val rawCidrs = mutableListOf<Pair<String, String>>()
            var successCount = 0

            for (feed in feeds) {
                try {
                    val body = downloadFeed(feed.url) ?: continue
                    val result = feed.parser(body, feed.name)
                    for ((domain, source) in result.domains) {
                        newDomains[domain] = source
                    }
                    for ((cidr, source) in result.cidrs) {
                        newTrie.insert(cidr, source)
                        rawCidrs.add(cidr to source)
                    }
                    successCount++
                    Log.i(TAG, "Parsed ${feed.name}: ${result.domains.size} domains, ${result.cidrs.size} CIDRs")
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to parse feed ${feed.name}: ${e.message}")
                }
            }

            if (successCount == 0) {
                Log.w(TAG, "All feeds failed to download")
                return false
            }

            // Atomic swap
            synchronized(this) {
                ipTrie = newTrie
                domainThreats.clear()
                domainThreats.putAll(newDomains)
                feedCount = successCount
                lastUpdated = System.currentTimeMillis()
                domainCount = domainThreats.size
                ipCidrCount = ipTrie.size
            }

            // Persist with raw CIDRs for cache reload
            val json = JSONObject()
            val domainArray = JSONArray()
            for ((domain, source) in newDomains) {
                domainArray.put(JSONObject().put("domain", domain).put("source", source))
            }
            json.put(CACHE_KEY_DOMAINS, domainArray)

            val cidrArray = JSONArray()
            for ((cidr, source) in rawCidrs) {
                cidrArray.put(JSONObject().put("cidr", cidr).put("source", source))
            }
            json.put(CACHE_KEY_IP_CIDRS, cidrArray)
            json.put(CACHE_KEY_FEEDS, successCount)
            json.put(CACHE_KEY_LAST_UPDATED, lastUpdated)

            File(context.filesDir, CACHE_FILE).writeText(json.toString())
            Log.i(TAG, "Threat intel refreshed and persisted: $domainCount domains, $ipCidrCount CIDRs from $successCount feeds")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Threat intel refresh failed: ${e.message}")
            false
        }
    }
}
