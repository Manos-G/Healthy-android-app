package com.healthy.app.analysis

import com.healthy.app.data.entity.Weight
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/** The weight trend (spec 8.2) and the entry wheel's bounds (spec 8.1). */
class WeightTrendTest {

    private fun w(date: String, kg: Double) = Weight(date = date, weightKg = kg, timestamp = 1L)

    @Test
    fun `the first day seeds the trend with its own reading`() {
        val points = WeightTrend.series(listOf(w("2026-03-01", 81.0)))
        assertEquals(81.0, points.single().trendKg, 0.0001)
    }

    @Test
    fun `the trend moves a tenth of the way towards each new reading`() {
        val points = WeightTrend.series(listOf(w("2026-03-01", 80.0), w("2026-03-02", 90.0)))
        // 80 + 0.1 * (90 - 80) = 81
        assertEquals(81.0, points[1].trendKg, 0.0001)
    }

    /** The point of the smoothing: the line must move less than the dots. */
    @Test
    fun `the trend line moves less than the daily readings`() {
        val noisy = listOf(80.0, 82.0, 79.0, 81.5, 78.5, 81.0, 80.0)
            .mapIndexed { i, kg -> w("2026-03-0${i + 1}", kg) }
        val points = WeightTrend.series(noisy)

        val dailySwing = points.zipWithNext { a, b -> abs(b.weightKg - a.weightKg) }.max()
        val trendSwing = points.zipWithNext { a, b -> abs(b.trendKg - a.trendKg) }.max()
        assertTrue("the trend must be quieter than the readings", trendSwing < dailySwing)
    }

    @Test
    fun `a steady weight gives a flat trend`() {
        val points = WeightTrend.series((1..10).map { w("2026-03-%02d".format(it), 75.0) })
        points.forEach { assertEquals(75.0, it.trendKg, 0.0001) }
        assertEquals(0.0, WeightTrend.changeOverDays(points)!!, 0.0001)
    }

    @Test
    fun `no change is reported from a single reading`() {
        assertNull(WeightTrend.changeOverDays(WeightTrend.series(listOf(w("2026-03-01", 80.0)))))
    }

    @Test
    fun `the change is measured on the trend, not the last reading`() {
        // Steady at 80, then one heavy day. The reading jumps 5 kg; the trend
        // must not.
        val weights = (1..10).map { w("2026-03-%02d".format(it), 80.0) } + w("2026-03-11", 85.0)
        val points = WeightTrend.series(weights)
        val change = WeightTrend.changeOverDays(points)!!
        assertEquals("one heavy day moves the trend by half a kilo", 0.5, change, 0.0001)
    }

    @Test
    fun `the wheel spans three kilos either side of the previous reading`() {
        val range = WeightTrend.wheelRange(81.4)
        assertEquals(78.4, range.start, 0.0001)
        assertEquals(84.4, range.endInclusive, 0.0001)
        assertEquals(81.4, WeightTrend.wheelStart(81.4), 0.0001)
    }

    @Test
    fun `with no previous reading the wheel starts at seventy`() {
        val range = WeightTrend.wheelRange(null)
        assertEquals(40.0, range.start, 0.0001)
        assertEquals(150.0, range.endInclusive, 0.0001)
        assertEquals(70.0, WeightTrend.wheelStart(null), 0.0001)
    }
}
