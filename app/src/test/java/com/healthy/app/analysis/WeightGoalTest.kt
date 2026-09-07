package com.healthy.app.analysis

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Weight goals (spec 8.5 and 8.6). */
class WeightGoalTest {

    private fun points(vararg trend: Double): List<WeightTrend.Point> =
        trend.mapIndexed { i, t ->
            WeightTrend.Point(date = "2026-03-%02d".format(i + 1), weightKg = t, trendKg = t)
        }

    /** Acceptance test 25: refuse a rate above 1 percent of body weight. */
    @Test
    fun `a rate above one percent of body weight is refused`() {
        val check = WeightGoal.checkRate(rateKgPerWeek = 1.2, bodyWeightKg = 80.0)
        assertTrue(check is WeightGoal.RateCheck.TooFast)
        val tooFast = check as WeightGoal.RateCheck.TooFast
        assertEquals(0.8, tooFast.limit, 0.0001)
        assertTrue("the message must state the limit", tooFast.message.contains("0.80 kg"))
    }

    @Test
    fun `a rate at the limit is allowed`() {
        assertEquals(WeightGoal.RateCheck.Allowed, WeightGoal.checkRate(0.8, 80.0))
    }

    @Test
    fun `gaining too fast is refused on the same grounds`() {
        assertTrue(WeightGoal.checkRate(-1.5, 80.0) is WeightGoal.RateCheck.TooFast)
    }

    @Test
    fun `the limit follows body weight`() {
        assertEquals(0.6, WeightGoal.maximumRate(60.0), 0.0001)
        assertEquals(1.1, WeightGoal.maximumRate(110.0), 0.0001)
    }

    /** Acceptance test 27: hold mode compares the trend, not the daily weight. */
    @Test
    fun `a trend inside the band says nothing however noisy the readings`() {
        val noisy = listOf(78.0, 82.0, 79.5, 81.0).mapIndexed { i, daily ->
            // Readings swing two kilos; the trend sits on target.
            WeightTrend.Point("2026-03-0${i + 1}", weightKg = daily, trendKg = 80.0)
        }
        val status = WeightGoal.holdStatus(noisy, targetKg = 80.0)!!
        assertTrue(status.insideBand)
        assertNull("a daily reading outside the band must not trigger a message", status.message)
    }

    @Test
    fun `nothing is said until the trend is outside the band for seven days`() {
        val sixDaysOut = points(80.0, 81.5, 81.6, 81.7, 81.8, 81.9, 82.0)
        // The first point is inside, so only six consecutive days are outside.
        val status = WeightGoal.holdStatus(sixDaysOut, targetKg = 80.0)!!
        assertFalse(status.insideBand)
        assertEquals(6, status.daysOutside)
        assertNull(status.message)
    }

    @Test
    fun `after seven days outside the message states direction and size`() {
        val sevenOut = points(80.0, 81.5, 81.6, 81.7, 81.8, 81.9, 82.0, 82.1)
        val status = WeightGoal.holdStatus(sevenOut, targetKg = 80.0)!!
        assertEquals(7, status.daysOutside)
        val message = assertNotNull(status.message).let { status.message!! }
        assertTrue(message.contains("above"))
        assertTrue(message.contains("2.1 kg"))
        // Spec 8.5: it gives no instruction.
        assertFalse(message.contains("should"))
        assertFalse(message.contains("try"))
    }

    @Test
    fun `drifting below the band reads as below`() {
        val below = points(80.0, 78.5, 78.4, 78.3, 78.2, 78.1, 78.0, 77.9)
        val status = WeightGoal.holdStatus(below, targetKg = 80.0)!!
        assertEquals(7, status.daysOutside)
        assertTrue(status.message!!.contains("below"))
    }

    @Test
    fun `the target line starts at the trend on day one and follows the rate`() {
        val progress = WeightGoal.progress(points(80.0, 79.9, 79.8), rateKgPerWeek = -0.7)!!
        assertEquals(80.0, progress.targetLine[0], 0.0001)
        // -0.7 kg a week is -0.1 a day.
        assertEquals(79.9, progress.targetLine[1], 0.0001)
        assertEquals(79.8, progress.targetLine[2], 0.0001)
    }

    @Test
    fun `the achieved rate is measured from the trend across the window`() {
        // 15 days, trend falling 0.1 a day; the last 14 give 13 days of change.
        val series = points(*DoubleArray(15) { 80.0 - it * 0.1 })
        val progress = WeightGoal.progress(series, rateKgPerWeek = -0.7)!!
        assertEquals(-0.7, progress.actualRateKgPerWeek!!, 0.0001)
    }

    @Test
    fun `no achieved rate from a single point`() {
        assertNull(WeightGoal.progress(points(80.0), -0.5)!!.actualRateKgPerWeek)
    }
}
