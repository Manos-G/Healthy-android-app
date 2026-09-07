package com.healthy.app.analysis

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.sin

/** The heart rate hypnogram (spec 18). */
class HypnogramTest {

    private val start = 1_772_000_000_000L
    private fun at(minutes: Int) = start + minutes * 60_000L

    /** A night of [minutes] with a sample every [everyMinutes]. */
    private fun night(
        minutes: Int,
        everyMinutes: Int = 1,
        bpm: (Int) -> Int,
    ): List<Hypnogram.Sample> =
        (0..minutes step everyMinutes).map { Hypnogram.Sample(at(it), bpm(it)) }

    /** Acceptance test 30: too few samples means a message, not a chart. */
    @Test
    fun `a sparse night is refused rather than interpolated`() {
        // 8 hours needs 48 samples; give it 10.
        val sparse = night(480, everyMinutes = 48) { 55 }
        val result = Hypnogram.analyse(sparse, at(0), at(480))
        assertTrue(result is Hypnogram.Result.TooSparse)
        val sparseResult = result as Hypnogram.Result.TooSparse
        assertEquals(48, sparseResult.required)
        assertTrue(sparseResult.samples < sparseResult.required)
    }

    @Test
    fun `exactly one sample per ten minutes is enough`() {
        val result = Hypnogram.analyse(night(480, everyMinutes = 10) { 55 }, at(0), at(480))
        assertTrue(result is Hypnogram.Result.Found)
    }

    /**
     * A night shaped like four 90-minute cycles should read as four troughs
     * about 90 minutes apart.
     */
    @Test
    fun `four sinusoidal cycles are found at the right spacing`() {
        val samples = night(480) { minute ->
            (58 + 8 * sin(2 * PI * minute / 90.0 + PI / 2)).toInt()
        }
        val result = Hypnogram.analyse(samples, at(0), at(480)) as Hypnogram.Result.Found

        assertTrue("expected about five troughs, got ${result.cycleCount}", result.cycleCount in 4..6)
        assertEquals(90.0, result.cycleLengthMinutes!!.toDouble(), 6.0)
    }

    /** Spec 18.3: a median throws away a bad contact reading; a mean does not. */
    @Test
    fun `the rolling median removes a single spike`() {
        val samples = (0..10).map {
            Hypnogram.Sample(at(it), if (it == 5) 180 else 55)
        }
        val smoothed = Hypnogram.rollingMedian(samples, 5)
        assertTrue("the spike must not survive", smoothed.none { it.bpm > 60 })
    }

    @Test
    fun `the median keeps a real sustained change`() {
        val samples = (0..20).map { Hypnogram.Sample(at(it), if (it < 10) 50 else 70) }
        val smoothed = Hypnogram.rollingMedian(samples, 5)
        assertEquals(50, smoothed.first().bpm)
        assertEquals(70, smoothed.last().bpm)
    }

    /** Spec 18.3: two minima less than 50 minutes apart are one trough. */
    @Test
    fun `close troughs are joined into one`() {
        // Two dips 20 minutes apart inside an otherwise flat night.
        val samples = night(480) { minute ->
            when (minute) {
                in 98..102 -> 48
                in 118..122 -> 47
                else -> 60
            }
        }
        val result = Hypnogram.analyse(samples, at(0), at(480)) as Hypnogram.Result.Found
        assertEquals("two dips 20 minutes apart are one cycle", 1, result.cycleCount)
        // The deeper of the two is kept.
        assertEquals(47, result.minima.single().bpm)
    }

    @Test
    fun `the baseline is the tenth percentile of the smoothed values`() {
        val samples = night(480) { minute -> 50 + minute / 20 }
        val result = Hypnogram.analyse(samples, at(0), at(480)) as Hypnogram.Result.Found
        assertTrue(result.baselineBpm in 50..54)
        assertEquals(50, result.lowestBpm)
    }

    /**
     * A night with no variation has no cycles. Reporting one would be the
     * app inventing structure it did not measure, which is the exact failure
     * spec 18.2 exists to prevent.
     */
    @Test
    fun `a flat night reports no cycles at all`() {
        val result = Hypnogram.analyse(night(480) { 55 }, at(0), at(480)) as Hypnogram.Result.Found
        assertEquals(0, result.cycleCount)
        assertEquals(null, result.cycleLengthMinutes)
    }

    @Test
    fun `a flat-bottomed trough still counts once`() {
        val samples = night(480) { minute -> if (minute in 200..215) 46 else 62 }
        val result = Hypnogram.analyse(samples, at(0), at(480)) as Hypnogram.Result.Found
        assertEquals("a wide trough is one cycle, not sixteen", 1, result.cycleCount)
        assertEquals(46, result.minima.single().bpm)
    }

    @Test
    fun `samples outside the sleep window are ignored`() {
        val inside = night(480) { 55 }
        val outside = listOf(Hypnogram.Sample(at(600), 90), Hypnogram.Sample(at(-60), 90))
        val result = Hypnogram.analyse(inside + outside, at(0), at(480)) as Hypnogram.Result.Found
        assertTrue("a daytime reading must not raise the night's lowest", result.lowestBpm == 55)
    }

    /** Spec 18.2: this must never output a stage label. */
    @Test
    fun `the result names no sleep stages`() {
        val result = Hypnogram.analyse(night(480) { 55 }, at(0), at(480))
        val text = result.toString().lowercase()
        listOf("deep", "light", "rem", "awake").forEach { stage ->
            assertTrue("the hypnogram must not label stages, found '$stage'", stage !in text)
        }
    }
}
