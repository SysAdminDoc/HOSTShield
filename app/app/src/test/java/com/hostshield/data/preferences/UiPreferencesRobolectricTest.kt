package com.hostshield.data.preferences

import android.content.Context
import androidx.test.core.app.ApplicationProvider
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
}
