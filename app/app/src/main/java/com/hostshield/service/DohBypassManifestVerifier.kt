package com.hostshield.service

import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.security.MessageDigest
import java.security.Signature
import java.security.cert.CertificateFactory
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import java.util.Base64
import java.util.Locale

internal data class VerifiedDohBypassManifest(
    val version: Int,
    val updated: String,
    val createdAt: String,
    val payloadSha256: String,
    val domains: Set<String>,
    val wildcards: Set<String>,
    val ipSets: DnsTrapIpSets?
)

internal object DohBypassManifestVerifier {
    const val SCHEMA_VERSION = 1
    const val IP_SET_SCHEMA_VERSION = 2
    const val MAX_ENTRIES = 500
    const val MAX_IP_ENTRIES = 128
    const val SIGNATURE_ALGORITHM = "SHA256withRSA"
    const val KEY_ID = "hostshield-release-rsa-v1"

    private const val PINNED_CERTIFICATE_BASE64 =
        "MIIDijCCAnKgAwIBAgIJAJGI0PdyUq8fMA0GCSqGSIb3DQEBDAUAMHIxCzAJBgNVBAYTAlVTMRAwDgYDVQQIEwdVbmtub3duMRAwDgYDVQQHEwdVbmtub3duMRQwEgYDVQQKEwtTeXNBZG1pbkRvYzEUMBIGA1UECxMLU3lzQWRtaW5Eb2MxEzARBgNVBAMTCkhvc3RTaGllbGQwIBcNMjYwMzIwMTkzODIzWhgPMjA1MzA4MDUxOTM4MjNaMHIxCzAJBgNVBAYTAlVTMRAwDgYDVQQIEwdVbmtub3duMRAwDgYDVQQHEwdVbmtub3duMRQwEgYDVQQKEwtTeXNBZG1pbkRvYzEUMBIGA1UECxMLU3lzQWRtaW5Eb2MxEzARBgNVBAMTCkhvc3RTaGllbGQwggEiMA0GCSqGSIb3DQEBAQUAA4IBDwAwggEKAoIBAQDS48ZqcuMyZmmN6xNJWAHL3a5pKrHfAQqgF9VY/Za4u9BbvFB0QXvOS2zBRXJjaa3bFlzw0ZDFzhuqpWXIrrEPauhl35m946ezXZC899yUU/Aue0re+F4qDsDhy0ASf6I1aMeCNTXqkYgOLzoKLzeFTRFvAMhbi8AK9s6TMJu/8V0vbMjyxVuVspmznrrZ3s9HXnDr/nMw68EdT87KMn4W+kmBM2332caHNEmORQs9jSBr4Im7aXI79X9HP4s1MK9S0lVCJaRi6/R6eA718RRtuBclSQnpY5hPKxmdUqxZDL4CRQFz9SPR9UVfZqG7OT5A7uI1C/b/rOO9400stDjrAgMBAAGjITAfMB0GA1UdDgQWBBRGd6jHivlGAsdbb7Mch7fmC72qbjANBgkqhkiG9w0BAQwFAAOCAQEAdyoOv5Yxi6qTr6B3FZiraQzm0rxlKQfdPHY9ZvQxdJXsRxee/PgTgV4gQDCd/bnFOgtrHN4b94eN5qf/pLiBSfBSb5/8fVnvyc/oc0bozXCFALtXehq9Zj+vWsJZdWwso/mI7qXa8UwhhPT8mgUI7ONfkga3MXtoH2sSMp4ShpG9r3mKBMRKRoKuRoTtlpPgk2UCPFtTZZ0yx8H7y+Yoe0FDbMV28aNs7VWUUFekcv+EsJvI4vNYNpGvImF/hg0QCD3MEtLLYhtMsgTUnSRIBm2fHWSGau+zpER3OmNrQ0bSrDyMQP+P75CwHIqvuS30Hh3uW0KVkeDZUHlbL7KJNg=="

    private val domainRegex =
        Regex("""^(?:[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?\.)+[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])$""")
    private val dateRegex = Regex("""^\d{4}-\d{2}-\d{2}$""")
    private val utcInstantRegex = Regex("""^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}Z$""")

