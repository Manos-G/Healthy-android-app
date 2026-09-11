package com.healthy.app.health

import org.junit.Assert.assertEquals
import org.junit.Test

/** Telling two records of one sleep apart from two separate sleeps. */
class SleepSessionsTest {

    private fun at(hour: Int, minute: Int = 0) = (hour * 60L + minute) * 60_000L

    /**
     * Reported from the phone: sleeping twice in a day recorded only the
     * later sleep. A night and an afternoon nap do not overlap, so both are
     * real and both have to count.
     */
    @Test
    fun `a night and a nap are two sleeps`() {
        val night = SleepAnalysis.Session(at(2), at(9), stageCount = 40)
        val nap = SleepAnalysis.Session(at(15), at(16), stageCount = 3)
        val sleeps = SleepAnalysis.distinctSleeps(listOf(night, nap))
        assertEquals(2, sleeps.size)
        // Seven hours plus one, not the fourteen hours they span.
        assertEquals(8 * 60, SleepAnalysis.totalMinutes(sleeps))
    }

    /** Two apps describing the same night is still one sleep. */
    @Test
    fun `overlapping records of one night collapse to the denser one`() {
        val sparse = SleepAnalysis.Session(at(2), at(9, 30), stageCount = 0)
        val detailed = SleepAnalysis.Session(at(2, 10), at(9), stageCount = 42)
        val sleeps = SleepAnalysis.distinctSleeps(listOf(sparse, detailed))
        assertEquals(1, sleeps.size)
        assertEquals(42, sleeps.first().stageCount)
        // The longer but featureless record must not win on duration alone.
        assertEquals(detailed, sleeps.first())
    }

    /** Three records chaining into one another are one sleep, not two. */
    @Test
    fun `a chain of overlapping records stays one sleep`() {
        val a = SleepAnalysis.Session(at(2), at(5), stageCount = 5)
        val b = SleepAnalysis.Session(at(4), at(7), stageCount = 9)
        val c = SleepAnalysis.Session(at(6), at(9), stageCount = 4)
        assertEquals(1, SleepAnalysis.distinctSleeps(listOf(a, b, c)).size)
    }

    /** Order of arrival must not change the answer. */
    @Test
    fun `the result does not depend on the order records arrive in`() {
        val night = SleepAnalysis.Session(at(2), at(9), stageCount = 40)
        val nap = SleepAnalysis.Session(at(15), at(16), stageCount = 3)
        assertEquals(
            SleepAnalysis.distinctSleeps(listOf(night, nap)),
            SleepAnalysis.distinctSleeps(listOf(nap, night)),
        )
    }

    /** Sleeps that merely touch end-to-start are separate, not overlapping. */
    @Test
    fun `a sleep ending as another begins is two sleeps`() {
        val first = SleepAnalysis.Session(at(2), at(5), stageCount = 3)
        val second = SleepAnalysis.Session(at(5), at(8), stageCount = 3)
        assertEquals(2, SleepAnalysis.distinctSleeps(listOf(first, second)).size)
    }

    @Test
    fun `no sessions is no sleep`() {
        assertEquals(emptyList<SleepAnalysis.Session>(), SleepAnalysis.distinctSleeps(emptyList()))
        assertEquals(0, SleepAnalysis.totalMinutes(emptyList()))
    }

    /**
     * The night reported from the phone: an afternoon sleep of 5 h 07 m and an
     * early-morning one of 3 h 24 m, both filed under the same logical day
     * because the second began before 04:00.
     *
     * The total is right at 8 h 31 m. What was wrong was describing it with
     * the earliest start and the latest end, which claimed a single sleep from
     * 13:22 to 06:00 — seventeen hours, eight of them awake.
     */
    @Test
    fun `the longest sleep is the one the clock times describe`() {
        val afternoon = SleepAnalysis.Session(at(13, 22), at(18, 29), stageCount = 30)
        val earlyHours = SleepAnalysis.Session(at(26, 36), at(30, 0), stageCount = 20)
        val sleeps = SleepAnalysis.distinctSleeps(listOf(afternoon, earlyHours))

        assertEquals(2, sleeps.size)
        assertEquals(511, SleepAnalysis.totalMinutes(sleeps))

        val main = sleeps.maxByOrNull { it.millis }!!
        assertEquals(afternoon, main)
        assertEquals(307, (main.millis / 60_000L).toInt())
        // The stretch that must never be reported as one sleep.
        assertEquals(998, ((sleeps.maxOf { it.end } - sleeps.minOf { it.start }) / 60_000L).toInt())
    }
}
