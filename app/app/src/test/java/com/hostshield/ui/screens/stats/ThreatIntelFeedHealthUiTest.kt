package com.hostshield.ui.screens.stats

import com.hostshield.service.ThreatIntelManager
import org.junit.Assert.assertEquals
import org.junit.Test

class ThreatIntelFeedHealthUiTest {

    @Test
    fun `maps recent successful feed to healthy`() {
        val now = 1_000_000L
        val ui = ThreatIntelFeedHealthUi.fromHealth(
            ThreatIntelManager.FeedHealth(
                name = "URLhaus",
                lastSuccess = now - 60_000L,
                httpStatus = 200,
                entryCount = 42,
                bytesDownloaded = 4096L,
                sha256 = "abcdef1234567890"
            ),
            nowMs = now
        )

        assertEquals(ThreatIntelFeedStatus.HEALTHY, ui.status)
        assertEquals("abcdef123456", ui.sha256Short)
    }

    @Test
    fun `maps old successful feed to stale`() {
        val now = 4 * 24 * 60 * 60 * 1000L
        val ui = ThreatIntelFeedHealthUi.fromHealth(
            ThreatIntelManager.FeedHealth(
                name = "Spamhaus DROP",
                lastSuccess = now - (3 * 24 * 60 * 60 * 1000L),
                httpStatus = 200
            ),
            nowMs = now
        )

        assertEquals(ThreatIntelFeedStatus.STALE, ui.status)
    }

    @Test
    fun `maps failed feed without prior success to failed`() {
        val now = 1_000_000L
        val ui = ThreatIntelFeedHealthUi.fromHealth(
            ThreatIntelManager.FeedHealth(
                name = "Emerging Threats",
                lastFailure = now - 60_000L,
                consecutiveFailures = 2,
                lastError = "HTTP 503"
            ),
            nowMs = now
        )

        assertEquals(ThreatIntelFeedStatus.FAILED, ui.status)
    }

    @Test
    fun `maps newer failure after success to degraded`() {
        val now = 1_000_000L
        val ui = ThreatIntelFeedHealthUi.fromHealth(
            ThreatIntelManager.FeedHealth(
                name = "Disconnect Malware",
                lastSuccess = now - 30_000L,
                lastFailure = now - 10_000L,
                consecutiveFailures = 1
            ),
            nowMs = now
        )

        assertEquals(ThreatIntelFeedStatus.DEGRADED, ui.status)
    }

    @Test
    fun `maps never refreshed feed to no cache`() {
        val ui = ThreatIntelFeedHealthUi.fromHealth(
            ThreatIntelManager.FeedHealth(name = "URLhaus"),
            nowMs = 1_000_000L
        )

        assertEquals(ThreatIntelFeedStatus.NEVER_REFRESHED, ui.status)
    }
}