    fun verify(json: String, cachedVersion: Int = 0): Result<VerifiedDohBypassManifest> = runCatching {
        val obj = JSONObject(json)
        val schema = obj.optInt("schema", 0)
        require(schema == SCHEMA_VERSION || schema == IP_SET_SCHEMA_VERSION) {
            "Remote DoH bypass manifest schema $schema is unsupported"
        }

        val version = obj.optInt("version", 0)
        require(version >= 1) { "Remote DoH bypass manifest version must be >= 1" }
        require(version >= cachedVersion) {
            "Remote DoH bypass manifest rollback rejected: v$version < cached v$cachedVersion"
        }

        val updated = obj.optString("updated", "").trim()
        require(dateRegex.matches(updated)) {
            "Remote DoH bypass manifest updated date must be yyyy-MM-dd"
        }
        val createdAt = obj.optString("created_at", "").trim()
        require(utcInstantRegex.matches(createdAt)) {
            "Remote DoH bypass manifest created_at must be UTC yyyy-MM-ddTHH:mm:ssZ"
        }

        val domains = readDomainSet(obj, "domains")
        val wildcards = readDomainSet(obj, "wildcards")
        val entryCount = domains.size + wildcards.size
        require(entryCount > 0) { "Remote DoH bypass manifest must contain at least one entry" }
        require(entryCount <= MAX_ENTRIES) {
            "Remote DoH bypass manifest has $entryCount entries; maximum is $MAX_ENTRIES"
        }

        val ipSets = if (schema == IP_SET_SCHEMA_VERSION) {
            readIpSets(obj)
        } else {
            null
        }

        val canonical = canonicalPayload(
            version = version,
            updated = updated,
            createdAt = createdAt,
            domains = domains,
            wildcards = wildcards,
            ipSets = ipSets,
            schema = schema
        )
        val payloadSha256 = sha256Hex(canonical)
        val expectedHash = obj.optString("payload_sha256", "").trim().lowercase(Locale.US)
        require(expectedHash == payloadSha256) {
            "Remote DoH bypass manifest payload hash mismatch"
        }

        val signatureObject = obj.optJSONObject("signature")
            ?: error("Remote DoH bypass manifest is missing signature object")
        require(signatureObject.optString("algorithm", "") == SIGNATURE_ALGORITHM) {
            "Remote DoH bypass manifest signature algorithm is unsupported"
        }
        require(signatureObject.optString("key_id", "") == KEY_ID) {
            "Remote DoH bypass manifest signature key_id is unsupported"
        }
        val signatureBytes = Base64.getDecoder().decode(signatureObject.optString("value", ""))
        require(verifySignature(canonical.toByteArray(Charsets.UTF_8), signatureBytes)) {
            "Remote DoH bypass manifest signature verification failed"
        }

        VerifiedDohBypassManifest(
            version = version,
            updated = updated,
            createdAt = createdAt,
            payloadSha256 = payloadSha256,
            domains = domains,
            wildcards = wildcards,
            ipSets = ipSets
        )
    }

    internal fun canonicalPayload(
        version: Int,
        updated: String,
        createdAt: String,
        domains: Set<String>,
        wildcards: Set<String>,
        ipSets: DnsTrapIpSets? = null,
        schema: Int = SCHEMA_VERSION
    ): String {
        require(schema == SCHEMA_VERSION || schema == IP_SET_SCHEMA_VERSION) {
            "Unsupported DoH bypass manifest schema: $schema"
        }
        if (schema == IP_SET_SCHEMA_VERSION) {
            requireNotNull(ipSets) { "Schema 2 requires signed IP sets" }
        }
        return buildString {
            append("schema=").append(schema).append('\n')
            append("version=").append(version).append('\n')
            append("updated=").append(updated).append('\n')
            append("created_at=").append(createdAt).append('\n')
            append("domains=").append(domains.sorted().joinToString(",")).append('\n')
            append("wildcards=").append(wildcards.sorted().joinToString(","))
            if (schema == IP_SET_SCHEMA_VERSION) {
                append('\n')
                append("dns_trap_ipv4=")
                    .append(ipSets!!.dnsTrapIpv4.sorted().joinToString(","))
                    .append('\n')
                append("dns_trap_ipv6=")
                    .append(ipSets.dnsTrapIpv6.sorted().joinToString(","))
                    .append('\n')
                append("doh_bypass_ipv4=")
                    .append(ipSets.dohBypassIpv4.sorted().joinToString(","))
                    .append('\n')
                append("doh_bypass_ipv6=")
                    .append(ipSets.dohBypassIpv6.sorted().joinToString(","))
            }
        }
    }

