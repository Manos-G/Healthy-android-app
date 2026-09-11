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
     * Nights carry the calendar date their sleep began, so the night just
     * finished by someone who falls asleep after midnight began today.
     */
    @Test
    fun `lastNight is the calendar date, since a night is named by its start`() {
        assertEquals(HealthyDay.sleepToday(athens), HealthyDay.lastNight(athens))
    }

    /**
     * The two boundaries, which exist because eating and sleeping ask
     * different questions of the same instant.
     *
     * A coffee at 02:38 belongs to the day still being lived. The sleep that
     * begins in the same minute is the night of the new date.
     */
    @Test
    fun `one instant is yesterday's intake and tonight's sleep`() {
        val lateNight = at(2026, 3, 5, 2, 38)
        assertEquals("2026-03-04", HealthyDay.dayOf(lateNight, athens))
        assertEquals("2026-03-05", HealthyDay.sleepDayOf(lateNight, athens))
    }

    @Test
    fun `a sleep day runs midnight to midnight`() {
        assertEquals(at(2026, 3, 5, 0, 0), HealthyDay.sleepStartOf("2026-03-05", athens))
        assertEquals(at(2026, 3, 6, 0, 0), HealthyDay.sleepEndOf("2026-03-05", athens))
    }

    /**
     * The join the comparison table depends on: a night has to find the day
     * whose coffee came before it, and the two no longer share a name.
     */
    @Test
    fun `a night after midnight belongs to the previous intake day`() {
        val sleptAt = at(2026, 3, 5, 2, 38)
        assertEquals(
            "2026-03-04",
            HealthyDay.intakeDayForNight("2026-03-05", sleptAt, athens),
        )
    }

    /** Falling asleep before midnight keeps the night and the intake aligned. */
    @Test
    fun `a night before midnight belongs to the same intake day`() {
        val sleptAt = at(2026, 3, 5, 23, 0)
        assertEquals(
            "2026-03-05",
            HealthyDay.intakeDayForNight("2026-03-05", sleptAt, athens),
        )
    }

    /** With no recorded start, the night is assumed to have begun at midnight. */
    @Test
    fun `an unknown sleep start falls back to the day being lived at midnight`() {
        assertEquals("2026-03-04", HealthyDay.intakeDayForNight("2026-03-05", 0L, athens))
    }
}
