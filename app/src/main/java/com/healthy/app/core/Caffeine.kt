package com.healthy.app.core

import com.healthy.app.data.entity.Drink
import kotlin.math.pow

/**
 * The caffeine decay model (spec 6).
 *
 * ```
 * level(t) = sum over all doses of: mg x 0.5 ^ ((t - doseTime) / halfLife)
 * ```
 *
 * This is a one-compartment model with a flat half-life. It ignores absorption
 * time and knows nothing about the user's genetics, so it is a tool for
 * comparing the user's own days against each other, not a measurement of blood
 * concentration. The settings screen says so in those words.
 */
object Caffeine {

    /** Hours. The literature range is 4 to 6; the user can change it. */
    const val DEFAULT_HALF_LIFE_HOURS: Double = 5.0

    /** Milligrams. A rule of thumb, not a measured fact about this user. */
    const val DEFAULT_BEDTIME_LIMIT_MG: Int = 50

    private const val MILLIS_PER_HOUR = 3_600_000.0

    /** The level in milligrams at [atMillis], from every dose taken before it. */
    fun levelAt(doses: List<Drink>, atMillis: Long, halfLifeHours: Double): Double {
        if (halfLifeHours <= 0) return 0.0
        val halfLifeMillis = halfLifeHours * MILLIS_PER_HOUR
        return doses.sumOf { dose ->
            if (dose.timestamp > atMillis) {
                0.0
            } else {
                dose.mg * 0.5.pow((atMillis - dose.timestamp) / halfLifeMillis)
            }
        }
    }

    /**
     * The level sampled evenly across `[from, to]`, for the curve on the Today
     * screen. Returns [samples] + 1 points so both ends are included.
     */
    fun curve(
        doses: List<Drink>,
        from: Long,
        to: Long,
        halfLifeHours: Double,
        samples: Int = 120,
    ): List<Double> {
        val span = (to - from).toDouble()
        return (0..samples).map { i ->
            levelAt(doses, from + (span * i / samples).toLong(), halfLifeHours)
        }
    }
}
