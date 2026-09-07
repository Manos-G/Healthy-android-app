package com.healthy.app.ui.notes

/**
 * Turns what the user types into an FTS MATCH query (spec 15.4).
 *
 * Two things to get right. A trailing wildcard makes the search run as they
 * type, so "thro" already finds "throat". And every term is stripped to
 * letters and digits and then quoted, so an apostrophe, a hyphen or a stray
 * quote is searched for rather than parsed: FTS treats several punctuation
 * marks as operators, and a note about a "sore throat - day 2" must not throw
 * a syntax error mid-keystroke.
 */
object NoteSearch {

    /** What to MATCH against when the user has typed nothing usable. */
    const val MATCHES_NOTHING = "\"\""

    fun toMatchQuery(raw: String): String {
        val terms = raw.trim()
            .split(Regex("\\s+"))
            .map { term -> term.filter { it.isLetterOrDigit() } }
            .filter { it.isNotEmpty() }

        if (terms.isEmpty()) return MATCHES_NOTHING
        return terms.joinToString(" ") { "\"$it\"*" }
    }
}
