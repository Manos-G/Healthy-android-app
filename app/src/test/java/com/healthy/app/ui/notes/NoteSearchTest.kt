package com.healthy.app.ui.notes

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Building the FTS query from what the user types (spec 15.4). */
class NoteSearchTest {

    @Test
    fun `a term gets a trailing wildcard so search runs as you type`() {
        assertEquals("\"thro\"*", NoteSearch.toMatchQuery("thro"))
    }

    @Test
    fun `several words become several prefixed terms`() {
        assertEquals("\"sore\"* \"thro\"*", NoteSearch.toMatchQuery("sore thro"))
    }

    /**
     * FTS treats punctuation as syntax. A note title typed mid-keystroke must
     * not throw, so anything that is not a letter or digit is stripped.
     */
    @Test
    fun `punctuation is stripped rather than reaching the MATCH clause`() {
        assertEquals("\"sore\"* \"throat\"* \"day\"* \"2\"*", NoteSearch.toMatchQuery("sore throat - day 2"))
        assertEquals("\"dont\"*", NoteSearch.toMatchQuery("don't"))
        // OR survives as a quoted term, which is the point: it is searched
        // for as a word rather than acting as an FTS operator.
        assertEquals("\"a\"* \"OR\"* \"b\"*", NoteSearch.toMatchQuery("a\" OR b"))
    }

    @Test
    fun `a quote alone cannot become an operator`() {
        val query = NoteSearch.toMatchQuery("\"")
        assertEquals(NoteSearch.MATCHES_NOTHING, query)
    }

    @Test
    fun `blank input matches nothing rather than everything`() {
        assertEquals(NoteSearch.MATCHES_NOTHING, NoteSearch.toMatchQuery(""))
        assertEquals(NoteSearch.MATCHES_NOTHING, NoteSearch.toMatchQuery("   "))
        assertEquals(NoteSearch.MATCHES_NOTHING, NoteSearch.toMatchQuery("--- ***"))
    }

    @Test
    fun `extra spaces do not create empty terms`() {
        assertEquals("\"sore\"* \"throat\"*", NoteSearch.toMatchQuery("  sore    throat  "))
    }

    @Test
    fun `every produced term is quoted and wildcarded`() {
        val query = NoteSearch.toMatchQuery("teeth pain returned")
        query.split(" ").forEach { term ->
            assertTrue("'$term' is not quoted", term.startsWith("\""))
            assertTrue("'$term' has no wildcard", term.endsWith("\"*"))
        }
    }
}
