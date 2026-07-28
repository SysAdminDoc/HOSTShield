package com.hostshield.data.repository

import com.hostshield.data.model.SourceCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SourceRepositoryDefaultsTest {

    @Test
    fun `Spotify Ads is an optional built-in source`() {
        val source = spotifyAdsDefaultSource()

        assertEquals("Spotify Ads", source.label)
        assertEquals(
            "https://raw.githubusercontent.com/Mireli5656/adblock360-/refs/heads/main/lists/spotifyadlist.hosts",
            source.url
        )
        assertEquals(SourceCategory.ADS, source.category)
        assertTrue(source.isBuiltin)
        assertFalse(source.enabled)
        assertTrue(source.description.contains("May interrupt playback"))
    }
}
