package com.hostshield.util

import com.google.common.truth.Truth.assertThat
import okhttp3.OkHttpClient
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Regression coverage for the PROPFIND response parser. The root-listing case
 * previously returned an empty list for EVERY server response: `parentPath="/"`
 * normalizes to `""` and every href endsWith(""), so the skip-parent check ate
 * all entries — "Test connection" always reported no remote files and the
 * post-upload refresh made successful uploads look like they vanished.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class WebDavSyncPropfindRobolectricTest {

    private val sync = WebDavSync(OkHttpClient())

    private fun propfindXml(vararg hrefs: String): String = buildString {
        append("""<?xml version="1.0"?><d:multistatus xmlns:d="DAV:">""")
        for (href in hrefs) {
            append("<d:response><d:href>").append(href).append("</d:href>")
            append("<d:propstat><d:prop><d:getcontentlength>42</d:getcontentlength>")
            append("</d:prop></d:propstat></d:response>")
        }
        append("</d:multistatus>")
    }

    @Test
    fun `root listing returns entries instead of skipping everything`() {
        val xml = propfindXml("/", "/hostshield_backup.json", "/HostShield/")
        val files = sync.parsePropfindResponse(xml, "/")

        assertThat(files.map { it.name }).containsExactly("hostshield_backup.json", "HostShield")
    }

    @Test
    fun `nested listing still skips the parent collection entry`() {
        val xml = propfindXml(
            "/HostShield/backups/",
            "/HostShield/backups/backup-1.json",
            "/HostShield/backups/backup-2.json",
        )
        val files = sync.parsePropfindResponse(xml, "/HostShield/backups")

        assertThat(files.map { it.name }).containsExactly("backup-1.json", "backup-2.json")
    }

    @Test
    fun `traversal segments are still rejected`() {
        val xml = propfindXml("/../etc/passwd", "/ok.json")
        val files = sync.parsePropfindResponse(xml, "/")

        assertThat(files.map { it.name }).containsExactly("ok.json")
    }
}
