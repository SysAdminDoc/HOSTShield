package com.hostshield.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Boundary coverage for the threat-intel IPv4 CIDR matcher.
 *
 * The trie decides whether a resolved address belongs to a known-malicious
 * range, but only the feed *parsers* were tested — the matching itself, where an
 * off-by-one in the prefix walk silently widens or narrows every block, had no
 * coverage.
 */
class IpRadixTrieTest {

    private fun trieOf(vararg cidrs: Pair<String, String>) =
        ThreatIntelManager.IpRadixTrie().apply {
            cidrs.forEach { (cidr, source) -> insert(cidr, source) }
        }

    @Test
    fun `an address inside a slash 24 matches and reports its feed`() {
        val trie = trieOf("192.0.2.0/24" to "URLhaus")
        assertEquals("URLhaus", trie.lookup("192.0.2.1"))
        assertEquals("URLhaus", trie.lookup("192.0.2.255"))
    }

    @Test
    fun `addresses just outside a slash 24 do not match`() {
        val trie = trieOf("192.0.2.0/24" to "URLhaus")
        assertNull(trie.lookup("192.0.1.255"))
        assertNull(trie.lookup("192.0.3.0"))
    }

    @Test
    fun `slash 8 covers its whole range`() {
        val trie = trieOf("10.0.0.0/8" to "Spamhaus")
        assertEquals("Spamhaus", trie.lookup("10.0.0.1"))
        assertEquals("Spamhaus", trie.lookup("10.255.255.254"))
        assertNull(trie.lookup("11.0.0.1"))
        assertNull(trie.lookup("9.255.255.255"))
    }

    @Test
    fun `slash 28 boundaries are exact`() {
        // 203.0.113.16/28 spans .16 through .31
        val trie = trieOf("203.0.113.16/28" to "ET")
        assertNull(trie.lookup("203.0.113.15"))
        assertEquals("ET", trie.lookup("203.0.113.16"))
        assertEquals("ET", trie.lookup("203.0.113.31"))
        assertNull(trie.lookup("203.0.113.32"))
    }

    @Test
    fun `slash 32 matches only the single host`() {
        val trie = trieOf("198.51.100.7/32" to "URLhaus")
        assertEquals("URLhaus", trie.lookup("198.51.100.7"))
        assertNull(trie.lookup("198.51.100.6"))
        assertNull(trie.lookup("198.51.100.8"))
    }

    // Overlapping ranges: the walk returns as soon as it reaches a malicious node,
    // so the BROADEST enclosing range answers. Either way the address is detected —
    // only the feed attributed in the UI differs — so this pins current behavior
    // rather than asserting most-specific-wins.
    @Test
    fun `an address covered by overlapping ranges is attributed to the broadest`() {
        val trie = trieOf(
            "192.0.0.0/8" to "Broad",
            "192.0.2.0/24" to "Specific",
        )
        assertEquals("Broad", trie.lookup("192.0.2.5"))
        assertEquals("Broad", trie.lookup("192.1.1.1"))
    }

    @Test
    fun `a nested range is still detected when the enclosing one is absent`() {
        val trie = trieOf("192.0.2.0/24" to "Specific")
        assertEquals("Specific", trie.lookup("192.0.2.5"))
        assertNull(trie.lookup("192.1.1.1"))
    }

    @Test
    fun `malformed input never matches`() {
        val trie = trieOf("192.0.2.0/24" to "URLhaus")
        assertNull(trie.lookup(""))
        assertNull(trie.lookup("not-an-ip"))
        assertNull(trie.lookup("192.0.2"))
        assertNull(trie.lookup("999.0.2.1"))
    }

    @Test
    fun `an empty trie matches nothing`() {
        assertNull(ThreatIntelManager.IpRadixTrie().lookup("192.0.2.1"))
    }
}
