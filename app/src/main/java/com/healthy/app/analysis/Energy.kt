package com.healthy.app.analysis

import kotlin.math.roundToInt

/**
 * Energy need and the daily target (spec 16).
 *
 * The governing idea is spec 16.1: measure the energy need, do not calculate
 * it. A formula from height, weight, age and sex is often 300 kcal wrong, and
 * this app already holds the two things that give the real answer — a weight
 * trend and a food log.
 */
object Energy {

    /** Kilocalories in one kilogram of mixed body tissue (spec 16.1, 17). */
    const val KCAL_PER_KG = 7700.0

    /** The measured window (spec 16.1). Below this the estimate is a formula. */
    const val WINDOW_DAYS = 14

    enum class Source {
        /** Measured from intake against the weight trend. The real answer. */
        Measured,

        /** Mifflin-St Jeor. A start value only, replaced when the window fills. */
        Estimated,
    }

    data class Maintenance(
        val kcal: Int,
        val source: Source,
        /** Days of data behind the number (spec 16.1). */
        val days: Int,
    )

    enum class Sex { Female, Male }

    /**
     * Maintenance energy measured from the data (spec 16.1).
     *
     * ```
     * TDEE = mean daily intake + (trend change in kg x 7700) / days
     * ```
     *
     * A trend that fell while eating a given amount means the true need was
     * higher than that amount by the energy the lost tissue supplied.
     *
     * Null when the window is not full: the caller then falls back to
     * [mifflinStJeor] and says so, because spec 16.2 forbids mixing the two.
     */
    fun measuredMaintenance(
        dailyIntakes: List<Int>,
        trendChangeKg: Double,
        days: Int = WINDOW_DAYS,
    ): Maintenance? {
        if (dailyIntakes.size < days || days <= 0) return null
        val window = dailyIntakes.takeLast(days)
        val meanIntake = window.average()
        val fromTissue = (trendChangeKg * KCAL_PER_KG) / days
        return Maintenance(
            kcal = (meanIntake - fromTissue).roundToInt(),
            source = Source.Measured,
            days = days,
        )
    }

    /**
     * Mifflin-St Jeor (spec 16.2), used only as a start value.
     *
     * The activity multiplier is deliberately absent: it is a guess on top of
     * an estimate, and the measured window replaces the whole thing within two
     * weeks.
     */
    fun mifflinStJeor(
        weightKg: Double,
        heightCm: Double,
        ageYears: Int,
        sex: Sex,
        activityFactor: Double = 1.4,
    ): Maintenance {
        val base = 10 * weightKg + 6.25 * heightCm - 5 * ageYears +
            if (sex == Sex.Male) 5 else -161
        return Maintenance(
            kcal = (base * activityFactor).roundToInt(),
            source = Source.Estimated,
            days = 0,
        )
    }

    data class Target(
        val kcal: Int,
        /** True when the floor bit and the requested rate was not achievable. */
        val clampedToBasal: Boolean,
        val basalKcal: Int,
    )

    /**
     * The daily energy target (spec 16.3).
     *
     * ```
     * target = TDEE - (rate in kg each week x 7700 / 7)
     * ```
     *
     * Floored at the basal metabolic rate. A target below the energy the body
     * spends at rest is not a plan, so when the arithmetic goes under it the
     * floor wins and the caller is told the chosen rate is too fast.
     */
    fun dailyTarget(maintenanceKcal: Int, rateKgPerWeek: Double, basalKcal: Int): Target {
        val raw = maintenanceKcal - (rateKgPerWeek * KCAL_PER_KG / 7.0)
        return if (raw < basalKcal) {
            Target(kcal = basalKcal, clampedToBasal = true, basalKcal = basalKcal)
        } else {
            Target(kcal = raw.roundToInt(), clampedToBasal = false, basalKcal = basalKcal)
        }
    }

    /** Basal metabolic rate: Mifflin-St Jeor with no activity multiplier. */
    fun basalRate(weightKg: Double, heightCm: Double, ageYears: Int, sex: Sex): Int =
        mifflinStJeor(weightKg, heightCm, ageYears, sex, activityFactor = 1.0).kcal
}
