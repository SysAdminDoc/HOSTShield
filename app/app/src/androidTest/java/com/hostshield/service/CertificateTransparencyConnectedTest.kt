package com.hostshield.service

import android.os.Build
import android.security.NetworkSecurityPolicy
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.hostshield.util.DiagnosticEventStore
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream

@RunWith(AndroidJUnit4::class)
class CertificateTransparencyConnectedTest {

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()

    @Test
    fun api36PlusRequiresCtForEveryBuiltInDohAndDotHostname() {
        assumeTrue("Certificate Transparency starts on API 36", Build.VERSION.SDK_INT >= 36)

        val hosts = (DohResolver.Provider.entries.map { it.hostname } +
            DotResolver.Provider.entries.map { it.hostname }).distinct()
        val policy = NetworkSecurityPolicy.getInstance()

        hosts.forEach { hostname ->
            assertTrue(
                "Certificate Transparency must be required for $hostname",
                policy.isCertificateTransparencyVerificationRequired(hostname)
            )
        }
    }

    @Test
    fun api37ResolvesAtLeastOnePinnedEncryptedDnsProvider() = runBlocking {
        assumeTrue("This live smoke targets the current API 37 platform", Build.VERSION.SDK_INT >= 37)

        val query = dnsQuery("example.com")
        val dohResolver = DohResolver(
            doh3Resolver = Doh3Resolver(),
            diagnosticEvents = DiagnosticEventStore(context)
        )
        val dohResults = DohResolver.Provider.entries.map { provider ->
            val response = withTimeoutOrNull(15_000L) {
                dohResolver.resolveWithMetadata(query, provider)
            }
            "${provider.name}:${response?.provider?.name ?: "FAIL"}"
        }
        val dotResponse = withTimeoutOrNull(15_000L) {
            DotResolver().resolve(query, DotResolver.Provider.CLOUDFLARE)
        }

        assertTrue(
            "Pinned encrypted DNS did not resolve on API 37: doh=$dohResults dot=${dotResponse?.size}",
            dohResults.any { !it.endsWith(":FAIL") } || (dotResponse?.size ?: 0) >= 12
        )
    }

    private fun dnsQuery(domain: String): ByteArray {
        val bytes = ByteArrayOutputStream()
        DataOutputStream(bytes).use { output ->
            output.writeShort(0x4A37)
            output.writeShort(0x0100)
            output.writeShort(1)
            output.writeShort(0)
            output.writeShort(0)
            output.writeShort(0)
            domain.split('.').forEach { label ->
                output.writeByte(label.length)
                output.writeBytes(label)
            }
            output.writeByte(0)
            output.writeShort(1)
            output.writeShort(1)
        }
        return bytes.toByteArray()
    }
}
