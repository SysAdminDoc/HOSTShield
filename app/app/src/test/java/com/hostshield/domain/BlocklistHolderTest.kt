package com.hostshield.domain

import com.hostshield.data.model.RuleType
import com.hostshield.data.model.UserRule
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import kotlinx.coroutines.runBlocking
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

class BlocklistHolderTest {

    private lateinit var holder: BlocklistHolder
    // domainCount includes the hardcoded DoH-bypass set added by update().
    // We snapshot it after an empty update and treat it as the baseline.
    private var dohBaseline: Int = 0

    @Before
    fun setup() {
        holder = BlocklistHolder()
        holder.update(emptySet(), emptyList())
        dohBaseline = holder.domainCount
        holder.clear()
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
        assertEquals(2 + dohBaseline, holder.domainCount)
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
    fun `source wildcard allow overrides source wildcard block`() {
        holder.update(
            newDomains = emptySet(),
            wildcards = emptyList(),
            sourceWildcardBlocks = setOf("actor"),
            sourceWildcardAllows = setOf("trusted.actor")
        )

        assertTrue(holder.isBlocked("bad.actor"))
        assertTrue(holder.isBlocked("deep.bad.actor"))
        assertFalse(holder.isBlocked("trusted.actor"))
        assertFalse(holder.isBlocked("cdn.trusted.actor"))
    }

    @Test
    fun `decision reports source list attribution for exact and wildcard matches`() {
        holder.update(
            newDomains = setOf("ads.example.com"),
            wildcards = emptyList(),
            sourceWildcardBlocks = setOf("tracker.example.com"),
            exactBlockOrigins = mapOf("ads.example.com" to "Fixture Blocklist"),
            sourceWildcardBlockOrigins = mapOf("tracker.example.com" to "Fixture Wildcards")
        )

        val exact = holder.decide("ads.example.com")
        assertTrue(exact.blocked)
        assertEquals("source_list", exact.reason)
        assertEquals("Fixture Blocklist", exact.source)
        assertEquals("ads.example.com", exact.matchedValue)

        val wildcard = holder.decide("cdn.tracker.example.com")
        assertTrue(wildcard.blocked)
        assertEquals("source_list", wildcard.reason)
        assertEquals("Fixture Wildcards", wildcard.source)
        assertEquals("tracker.example.com", wildcard.matchedValue)
    }

    @Test
    fun `decision reports allowlist precedence over source blocks`() {
        holder.update(
            newDomains = setOf("ads.example.com"),
            wildcards = emptyList(),
            sourceExactAllows = setOf("ads.example.com")
        )

        val decision = holder.decide("ads.example.com")

        assertFalse(decision.blocked)
        assertEquals("allowlist", decision.reason)
        assertEquals("Source allowlist", decision.source)
        assertTrue(decision.precedence.contains("overrides"))
    }

    @Test
    fun `decision reports user exact allow precedence over source blocks`() {
        holder.update(
            newDomains = setOf("ads.example.com"),
            wildcards = emptyList(),
            userExactAllows = setOf("ads.example.com")
        )

        val decision = holder.decide("ads.example.com")

        assertFalse(decision.blocked)
        assertEquals("allowlist", decision.reason)
        assertEquals("User allow rule", decision.source)
        assertTrue(decision.precedence.contains("threat intel"))
    }

    @Test
    fun `allowDomain records immediate explicit allow decision`() {
        holder.update(setOf("ads.example.com"), emptyList())
        val before = holder.domainCount

        holder.allowDomain("ads.example.com")
        val decision = holder.decide("ads.example.com")

        assertFalse(decision.blocked)
        assertEquals("allowlist", decision.reason)
        assertEquals("User allow rule", decision.source)
        assertEquals(before - 1, holder.domainCount)
    }

    @Test
    fun `dns type block rules require matching query type`() {
        holder.update(
            newDomains = emptySet(),
            wildcards = emptyList(),
            dnsTypeRules = listOf(
                DnsTypeRule(
                    domain = "typed.example",
                    dnsTypes = setOf(28),
                    source = "Typed source"
                )
            )
        )

        assertFalse(holder.isBlocked("typed.example"))
        assertFalse(holder.isBlocked("typed.example", 1))
        val decision = holder.decide("api.typed.example", 28)
        assertTrue(decision.blocked)
        assertEquals("dns_type_rule", decision.reason)
        assertEquals("Typed source", decision.source)
    }

    @Test
    fun `negated dns type block rules block every query except excluded types`() {
        holder.update(
            newDomains = emptySet(),
            wildcards = emptyList(),
            dnsTypeRules = listOf(
                DnsTypeRule(
                    domain = "no-ipv6.example",
                    dnsTypes = setOf(1),
                    dnsTypesNegated = true
                )
            )
        )

        assertFalse(holder.isBlocked("no-ipv6.example", 1))
        assertTrue(holder.isBlocked("no-ipv6.example", 28))
        assertTrue(holder.isBlocked("www.no-ipv6.example", 28))
    }

    @Test
    fun `dns type allow overrides untyped source block only for matching type`() {
        holder.update(
            newDomains = setOf("safe.example"),
            wildcards = emptyList(),
            dnsTypeRules = listOf(
                DnsTypeRule(
                    domain = "safe.example",
                    dnsTypes = setOf(1),
                    allow = true,
                    source = "Typed allow"
                )
            )
        )

        val allowed = holder.decide("safe.example", 1)
        val blocked = holder.decide("safe.example", 28)

        assertFalse(allowed.blocked)
        assertEquals("dns_type_allow", allowed.reason)
        assertTrue(blocked.blocked)
        assertEquals("source_list", blocked.reason)
    }

    @Test
    fun `decision keeps DoH bypass blocked despite allowlists`() {
        holder.update(
            newDomains = emptySet(),
            wildcards = emptyList(),
            sourceExactAllows = setOf("dns.google"),
            sourceWildcardAllows = setOf("dns.nextdns.io")
        )

        val exact = holder.decide("dns.google")
        val wildcard = holder.decide("abc123.dns.nextdns.io")

        assertTrue(exact.blocked)
        assertEquals("doh_bypass", exact.reason)
        assertTrue(wildcard.blocked)
        assertEquals("doh_bypass", wildcard.reason)
    }

    @Test
    fun `preview export includes effective exact and source wildcard entries`() {
        holder.update(
            newDomains = setOf("ads.example.com"),
            wildcards = emptyList(),
            sourceWildcardBlocks = setOf("Tracker.Example")
        )

        val keys = holder.exportBlockKeysForPreview()

        assertTrue(keys.contains("ads.example.com"))
        assertTrue(keys.contains("*.tracker.example"))
        assertFalse(keys.contains("dns.google"))
    }

    @Test
    fun `addDomain increments count and blocks`() {
        holder.update(setOf("initial.com"), emptyList())
        val before = holder.domainCount

        holder.addDomain("added.com")
        assertEquals(before + 1, holder.domainCount)
        assertTrue(holder.isBlocked("added.com"))
        assertTrue(holder.isBlocked("initial.com"))

        // Adding the same domain twice must not double-count.
        holder.addDomain("added.com")
        assertEquals(before + 1, holder.domainCount)
    }

    @Test
    fun `removeDomain decrements count and unblocks`() {
        holder.update(setOf("a.com", "b.com"), emptyList())
        val before = holder.domainCount

        holder.removeDomain("a.com")
        assertEquals(before - 1, holder.domainCount)
        assertFalse(holder.isBlocked("a.com"))
        assertTrue(holder.isBlocked("b.com"))

        // Removing a domain that was never added must NOT drop the counter.
        holder.removeDomain("never-existed.example")
        assertEquals(before - 1, holder.domainCount)
    }

    @Test
    fun `clear resets everything`() {
        holder.update(setOf("a.com", "b.com"), emptyList())
        holder.clear()

        assertEquals(0, holder.domainCount)
        assertFalse(holder.isBlocked("a.com"))
        assertFalse(holder.isBlocked("b.com"))
    }

    @Test
    fun `pathological regex times out without disabling safe regex rules`() {
        holder.update(
            newDomains = emptySet(),
            wildcards = emptyList(),
            regexRules = listOf(
                UserRule(hostname = "(a|aa)+b", type = RuleType.BLOCK, isRegex = true),
                UserRule(hostname = "tracker\\.example", type = RuleType.BLOCK, isRegex = true)
            )
        )

        val started = System.nanoTime()
        assertFalse(holder.isBlocked("${"a".repeat(180)}.example"))
        val elapsedMs = (System.nanoTime() - started) / 1_000_000

        assertTrue("Pathological regex should be bounded, took ${elapsedMs}ms", elapsedMs < 250)
        assertTrue(holder.isBlocked("ads.tracker.example"))
    }

    @Test
    fun `cached decisions are tied to the snapshot that produced them`() {
        holder.update(
            newDomains = emptySet(),
            wildcards = listOf(UserRule(hostname = "*.example.com", type = RuleType.BLOCK, enabled = true))
        )

        assertTrue(holder.isBlocked("ads.example.com"))

        holder.update(newDomains = emptySet(), wildcards = emptyList())

        assertFalse(holder.isBlocked("ads.example.com"))
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

        assertEquals(100_000 + dohBaseline, holder.domainCount)

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
        assertEquals(3 + dohBaseline, holder.getBlockedCount())
    }

    @Test
    fun `async update does not interrupt concurrent readers`() = runBlocking {
        holder.update(setOf("old.example.com"), emptyList())
        val started = CountDownLatch(1)
        val running = AtomicBoolean(true)
        val failures = AtomicInteger(0)

        val reader = Thread {
            started.countDown()
            while (running.get()) {
                try {
                    holder.isBlocked("old.example.com")
                    holder.isBlocked("new.example.com")
                    holder.isBlocked("safe.example.com")
                } catch (_: Throwable) {
                    failures.incrementAndGet()
                }
            }
        }

        reader.start()
        started.await()
        repeat(100) {
            holder.updateAsync(setOf("new.example.com"), emptyList())
            holder.updateAsync(setOf("old.example.com"), emptyList())
        }
        running.set(false)
        reader.join()

        assertEquals("Concurrent readers should not fail during async snapshot swaps", 0, failures.get())
        assertTrue(holder.isBlocked("old.example.com"))
        assertFalse(holder.isBlocked("new.example.com"))
    }
}
