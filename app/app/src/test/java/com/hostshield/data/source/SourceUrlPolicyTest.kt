package com.hostshield.data.source

import com.hostshield.data.model.HostSource
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SourceUrlPolicyTest {
    @Test
    fun `accepts complete https source urls`() {
        val validation = SourceUrlPolicy.validate("  https://lists.example.com/hosts.txt  ")

        assertTrue(validation.isValid)
        assertEquals("https://lists.example.com/hosts.txt", validation.normalizedUrl)
    }

    @Test
    fun `rejects public http source urls`() {
        val validation = SourceUrlPolicy.validate("http://lists.example.com/hosts.txt")

        assertFalse(validation.isValid)
        assertTrue(validation.errorMessage.orEmpty().contains("HTTPS"))
    }

    @Test
    fun `rejects lan and private http source urls`() {
        val urls = listOf(
            "http://192.168.1.10/hosts.txt",
            "http://10.0.0.5/blocklist.txt",
            "http://localhost:8080/hosts.txt",
            "http://router.local/hosts.txt"
        )

        urls.forEach { url ->
            val validation = SourceUrlPolicy.validate(url)
            assertFalse("$url should be rejected", validation.isValid)
            assertTrue(validation.errorMessage.orEmpty().contains("LAN mirrors"))
        }
    }

    @Test
    fun `rejects unsupported schemes and incomplete urls`() {
        listOf("ftp://lists.example.com/hosts.txt", "lists.example.com/hosts.txt", "https://")
            .forEach { url ->
                assertFalse("$url should be rejected", SourceUrlPolicy.validate(url).isValid)
            }
    }

    @Test
    fun `downloader fails http sources before network access`() = runTest {
        val downloader = SourceDownloader()

        val validation = downloader.validate("http://192.168.1.10/hosts.txt")
        val download = downloader.download(
            HostSource(url = "http://192.168.1.10/hosts.txt", label = "LAN")
        )

        assertTrue(validation.isFailure)
        assertTrue(validation.exceptionOrNull()?.message.orEmpty().contains("HTTPS"))
        assertTrue(download.isFailure)
        assertTrue(download.exceptionOrNull()?.message.orEmpty().contains("HTTPS"))
    }
}