    private fun readIpSets(obj: JSONObject): DnsTrapIpSets {
        val requiredNames = listOf(
            "dns_trap_ipv4",
            "dns_trap_ipv6",
            "doh_bypass_ipv4",
            "doh_bypass_ipv6"
        )
        requiredNames.forEach { name ->
            require(obj.has(name)) {
                "Schema 2 DoH bypass manifest is missing $name"
            }
        }
        val ipSets = DnsTrapIpSets(
            dnsTrapIpv4 = readIpSet(obj, "dns_trap_ipv4", ipv6 = false),
            dnsTrapIpv6 = readIpSet(obj, "dns_trap_ipv6", ipv6 = true),
            dohBypassIpv4 = readIpSet(obj, "doh_bypass_ipv4", ipv6 = false),
            dohBypassIpv6 = readIpSet(obj, "doh_bypass_ipv6", ipv6 = true)
        )
        val total = ipSets.dnsTrapIpv4.size +
            ipSets.dnsTrapIpv6.size +
            ipSets.dohBypassIpv4.size +
            ipSets.dohBypassIpv6.size
        require(ipSets.isComplete) {
            "Schema 2 DoH bypass manifest must provide every IPv4/IPv6 IP set"
        }
        require(total > 0) { "Schema 2 DoH bypass manifest must contain at least one IP" }
        require(total <= MAX_IP_ENTRIES) {
            "Schema 2 DoH bypass manifest has $total IPs; maximum is $MAX_IP_ENTRIES"
        }
        return ipSets
    }

    private fun readIpSet(obj: JSONObject, name: String, ipv6: Boolean): Set<String> {
        val array = obj.optJSONArray(name)
            ?: error("Schema 2 DoH bypass manifest field $name must be an array")
        val values = linkedSetOf<String>()
        for (i in 0 until array.length()) {
            val raw = array.optString(i, "").trim()
            require(raw.isNotEmpty()) { "Schema 2 DoH bypass manifest contains blank $name entry" }
            require((":" in raw) == ipv6) {
                "Schema 2 DoH bypass manifest $name contains an address from the wrong family: $raw"
            }
            val route = VpnRouteCanonicalizer.canonicalize(
                raw,
                if (ipv6) 128 else 32
            )
            val parsed = InetAddress.getByName(route.address)
            require(if (ipv6) parsed is Inet6Address else parsed is Inet4Address) {
                "Schema 2 DoH bypass manifest $name contains an address from the wrong family: $raw"
            }
            require(
                !parsed.isAnyLocalAddress &&
                    !parsed.isLoopbackAddress &&
                    !parsed.isMulticastAddress &&
                    !parsed.isLinkLocalAddress
            ) {
                "Schema 2 DoH bypass manifest $name contains a non-routable address: $raw"
            }
            require(values.add(route.address)) {
                "Schema 2 DoH bypass manifest contains duplicate $name entry: $raw"
            }
        }
        return values
    }

    private fun readDomainSet(obj: JSONObject, name: String): Set<String> {
        val array = obj.optJSONArray(name) ?: return emptySet()
        val values = linkedSetOf<String>()
        for (i in 0 until array.length()) {
            val value = normalizeDomain(array.optString(i, ""))
            require(values.add(value)) {
                "Remote DoH bypass manifest contains duplicate $name entry: $value"
            }
        }
        return values
    }

    private fun normalizeDomain(value: String): String {
        val candidate = value.trim().lowercase(Locale.US)
        require(
            candidate.length in 4..253 &&
                !candidate.startsWith("*.") &&
                !candidate.startsWith(".") &&
                !candidate.endsWith(".") &&
                domainRegex.matches(candidate)
        ) {
            "Remote DoH bypass manifest contains invalid domain entry: $value"
        }
        return candidate
    }

    private fun sha256Hex(value: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    private fun verifySignature(payload: ByteArray, signatureBytes: ByteArray): Boolean {
        val signature = Signature.getInstance(SIGNATURE_ALGORITHM)
        signature.initVerify(pinnedCertificatePublicKey())
        signature.update(payload)
        return signature.verify(signatureBytes)
    }

    private fun pinnedCertificatePublicKey() =
        CertificateFactory.getInstance("X.509")
            .generateCertificate(
                ByteArrayInputStream(Base64.getDecoder().decode(PINNED_CERTIFICATE_BASE64))
            )
            .publicKey
}
