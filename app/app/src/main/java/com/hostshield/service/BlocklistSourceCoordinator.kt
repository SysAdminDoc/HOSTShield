package com.hostshield.service

import com.hostshield.data.model.HostSource
import com.hostshield.data.model.RuleType
import com.hostshield.data.model.SourceHealth
import com.hostshield.data.repository.HostShieldRepository
import com.hostshield.data.source.DownloadResult
import com.hostshield.data.source.SourceDownloadException
import com.hostshield.data.source.SourceDownloader
import com.hostshield.data.source.sourceHttpStatus
import com.hostshield.domain.BlocklistHolder
import com.hostshield.domain.DnsTypeRule
import com.hostshield.domain.parser.HostsParser
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max

data class BlocklistSourceSnapshot(
    val blockDomains: Set<String>,
    val sourceExactAllows: Set<String>,
    val sourceWildcardBlocks: Set<String>,
    val sourceWildcardAllows: Set<String>,
    val dnsTypeRules: List<DnsTypeRule>,
    val exactBlockOrigins: Map<String, String>,
    val wildcardBlockOrigins: Map<String, String>,
    val failedSources: List<SourceFailureNotice>,
    val downloadedSourceCount: Int,
    val enabledSourceCount: Int,
)

data class BlocklistRebuildResult(
    val domainCount: Int,
    val wildcardRuleCount: Int,
    val regexRuleCount: Int,
    val snapshot: BlocklistSourceSnapshot,
    /**
     * True when the live blocklist snapshot was preserved instead of swapped
     * because every enabled source failed to download (e.g. offline refresh).
     * Callers should treat this as "keep protecting with what we have" and
     * schedule a retry, not as a successful rebuild.
     */
    val preservedOnTotalFailure: Boolean = false,
)

