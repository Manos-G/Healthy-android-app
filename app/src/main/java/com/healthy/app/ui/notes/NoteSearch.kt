package com.healthy.app.ui.notes

/**
 * Turns what the user types into an FTS4 MATCH query (spec 15.4).
 *
 * The search runs as the user types, so each term needs the prefix operator:
 * "thro" has to find "throat". In FTS4 that operator only applies to a bare
 * token — `thro*`. Quoting the token makes it a phrase, and `"thro"*` matches
 * nothing at all, which is a mistake no amount of string assertion catches
 * because the string looks perfectly reasonable.
 *
 * Safety therefore cannot come from quoting. It comes from the term itself:
 * every character that is not a letter or digit is stripped, which removes
 * every FTS operator character, and the result is lowercased so a typed "OR"
 * becomes the ordinary token `or` rather than the boolean operator, which FTS4
 * only recognises in upper case.
 */
object NoteSearch {

    /** What to MATCH against when the user has typed nothing usable. */
    const val MATCHES_NOTHING = "\"\""

    fun toMatchQuery(raw: String): String {
        val terms = raw.trim()
            .split(Regex("\\s+"))
            .map { term -> term.filter { it.isLetterOrDigit() }.lowercase() }
            .filter { it.isNotEmpty() }

        if (terms.isEmpty()) return MATCHES_NOTHING
        return terms.joinToString(" ") { "$it*" }
    }
}
