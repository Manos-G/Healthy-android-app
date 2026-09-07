package com.healthy.app.analysis

import com.healthy.app.data.entity.Weight

/**
 * The weight trend (spec 8.2).
 *
 * A daily weight moves a kilogram or two on water, food and glycogen alone.
 * The daily number is noise; the smoothed line is the signal, which is why the
 * chart draws the line strong and the readings quiet.
 *
 * ```
 * trend(today) = trend(yesterday) + 0.1 x (weight(today) - trend(yesterday))
 * ```
 *
 * This is the Hacker's Diet moving average, and 0.1 is the factor the sources
 * screen quotes.
 */
object WeightTrend {

    const val SMOOTHING = 0.1

    data class Point(
        val date: String,
        val weightKg: Double,
        val trendKg: Double,
    )

    /**
     * Smooths a series that must already be in date order.
     *
     * The first day seeds the trend with its own reading (spec 8.2), so the
     * line starts on the body rather than climbing towards it from nothing.
     */
    fun series(weights: List<Weight>): List<Point> {
        var trend: Double? = null
        return weights.map { w ->
            val current = trend?.let { it + SMOOTHING * (w.weightKg - it) } ?: w.weightKg
            trend = current
            Point(date = w.date, weightKg = w.weightKg, trendKg = current)
        }
    }

    /**
     * The change in the *trend* across the window, never the change in the
     * daily reading (spec 8.2). Null until there are two points to compare.
     */
    fun changeOverDays(points: List<Point>, days: Int = 30): Double? {
        if (points.size < 2) return null
        val window = points.takeLast(days)
        if (window.size < 2) return null
        return window.last().trendKg - window.first().trendKg
    }

    /** The wheel's range: the previous weight plus or minus 3 kg (spec 8.1). */
    fun wheelRange(previousKg: Double?): ClosedFloatingPointRange<Double> =
        if (previousKg == null) {
            DEFAULT_MIN..DEFAULT_MAX
        } else {
            (previousKg - DAILY_SWING)..(previousKg + DAILY_SWING)
        }

    fun wheelStart(previousKg: Double?): Double = previousKg ?: DEFAULT_START

    const val STEP_KG = 0.1
    const val DAILY_SWING = 3.0
    const val DEFAULT_START = 70.0
    const val DEFAULT_MIN = 40.0
    const val DEFAULT_MAX = 150.0
}
