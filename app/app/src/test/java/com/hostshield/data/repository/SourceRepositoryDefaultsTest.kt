package com.hostshield.data.repository

import com.hostshield.data.model.SourceCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class SourceRepositoryDefaultsTest {

    @Test
    fun `Spotify Ads is an optional built-in source`() {
        val source = spotifyAdsDefaultSource()

        assertEquals("Spotify Ads", source.label)
        assertEquals(SPOTIFY_ADS_SOURCE_URL, source.url)
        assertEquals(SourceCategory.ADS, source.category)
        assertTrue(source.isBuiltin)
        assertFalse(source.enabled)
        assertTrue(source.description.contains("May interrupt playback"))
        assertTrue(source.description.contains("~84 entries"))
    }

    @Test
    fun `HostShield Spotify Ads list includes the live device discoveries`() {
        val file = listOf(
            File("blocklists/SpotifyAds.txt"),
            File("../blocklists/SpotifyAds.txt"),
            File("../../blocklists/SpotifyAds.txt")
        ).first { it.isFile }
        val entries = file.readLines()
            .map(String::trim)
            .filter { it.isNotEmpty() && !it.startsWith("#") }

        assertEquals(84, entries.size)
        assertEquals(entries.size, entries.distinct().size)
        assertTrue(entries.all { it.startsWith("0.0.0.0 ") })
        setOf(
            "0.0.0.0 aet.spotify.com",
            "0.0.0.0 audio-cf.spotifycdn.com",
            "0.0.0.0 heads-fa-tls13.spotifycdn.com",
            "0.0.0.0 verifi.podscribe.com",
            "0.0.0.0 video-akpcw.spotifycdn.com"
        ).forEach { discoveredHost ->
            assertTrue("$discoveredHost missing", discoveredHost in entries)
        }
    }
}
