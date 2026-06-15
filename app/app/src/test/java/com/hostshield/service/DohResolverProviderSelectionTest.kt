package com.hostshield.service

import org.junit.Assert.assertEquals
import org.junit.Test

class DohResolverProviderSelectionTest {

    @Test
    fun `selected provider is honored even when another provider has lower latency`() {
        val selected = DohResolver.Provider.QUAD9
        val fastestObserved = DohResolver.Provider.CLOUDFLARE

        assertEquals(
            selected,
            DohResolver.choosePrimaryProvider(selected, fastestObserved)
        )
    }

    @Test
    fun `selected provider is honored before latency data exists`() {
        val selected = DohResolver.Provider.ADGUARD

        assertEquals(
            selected,
            DohResolver.choosePrimaryProvider(selected, fastestObservedProvider = null)
        )
    }
}