@Singleton
class BlocklistSourceCoordinator @Inject constructor(
    private val repository: HostShieldRepository,
    private val downloader: SourceDownloader,
    private val blocklistHolder: BlocklistHolder,
    private val dohBypassUpdater: DohBypassUpdater,
) {
    suspend fun downloadEnabledSourcesForFullSnapshot(): BlocklistSourceSnapshot {
        // When a blocking profile is active and declares an explicit source set,
        // restrict the rebuild to those sources so profile switching actually
        // changes what is blocked (an empty source_ids means "all enabled").
        val profileSourceIds = repository.getActiveProfile()
            ?.sourceIds
            ?.split(',')
            ?.mapNotNull { it.trim().toLongOrNull() }
            ?.toSet()
            ?.takeIf { it.isNotEmpty() }
        val blockSources = repository.getEnabledBlockSources()
            .let { list -> if (profileSourceIds != null) list.filter { it.id in profileSourceIds } else list }
        // Allowlist sources are always applied: they only subtract from the
        // blocklist, so narrowing a profile must never accidentally re-block a
        // domain the user allowlisted.
        val allowlistSources = repository.getEnabledAllowlistSources()
        val enabledSourceCount = blockSources.size + allowlistSources.size
        val blockDomains = mutableSetOf<String>()
        val sourceExactAllows = mutableSetOf<String>()
        val sourceWildcardBlocks = mutableSetOf<String>()
        val sourceWildcardAllows = mutableSetOf<String>()
        val dnsTypeRules = mutableListOf<DnsTypeRule>()
        val exactBlockOrigins = mutableMapOf<String, String>()
        val wildcardBlockOrigins = mutableMapOf<String, String>()
        val failedSources = mutableListOf<SourceFailureNotice>()
        var downloadedSourceCount = 0

        for (source in blockSources) {
            val result = downloader.download(source, forceDownload = true)
            val dl = result.getOrNull()
            if (dl != null && !dl.notModified) {
                downloadedSourceCount++
                val parsed = HostsParser.parseForBlocking(dl.content)
                blockDomains.addAll(parsed.blockDomains)
                parsed.blockDomains.forEach { exactBlockOrigins.putIfAbsent(it, source.label) }
                sourceExactAllows.addAll(parsed.allowDomains)
                sourceWildcardBlocks.addAll(parsed.wildcardBlockDomains)
                parsed.wildcardBlockDomains.forEach {
                    wildcardBlockOrigins.putIfAbsent(it, source.label)
                }
                sourceWildcardAllows.addAll(parsed.wildcardAllowDomains)
                dnsTypeRules.addAll(parsed.dnsTypeRules.map { it.normalized(source.label) })
                persistSuccessfulDownload(source, dl, parsed.entryCount, parsed.parseWarning)
            } else {
                val err = result.exceptionOrNull()
                    ?: SourceDownloadException(
                        "Full blocklist snapshot returned HTTP 304 without content",
                        304,
                    )
                failedSources += recordFailure(source, err)
            }
        }

        for (source in allowlistSources) {
            val result = downloader.download(source, forceDownload = true)
            val dl = result.getOrNull()
            if (dl != null && !dl.notModified) {
                downloadedSourceCount++
                val parsed = HostsParser.parseForAllowing(dl.content)
                sourceExactAllows.addAll(parsed.allowDomains)
                sourceWildcardAllows.addAll(parsed.wildcardAllowDomains)
                dnsTypeRules.addAll(parsed.dnsTypeAllowRules.map { it.normalized(source.label) })
                persistSuccessfulDownload(source, dl, parsed.entryCount, parsed.parseWarning)
            } else {
                val err = result.exceptionOrNull()
                    ?: SourceDownloadException(
                        "Full allowlist snapshot returned HTTP 304 without content",
                        304,
                    )
                failedSources += recordFailure(source, err)
            }
        }

        return BlocklistSourceSnapshot(
            blockDomains = blockDomains,
            sourceExactAllows = sourceExactAllows,
            sourceWildcardBlocks = sourceWildcardBlocks,
            sourceWildcardAllows = sourceWildcardAllows,
            dnsTypeRules = dnsTypeRules,
            exactBlockOrigins = exactBlockOrigins,
            wildcardBlockOrigins = wildcardBlockOrigins,
            failedSources = failedSources,
            downloadedSourceCount = downloadedSourceCount,
            enabledSourceCount = enabledSourceCount,
        )
    }

    suspend fun rebuildBlocklistHolder(
        extraExactBlockOrigins: Map<String, String> = emptyMap(),
    ): BlocklistRebuildResult {
        val snapshot = downloadEnabledSourcesForFullSnapshot()

        // Fail-safe: if every enabled source failed to download (offline refresh,
        // upstream outage) and a populated blocklist is already live, keep it
        // rather than swapping in a near-empty snapshot. Source content is
        // network-only with no disk cache, so an unconditional swap here would
        // silently drop the user to ~zero blocked domains until the next
        // successful refresh. A fresh (empty) holder still swaps so first-run and
        // legitimately-empty configurations behave normally.
        if (snapshot.enabledSourceCount > 0 &&
            snapshot.downloadedSourceCount == 0 &&
            snapshot.blockDomains.isEmpty() &&
            blocklistHolder.domainCount > 0
        ) {
            return BlocklistRebuildResult(
                domainCount = blocklistHolder.domainCount,
                wildcardRuleCount = blocklistHolder.wildcardRules.size,
                regexRuleCount = repository.getEnabledRegexRules().size,
                snapshot = snapshot,
                preservedOnTotalFailure = true,
            )
        }

        val allDomains = snapshot.blockDomains.toMutableSet()
        val sourceAllowDomains = snapshot.sourceExactAllows.toMutableSet()
        val sourceWildcardBlocks = snapshot.sourceWildcardBlocks.toMutableSet()
        val sourceWildcardAllows = snapshot.sourceWildcardAllows.toMutableSet()
        val dnsTypeRules = snapshot.dnsTypeRules.toMutableList()
        val exactBlockOrigins = snapshot.exactBlockOrigins.toMutableMap()
        val wildcardBlockOrigins = snapshot.wildcardBlockOrigins.toMutableMap()

        extraExactBlockOrigins.forEach { (domain, origin) ->
            val normalizedDomain = domain.lowercase()
            if (normalizedDomain.isNotBlank()) {
                allDomains.add(normalizedDomain)
                exactBlockOrigins[normalizedDomain] = origin
            }
        }

        repository.getEnabledRulesByType(RuleType.BLOCK).filter { !it.isWildcard }
            .forEach { rule ->
                val hostname = rule.hostname.lowercase()
                allDomains.add(hostname)
                exactBlockOrigins[hostname] = "User block rule"
            }

        val allowRules = repository.getEnabledRulesByType(RuleType.ALLOW)
        val userExactAllows = allowRules
            .filter { !it.isWildcard && !it.isRegex }
            .map { it.hostname.lowercase() }
            .toSet()
        allowRules.filter { !it.isWildcard }
            .forEach { allDomains.remove(it.hostname.lowercase()) }
        allDomains.removeAll(sourceAllowDomains)

        dohBypassUpdater.mergeCachedInto(
            allDomains,
            sourceWildcardBlocks,
            exactBlockOrigins,
            wildcardBlockOrigins,
        )

        val wildcards = repository.getEnabledWildcards()
        val regexRules = repository.getEnabledRegexRules()
        blocklistHolder.updateAsync(
            allDomains,
            wildcards,
            regexRules,
            sourceWildcardBlocks = sourceWildcardBlocks,
            sourceWildcardAllows = sourceWildcardAllows,
            exactBlockOrigins = exactBlockOrigins,
            sourceWildcardBlockOrigins = wildcardBlockOrigins,
            sourceExactAllows = sourceAllowDomains,
            userExactAllows = userExactAllows,
            dnsTypeRules = dnsTypeRules,
        )

        return BlocklistRebuildResult(
            domainCount = allDomains.size + sourceWildcardBlocks.size,
            wildcardRuleCount = wildcards.size,
            regexRuleCount = regexRules.size,
            snapshot = snapshot,
        )
    }

    private suspend fun persistSuccessfulDownload(
        source: HostSource,
        dl: DownloadResult,
        entryCount: Int,
        parseWarning: String,
    ) {
        val previousCount = source.entryCount
        repository.updateSourceDownloadMeta(
            id = source.id,
            entryCount = entryCount,
            etag = dl.etag,
            sizeBytes = dl.sizeBytes,
            parseWarning = parseWarning,
            prevEntryCount = previousCount,
            domainsAdded = max(entryCount - previousCount, 0),
            domainsRemoved = max(previousCount - entryCount, 0),
        )
    }

    private suspend fun recordFailure(source: HostSource, err: Throwable): SourceFailureNotice {
        val failures = source.consecutiveFailures + 1
        val health = if (failures >= DEAD_FAILURE_THRESHOLD) SourceHealth.DEAD else SourceHealth.ERROR
        val httpStatus = err.sourceHttpStatus()
        repository.updateSourceHealth(
            source.id,
            health,
            err.message ?: "Unknown error",
            failures,
            httpStatus,
        )
        return SourceFailureNotice(
            label = source.label,
            url = source.url,
            error = err.message ?: err.javaClass.simpleName,
            httpStatus = httpStatus,
            lastSuccessfulUpdate = source.lastUpdated,
            consecutiveFailures = failures,
        )
    }

    private companion object {
        const val DEAD_FAILURE_THRESHOLD = 5
    }
}
