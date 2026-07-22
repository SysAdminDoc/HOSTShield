package com.hostshield.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream

class TrackerSignatureDbTest {

    private val dexSignatures = listOf(
        "com.example.tracker" to "Lcom/example/tracker/",
        "io.other.sdk" to "Lio/other/sdk/"
    )
    private val overlap = dexSignatures.maxOf { it.second.length } - 1

    private fun scan(payload: String, chunkBytes: Int): Set<String> {
        val prefixes = mutableSetOf<String>()
        TrackerSignatureDb.scanDexStream(
            ByteArrayInputStream(payload.toByteArray(Charsets.ISO_8859_1)),
            dexSignatures,
            overlap,
            ByteArray(chunkBytes + overlap),
            prefixes
        )
        return prefixes
    }

    @Test
    fun `finds signature fully inside one chunk`() {
        val payload = "xx" + "Lcom/example/tracker/Foo;" + "y".repeat(200)
        assertEquals(setOf("com.example.tracker"), scan(payload, 1024))
    }

    @Test
    fun `finds signature spanning a chunk boundary`() {
        val sig = "Lcom/example/tracker/"
        val chunk = 64
        // Straddle the first buffer boundary at every possible split point
        for (offset in 1 until sig.length) {
            val payload = "x".repeat(chunk + overlap - offset) + sig + "Bar;" + "y".repeat(50)
            assertEquals("offset $offset", setOf("com.example.tracker"), scan(payload, chunk))
        }
    }

    @Test
    fun `reports no match when signature is absent`() {
        assertTrue(scan("Lcom/example/other/Foo;".repeat(20), 16).isEmpty())
    }

    @Test
    fun `finds multiple signatures across chunks`() {
        val payload = "Lcom/example/tracker/A;" + "z".repeat(300) + "Lio/other/sdk/B;"
        assertEquals(setOf("com.example.tracker", "io.other.sdk"), scan(payload, 32))
    }
}
