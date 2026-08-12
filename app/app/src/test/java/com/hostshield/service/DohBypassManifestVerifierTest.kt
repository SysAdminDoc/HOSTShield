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

    @Test
    fun `schema two canonical payload binds both IP families to the signature`() {
        val payload = DohBypassManifestVerifier.canonicalPayload(
            version = 2,
            updated = "2026-08-12",
            createdAt = "2026-08-12T00:00:00Z",
            domains = setOf("doh.example"),
            wildcards = setOf("example.com"),
            ipSets = DnsTrapIpSets(
                dnsTrapIpv4 = setOf("8.8.8.8"),
                dnsTrapIpv6 = setOf("2001:4860:4860:0:0:0:0:8888"),
                dohBypassIpv4 = setOf("1.1.1.1"),
                dohBypassIpv6 = setOf("2606:4700:4700:0:0:0:0:1111")
            ),
            schema = DohBypassManifestVerifier.IP_SET_SCHEMA_VERSION
        )

        assertThat(payload).contains("schema=2")
        assertThat(payload).contains("dns_trap_ipv4=8.8.8.8")
        assertThat(payload).contains("dns_trap_ipv6=2001:4860:4860:0:0:0:0:8888")
        assertThat(payload).contains("doh_bypass_ipv6=2606:4700:4700:0:0:0:0:1111")
    }

    private fun committedManifest(): String {
        return listOf(
            File("../../doh-bypass-list.json"),
            File("../doh-bypass-list.json"),
            File("doh-bypass-list.json")
        ).first { it.exists() }.readText()
    }
}
