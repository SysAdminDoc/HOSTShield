package com.hostshield.util

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UpdateCheckerTest {

    private fun release(tag: String, draft: Boolean = false, prerelease: Boolean = false): JSONObject =
        JSONObject()
            .put("tag_name", tag)
            .put("draft", draft)
            .put("prerelease", prerelease)

    @Test
    fun `selects first stable release skipping drafts and prereleases`() {
        val releases = JSONArray()
            .put(release("v9.9.9", draft = true))
            .put(release("v9.9.8", prerelease = true))
            .put(release("v6.9.0"))

        val selected = UpdateChecker.selectLatestStableRelease(releases)
        assertEquals("v6.9.0", selected?.getString("tag_name"))
    }

    @Test
    fun `returns null when page has only drafts and prereleases`() {
        // A draft/prerelease-only page must never prompt an update
        val releases = JSONArray()
            .put(release("v9.9.9", draft = true))
            .put(release("v9.9.8", prerelease = true))

        assertNull(UpdateChecker.selectLatestStableRelease(releases))
    }

    @Test
    fun `returns null for empty release page`() {
        assertNull(UpdateChecker.selectLatestStableRelease(JSONArray()))
    }
}
