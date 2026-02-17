package com.hostshield.util

import com.hostshield.service.formatBytes
import org.junit.Assert.*
import org.junit.Test

class FormatBytesTest {

    @Test
    fun `zero bytes`() {
        assertEquals("0 B", formatBytes(0))
    }

    @Test
    fun `negative bytes`() {
        assertEquals("0 B", formatBytes(-1))
    }

    @Test
    fun `bytes range`() {
        assertEquals("512 B", formatBytes(512))
    }

    @Test
    fun `kilobytes`() {
        val result = formatBytes(1536) // 1.5 KB
        assertTrue(result.contains("KB"))
    }

    @Test
    fun `megabytes`() {
        val result = formatBytes(5_242_880) // 5 MB
        assertTrue(result.contains("MB"))
    }

    @Test
    fun `gigabytes`() {
        val result = formatBytes(2_147_483_648) // 2 GB
        assertTrue(result.contains("GB"))
    }
}
