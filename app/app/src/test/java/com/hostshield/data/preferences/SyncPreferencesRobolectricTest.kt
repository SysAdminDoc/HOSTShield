package com.hostshield.data.preferences

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SyncPreferencesRobolectricTest {

    private fun prefs(secureStore: SecureStore = mockk(relaxed = true)): SyncPreferences {
        val context: Context = ApplicationProvider.getApplicationContext()
        return SyncPreferences(context, secureStore)
    }

    @Test
    fun `sync url hash round trip`() = runBlocking {
        val p = prefs()
        val url = "https://example.com/list-${System.nanoTime()}.txt"
        assertNull(p.getSyncUrlHash(url))

        p.setSyncUrlHash(url, "deadbeef")
        assertEquals("deadbeef", p.getSyncUrlHash(url))
    }

    @Test
    fun `hashCode-colliding urls do not cross-wire integrity hashes`() = runBlocking {
        val p = prefs()
        // "Aa" and "BB" have identical String.hashCode() — under the old
        // hashCode()-based keying these two URLs shared one preference key.
        val tag = System.nanoTime()
        val urlA = "https://Aa.example/$tag"
        val urlB = "https://BB.example/$tag"
        assertEquals(urlA.hashCode(), urlB.hashCode())

        p.setSyncUrlHash(urlA, "hash-for-a")
        p.setSyncUrlHash(urlB, "hash-for-b")

        assertEquals("hash-for-a", p.getSyncUrlHash(urlA))
        assertEquals("hash-for-b", p.getSyncUrlHash(urlB))
    }

    @Test
    fun `webdavPassword is a cold flow that defers SecureStore reads`() = runBlocking {
        val secureStore = mockk<SecureStore>()
        every { secureStore.getString("sec_webdav_password") } returns "first-value"
        val p = prefs(secureStore)

        // Property access alone must not hit the Keystore/disk.
        val flow = p.webdavPassword
        verify(exactly = 0) { secureStore.getString(any()) }

        assertEquals("first-value", flow.first())
        verify(exactly = 1) { secureStore.getString("sec_webdav_password") }

        // A later write must be visible to subsequent collections (flowOf cached forever).
        every { secureStore.getString("sec_webdav_password") } returns "second-value"
        assertEquals("second-value", p.webdavPassword.first())
    }
}
