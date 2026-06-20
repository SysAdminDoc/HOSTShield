package com.hostshield.ui

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.hostshield.R
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class StringResourcesRobolectricTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun `app_name resource is non-empty`() {
        val name = context.getString(R.string.app_name)
        assertTrue("app_name should not be blank", name.isNotBlank())
        assertEquals("HostShield", name)
    }

    @Test
    fun `settings section labels resolve without crash`() {
        val labels = listOf(
            R.string.section_protection,
            R.string.section_appearance,
            R.string.section_about
        )
        labels.forEach { resId ->
            val value = context.getString(resId)
            assertTrue("String resource $resId should not be blank", value.isNotBlank())
        }
    }

    @Test
    fun `dynamic color string resources exist`() {
        val label = context.getString(R.string.settings_dynamic_color)
        val sub = context.getString(R.string.settings_dynamic_color_sub)
        assertTrue(label.isNotBlank())
        assertTrue(sub.isNotBlank())
    }
}
