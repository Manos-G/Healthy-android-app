package com.healthy.app.health

import com.healthy.app.data.entity.StageBlock
import kotlin.math.ceil

/**
 * The arithmetic behind a synced night (spec 3.3).
 *
 * Pure functions over plain values, so every rule here is testable on the JVM
 * without a device, a watch or a Health Connect provider.
 */
object SleepAnalysis {

    data class StageTotals(
        val deepMin: Int?,
        val lightMin: Int?,
        val remMin: Int?,
        val awakeMin: Int?,
        val wakeups: Int?,
    ) {
        companion object {
            /** A night with no stage list at all: every field absent, none zero. */
            val ABSENT = StageTotals(null, null, null, null, null)
        }
    }

    /**
     * Sums the stage minutes and counts the wake-ups.
     *
     * Two absences matter here and neither is a zero:
     *
     * An empty stage list means the watch reported no stages, so every field is
     * null and the UI hides them.
     *
     * No awake blocks means the wake-up count is unknown, not zero. This device
     * can write stage data with no awake blocks at all, and a zero would be a
     * measurement the watch never made (spec 3.3).
     */
    fun stageTotals(blocks: List<StageBlock>): StageTotals {
        if (blocks.isEmpty()) return StageTotals.ABSENT

        fun minutesOf(type: String): Int =
            blocks.filter { it.type == type }
                .sumOf { (it.endTime - it.startTime).coerceAtLeast(0) / 60_000L }
                .toInt()

        val awakeCount = blocks.count { it.type == StageBlock.AWAKE }

        return StageTotals(
            deepMin = minutesOf(StageBlock.DEEP),
            lightMin = minutesOf(StageBlock.LIGHT),
            remMin = minutesOf(StageBlock.REM),
            awakeMin = minutesOf(StageBlock.AWAKE),
            wakeups = awakeCount.takeIf { it > 0 },
        )
    }

    /**
     * The sleep cycle length in minutes, from the watch's deep blocks
     * (spec 3.3): the median interval between adjacent deep block starts.
     *
     * Null below three deep blocks, where the caller shows "too few blocks".
     * Two blocks give a single interval, which is a reading rather than a
     * median and would look far more certain than it is.
     */
    fun cycleLengthFromDeepBlocks(blocks: List<StageBlock>): Int? {
        val starts = blocks.filter { it.type == StageBlock.DEEP }
            .map { it.startTime }
            .sorted()
        if (starts.size < 3) return null

        val gaps = starts.zipWithNext { a, b -> (b - a) / 60_000L }
        return median(gaps.map { it.toDouble() })?.toInt()
    }

    /**
     * The resting heart rate: the 5th percentile of the beats-per-minute
     * values inside the sleep window (spec 3.3).
     *
     * Not the single lowest sample, because one bad contact reading would set
     * it. The percentile keeps a stray low value from deciding the number.
     *
     * Nearest-rank: with the values sorted, take the one at ceil(0.05 * n).
     * With few values this lands on the lowest, which is the honest answer when
     * there is not enough data to reject anything.
     */
    fun percentile(values: List<Int>, fraction: Double): Int? {
        if (values.isEmpty()) return null
        val sorted = values.sorted()
        val rank = ceil(fraction * sorted.size).toInt().coerceIn(1, sorted.size)
        return sorted[rank - 1]
    }

    fun restingHeartRate(bpmValues: List<Int>): Int? = percentile(bpmValues, 0.05)

    /** The mean blood oxygen across the window. Absent when nothing was recorded. */
    fun meanSpo2(values: List<Double>): Double? =
        if (values.isEmpty()) null else (values.sum() / values.size)

    private fun median(values: List<Double>): Double? {
        if (values.isEmpty()) return null
        val sorted = values.sorted()
        val mid = sorted.size / 2
        return if (sorted.size % 2 == 1) sorted[mid] else (sorted[mid - 1] + sorted[mid]) / 2.0
    }
}
