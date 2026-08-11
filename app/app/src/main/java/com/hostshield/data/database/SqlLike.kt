package com.hostshield.data.database

/**
 * Escaping for user-supplied text interpolated into a SQL `LIKE` pattern.
 *
 * The DAO queries append `ESCAPE '\'`; without escaping the input, a search for
 * `100%` matched every row and `_` matched any character — wrong results (not
 * injection: the values are still bound parameters).
 */
object SqlLike {
    /** Escape `\`, `%`, and `_` so the query text is matched literally. */
    fun escape(query: String): String =
        query.replace("\\", "\\\\")
            .replace("%", "\\%")
            .replace("_", "\\_")
}
