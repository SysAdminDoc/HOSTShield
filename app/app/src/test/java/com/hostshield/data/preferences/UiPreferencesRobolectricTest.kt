package com.hostshield.data.preferences

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
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
