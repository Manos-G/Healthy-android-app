package com.healthy.app.ui.notes

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Building the FTS query from what the user types (spec 15.4).
 *
 * These assert the shape of the string. They cannot assert that SQLite agrees
 * with it — an earlier version produced `"thro"*`, which reads as sensible
 * quoting and matches nothing. `SchemaTest.aPrefixMatchesBeforeTheWordIsFinished`
 * is the test that actually proves the syntax, and it runs on the device.
 */
class NoteSearchTest {

    @Test
    fun `a term gets a bare trailing wildcard, not a quoted one`() {
        assertEquals("thro*", NoteSearch.toMatchQuery("thro"))
    }

    @Test
    fun `the wildcard is never applied to a quoted phrase`() {
        val query = NoteSearch.toMatchQuery("sore throat")
        assertFalse("a quoted token cannot take the prefix operator", query.contains("\"*"))
    }

    @Test
    fun `several words become several prefixed terms`() {
        assertEquals("sore* thro*", NoteSearch.toMatchQuery("sore thro"))
    }

    /** FTS reads punctuation as syntax, so none of it may reach the query. */
    @Test
    fun `punctuation is stripped rather than reaching the MATCH clause`() {
        assertEquals("sore* throat* day* 2*", NoteSearch.toMatchQuery("sore throat - day 2"))
        assertEquals("dont*", NoteSearch.toMatchQuery("don't"))
    }

    /**
     * FTS4 recognises AND, OR, NOT and NEAR only in upper case, so lowercasing
     * turns a typed operator into an ordinary token.
     */
    @Test
    fun `a typed boolean operator becomes an ordinary token`() {
        assertEquals("a* or* b*", NoteSearch.toMatchQuery("a OR b"))
        assertEquals("pain* not* sleep*", NoteSearch.toMatchQuery("pain NOT sleep"))
        assertEquals("near*", NoteSearch.toMatchQuery("NEAR"))
    }

    @Test
    fun `no query contains an unescaped operator character`() {
        val query = NoteSearch.toMatchQuery("a\" OR (b) -c ^d")
        query.replace("*", "").split(" ").forEach { term ->
            assertTrue(
                "'$term' is not purely alphanumeric",
                term.all { it.isLetterOrDigit() },
            )
        }
    }

    @Test
    fun `blank input matches nothing rather than everything`() {
        assertEquals(NoteSearch.MATCHES_NOTHING, NoteSearch.toMatchQuery(""))
        assertEquals(NoteSearch.MATCHES_NOTHING, NoteSearch.toMatchQuery("   "))
        assertEquals(NoteSearch.MATCHES_NOTHING, NoteSearch.toMatchQuery("--- ***"))
    }

    @Test
    fun `extra spaces do not create empty terms`() {
        assertEquals("sore* throat*", NoteSearch.toMatchQuery("  sore    throat  "))
    }
}
