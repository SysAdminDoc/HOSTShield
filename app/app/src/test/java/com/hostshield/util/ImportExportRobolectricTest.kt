package com.hostshield.util

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.hostshield.data.model.RuleType
import com.hostshield.data.model.UserRule
import com.hostshield.data.model.HostSource
import com.hostshield.data.model.SourceCategory
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ImportExportRobolectricTest {

    private lateinit var context: Context
    private lateinit var exporter: ImportExportUtil

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        exporter = ImportExportUtil()
    }

    @Test
    fun `exportJson produces valid JSON and roundtrips through file`() {
        val rules = listOf(
            UserRule(hostname = "ads.example.com", type = RuleType.BLOCK),
            UserRule(hostname = "safe.example.com", type = RuleType.ALLOW)
        )
        val sources = listOf(
            HostSource(url = "https://example.com/hosts.txt", label = "Test", category = SourceCategory.ADS)
        )
        val json = exporter.exportJson(rules, sources)
        val parsed = JSONObject(json)
        assertEquals("HostShield", parsed.getString("app"))
        assertEquals(2, parsed.getJSONArray("rules").length())
        assertEquals(1, parsed.getJSONArray("sources").length())

        val file = File(context.cacheDir, "test-export.json")
        file.writeText(json)
        val readBack = JSONObject(file.readText())
        assertEquals(parsed.toString(), readBack.toString())
        file.delete()
    }

    @Test
    fun `exported rule preserves all fields`() {
        val rule = UserRule(
            hostname = "tracker.example.com",
            type = RuleType.BLOCK,
            redirectIp = "",
            comment = "test comment",
            isWildcard = false,
            isRegex = false,
            enabled = true
        )
        val json = exporter.exportJson(listOf(rule), emptyList())
        val parsed = JSONObject(json)
        val exported = parsed.getJSONArray("rules").getJSONObject(0)
        assertEquals("tracker.example.com", exported.getString("hostname"))
        assertEquals("BLOCK", exported.getString("type"))
        assertEquals("test comment", exported.getString("comment"))
        assertTrue(exported.getBoolean("enabled"))
    }
}
