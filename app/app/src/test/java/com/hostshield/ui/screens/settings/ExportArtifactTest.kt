package com.hostshield.ui.screens.settings

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayOutputStream

class ExportArtifactTest {

    @get:Rule
    val tempDir = TemporaryFolder()

    @Test
    fun `content artifact captures metadata and writes UTF-8 bytes`() {
        val content = "host,blocked\nads.example,12\n"
        val artifact = buildContentExportArtifact(
            kind = ExportArtifactKind.STATS_CSV,
            fileName = "hostshield_stats.csv",
            mimeType = "text/csv",
            privacyNotice = "Contains local stats.",
            shareSubject = "HostShield Statistics CSV",
            chooserTitle = "Share Statistics CSV",
            content = content
        )

        val output = ByteArrayOutputStream()
        artifact.writeExportBytesTo(output)

        assertEquals(ExportArtifactKind.STATS_CSV, artifact.kind)
        assertEquals("hostshield_stats.csv", artifact.fileName)
        assertEquals("text/csv", artifact.mimeType)
        assertEquals(content.toByteArray(Charsets.UTF_8).size.toLong(), artifact.sizeBytes)
        assertEquals(content, output.toString(Charsets.UTF_8.name()))
    }

    @Test
    fun `file artifact preserves filename size and streams file bytes`() {
        val file = tempDir.newFile("hostshield_all_test.pcap")
        val bytes = byteArrayOf(0x0A, 0x0B, 0x0C, 0x0D)
        file.writeBytes(bytes)

        val artifact = buildFileExportArtifact(
            kind = ExportArtifactKind.PCAP,
            file = file,
            mimeType = "application/vnd.tcpdump.pcap",
            privacyNotice = "Contains packet evidence.",
            shareSubject = "HostShield PCAP Export",
            chooserTitle = "Share PCAP"
        )

        val output = ByteArrayOutputStream()
        artifact.writeExportBytesTo(output)

        assertEquals(ExportArtifactKind.PCAP, artifact.kind)
        assertEquals(file.name, artifact.fileName)
        assertEquals(file.length(), artifact.sizeBytes)
        assertTrue(artifact.filePath!!.endsWith(file.name))
        assertArrayEquals(bytes, output.toByteArray())
    }
}
