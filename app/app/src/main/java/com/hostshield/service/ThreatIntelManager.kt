package com.hostshield.service

import android.content.Context
import android.util.Log
import com.hostshield.data.source.BoundedResponseReader
import com.hostshield.util.BoundedInputReader
import dagger.hilt.android.qualifiers.ApplicationContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

private val THREAT_IPV4_TOKEN = Regex("""(?<![\d.])(?:\d{1,3}\.){3}\d{1,3}(?:/\d{1,2})?(?![\d.])""")
private val THREAT_DOMAIN_LABEL = Regex("""^[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?$""")

internal fun parseThreatIpCidrs(body: String, source: String): List<Pair<String, String>> {
    val cidrs = LinkedHashSet<Pair<String, String>>()
    for (line in body.lineSequence()) {
        val searchable = line.substringBefore("#").substringBefore(";")
        for (match in THREAT_IPV4_TOKEN.findAll(searchable)) {
            normalizeThreatIpToken(match.value)?.let { cidrs.add(it to source) }
        }
    }
    return cidrs.toList()
}

private fun normalizeThreatIpToken(token: String): String? {
    val parts = token.split("/", limit = 2)
    val octets = parts[0].split(".").map { it.toIntOrNull() ?: return null }
    if (octets.size != 4 || octets.any { it !in 0..255 }) return null
    val prefixLength = parts.getOrNull(1)?.toIntOrNull() ?: 32
    if (prefixLength !in 8..32) return null
    return "${octets.joinToString(".")}/$prefixLength"
}

internal fun normalizeThreatDomainToken(token: String): String? {
    val domain = token.trim().trimEnd('.').lowercase()
    if (domain.length !in 3..253) return null
    if (domain.startsWith("*.") || domain.startsWith(".") || domain.contains("://")) return null
    if (domain.any { it.isWhitespace() }) return null
    if (domain in setOf("localhost", "localhost.localdomain", "local", "broadcasthost")) return null

    val labels = domain.split('.')
    if (labels.size < 2) return null
    if (labels.any { it.isEmpty() || it.length > 63 || !THREAT_DOMAIN_LABEL.matches(it) }) return null
    return domain
}

