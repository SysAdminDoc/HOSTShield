package com.hostshield.data.database

import org.junit.Assert.assertEquals
import org.junit.Test

class SqlLikeTest {

    // Regression: the LIKE queries had no ESCAPE clause and the input was not
    // escaped, so searching for "100%" matched every row and "_" matched any
    // single character.
    @Test
    fun `wildcards in user input are escaped`() {
        assertEquals("100\\%", SqlLike.escape("100%"))
        assertEquals("a\\_b", SqlLike.escape("a_b"))
        assertEquals("\\%\\_\\%", SqlLike.escape("%_%"))
    }

    @Test
    fun `the escape character itself is escaped first`() {
        assertEquals("a\\\\b", SqlLike.escape("a\\b"))
        // A backslash followed by a percent must not become an escaped percent.
        assertEquals("\\\\\\%", SqlLike.escape("\\%"))
    }

    @Test
    fun `ordinary search text is unchanged`() {
        assertEquals("ads.example.com", SqlLike.escape("ads.example.com"))
        assertEquals("", SqlLike.escape(""))
        assertEquals("Example App", SqlLike.escape("Example App"))
    }
}
