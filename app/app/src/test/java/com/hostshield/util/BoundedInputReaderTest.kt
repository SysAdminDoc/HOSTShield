package com.hostshield.util

import com.google.common.truth.Truth.assertThat
import org.junit.Assert.fail
import org.junit.Test
import java.io.ByteArrayInputStream

class BoundedInputReaderTest {

    @Test
    fun `reads exact byte limit`() {
        val bytes = BoundedInputReader.readBytes(
            ByteArrayInputStream("abcde".toByteArray()),
            maxBytes = 5,
            label = "Import file"
        )

        assertThat(String(bytes)).isEqualTo("abcde")
    }

    @Test
    fun `rejects input after byte limit without materializing full stream`() {
        val error = try {
            BoundedInputReader.readUtf8(
                ByteArrayInputStream("abcdef".toByteArray()),
                maxBytes = 5,
                label = "Backup file"
            )
            fail("Expected InputLimitExceededException")
            error("unreachable")
        } catch (e: InputLimitExceededException) {
            e
        }

        assertThat(error.message).contains("Backup file exceeds")
        assertThat(error.message).contains("limit")
    }
}
