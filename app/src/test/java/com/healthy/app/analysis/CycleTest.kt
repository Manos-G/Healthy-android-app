package com.healthy.app.analysis

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Menstrual cycle arithmetic (spec 10.3, 10.4). */
class CycleTest {

    private val starts = listOf("2026-01-05", "2026-02-02", "2026-03-02")

    @Test
    fun `the first day of a period is day one, not day zero`() {
        assertEquals(1, Cycle.dayOfCycle(starts, "2026-03-02"))
    }

    @Test
    fun `cycle day counts from the most recent start`() {
        assertEquals(6, Cycle.dayOfCycle(starts, "2026-03-07"))
        assertEquals(15, Cycle.dayOfCycle(starts, "2026-03-16"))
    }

    @Test
    fun `a date between two periods uses the earlier one`() {
        // 2 February to 1 March is 27 days in a 28-day February, so day 28.
        assertEquals(28, Cycle.dayOfCycle(starts, "2026-03-01"))
    }

    @Test
    fun `a date before any recorded period has no cycle day`() {
        assertNull(Cycle.dayOfCycle(starts, "2026-01-01"))
    }

    @Test
    fun `no recorded periods means no cycle day`() {
        assertNull(Cycle.dayOfCycle(emptyList(), "2026-03-07"))
    }

    @Test
    fun `a malformed date does not crash`() {
        assertNull(Cycle.dayOfCycle(starts, "not a date"))
        assertNull(Cycle.dayOfCycle(listOf("rubbish"), "2026-03-07"))
    }

    @Test
    fun `cycle lengths are the gaps between starts`() {
        assertEquals(listOf(28, 28), Cycle.cycleLengths(starts))
    }

    @Test
    fun `the median needs two periods`() {
        assertNull(Cycle.medianCycleLength(listOf("2026-01-05")))
        assertEquals(28, Cycle.medianCycleLength(starts))
    }

    @Test
    fun `an irregular history takes the median rather than the mean`() {
        val irregular = listOf("2026-01-01", "2026-01-29", "2026-03-05", "2026-04-02")
        // Gaps of 28, 35, 28. Median 28; the mean would be 30.3.
        assertEquals(28, Cycle.medianCycleLength(irregular))
    }

    @Test
    fun `nights group by the cycle day they fell on`() {
        val nights = listOf("2026-03-02", "2026-03-03", "2026-03-30")
        val grouped = Cycle.byCycleDay(nights, starts) { it }
        assertEquals(listOf("2026-03-02"), grouped[1])
        assertEquals(listOf("2026-03-03"), grouped[2])
        assertEquals(listOf("2026-03-30"), grouped[29])
    }

    @Test
    fun `a night before any period is left out of the grouping`() {
        val grouped = Cycle.byCycleDay(listOf("2025-12-01"), starts) { it }
        assertEquals(0, grouped.size)
    }
}
