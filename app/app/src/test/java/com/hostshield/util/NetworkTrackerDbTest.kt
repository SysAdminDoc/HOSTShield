package com.hostshield.util

import com.google.common.truth.Truth.assertThat
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.Locale
import org.junit.Test

class NetworkTrackerDbTest {
    @Test
    fun loadsAuditedDatasetRowsWithProvenanceAndSuffixMatching() {
        val db = NetworkTrackerDb(
            """
            # domain owner category source
            tracker.example.com	Example Ads	Advertising	hostshield-audit:test
            """.trimIndent()
        )

        val result = db.lookup("pixel.tracker.example.com")

        assertThat(result?.owner).isEqualTo("Example Ads")
        assertThat(result?.category).isEqualTo("Advertising")
        assertThat(result?.source).isEqualTo("hostshield-audit:test")
        assertThat(db.lookup("HTTPS://PIXEL.TRACKER.EXAMPLE.COM.")?.owner)
            .isEqualTo("Example Ads")
    }

    @Test
    fun datasetOverridesLegacyFallbackEntries() {
        val db = NetworkTrackerDb(
            "doubleclick.net	Example Override	Measurement	hostshield-audit:override"
        )

        val result = db.lookup("ad.doubleclick.net")

        assertThat(result?.owner).isEqualTo("Example Override")
        assertThat(result?.category).isEqualTo("Measurement")
        assertThat(result?.source).isEqualTo("hostshield-audit:override")
    }

    @Test
    fun ignoresMalformedDatasetRows() {
        val db = NetworkTrackerDb(
            """
            malformed-row
            valid.example	Valid Owner	Analytics	hostshield-audit:valid
            """.trimIndent()
        )

        assertThat(db.lookup("malformed-row")).isNull()
        assertThat(db.lookup("valid.example")?.owner).isEqualTo("Valid Owner")
    }

    @Test
    fun checkedAttributionAssetHasProvenanceAndUniqueDomains() {
        val rows = Files.readAllLines(trackerAttributionAsset())
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") }

        val normalizedDomains = rows.map { row ->
            val columns = row.split('\t')
            assertThat(columns.size).isEqualTo(4)
            assertThat(columns[1].trim()).isNotEmpty()
            assertThat(columns[2].trim()).isNotEmpty()
            assertThat(columns[3].trim()).startsWith("hostshield-audit:")
            normalizeDomain(columns[0])
        }

        assertThat(rows.size).isAtLeast(100)
        assertThat(normalizedDomains).containsNoDuplicates()
    }

    private fun trackerAttributionAsset(): Path {
        val candidates = listOf(
            Paths.get("src/main/assets/tracker_attribution.tsv"),
            Paths.get("app/src/main/assets/tracker_attribution.tsv")
        )
        return candidates.firstOrNull(Files::exists)
            ?: error("tracker_attribution.tsv not found under ${Paths.get("").toAbsolutePath()}")
    }

    private fun normalizeDomain(domain: String): String {
        val normalized = domain.trim()
            .lowercase(Locale.ROOT)
            .removePrefix("http://")
            .removePrefix("https://")
            .trimStart('.')
            .trimEnd('.')
        assertThat(normalized).contains(".")
        assertThat(normalized.any { it.isWhitespace() || it == '/' }).isFalse()
        return normalized
    }
}
