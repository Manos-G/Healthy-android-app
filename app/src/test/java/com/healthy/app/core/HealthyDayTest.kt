package com.healthy.app.core

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * The 04:00 day boundary (spec 4.4). Every date calculation in the app goes
 * through here, so these are the tests that stop a whole class of bugs.
 */
class HealthyDayTest {

    private val athens: ZoneId = ZoneId.of("Europe/Athens")

    private fun at(y: Int, m: Int, d: Int, h: Int, min: Int): Long =
        ZonedDateTime.of(y, m, d, h, min, 0, 0, athens).toInstant().toEpochMilli()

    @Test
    fun `a time before 04_00 belongs to the previous logical day`() {
        assertEquals("2026-03-04", HealthyDay.dayOf(at(2026, 3, 5, 2, 38), athens))
        assertEquals("2026-03-04", HealthyDay.dayOf(at(2026, 3, 5, 3, 59), athens))
    }

    @Test
    fun `04_00 starts the new logical day`() {
        assertEquals("2026-03-05", HealthyDay.dayOf(at(2026, 3, 5, 4, 0), athens))
    }

    @Test
    fun `an afternoon wake-up is the day it happens in`() {
        assertEquals("2026-03-05", HealthyDay.dayOf(at(2026, 3, 5, 13, 49), athens))
    }

    @Test
    fun `a logical day runs from 04_00 to 04_00`() {
        val start = HealthyDay.startOf("2026-03-05", athens)
        val end = HealthyDay.endOf("2026-03-05", athens)
        assertEquals(at(2026, 3, 5, 4, 0), start)
        assertEquals(at(2026, 3, 6, 4, 0), end)
        assertEquals("2026-03-05", HealthyDay.dayOf(start, athens))
        // The end instant already belongs to the next day.
        assertEquals("2026-03-06", HealthyDay.dayOf(end, athens))
    }

    @Test
    fun `the boundary round-trips across a month end`() {
        assertEquals("2026-02-28", HealthyDay.dayOf(at(2026, 3, 1, 1, 0), athens))
        assertEquals("2026-03-01", HealthyDay.plusDays("2026-02-28", 1))
    }

    /**
     * The morning screen opens on the night the user just finished, which is
     * yesterday's logical day: sleep that starts before 04:00 files under the
     * previous day, so today's night has not happened yet.
     */
    @Test
    fun `lastNight is one logical day before today`() {
        val today = HealthyDay.today(athens)
        assertEquals(HealthyDay.plusDays(today, -1), HealthyDay.lastNight(athens))
    }
}
