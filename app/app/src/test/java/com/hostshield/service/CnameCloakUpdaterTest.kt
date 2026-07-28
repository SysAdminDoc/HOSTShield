package com.hostshield.service

import com.hostshield.data.preferences.AppPreferences
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test

class CnameCloakUpdaterTest {

    @Test
    fun `AdGuard source uses current original tracker destinations`() = runTest {
        val requestedUrls = mutableListOf<String>()
        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                requestedUrls += chain.request().url.toString()
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body("tracker.example\n".toResponseBody("text/plain".toMediaType()))
                    .build()
            }
            .build()
        val prefs = mockk<AppPreferences>(relaxed = true)

        val count = CnameCloakUpdater(prefs, client).fetchAndUpdate()

        assertEquals(1, count)
        assertEquals(
            "https://raw.githubusercontent.com/AdguardTeam/cname-trackers/master/data/combined_original_trackers_justdomains.txt",
            requestedUrls.first()
        )
        coVerify { prefs.setCnameCloakDomains("tracker.example") }
    }

    @Test
    fun `network cancellation is propagated`() = runTest {
        val cancellation = CancellationException("constraints changed")
        val client = OkHttpClient.Builder()
            .addInterceptor { throw cancellation }
            .build()
        val updater = CnameCloakUpdater(mockk(relaxed = true), client)

        val thrown = try {
            updater.fetchAndUpdate()
            fail("Expected CancellationException")
            null
        } catch (error: CancellationException) {
            error
        }

        assertEquals(cancellation.message, thrown?.message)
    }
}
