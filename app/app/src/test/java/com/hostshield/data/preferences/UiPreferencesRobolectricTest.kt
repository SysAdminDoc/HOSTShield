package com.hostshield.data.preferences

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class UiPreferencesRobolectricTest {

    @Test
    fun `UiPreferences can be constructed with Robolectric context`() {
        val context: Context = ApplicationProvider.getApplicationContext()
        val prefs = UiPreferences(context)
        assertNotNull(prefs)
        assertNotNull(prefs.accentColor)
        assertNotNull(prefs.highContrastAmoled)
        assertNotNull(prefs.dynamicColor)
    }

    @Test
    fun `application context provides valid package name`() {
        val context: Context = ApplicationProvider.getApplicationContext()
        assertNotNull(context.packageName)
        assertTrue(context.packageName.isNotBlank())
    }

    @Test
    fun `cache directory exists under Robolectric`() {
        val context: Context = ApplicationProvider.getApplicationContext()
        val cacheDir = context.cacheDir
        assertNotNull(cacheDir)
        assertTrue(cacheDir.exists() || cacheDir.mkdirs())
    }

    @Test
    fun `pinDomain and unpinDomain update the pinned set`(): Unit = runBlocking {
        val context: Context = ApplicationProvider.getApplicationContext()
        val prefs = UiPreferences(context)
        val a = "pin-a-${System.nanoTime()}.example.com"
        val b = "pin-b-${System.nanoTime()}.example.com"

        prefs.pinDomain(a.uppercase())
        prefs.pinDomain(b)
        val pinned = prefs.pinnedDomains.first()
        assertTrue(a.lowercase() in pinned)
        assertTrue(b in pinned)

        prefs.unpinDomain(a)
        val afterUnpin = prefs.pinnedDomains.first()
        assertFalse(a.lowercase() in afterUnpin)
        assertTrue(b in afterUnpin)

        prefs.unpinDomain(b)
    }

    @Test
    fun `concurrent pins are not lost`() = runBlocking {
        val context: Context = ApplicationProvider.getApplicationContext()
        val prefs = UiPreferences(context)
        val tag = System.nanoTime()
        val domains = (0 until 20).map { "race-$tag-$it.example.com" }

        // Regression: read-modify-write via pinnedDomains.first() + setPinnedDomains()
        // could drop concurrent pins; mutation inside a single ds.edit {} serializes.
        kotlinx.coroutines.coroutineScope {
            domains.forEach { domain ->
                launch { prefs.pinDomain(domain) }
            }
        }

        val pinned = prefs.pinnedDomains.first()
        domains.forEach { domain -> assertTrue("missing $domain", domain in pinned) }

        kotlinx.coroutines.coroutineScope {
            domains.forEach { domain ->
                launch { prefs.unpinDomain(domain) }
            }
        }
        val afterUnpin = prefs.pinnedDomains.first()
        domains.forEach { domain -> assertFalse("still pinned $domain", domain in afterUnpin) }
    }

    @Test
    fun `saved dense list filters are scoped capped and deduped`() = runBlocking {
        val context: Context = ApplicationProvider.getApplicationContext()
        val prefs = UiPreferences(context)
        val screen = "unit-${System.nanoTime()}"

        prefs.clearDenseListFilters(screen)
        repeat(10) { index ->
            prefs.saveDenseListFilter(screen, "Filter $index", """{"query":"$index"}""")
        }

        val capped = prefs.savedDenseListFilters(screen).first()
        assertEquals(8, capped.size)
        assertEquals("Filter 9", capped.first().label)

        prefs.saveDenseListFilter(screen, "Latest duplicate", """{"query":"9"}""")
        val deduped = prefs.savedDenseListFilters(screen).first()
        assertEquals(8, deduped.size)
        assertEquals("Latest duplicate", deduped.first().label)

        prefs.clearDenseListFilters(screen)
        assertTrue(prefs.savedDenseListFilters(screen).first().isEmpty())
    }
}