// Threat intelligence feed engine
//
// Downloads and indexes malicious IPs (CIDR prefixes) and domains from
// curated threat intelligence feeds:
//   - abuse.ch URLhaus (malware distribution domains)
//   - Spamhaus DROP (hijacked IP ranges — includes former eDROP data since 2024)
//   - Emerging Threats (compromised IPs)
//   - Disconnect Malware (malware domains)
//
// All feeds are public, unauthenticated HTTPS endpoints. No API keys required.
// Refresh cadence is daily (24h) via ThreatIntelWorker with WiFi-only constraint.
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
        private const val CACHE_KEY_FEED_HEALTH = "feed_health"
        private const val MAX_THREAT_FEED_BYTES = 10L * 1024L * 1024L
        private const val MAX_THREAT_CACHE_BYTES = 25L * 1024L * 1024L
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

    data class FeedHealth(
        val name: String,
        val lastSuccess: Long = 0L,
        val lastFailure: Long = 0L,
        val httpStatus: Int = 0,
        val entryCount: Int = 0,
        val bytesDownloaded: Long = 0L,
        val sha256: String = "",
        val consecutiveFailures: Int = 0,
        val lastError: String = ""
    )

    private data class DownloadResult(
        val body: String? = null,
        val httpStatus: Int = 0,
        val bytesDownloaded: Long = 0L,
        val error: String? = null
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
    @Volatile
    private var domainThreats = ConcurrentHashMap<String, String>()  // domain -> feedName

    @Volatile var feedCount = 0; private set
    @Volatile var domainCount = 0; private set
    @Volatile var ipCidrCount = 0; private set
    @Volatile var lastUpdated = 0L; private set
    @Volatile var feedHealthMap: Map<String, FeedHealth> = emptyMap(); private set

    // ── Feed Configuration ────────────────────────────────────
    //
    // Feed upstream policy notes (2026-06):
    //
    // URLhaus: Public hostfile download, no auth, no documented rate limit.
    //   Refreshed multiple times daily upstream; our 24h cadence is conservative.
    //
    // Spamhaus DROP: Public, no auth, no rate limit on the text endpoint.
    //   Updated roughly every 15 minutes upstream. eDROP was merged into DROP
    //   in 2024 (see spamhaus.org/faqs/do-not-route-or-peer-drop), so DROP
    //   alone provides full coverage — eDROP removed as a separate feed.
    //
    // Emerging Threats: Public compromised-IP list, no auth required.
    //   Whitespace-separated tokens with comment lines. Updated daily upstream.
    //
    // Disconnect Malware: Public domain list hosted on S3, no auth.
    //   Updated periodically; cadence not formally documented.

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
            val json = JSONObject(
                file.inputStream().use { stream ->
                    BoundedInputReader.readUtf8(stream, MAX_THREAT_CACHE_BYTES, "Threat intel cache")
                }
            )

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

            // Load feed health
            val restoredHealth = mutableMapOf<String, FeedHealth>()
            val healthArr = json.optJSONArray(CACHE_KEY_FEED_HEALTH)
            if (healthArr != null) {
                for (i in 0 until healthArr.length()) {
                    val h = healthArr.getJSONObject(i)
                    val name = h.getString("name")
                    restoredHealth[name] = FeedHealth(
                        name = name,
                        lastSuccess = h.optLong("last_success"),
                        lastFailure = h.optLong("last_failure"),
                        httpStatus = h.optInt("http_status"),
                        entryCount = h.optInt("entry_count"),
                        bytesDownloaded = h.optLong("bytes_downloaded"),
                        sha256 = h.optString("sha256", ""),
                        consecutiveFailures = h.optInt("consecutive_failures"),
                        lastError = h.optString("last_error", "")
                    )
                }
            }

            val swappedDomains = ConcurrentHashMap<String, String>(newDomains.size)
            swappedDomains.putAll(newDomains)
            synchronized(this) {
                ipTrie = newTrie
                domainThreats = swappedDomains
                feedCount = json.optInt(CACHE_KEY_FEEDS, 0)
                lastUpdated = json.optLong(CACHE_KEY_LAST_UPDATED, 0L)
                domainCount = domainThreats.size
                ipCidrCount = ipTrie.size
                feedHealthMap = restoredHealth
            }
            Log.i(TAG, "Loaded cached threat intel: $domainCount domains, $ipCidrCount IP CIDRs, ${restoredHealth.size} feed health records")
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

    fun isFeedStale(name: String, thresholdMs: Long = 48 * 60 * 60 * 1000L): Boolean {
        val health = feedHealthMap[name] ?: return true
        return health.lastSuccess == 0L ||
            (System.currentTimeMillis() - health.lastSuccess) > thresholdMs
    }

    fun getFeedHealthSnapshot(): List<FeedHealth> {
        val current = feedHealthMap
        return feeds.map { feed -> current[feed.name] ?: FeedHealth(name = feed.name) }
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
                val domain = normalizeThreatDomainToken(parts[1])
                if (domain != null) {
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
        return ParseResult(cidrs = parseThreatIpCidrs(body, source))
    }

    private fun parseDomainList(body: String, source: String): ParseResult {
        val domains = mutableListOf<Pair<String, String>>()
        for (line in body.lineSequence()) {
            val trimmed = line.trim()
            if (trimmed.isEmpty() || trimmed.startsWith("#")) continue
            val domain = normalizeThreatDomainToken(trimmed)
            if (domain != null) {
                domains.add(domain to source)
            }
        }
        return ParseResult(domains = domains)
    }

    // ── Network ───────────────────────────────────────────────

    private fun downloadFeed(url: String): DownloadResult {
        return try {
            val request = Request.Builder().url(url).build()
            okHttpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val body = BoundedResponseReader.readUtf8(
                        response,
                        MAX_THREAT_FEED_BYTES,
                        "threat feed"
                    )
                    DownloadResult(body = body.content, httpStatus = response.code, bytesDownloaded = body.sizeBytes)
                } else {
                    Log.w(TAG, "HTTP ${response.code} for $url")
                    DownloadResult(httpStatus = response.code, error = "HTTP ${response.code}")
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Download failed for $url: ${e.message}")
            DownloadResult(error = e.message ?: "Unknown error")
        }
    }

    // ── Persistence ───────────────────────────────────────────

    private fun sha256Hex(data: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(data.toByteArray())
            .joinToString("") { "%02x".format(it) }

    /** Full refresh: download all feeds, swap state atomically, persist to disk. */
    suspend fun refreshFeedsAndPersist(): Boolean {
        return try {
            val newTrie = IpRadixTrie()
            val newDomains = ConcurrentHashMap<String, String>()
            val rawCidrs = mutableListOf<Pair<String, String>>()
            val previousHealth = feedHealthMap
            val newHealth = mutableMapOf<String, FeedHealth>()
            var successCount = 0

            for (feed in feeds) {
                val prev = previousHealth[feed.name]
                val dl = downloadFeed(feed.url)
                val body = dl.body
                if (body != null) {
                    try {
                        val result = feed.parser(body, feed.name)
                        for ((domain, source) in result.domains) {
                            newDomains[domain] = source
                        }
                        for ((cidr, source) in result.cidrs) {
                            newTrie.insert(cidr, source)
                            rawCidrs.add(cidr to source)
                        }
                        val entries = result.domains.size + result.cidrs.size
                        newHealth[feed.name] = FeedHealth(
                            name = feed.name,
                            lastSuccess = System.currentTimeMillis(),
                            lastFailure = prev?.lastFailure ?: 0L,
                            httpStatus = dl.httpStatus,
                            entryCount = entries,
                            bytesDownloaded = dl.bytesDownloaded,
                            sha256 = sha256Hex(body),
                            consecutiveFailures = 0,
                            lastError = ""
                        )
                        successCount++
                        Log.i(TAG, "Parsed ${feed.name}: ${result.domains.size} domains, ${result.cidrs.size} CIDRs")
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to parse feed ${feed.name}: ${e.message}")
                        newHealth[feed.name] = FeedHealth(
                            name = feed.name,
                            lastSuccess = prev?.lastSuccess ?: 0L,
                            lastFailure = System.currentTimeMillis(),
                            httpStatus = dl.httpStatus,
                            entryCount = 0,
                            bytesDownloaded = dl.bytesDownloaded,
                            sha256 = "",
                            consecutiveFailures = (prev?.consecutiveFailures ?: 0) + 1,
                            lastError = "Parse: ${e.message?.take(120)}"
                        )
                    }
                } else {
                    newHealth[feed.name] = FeedHealth(
                        name = feed.name,
                        lastSuccess = prev?.lastSuccess ?: 0L,
                        lastFailure = System.currentTimeMillis(),
                        httpStatus = dl.httpStatus,
                        entryCount = 0,
                        bytesDownloaded = 0L,
                        sha256 = "",
                        consecutiveFailures = (prev?.consecutiveFailures ?: 0) + 1,
                        lastError = dl.error?.take(120) ?: "Download failed"
                    )
                }
            }

            if (successCount == 0) {
                Log.w(TAG, "All feeds failed to download")
                synchronized(this) { feedHealthMap = newHealth }
                return false
            }

            val swappedDomains = ConcurrentHashMap<String, String>(newDomains.size)
            swappedDomains.putAll(newDomains)
            synchronized(this) {
                ipTrie = newTrie
                domainThreats = swappedDomains
                feedCount = successCount
                lastUpdated = System.currentTimeMillis()
                domainCount = domainThreats.size
                ipCidrCount = ipTrie.size
                feedHealthMap = newHealth
            }

            // Persist with raw CIDRs and feed health for cache reload
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

            val healthArray = JSONArray()
            for ((_, h) in newHealth) {
                healthArray.put(JSONObject().apply {
                    put("name", h.name)
                    put("last_success", h.lastSuccess)
                    put("last_failure", h.lastFailure)
                    put("http_status", h.httpStatus)
                    put("entry_count", h.entryCount)
                    put("bytes_downloaded", h.bytesDownloaded)
                    put("sha256", h.sha256)
                    put("consecutive_failures", h.consecutiveFailures)
                    put("last_error", h.lastError)
                })
            }
            json.put(CACHE_KEY_FEED_HEALTH, healthArray)

            // Write atomically (temp + rename) so a process kill mid-write can't
            // truncate threat_intel_cache.json and leave loadCached() empty.
            val cacheFile = File(context.filesDir, CACHE_FILE)
            val tmpFile = File(context.filesDir, "$CACHE_FILE.tmp")
            tmpFile.writeText(json.toString())
            if (!tmpFile.renameTo(cacheFile)) {
                cacheFile.writeText(json.toString())
                tmpFile.delete()
            }
            val allFeedsSucceeded = successCount == feeds.size
            val status = if (allFeedsSucceeded) "complete" else "degraded"
            Log.i(TAG, "Threat intel refresh $status and persisted: $domainCount domains, $ipCidrCount CIDRs from $successCount/${feeds.size} feeds")
            allFeedsSucceeded
        } catch (e: Exception) {
            Log.e(TAG, "Threat intel refresh failed: ${e.message}")
            false
        }
    }
}
