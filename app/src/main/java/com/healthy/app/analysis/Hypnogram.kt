package com.healthy.app.analysis

import kotlin.math.ceil

/**
 * The heart rate hypnogram (spec 18).
 *
 * The watch reports sleep stages and they are not accurate: its deep blocks
 * repeat at the same size all night, while real deep sleep shortens as the
 * night goes on. The heart rate is a measurement, and it falls in deep sleep
 * and rises in REM, so the shape of the night is visible in it.
 *
 * This deliberately does **not** output stage labels (spec 18.2). A classifier
 * that printed "deep" and "light" would be a guess, and the app would repeat
 * the error it exists to correct. A cycle boundary is a measurement; a stage
 * name is not.
 */
object Hypnogram {

    /** A night needs at least one sample per this many minutes (spec 18.3). */
    const val REQUIRED_SAMPLE_INTERVAL_MINUTES = 10

    /** The rolling median window, in samples. */
    const val SMOOTHING_WINDOW = 5

    /** A minimum must be lowest within this many minutes on both sides. */
    const val MINIMUM_ISOLATION_MINUTES = 20

    /** Minima closer than this are the same trough seen twice. */
    const val MINIMUM_SEPARATION_MINUTES = 50

    /** A minimum must sit no higher than the baseline plus this. */
    const val BASELINE_TOLERANCE_BPM = 3

    data class Sample(val timeMillis: Long, val bpm: Int)

    data class Minimum(val timeMillis: Long, val bpm: Int)

    sealed interface Result {
        /**
         * Fewer samples than the night needs. The screen says so and draws
         * nothing (spec 18.3, acceptance test 30) — an interpolated chart from
         * sparse data would look like a measurement and would not be one.
         */
        data class TooSparse(val samples: Int, val required: Int) : Result

        data class Found(
            val smoothed: List<Sample>,
            val minima: List<Minimum>,
            val baselineBpm: Int,
            val cycleCount: Int,
            /** Median minutes between adjacent minima. Null below two. */
            val cycleLengthMinutes: Int?,
            val lowestBpm: Int,
        ) : Result
    }

    fun analyse(samples: List<Sample>, sleepStart: Long, sleepEnd: Long): Result {
        val nightMinutes = ((sleepEnd - sleepStart) / 60_000L).coerceAtLeast(1)
        val required = ceil(nightMinutes.toDouble() / REQUIRED_SAMPLE_INTERVAL_MINUTES).toInt()

        val inWindow = samples.filter { it.timeMillis in sleepStart..sleepEnd }
            .sortedBy { it.timeMillis }
        if (inWindow.size < required) return Result.TooSparse(inWindow.size, required)

        val smoothed = rollingMedian(inWindow, SMOOTHING_WINDOW)
        val baseline = percentile(smoothed.map { it.bpm }, 0.10) ?: return Result.TooSparse(inWindow.size, required)
        val minima = joinClose(findMinima(smoothed, baseline))

        val gaps = minima.zipWithNext { a, b -> (b.timeMillis - a.timeMillis) / 60_000.0 }

        return Result.Found(
            smoothed = smoothed,
            minima = minima,
            baselineBpm = baseline,
            cycleCount = minima.size,
            cycleLengthMinutes = median(gaps)?.toInt(),
            lowestBpm = smoothed.minOf { it.bpm },
        )
    }

    /**
     * A rolling median, not a mean (spec 18.3).
     *
     * A median throws away a bad contact reading; a mean lets it drag the
     * curve. On a wrist sensor that difference decides whether the minima are
     * real.
     */
    fun rollingMedian(samples: List<Sample>, window: Int): List<Sample> {
        if (samples.isEmpty()) return emptyList()
        val half = window / 2
        return samples.indices.map { i ->
            val from = (i - half).coerceAtLeast(0)
            val to = (i + half).coerceAtMost(samples.lastIndex)
            val slice = samples.subList(from, to + 1).map { it.bpm }.sorted()
            Sample(samples[i].timeMillis, slice[slice.size / 2])
        }
    }

    /**
     * A point is a minimum when nothing within 20 minutes on either side is
     * lower, and it sits within 3 bpm of the baseline (spec 18.3).
     */
    private fun findMinima(smoothed: List<Sample>, baseline: Int): List<Minimum> {
        val isolation = MINIMUM_ISOLATION_MINUTES * 60_000L
        val ceiling = baseline + BASELINE_TOLERANCE_BPM

        return smoothed.filter { point ->
            if (point.bpm > ceiling) return@filter false

            val left = smoothed.filter {
                it.timeMillis < point.timeMillis &&
                    point.timeMillis - it.timeMillis <= isolation
            }
            val right = smoothed.filter {
                it.timeMillis > point.timeMillis &&
                    it.timeMillis - point.timeMillis <= isolation
            }

            // Nothing within the window may be lower (spec 18.3).
            if ((left + right).any { it.bpm < point.bpm }) return@filter false

            // And it must actually be a trough. On a flat night the baseline
            // sits on the plateau, so without this every plateau sample would
            // qualify and the app would report cycles it never measured. A
            // plateau point is not "lower than every point within 20 minutes";
            // it is equal to them.
            left.any { it.bpm > point.bpm } && right.any { it.bpm > point.bpm }
        }.map { Minimum(it.timeMillis, it.bpm) }
    }

    /** Two minima less than 50 minutes apart are one trough (spec 18.3). */
    private fun joinClose(minima: List<Minimum>): List<Minimum> {
        if (minima.isEmpty()) return emptyList()
        val separation = MINIMUM_SEPARATION_MINUTES * 60_000L
        val kept = mutableListOf(minima.first())
        minima.drop(1).forEach { candidate ->
            val last = kept.last()
            if (candidate.timeMillis - last.timeMillis < separation) {
                // Keep the deeper of the two: it is the better estimate of
                // where the trough actually sat.
                if (candidate.bpm < last.bpm) kept[kept.lastIndex] = candidate
            } else {
                kept += candidate
            }
        }
        return kept
    }

    fun percentile(values: List<Int>, fraction: Double): Int? {
        if (values.isEmpty()) return null
        val sorted = values.sorted()
        val rank = ceil(fraction * sorted.size).toInt().coerceIn(1, sorted.size)
        return sorted[rank - 1]
    }

    private fun median(values: List<Double>): Double? {
        if (values.isEmpty()) return null
        val sorted = values.sorted()
        val mid = sorted.size / 2
        return if (sorted.size % 2 == 1) sorted[mid] else (sorted[mid - 1] + sorted[mid]) / 2.0
    }
}
