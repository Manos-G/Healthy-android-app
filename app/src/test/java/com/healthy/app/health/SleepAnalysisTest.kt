package com.healthy.app.health

import com.healthy.app.data.entity.StageBlock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The arithmetic of a synced night (spec 3.3). */
class SleepAnalysisTest {

    private var next = 1_772_000_000_000L

    private fun block(type: String, minutes: Long, gapBefore: Long = 0): StageBlock {
        next += gapBefore * 60_000
        val start = next
        next += minutes * 60_000
        return StageBlock(nightDate = "2026-03-04", type = type, startTime = start, endTime = next)
    }

    @Test
    fun `an empty stage list reports absence, not zeroes`() {
        val totals = SleepAnalysis.stageTotals(emptyList())
        assertNull(totals.deepMin)
        assertNull(totals.lightMin)
        assertNull(totals.remMin)
        assertNull(totals.awakeMin)
        assertNull(totals.wakeups)
    }

    @Test
    fun `minutes are summed for each stage type`() {
        val totals = SleepAnalysis.stageTotals(
            listOf(
                block(StageBlock.DEEP, 40),
                block(StageBlock.LIGHT, 90),
                block(StageBlock.REM, 25),
                block(StageBlock.DEEP, 20),
            )
        )
        assertEquals(60, totals.deepMin)
        assertEquals(90, totals.lightMin)
        assertEquals(25, totals.remMin)
    }

    /**
     * The case START-HERE asks about. Stage data with no awake blocks must
     * read "not reported", never zero: zero is a measurement the watch did
     * not make.
     */
    @Test
    fun `stages without awake blocks leave wakeups unknown`() {
        val totals = SleepAnalysis.stageTotals(
            listOf(block(StageBlock.DEEP, 40), block(StageBlock.LIGHT, 90))
        )
        assertEquals(0, totals.awakeMin)
        assertNull("no awake blocks means not reported, not zero", totals.wakeups)
    }

    @Test
    fun `awake blocks are counted when the watch writes them`() {
        val totals = SleepAnalysis.stageTotals(
            listOf(
                block(StageBlock.LIGHT, 60),
                block(StageBlock.AWAKE, 5),
                block(StageBlock.LIGHT, 60),
                block(StageBlock.AWAKE, 3),
            )
        )
        assertEquals(2, totals.wakeups)
        assertEquals(8, totals.awakeMin)
    }

    @Test
    fun `cycle length needs three deep blocks`() {
        val two = listOf(
            block(StageBlock.DEEP, 20),
            block(StageBlock.DEEP, 20, gapBefore = 70),
        )
        assertNull("two blocks give one interval, not a median", SleepAnalysis.cycleLengthFromDeepBlocks(two))
    }

    @Test
    fun `cycle length is the median gap between deep block starts`() {
        next = 1_772_000_000_000L
        val blocks = listOf(
            block(StageBlock.DEEP, 20),
            block(StageBlock.DEEP, 20, gapBefore = 70),
            block(StageBlock.DEEP, 20, gapBefore = 80),
        )
        assertEquals(95, SleepAnalysis.cycleLengthFromDeepBlocks(blocks))
    }

    /** Spec 3.3: not the lowest sample, because one bad reading would set it. */
    @Test
    fun `resting heart rate rejects a single low outlier`() {
        val values = listOf(38) + List(99) { 55 + it % 5 }
        assertEquals(55, SleepAnalysis.restingHeartRate(values))
    }

    @Test
    fun `the fifth percentile uses nearest rank`() {
        val values = (1..100).toList()
        assertEquals(5, SleepAnalysis.percentile(values, 0.05))
        assertEquals(50, SleepAnalysis.percentile(values, 0.50))
    }

    @Test
    fun `with few samples the percentile falls back to the lowest`() {
        assertEquals(52, SleepAnalysis.restingHeartRate(listOf(60, 52, 58)))
    }

    @Test
    fun `no heart rate samples means no resting rate`() {
        assertNull(SleepAnalysis.restingHeartRate(emptyList()))
    }

    @Test
    fun `blood oxygen is the mean and absent when nothing was recorded`() {
        assertEquals(95.5, SleepAnalysis.meanSpo2(listOf(95.0, 96.0))!!, 0.001)
        assertNull(SleepAnalysis.meanSpo2(emptyList()))
    }
}
