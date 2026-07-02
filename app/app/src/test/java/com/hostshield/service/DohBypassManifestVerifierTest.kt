package com.hostshield.service

import com.google.common.truth.Truth.assertThat
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Test
import java.io.File

class DohBypassManifestVerifierTest {

    @Test
    fun `committed manifest verifies with pinned release certificate`() {
        val result = DohBypassManifestVerifier.verify(committedManifest()).getOrThrow()

        assertThat(result.version).isEqualTo(1)
        assertThat(result.updated).isEqualTo("2026-06-12")
        assertThat(result.createdAt).isEqualTo("2026-07-02T00:00:00Z")
        assertThat(result.payloadSha256)
            .isEqualTo("af74ce7f3ef9c8549989a25d207f1426c3de6a2bc9af99f46f841709b9439acf")
        assertThat(result.domains).contains("dnsforge.de")
        assertThat(result.wildcards).contains("dnswarden.com")
    }

    @Test
    fun `tampered manifest is rejected before storage`() {
        val obj = JSONObject(committedManifest())
        obj.getJSONArray("domains").put("tampered.example")

        val error = DohBypassManifestVerifier.verify(obj.toString()).exceptionOrNull()

        assertThat(error).isNotNull()
        assertThat(error!!.message).contains("payload hash mismatch")
    }

    @Test
    fun `downgrade below cached version is rejected`() {
        val error = DohBypassManifestVerifier.verify(
            committedManifest(),
            cachedVersion = 2
        ).exceptionOrNull()

        assertThat(error).isNotNull()
        assertThat(error!!.message).contains("rollback rejected")
    }

    @Test
    fun `over cap manifest is rejected instead of truncated`() {
        val obj = JSONObject(committedManifest())
        val domains = JSONArray()
        repeat(DohBypassManifestVerifier.MAX_ENTRIES + 1) { index ->
            domains.put("doh-$index.example.com")
        }
        obj.put("domains", domains)
        obj.put("wildcards", JSONArray())

        val error = DohBypassManifestVerifier.verify(obj.toString()).exceptionOrNull()

        assertThat(error).isNotNull()
        assertThat(error!!.message).contains("maximum is ${DohBypassManifestVerifier.MAX_ENTRIES}")
    }

    @Test
    fun `canonical payload is order independent after normalization`() {
        val first = DohBypassManifestVerifier.canonicalPayload(
            version = 7,
            updated = "2026-07-02",
            createdAt = "2026-07-02T00:00:00Z",
            domains = setOf("z.example", "a.example"),
            wildcards = setOf("b.example", "a.example")
        )
        val second = DohBypassManifestVerifier.canonicalPayload(
            version = 7,
            updated = "2026-07-02",
            createdAt = "2026-07-02T00:00:00Z",
            domains = setOf("a.example", "z.example"),
            wildcards = setOf("a.example", "b.example")
        )

        assertThat(first).isEqualTo(second)
        assertThat(first).contains("domains=a.example,z.example")
        assertThat(first).contains("wildcards=a.example,b.example")
    }

    private fun committedManifest(): String {
        return listOf(
            File("../../doh-bypass-list.json"),
            File("../doh-bypass-list.json"),
            File("doh-bypass-list.json")
        ).first { it.exists() }.readText()
    }
}
