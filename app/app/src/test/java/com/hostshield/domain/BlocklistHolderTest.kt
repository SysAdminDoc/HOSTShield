package com.hostshield.domain

import com.hostshield.data.model.RuleType
import com.hostshield.data.model.UserRule
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class BlocklistHolderTest {

    private lateinit var holder: BlocklistHolder

    @Before
    fun setup() {
        holder = BlocklistHolder()
    }

    @Test
    fun `empty blocklist blocks nothing`() {
        assertFalse(holder.isBlocked("google.com"))
        assertFalse(holder.isBlocked("example.com"))
        assertEquals(0, holder.domainCount)
    }

    @Test
    fun `exact domain match blocks correctly`() {
        val domains = setOf("ads.example.com", "tracker.evil.com")
        holder.update(domains, emptyList())

        assertTrue(holder.isBlocked("ads.example.com"))
        assertTrue(holder.isBlocked("tracker.evil.com"))
        assertFalse(holder.isBlocked("example.com"))
        assertFalse(holder.isBlocked("safe.example.com"))
        assertEquals(2, holder.domainCount)
    }

    @Test
    fun `case insensitive matching`() {
        holder.update(setOf("ADS.Example.COM"), emptyList())

        assertTrue(holder.isBlocked("ads.example.com"))
        assertTrue(holder.isBlocked("ADS.EXAMPLE.COM"))
        assertTrue(holder.isBlocked("Ads.Example.Com"))
    }

    @Test
    fun `wildcard block rule blocks subdomains`() {
        val wildcards = listOf(
            UserRule(hostname = "*.doubleclick.net", type = RuleType.BLOCK, enabled = true)
        )
        holder.update(emptySet(), wildcards)

        assertTrue(holder.isBlocked("ad.doubleclick.net"))
        assertTrue(holder.isBlocked("stats.ad.doubleclick.net"))
        assertTrue(holder.isBlocked("any.sub.domain.doubleclick.net"))
        // The base domain itself might not be blocked (depends on terminal flag)
    }

    @Test
    fun `wildcard allow overrides wildcard block`() {
        val wildcards = listOf(
            UserRule(hostname = "*.example.com", type = RuleType.BLOCK, enabled = true),
            UserRule(hostname = "*.safe.example.com", type = RuleType.ALLOW, enabled = true)
        )
        holder.update(emptySet(), wildcards)

        assertTrue(holder.isBlocked("ads.example.com"))
        assertFalse(holder.isBlocked("api.safe.example.com"))
        assertFalse(holder.isBlocked("deep.sub.safe.example.com"))
    }

    @Test
    fun `addDomain increments count and blocks`() {
        holder.update(setOf("initial.com"), emptyList())
        assertEquals(1, holder.domainCount)

        holder.addDomain("added.com")
        assertEquals(2, holder.domainCount)
        assertTrue(holder.isBlocked("added.com"))
        assertTrue(holder.isBlocked("initial.com"))
    }

    @Test
    fun `removeDomain decrements count and unblocks`() {
        holder.update(setOf("a.com", "b.com"), emptyList())
        assertEquals(2, holder.domainCount)

        holder.removeDomain("a.com")
        assertEquals(1, holder.domainCount)
        assertFalse(holder.isBlocked("a.com"))
        assertTrue(holder.isBlocked("b.com"))
    }

    @Test
    fun `clear resets everything`() {
        holder.update(setOf("a.com", "b.com"), emptyList())
        holder.clear()

        assertEquals(0, holder.domainCount)
        assertFalse(holder.isBlocked("a.com"))
        assertTrue(holder.domains.isEmpty())
    }

    @Test
    fun `subdomain of blocked domain is not blocked`() {
        // Only the exact domain should be blocked, not arbitrary subdomains
        holder.update(setOf("example.com"), emptyList())

        assertTrue(holder.isBlocked("example.com"))
        assertFalse(holder.isBlocked("sub.example.com"))
        assertFalse(holder.isBlocked("deep.sub.example.com"))
    }

    @Test
    fun `large blocklist performance`() {
        val domains = (1..100_000).map { "domain$it.example.com" }.toSet()
        holder.update(domains, emptyList())

        assertEquals(100_000, holder.domainCount)

        // Lookups should be fast even with 100k entries
        val start = System.nanoTime()
        repeat(1000) {
            holder.isBlocked("domain${it}.example.com")
            holder.isBlocked("nonexistent${it}.test.com")
        }
        val elapsed = (System.nanoTime() - start) / 1_000_000
        // 2000 lookups should finish well under 1 second
        assertTrue("2000 lookups took ${elapsed}ms", elapsed < 1000)
    }

    @Test
    fun `getBlockedCount returns accurate count`() {
        holder.update(setOf("a.com", "b.com", "c.com"), emptyList())
        assertEquals(3, holder.getBlockedCount())
    }
}
