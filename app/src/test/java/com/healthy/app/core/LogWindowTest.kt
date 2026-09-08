package com.healthy.app.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId

class LogWindowTest {

    private val zone: ZoneId = ZoneId.of("Europe/Athens")
    private fun at(text: String) = LogWindow.at(text, zone)

    /**
     * The reason this exists. At 04:20 the logical day has just rolled, so a
     * drink at 03:15 is no longer "today" — and vanished off the screen while
     * still being the most recent thing the user drank.
     */
    @Test
    fun `a rolling window still holds a drink from before the boundary`() {
        val now = at("2026-09-08T04:20:00")
        val start = LogWindow.Rolling.startMillis(now, zone)
        assertTrue(at("2026-09-08T03:15:00") in start..now)
        // And the logical day, correctly, does not.
        val today = LogWindow.Day(HealthyDay.dayOf(now, zone))
        assertTrue(at("2026-09-08T03:15:00") < today.startMillis(now, zone))
    }

    @Test
    fun `a day window is the 04 00 boundary, not midnight`() {
        val now = at("2026-09-08T13:00:00")
        val day = LogWindow.Day("2026-09-07")
        assertEquals(at("2026-09-07T04:00:00"), day.startMillis(now, zone))
        assertEquals(at("2026-09-08T04:00:00"), day.endMillis(now, zone))
    }

    /** A rolling window has no day of its own, so daily targets borrow today's. */
    @Test
    fun `totals for a rolling window are measured against the current day`() {
        val now = at("2026-09-08T04:20:00")
        assertEquals("2026-09-08", LogWindow.Rolling.dayForTotals(now, zone))
        assertEquals("2026-09-05", LogWindow.Day("2026-09-05").dayForTotals(now, zone))
    }

    @Test
    fun `stepping back goes rolling, today, yesterday, and on`() {
        val now = at("2026-09-08T13:00:00")
        val today = LogWindow.earlier(LogWindow.Rolling, now, zone)
        assertEquals(LogWindow.Day("2026-09-08"), today)
        assertEquals(LogWindow.Day("2026-09-07"), LogWindow.earlier(today, now, zone))
    }

    @Test
    fun `stepping forward stops at the rolling view`() {
        val now = at("2026-09-08T13:00:00")
        assertEquals(LogWindow.Day("2026-09-08"), LogWindow.later(LogWindow.Day("2026-09-07"), now, zone))
        assertEquals(LogWindow.Rolling, LogWindow.later(LogWindow.Day("2026-09-08"), now, zone))
        assertNull(LogWindow.later(LogWindow.Rolling, now, zone))
    }

    @Test
    fun `labels name the day rather than printing a bare date`() {
        val now = at("2026-09-08T13:00:00")
        assertEquals("Last 24 hours", LogWindow.label(LogWindow.Rolling, now, zone))
        assertEquals("Today, since 04:00", LogWindow.label(LogWindow.Day("2026-09-08"), now, zone))
        assertEquals("Yesterday", LogWindow.label(LogWindow.Day("2026-09-07"), now, zone))
        assertEquals("Sat 5 Sep", LogWindow.label(LogWindow.Day("2026-09-05"), now, zone))
    }

    /** The divider that tells the user where one logical day ended. */
    @Test
    fun `the boundary is inside a rolling window just after 04 00`() {
        val now = at("2026-09-08T04:20:00")
        assertEquals(at("2026-09-08T04:00:00"), LogWindow.boundaryWithin(LogWindow.Rolling, now, zone))
        assertNull(LogWindow.boundaryWithin(LogWindow.Day("2026-09-08"), now, zone))
    }

    /**
     * The Food tab crashed on open.
     *
     * Compose draws the initial state before the first flow emission, so the
     * label was asked for a day that had not been computed yet — an empty
     * string, straight into LocalDate.parse. A label helper that throws on an
     * input it can be handed is a landmine, so it falls back to the raw text
     * rather than taking the screen down.
     */
    @Test
    fun `an unparseable day labels itself instead of throwing`() {
        val now = at("2026-09-08T13:00:00")
        assertEquals("", LogWindow.label(LogWindow.Day(""), now, zone))
        assertEquals("not-a-date", LogWindow.label(LogWindow.Day("not-a-date"), now, zone))
    }

    /** Just before 04:00 the whole rolling window sits inside one logical day. */
    @Test
    fun `there is no divider when the window holds no boundary`() {
        val now = at("2026-09-08T03:30:00")
        assertEquals(at("2026-09-07T04:00:00"), LogWindow.boundaryWithin(LogWindow.Rolling, now, zone))
    }
}
