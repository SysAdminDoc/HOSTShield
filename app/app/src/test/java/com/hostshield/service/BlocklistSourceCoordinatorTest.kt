package com.hostshield.service

import com.hostshield.data.model.HostSource
import com.hostshield.data.model.SourceCategory
import com.hostshield.data.model.SourceHealth
import com.hostshield.data.repository.HostShieldRepository
import com.hostshield.data.source.DownloadResult
import com.hostshield.data.source.SourceDownloadException
import com.hostshield.data.source.SourceDownloader
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
    private lateinit var coordinator: BlocklistSourceCoordinator

    @Before
    fun setUp() {
        repository = mockk(relaxed = true)
        downloader = mockk()
        coordinator = BlocklistSourceCoordinator(repository, downloader)

        coEvery { repository.updateSource(any()) } just Runs
        coEvery { repository.updateSourceHealth(any(), any(), any(), any(), any()) } just Runs
        coEvery { repository.getEnabledAllowlistSources() } returns emptyList()
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
        val updatedSources = mutableListOf<HostSource>()
        coEvery { repository.getEnabledBlockSources() } returns listOf(source)
        coEvery { repository.updateSource(capture(updatedSources)) } just Runs
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
        val updated = updatedSources.single()
        assertEquals(3, updated.entryCount)
        assertEquals(1, snapshot.dnsTypeRules.size)
        assertEquals("typed.example", snapshot.dnsTypeRules.single().domain)
        assertEquals("Example", snapshot.dnsTypeRules.single().source)
        assertEquals("new", updated.etag)
        assertEquals("Wed, 24 Jun 2026 12:00:00 GMT", updated.lastModifiedOnline)
        assertEquals(64, updated.sizeBytes)
        assertEquals(SourceHealth.OK, updated.health)
        assertEquals("", updated.lastError)
        assertEquals(0, updated.lastHttpStatus)
        assertEquals(0, updated.consecutiveFailures)
        assertEquals(1, updated.prevEntryCount)
        assertEquals(2, updated.domainsAdded)
        assertEquals(0, updated.domainsRemoved)
        assertTrue(updated.lastUpdated > 0)
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
}
