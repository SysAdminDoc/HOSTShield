package com.hostshield.service

import com.hostshield.data.model.FirewallRule
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Ordering and completeness coverage for the generated iptables script.
 *
 * This is where the v6.9.59 P0 lived: the apply job was split so the embedded
 * clear script (which deletes the chains) ran *after* the `-N` creates, leaving
 * the per-app firewall silently applying nothing while reporting success. The
 * script is a pure `List<FirewallRule> -> List<String>` function, so the shape
 * can be pinned without a rooted device.
 */
class IptablesManagerScriptTest {

    private val manager = IptablesManager(mockk(relaxed = true), mockk(relaxed = true), mockk(relaxed = true), mockk(relaxed = true))

    private val chains = listOf("hs-main", "hs-wifi", "hs-mobile", "hs-vpn", "hs-tether", "hs-lan", "hs-reject")

    private fun script(
        rules: List<FirewallRule> = emptyList(),
        mode: IptablesManager.FirewallMode = IptablesManager.FirewallMode.BLACKLIST,
    ) = manager.buildScript(rules, mode)

    // The P0 tripwire: every chain must be created after the last delete of that
    // chain, or the script tears down what it just built.
    @Test
    fun `each chain is created after the clear phase deletes it`() {
        val cmds = script()
        for (chain in chains) {
            val lastDelete = cmds.indexOfLast { it.contains("-X $chain") }
            val firstCreate = cmds.indexOfFirst { it.contains("-N $chain") }
            assertTrue("no delete emitted for $chain", lastDelete >= 0)
            assertTrue("no create emitted for $chain", firstCreate >= 0)
            assertTrue(
                "$chain is created at $firstCreate but deleted later at $lastDelete",
                firstCreate > lastDelete
            )
        }
    }

    @Test
    fun `the OUTPUT jump into hs-main is installed exactly once per family`() {
        val cmds = script()
        assertEquals(
            "iptables OUTPUT jump",
            1,
            cmds.count { it.startsWith("iptables -I OUTPUT -j hs-main") }
        )
        assertEquals(
            "ip6tables OUTPUT jump",
            1,
            cmds.count { it.startsWith("ip6tables -I OUTPUT -j hs-main") }
        )
    }

    @Test
    fun `the OUTPUT jump is installed after hs-main exists`() {
        val cmds = script()
        val create = cmds.indexOfFirst { it.contains("-N hs-main") }
        val jump = cmds.indexOfFirst { it.startsWith("iptables -I OUTPUT -j hs-main") }
        assertTrue("jump at $jump precedes chain creation at $create", jump > create)
    }

    @Test
    fun `the clear script removes every chain the build script creates`() {
        val built = script()
            .mapNotNull { Regex("""-N (hs-[a-z]+)""").find(it)?.groupValues?.get(1) }
            .toSet()
        val cleared = manager.buildClearScript()
            .mapNotNull { Regex("""-X (hs-[a-z]+)""").find(it)?.groupValues?.get(1) }
            .toSet()

        // Completeness, not a hand-written list: a chain added to buildScript
        // without a matching teardown leaks across apply cycles.
        assertEquals("chains created but never cleared", emptySet<String>(), built - cleared)
    }

    @Test
    fun `a blocked uid produces owner-matched rules on the interface chains`() {
        val rule = FirewallRule(
            uid = 10042,
            packageName = "com.example.app",
            appLabel = "Example",
            wifiAllowed = false,
            mobileAllowed = false,
        )
        val cmds = script(listOf(rule))

        assertTrue(
            "no wifi owner rule for the blocked uid",
            cmds.any { it.contains("hs-wifi") && it.contains("--uid-owner 10042") }
        )
        assertTrue(
            "no mobile owner rule for the blocked uid",
            cmds.any { it.contains("hs-mobile") && it.contains("--uid-owner 10042") }
        )
    }

    @Test
    fun `an allowed uid emits no owner rules`() {
        val rule = FirewallRule(
            uid = 10043,
            packageName = "com.example.allowed",
            appLabel = "Allowed",
            wifiAllowed = true,
            mobileAllowed = true,
        )
        assertTrue(script(listOf(rule)).none { it.contains("--uid-owner 10043") })
    }

    @Test
    fun `the reject chain logs before rejecting`() {
        val cmds = script()
        val log = cmds.indexOfFirst { it.contains("hs-reject") && it.contains("NFLOG") }
        val reject = cmds.indexOfFirst { it.contains("hs-reject") && it.contains("-j REJECT") }
        assertTrue("no NFLOG rule in the reject chain", log >= 0)
        assertTrue("reject at $reject precedes logging at $log", reject > log)
    }
}
