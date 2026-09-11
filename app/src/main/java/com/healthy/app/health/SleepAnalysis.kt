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

    /**
     * One sleep session, reduced to what deciding between them needs.
     *
     * Kept free of Health Connect types so the choosing can be tested without
     * a device, which is the whole reason this object exists.
     */
    data class Session(
        val start: Long,
        val end: Long,
        val stageCount: Int,
        val index: Int = 0,
    ) {
        val millis: Long get() = (end - start).coerceAtLeast(0L)
        fun overlaps(other: Session): Boolean = start < other.end && other.start < end
    }

    /**
     * The separate sleeps in a day, with duplicate records of the same sleep
     * collapsed.
     *
     * Two different things look alike in the record list and had been treated
     * as one. Two apps writing the same night — Mi Fitness and Gadgetbridge
     * both do — produce overlapping sessions describing one sleep, and only
     * the more detailed one should count. A night and an afternoon nap produce
     * sessions that do not overlap at all, and both are real sleep; keeping
     * only the longer threw the other away, so a day with two sleeps recorded
     * one.
     *
     * Overlap is what tells them apart, because two records of one sleep must
     * cover the same hours and two separate sleeps cannot.
     */
    fun distinctSleeps(sessions: List<Session>): List<Session> {
        if (sessions.isEmpty()) return emptyList()
        val ordered = sessions.sortedBy { it.start }
        val clusters = mutableListOf<MutableList<Session>>()

        for (session in ordered) {
            val cluster = clusters.lastOrNull()
            // Compared against the cluster's full span, so three records that
            // chain into one another stay one sleep.
            val spans = cluster?.let {
                Session(it.minOf { s -> s.start }, it.maxOf { s -> s.end }, 0)
            }
            if (spans != null && spans.overlaps(session)) {
                cluster.add(session)
            } else {
                clusters.add(mutableListOf(session))
            }
        }

        // Within one sleep, the denser record wins: more stage blocks means
        // more detail. Duration only breaks a tie, so a long featureless
        // session never beats a shorter one that describes the sleep.
        return clusters.mapNotNull { cluster ->
            cluster.maxWithOrNull(compareBy({ it.stageCount }, { it.millis }))
        }
    }

    /**
     * Time actually asleep across [sleeps], which is their sum and not the
     * span between the first and the last. A nap at 15:00 after a night that
     * ended at 09:00 must not count the six waking hours between them.
     */
    fun totalMinutes(sleeps: List<Session>): Int =
        (sleeps.sumOf { it.millis } / 60_000L).toInt()


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
