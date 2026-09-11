package com.healthy.app.core

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * The logical day boundary (spec 4.4). Every date calculation in the app goes
 * through here, so these are the tests that stop a whole class of bugs.
 *
 * Written against [HealthyDay.BOUNDARY_HOUR] rather than a typed-in hour, so
 * moving the boundary moves the tests with it and they keep testing the rule
 * instead of a number that used to be true.
 */
class HealthyDayTest {

    private val athens: ZoneId = ZoneId.of("Europe/Athens")

    private fun at(y: Int, m: Int, d: Int, h: Int, min: Int): Long =
        ZonedDateTime.of(y, m, d, h, min, 0, 0, athens).toInstant().toEpochMilli()

    private val boundary = HealthyDay.BOUNDARY_HOUR

    @Test
    fun `a time before the boundary belongs to the previous logical day`() {
        assertEquals("2026-03-04", HealthyDay.dayOf(at(2026, 3, 5, 0, 1), athens))
        assertEquals("2026-03-04", HealthyDay.dayOf(at(2026, 3, 5, boundary - 1, 59), athens))
    }

    @Test
    fun `the boundary hour starts the new logical day`() {
        assertEquals("2026-03-05", HealthyDay.dayOf(at(2026, 3, 5, boundary, 0), athens))
    }

    @Test
    fun `a time after the boundary is the day it happens in`() {
        assertEquals("2026-03-05", HealthyDay.dayOf(at(2026, 3, 5, 23, 30), athens))
    }

    @Test
    fun `a logical day runs from one boundary to the next`() {
        val start = HealthyDay.startOf("2026-03-05", athens)
        val end = HealthyDay.endOf("2026-03-05", athens)
        assertEquals(at(2026, 3, 5, boundary, 0), start)
        assertEquals(at(2026, 3, 6, boundary, 0), end)
        assertEquals("2026-03-05", HealthyDay.dayOf(start, athens))
        // The end instant already belongs to the next day.
        assertEquals("2026-03-06", HealthyDay.dayOf(end, athens))
    }

    @Test
    fun `the boundary round-trips across a month end`() {
        assertEquals("2026-02-28", HealthyDay.dayOf(at(2026, 3, 1, 0, 30), athens))
        assertEquals("2026-03-01", HealthyDay.plusDays("2026-02-28", 1))
    }

    /**
     * The morning screen opens on the night the user just finished, which is
     * yesterday's logical day: sleep that starts before the boundary files
     * under the previous day, so today's night has not happened yet.
     */
    @Test
    fun `lastNight is one logical day before today`() {
        val today = HealthyDay.today(athens)
        assertEquals(HealthyDay.plusDays(today, -1), HealthyDay.lastNight(athens))
    }
}
