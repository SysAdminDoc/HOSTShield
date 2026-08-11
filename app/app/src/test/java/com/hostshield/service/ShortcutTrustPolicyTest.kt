package com.hostshield.service

import com.hostshield.service.ShortcutTrustPolicy.Decision
import org.junit.Assert.assertEquals
import org.junit.Test

class ShortcutTrustPolicyTest {

    private val myUid = 10123
    private val systemUid = 1000
    private val launcher = "com.android.launcher3"

    private fun decide(
        sdkInt: Int = 34,
        callerUid: Int? = null,
        callerPackage: String? = null,
        homePackage: String? = launcher,
    ) = ShortcutTrustPolicy.decide(
        sdkInt = sdkInt,
        callerUid = callerUid,
        myUid = myUid,
        systemUid = systemUid,
        callerPackage = callerPackage,
        homePackage = homePackage,
    )

    // Regression: the pre-34 branch read `referrer?.host ?: return true`, so any app
    // could send a host-less EXTRA_REFERRER and silently toggle the DNS firewall.
    // Referrer is caller-supplied on those versions, so nothing there is trustworthy.
    @Test
    fun `below API 34 no caller is ever auto-trusted`() {
        assertEquals(Decision.UNVERIFIABLE, decide(sdkInt = 26))
        assertEquals(Decision.UNVERIFIABLE, decide(sdkInt = 33))
        assertEquals(
            Decision.UNVERIFIABLE,
            decide(sdkInt = 33, callerUid = systemUid, callerPackage = launcher)
        )
    }

    @Test
    fun `API 34 plus trusts our own process`() {
        assertEquals(Decision.TRUSTED, decide(callerUid = myUid))
    }

    @Test
    fun `API 34 plus trusts the system`() {
        assertEquals(Decision.TRUSTED, decide(callerUid = systemUid))
    }

    @Test
    fun `API 34 plus trusts the default launcher`() {
        assertEquals(
            Decision.TRUSTED,
            decide(callerUid = 10999, callerPackage = launcher)
        )
    }

    @Test
    fun `API 34 plus rejects a third-party app`() {
        assertEquals(
            Decision.UNTRUSTED,
            decide(callerUid = 10999, callerPackage = "com.evil.app")
        )
    }

    @Test
    fun `API 34 plus rejects an unavailable caller identity`() {
        assertEquals(Decision.UNTRUSTED, decide(callerUid = null, callerPackage = launcher))
    }

    @Test
    fun `API 34 plus rejects a launcher claim when home cannot be resolved`() {
        assertEquals(
            Decision.UNTRUSTED,
            decide(callerUid = 10999, callerPackage = launcher, homePackage = null)
        )
    }
}
