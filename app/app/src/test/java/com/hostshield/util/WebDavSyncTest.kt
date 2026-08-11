package com.hostshield.util

import com.google.common.truth.Truth.assertThat
import okhttp3.OkHttpClient
import org.junit.Test

class WebDavSyncTest {

    @Test
    fun `normalizes HTTPS server URLs`() {
        assertThat(WebDavSync.normalizeServerUrl(" https://cloud.example.com/remote.php/dav/files/user/ "))
            .isEqualTo("https://cloud.example.com/remote.php/dav/files/user")
        assertThat(WebDavSync.normalizeServerUrl("https://cloud.example.com"))
            .isEqualTo("https://cloud.example.com")
    }

    @Test
    fun `rejects cleartext and embedded credentials`() {
        assertThat(WebDavSync.normalizedServerUrlOrNull("http://cloud.example.com/dav")).isNull()
        assertThat(WebDavSync.normalizedServerUrlOrNull("ftp://cloud.example.com/dav")).isNull()
        assertThat(WebDavSync.normalizedServerUrlOrNull("https://user:pass@cloud.example.com/dav")).isNull()
        assertThat(WebDavSync.normalizedServerUrlOrNull("not a url")).isNull()
    }

    @Test
    fun `low level operations fail closed for invalid server URLs`() {
        val sync = WebDavSync(OkHttpClient())
        val credentials = WebDavSync.Credentials("user", "password")

        assertThat(sync.upload("http://cloud.example.com/dav", credentials, "/backup.json", byteArrayOf(1))).isFalse()
        assertThat(sync.listFiles("http://cloud.example.com/dav", credentials, "/")).isNull()
        assertThat(sync.delete("http://cloud.example.com/dav", credentials, "/backup.json")).isFalse()
        assertThat(sync.createDirectory("http://cloud.example.com/dav", credentials, "/HostShield")).isFalse()
        assertThat(sync.testConnection("http://cloud.example.com/dav", credentials)).isFalse()
    }
}
