package com.hostshield.util

import org.junit.Assert.*
import org.junit.Test

class BackupRestoreUtilTest {

    @Test
    fun `restore validators reject unsafe source and rule fields`() {
        assertEquals(
            "https://lists.example.com/hosts.txt",
            BackupRestoreUtil.normalizeRestoredSourceUrl(" https://lists.example.com/hosts.txt ")
        )
        assertNull(BackupRestoreUtil.normalizeRestoredSourceUrl("http://192.168.1.50/hosts.txt"))

        assertEquals("example.com", BackupRestoreUtil.normalizeRestoredHostname(" Example.COM. "))
        // Wildcard rules keep their canonical "*." prefix — the Rules UI, QR import,
        // and HostsParser.matchesWildcard all dispatch on it.
        assertEquals("*.example.com", BackupRestoreUtil.normalizeRestoredHostname("*.example.com", isWildcard = true))
        assertEquals("*.example.com", BackupRestoreUtil.normalizeRestoredHostname("example.com", isWildcard = true))
        assertNull(BackupRestoreUtil.normalizeRestoredHostname("*.example.com", isWildcard = false))
        assertNull(BackupRestoreUtil.normalizeRestoredHostname("bad host.example"))
    }

    @Test
    fun `restored regex rules validate the pattern instead of hostname syntax`() {
        assertEquals("""^ads?\d+\..*""", BackupRestoreUtil.normalizeRestoredRegex("""^ads?\d+\..* """))
        assertNull(BackupRestoreUtil.normalizeRestoredRegex("("))
        assertNull(BackupRestoreUtil.normalizeRestoredRegex(""))
        assertNull(BackupRestoreUtil.normalizeRestoredRegex("a".repeat(501)))
    }

    @Test
    fun `restore validators reject bad redirect IPs and packages`() {
        assertTrue(BackupRestoreUtil.isValidRedirectIp("0.0.0.0"))
        assertTrue(BackupRestoreUtil.isValidRedirectIp("2001:4860:4860::8888"))
        assertFalse(BackupRestoreUtil.isValidRedirectIp("999.1.1.1"))
        assertFalse(BackupRestoreUtil.isValidRedirectIp("8.8.8.8; reboot"))

        assertEquals("com.example.app", BackupRestoreUtil.normalizePackageName(" com.example.app "))
        assertNull(BackupRestoreUtil.normalizePackageName("com.example;rm"))
        assertNull(BackupRestoreUtil.normalizePackageName("example"))
    }
}
