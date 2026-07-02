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
)

data class BlocklistRebuildResult(
    val domainCount: Int,
    val wildcardRuleCount: Int,
    val regexRuleCount: Int,
    val snapshot: BlocklistSourceSnapshot,
)

@Singleton
class BlocklistSourceCoordinator @Inject constructor(
    private val repository: HostShieldRepository,
    private val downloader: SourceDownloader,
    private val blocklistHolder: BlocklistHolder,
    private val dohBypassUpdater: DohBypassUpdater,
) {
    suspend fun downloadEnabledSourcesForFullSnapshot(): BlocklistSourceSnapshot {
        val blockSources = repository.getEnabledBlockSources()
        val allowlistSources = repository.getEnabledAllowlistSources()
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
        )
    }

    suspend fun rebuildBlocklistHolder(
        extraExactBlockOrigins: Map<String, String> = emptyMap(),
    ): BlocklistRebuildResult {
        val snapshot = downloadEnabledSourcesForFullSnapshot()
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
        repository.updateSource(
            source.copy(
                entryCount = entryCount,
                lastUpdated = System.currentTimeMillis(),
                lastModifiedOnline = dl.lastModified,
                etag = dl.etag,
                sizeBytes = dl.sizeBytes,
                health = SourceHealth.OK,
                lastError = parseWarning,
                lastHttpStatus = 0,
                consecutiveFailures = 0,
                prevEntryCount = previousCount,
                domainsAdded = max(entryCount - previousCount, 0),
                domainsRemoved = max(previousCount - entryCount, 0),
            )
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
