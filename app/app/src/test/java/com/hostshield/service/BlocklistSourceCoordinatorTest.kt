package com.hostshield.service

import com.hostshield.data.model.HostSource
import com.hostshield.data.model.RuleType
import com.hostshield.data.model.SourceCategory
import com.hostshield.data.model.SourceHealth
import com.hostshield.data.model.UserRule
import com.hostshield.data.repository.HostShieldRepository
import com.hostshield.data.source.DownloadResult
import com.hostshield.data.source.SourceDownloadException
import com.hostshield.data.source.SourceDownloader
import com.hostshield.domain.BlocklistHolder
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.just
import io.mockk.mockk
import io.mockk.Runs
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class BlocklistSourceCoordinatorTest {
    private lateinit var repository: HostShieldRepository
    private lateinit var downloader: SourceDownloader
    private lateinit var blocklistHolder: BlocklistHolder
    private lateinit var dohBypassUpdater: DohBypassUpdater
    private lateinit var coordinator: BlocklistSourceCoordinator

    @Before
    fun setUp() {
        repository = mockk(relaxed = true)
        downloader = mockk()
        blocklistHolder = BlocklistHolder()
        dohBypassUpdater = mockk(relaxed = true)
        coordinator = BlocklistSourceCoordinator(
            repository,
            downloader,
            blocklistHolder,
            dohBypassUpdater,
        )

        coEvery { repository.updateSource(any()) } just Runs
        coEvery { repository.updateSourceHealth(any(), any(), any(), any(), any()) } just Runs
        coEvery { repository.getEnabledAllowlistSources() } returns emptyList()
        coEvery { repository.getEnabledRulesByType(any()) } returns emptyList()
        coEvery { repository.getEnabledWildcards() } returns emptyList()
        coEvery { repository.getEnabledRegexRules() } returns emptyList()
        coEvery {
            dohBypassUpdater.mergeCachedInto(any(), any(), any(), any())
        } returns DohBypassUpdater.RemoteList()
    }

    @Test
    fun `full snapshot downloads are forced and persist block source metadata`() = runTest {
        val source = HostSource(
            id = 7,
            url = "https://example.com/hosts.txt",
            label = "Example",
            entryCount = 1,
            etag = "old",
            lastModifiedOnline = "old-date",
            sizeBytes = 10,
            health = SourceHealth.ERROR,
            consecutiveFailures = 2,
        )
        coEvery { repository.getEnabledBlockSources() } returns listOf(source)
        coEvery {
            downloader.download(source, forceDownload = true)
        } returns Result.success(
            DownloadResult(
                content = """
                    0.0.0.0 ads.example.com
                    0.0.0.0 tracker.example.com
                    ||typed.example^${'$'}dnstype=AAAA
                """.trimIndent(),
                etag = "new",
                lastModified = "Wed, 24 Jun 2026 12:00:00 GMT",
                sizeBytes = 64,
            )
        )

        val snapshot = coordinator.downloadEnabledSourcesForFullSnapshot()

        assertEquals(setOf("ads.example.com", "tracker.example.com"), snapshot.blockDomains)
        assertEquals(1, snapshot.downloadedSourceCount)
        assertTrue(snapshot.failedSources.isEmpty())
        assertEquals(1, snapshot.dnsTypeRules.size)
        assertEquals("typed.example", snapshot.dnsTypeRules.single().domain)
        assertEquals("Example", snapshot.dnsTypeRules.single().source)
        // Download metadata is persisted via a targeted column update (never a
        // full-row rewrite) so concurrent user edits are not clobbered.
        coVerify(exactly = 1) {
            repository.updateSourceDownloadMeta(
                id = 7,
                entryCount = 3,
                etag = "new",
                sizeBytes = 64,
                parseWarning = "",
                prevEntryCount = 1,
                domainsAdded = 2,
                domainsRemoved = 0,
            )
        }
        coVerify(exactly = 0) { repository.updateSource(any()) }
        coVerify(exactly = 1) { downloader.download(source, forceDownload = true) }
        coVerify(exactly = 0) { downloader.download(source) }
    }

    @Test
    fun `mixed block and allow sources keep allow domains for subtraction`() = runTest {
        val blockSource = HostSource(
            id = 1,
            url = "https://example.com/block.txt",
            label = "Block",
            entryCount = 3,
            category = SourceCategory.ADS,
        )
        val allowSource = HostSource(
            id = 2,
            url = "https://example.com/allow.txt",
            label = "Allow",
            entryCount = 0,
            category = SourceCategory.ALLOWLIST,
        )
        coEvery { repository.getEnabledBlockSources() } returns listOf(blockSource)
        coEvery { repository.getEnabledAllowlistSources() } returns listOf(allowSource)
        coEvery {
            downloader.download(blockSource, forceDownload = true)
        } returns Result.success(
            DownloadResult(
                content = """
                    ads.example.com
                    tracker.example.com
                """.trimIndent(),
                etag = "block-etag",
                sizeBytes = 32,
            )
        )
        coEvery {
            downloader.download(allowSource, forceDownload = true)
        } returns Result.success(
            DownloadResult(
                content = """
                    ads.example.com
                    cdn.example.com
                """.trimIndent(),
                etag = "allow-etag",
                sizeBytes = 31,
            )
        )

        val snapshot = coordinator.downloadEnabledSourcesForFullSnapshot()

        assertEquals(setOf("ads.example.com", "tracker.example.com"), snapshot.blockDomains)
        assertEquals(setOf("ads.example.com", "cdn.example.com"), snapshot.sourceExactAllows)
        assertEquals(setOf("tracker.example.com"), snapshot.blockDomains - snapshot.sourceExactAllows)
        assertEquals(2, snapshot.downloadedSourceCount)
        assertTrue(snapshot.failedSources.isEmpty())
    }

    @Test
    fun `conditional 304 responses are not requested during full snapshots`() = runTest {
        val source = HostSource(
            id = 3,
            url = "https://example.com/cached.txt",
            label = "Cached",
            etag = "cached-etag",
            lastModifiedOnline = "Wed, 24 Jun 2026 12:00:00 GMT",
        )
        coEvery { repository.getEnabledBlockSources() } returns listOf(source)
        coEvery {
            downloader.download(source, forceDownload = true)
        } returns Result.success(DownloadResult(content = "ads.example.com"))

        val snapshot = coordinator.downloadEnabledSourcesForFullSnapshot()

        assertEquals(setOf("ads.example.com"), snapshot.blockDomains)
        assertTrue(snapshot.failedSources.isEmpty())
        coVerify(exactly = 1) { downloader.download(source, forceDownload = true) }
        coVerify(exactly = 0) { downloader.download(source) }
    }

    @Test
    fun `scoped AdGuard source rules stay skipped and persist parse warning`() = runTest {
        val source = HostSource(
            id = 11,
            url = "https://example.com/adguard.txt",
            label = "Scoped",
            entryCount = 0,
        )
        val parseWarnings = mutableListOf<String>()
        coEvery { repository.getEnabledBlockSources() } returns listOf(source)
        coEvery {
            repository.updateSourceDownloadMeta(any(), any(), any(), any(), capture(parseWarnings), any(), any(), any())
        } just Runs
        coEvery {
            downloader.download(source, forceDownload = true)
        } returns Result.success(
            DownloadResult(
                content = """
                    ||global.example^
                    ||scoped.example^${'$'}app=com.example
                    ||client.example^${'$'}important,client=lan
                """.trimIndent(),
                etag = "scoped-etag",
                sizeBytes = 96,
            )
        )

        val snapshot = coordinator.downloadEnabledSourcesForFullSnapshot()

        assertEquals(setOf("global.example"), snapshot.sourceWildcardBlocks)
        assertFalse(snapshot.sourceWildcardBlocks.contains("scoped.example"))
        assertFalse(snapshot.sourceWildcardBlocks.contains("client.example"))
        val warning = parseWarnings.single()
        assertTrue(warning.contains("Skipped 2 scoped AdGuard rule(s)"))
        assertTrue(warning.contains("instead of applying them globally"))
    }

    @Test
    fun `download failures update health and return source notices`() = runTest {
        val source = HostSource(
            id = 9,
            url = "https://example.com/bad.txt",
            label = "Bad",
            consecutiveFailures = 4,
            lastUpdated = 1234L,
        )
        coEvery { repository.getEnabledBlockSources() } returns listOf(source)
        coEvery {
            downloader.download(source, forceDownload = true)
        } returns Result.failure(SourceDownloadException("HTTP 500: server error", 500))

        val snapshot = coordinator.downloadEnabledSourcesForFullSnapshot()

        assertTrue(snapshot.blockDomains.isEmpty())
        assertEquals(1, snapshot.failedSources.size)
        val notice = snapshot.failedSources.single()
        assertEquals("Bad", notice.label)
        assertEquals("https://example.com/bad.txt", notice.url)
        assertEquals(500, notice.httpStatus)
        assertEquals(5, notice.consecutiveFailures)
        assertEquals(1234L, notice.lastSuccessfulUpdate)
        coVerify {
            repository.updateSourceHealth(
                9,
                SourceHealth.DEAD,
                "HTTP 500: server error",
                5,
                500,
            )
        }
        coVerify(exactly = 0) { repository.updateSource(any()) }
    }

    @Test
    fun `total download failure preserves the live blocklist instead of swapping empty`() = runTest {
        // Seed the holder with a populated snapshot (simulating a prior good build).
        blocklistHolder.update(setOf("ads.example.com", "tracker.example.com"), emptyList(), emptyList())
        val before = blocklistHolder.domainCount
        assertTrue(before > 0)

        val source = HostSource(id = 21, url = "https://example.com/list.txt", label = "List")
        coEvery { repository.getEnabledBlockSources() } returns listOf(source)
        coEvery {
            downloader.download(source, forceDownload = true)
        } returns Result.failure(SourceDownloadException("offline", 0))

        val result = coordinator.rebuildBlocklistHolder()

        assertTrue(result.preservedOnTotalFailure)
        assertEquals(before, blocklistHolder.domainCount)
        assertEquals(before, result.domainCount)
    }

    @Test
    fun `rebuild holder merges sources user rules sync extras and doh bypasses`() = runTest {
        val blockSource = HostSource(
            id = 12,
            url = "https://example.com/block.txt",
            label = "Block Source",
            entryCount = 0,
        )
        val allowSource = HostSource(
            id = 13,
            url = "https://example.com/allow.txt",
            label = "Allow Source",
            entryCount = 0,
            category = SourceCategory.ALLOWLIST,
        )
        coEvery { repository.getEnabledBlockSources() } returns listOf(blockSource)
        coEvery { repository.getEnabledAllowlistSources() } returns listOf(allowSource)
        coEvery {
            downloader.download(blockSource, forceDownload = true)
        } returns Result.success(
            DownloadResult(
                content = """
                    ads.example.com
                    source-allowed.example.com
                    ||wild.example^
                    ||typed.example^${'$'}dnstype=AAAA
                """.trimIndent(),
            )
        )
        coEvery {
            downloader.download(allowSource, forceDownload = true)
        } returns Result.success(DownloadResult(content = "source-allowed.example.com"))
        coEvery { repository.getEnabledRulesByType(RuleType.BLOCK) } returns listOf(
            UserRule(hostname = "manual.example.com", type = RuleType.BLOCK),
        )
        coEvery { repository.getEnabledRulesByType(RuleType.ALLOW) } returns listOf(
            UserRule(hostname = "ads.example.com", type = RuleType.ALLOW),
        )
        coEvery { repository.getEnabledWildcards() } returns listOf(
            UserRule(hostname = "*.userwild.example", type = RuleType.BLOCK, isWildcard = true),
        )
        coEvery { repository.getEnabledRegexRules() } returns listOf(
            UserRule(hostname = ".*regexad\\.example", type = RuleType.BLOCK, isRegex = true),
        )
        coEvery {
            dohBypassUpdater.mergeCachedInto(any(), any(), any(), any())
        } answers {
            firstArg<MutableSet<String>>().add("doh.example.com")
            secondArg<MutableSet<String>>().add("doh-wild.example")
            arg<MutableMap<String, String>?>(2)?.put("doh.example.com", "DoH cache")
            arg<MutableMap<String, String>?>(3)?.put("doh-wild.example", "DoH cache")
            DohBypassUpdater.RemoteList(
                version = 1,
                domains = setOf("doh.example.com"),
                wildcards = setOf("doh-wild.example"),
            )
        }

        val result = coordinator.rebuildBlocklistHolder(
            mapOf("Sync.Example.com" to "Rule sync URL sync.example"),
        )

        assertEquals(5, result.domainCount)
        assertEquals(1, result.wildcardRuleCount)
        assertEquals(1, result.regexRuleCount)
        assertEquals(2, result.snapshot.downloadedSourceCount)
        assertFalse(blocklistHolder.decide("ads.example.com").blocked)
        assertEquals("User allow rule", blocklistHolder.decide("ads.example.com").source)
        assertFalse(blocklistHolder.decide("source-allowed.example.com").blocked)
        assertEquals("Source allowlist", blocklistHolder.decide("source-allowed.example.com").source)
        assertTrue(blocklistHolder.decide("manual.example.com").blocked)
        assertEquals("User block rule", blocklistHolder.decide("manual.example.com").source)
        assertTrue(blocklistHolder.decide("sync.example.com").blocked)
        assertEquals("Rule sync URL sync.example", blocklistHolder.decide("sync.example.com").source)
        assertTrue(blocklistHolder.decide("sub.wild.example").blocked)
        assertEquals("Block Source", blocklistHolder.decide("sub.wild.example").source)
        assertTrue(blocklistHolder.decide("doh.example.com").blocked)
        assertEquals("DoH cache", blocklistHolder.decide("doh.example.com").source)
        assertTrue(blocklistHolder.decide("sub.doh-wild.example").blocked)
        assertEquals("DoH cache", blocklistHolder.decide("sub.doh-wild.example").source)
        assertTrue(blocklistHolder.decide("typed.example", queryType = 28).blocked)
        assertEquals("Block Source", blocklistHolder.decide("typed.example", queryType = 28).source)
        assertTrue(blocklistHolder.decide("foo.regexad.example").blocked)
        assertEquals("User regex block rule", blocklistHolder.decide("foo.regexad.example").source)
        assertTrue(blocklistHolder.decide("child.userwild.example").blocked)
        assertEquals("User wildcard block rule", blocklistHolder.decide("child.userwild.example").source)
        coVerify(exactly = 1) {
            dohBypassUpdater.mergeCachedInto(any(), any(), any(), any())
        }
    }
}
