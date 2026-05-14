package com.hostshield.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RootShellRunnerTest {

    @Test
    fun `parseMagiskMajor extracts major version from Magisk version strings`() {
        assertEquals(26, RootShellRunner.parseMagiskMajor("26.4"))
        assertEquals(27, RootShellRunner.parseMagiskMajor("27.0:27000"))
        assertEquals(28, RootShellRunner.parseMagiskMajor("Magisk v28.1"))
    }

    @Test
    fun `parseMagiskMajor returns null for absent Magisk`() {
        assertNull(RootShellRunner.parseMagiskMajor(""))
        assertNull(RootShellRunner.parseMagiskMajor("unknown"))
        assertNull(RootShellRunner.parseMagiskMajor(null))
    }

    @Test
    fun `supportsMountMaster only enables Magisk 26 and newer`() {
        assertFalse(RootShellRunner.supportsMountMaster("25.2"))
        assertTrue(RootShellRunner.supportsMountMaster("26.0"))
        assertTrue(RootShellRunner.supportsMountMaster("27.0:27000"))
        assertFalse(RootShellRunner.supportsMountMaster("unknown"))
    }
}
