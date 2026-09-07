package com.healthy.app.analysis

import kotlin.math.abs

/**
 * Weight goals (spec 8.5 and 8.6).
 *
 * Two rules shape all of this. Every comparison is made against the *trend*,
 * never the daily reading, because a daily weight leaves any sensible band
 * constantly and means nothing. And a goal is a rate, never a date: a missed
 * date makes an app demand a larger deficit every week, which is how these
 * things turn punitive.
 */
object WeightGoal {

    /** Plus or minus this around a hold target (spec 8.5). */
    const val HOLD_BAND_KG = 1.0

    /** Days the trend must stay outside the band before anything is said. */
    const val HOLD_PATIENCE_DAYS = 7

    /** The ceiling on a change rate, as a fraction of body weight (spec 8.5). */
    const val MAX_RATE_FRACTION = 0.01

    /** The window for the achieved rate (spec 8.6). */
    const val ACTUAL_RATE_DAYS = 14

    sealed interface Mode {
        /** Records and shows the trend, sets no energy target (spec 8.5). */
        data object NoGoal : Mode

        data class Hold(val targetKg: Double) : Mode

        data class Change(val rateKgPerWeek: Double, val startedOn: String) : Mode
    }

    /** The largest rate allowed at this body weight (spec 8.5). */
    fun maximumRate(bodyWeightKg: Double): Double = bodyWeightKg * MAX_RATE_FRACTION

    sealed interface RateCheck {
        data object Allowed : RateCheck
        data class TooFast(val requested: Double, val limit: Double) : RateCheck {
            val message: String
                get() = "A rate of ${"%.2f".format(abs(requested))} kg a week is faster than " +
                    "${"%.2f".format(limit)} kg, which is 1 percent of your body weight. " +
                    "That is the limit this app will set."
        }
    }

    /**
     * Refuses a rate above 1 percent of body weight each week and states the
     * limit (spec 8.5). The sign is ignored: gaining too fast is refused on
     * the same grounds as losing too fast.
     */
    fun checkRate(rateKgPerWeek: Double, bodyWeightKg: Double): RateCheck {
        val limit = maximumRate(bodyWeightKg)
        return if (abs(rateKgPerWeek) > limit + 1e-9) {
            RateCheck.TooFast(rateKgPerWeek, limit)
        } else {
            RateCheck.Allowed
        }
    }

    data class HoldStatus(
        val insideBand: Boolean,
        val daysOutside: Int,
        val driftKg: Double,
        /** Null until the trend has been outside the band for a full week. */
        val message: String?,
    )

    /**
     * Compares the trend against the hold band (spec 8.5).
     *
     * Nothing is said until the trend has been outside for seven consecutive
     * days, and what is said states the direction and the size and stops there.
     * No instruction: the user knows what to do about their own weight.
     */
    fun holdStatus(points: List<WeightTrend.Point>, targetKg: Double): HoldStatus? {
        val latest = points.lastOrNull() ?: return null
        val low = targetKg - HOLD_BAND_KG
        val high = targetKg + HOLD_BAND_KG
        val drift = latest.trendKg - targetKg
        val inside = latest.trendKg in low..high

        // Count back while the trend stays on the same side of the band.
        var daysOutside = 0
        if (!inside) {
            val above = latest.trendKg > high
            for (point in points.asReversed()) {
                val stillOut = if (above) point.trendKg > high else point.trendKg < low
                if (!stillOut) break
                daysOutside++
            }
        }

        val message = if (daysOutside >= HOLD_PATIENCE_DAYS) {
            val direction = if (drift > 0) "above" else "below"
            "Your trend has been ${"%.1f".format(abs(drift))} kg $direction the band " +
                "for $daysOutside days."
        } else {
            null
        }

        return HoldStatus(inside, daysOutside, drift, message)
    }

    data class Progress(
        val targetRateKgPerWeek: Double,
        /** Measured from the trend across the last 14 days (spec 8.6). */
        val actualRateKgPerWeek: Double?,
        val targetLine: List<Double>,
    )

    /**
     * The two numbers that say whether the plan works (spec 8.6): the rate
     * asked for and the rate achieved. The spec is explicit that no other
     * number is necessary.
     *
     * The target line starts at the trend on the first day of the goal and
     * continues at the chosen rate.
     */
    fun progress(points: List<WeightTrend.Point>, rateKgPerWeek: Double): Progress? {
        if (points.isEmpty()) return null
        val start = points.first().trendKg
        val targetLine = points.indices.map { day -> start + rateKgPerWeek * day / 7.0 }

        val window = points.takeLast(ACTUAL_RATE_DAYS)
        val actual = if (window.size < 2) {
            null
        } else {
            val days = window.size - 1
            (window.last().trendKg - window.first().trendKg) / days * 7.0
        }

        return Progress(rateKgPerWeek, actual, targetLine)
    }
}
