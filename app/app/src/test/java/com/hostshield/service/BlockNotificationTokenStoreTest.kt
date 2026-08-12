package com.hostshield.service

import com.hostshield.data.preferences.SecureStore
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BlockNotificationTokenStoreTest {
    @Test
    fun `capability is one use and binds all notification fields`() {
        val secureStore = mockk<SecureStore>()
        var stored = ""
        every { secureStore.getString(any()) } answers { stored }
        every { secureStore.putString(any(), any()) } answers { stored = secondArg() }
        val store = BlockNotificationTokenStore(secureStore)

        val token = store.issue("allow_once", "ads.example.com", "Filter", "source_list")

        assertTrue(store.consume(token, "allow_once", "ads.example.com", "Filter", "source_list"))
        assertFalse(store.consume(token, "allow_once", "ads.example.com", "Filter", "source_list"))
        verify(exactly = 3) { secureStore.getString(any()) }
    }
}
