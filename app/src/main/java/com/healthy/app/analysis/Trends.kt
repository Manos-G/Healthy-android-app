package com.healthy.app.analysis

import com.healthy.app.data.entity.Night
import java.time.Instant
import java.time.ZoneId

/**
 * The comparison engine (spec 5.3).
 *
 * The whole app exists for this: sort the nights by how the user felt, put the
 * best third against the worst third, and show which inputs differ. Everything
 * here is a pure function over plain values, so the arithmetic can be tested
 * without a device.
 */
object Trends {

    /** The spec's floor: below this the table shows a count instead. */
    const val MINIMUM_RATED_NIGHTS = 6

    /**
     * One night plus the context the `night` table does not hold: what was
     * drunk during its logical day, and the caffeine still active at bedtime.
     */
    data class Sample(
        val night: Night,
        val caffeineMg: Int,
        val bedtimeMg: Int,
    ) {
        /**
         * How good the day was, from the two ratings.
         *
         * A night with one rating still counts (spec 5.2): the morning form
         * saves alertness before the afternoon rating can exist, so averaging
         * what is present beats discarding half the data.
         */
        val score: Double?
            get() = listOfNotNull(night.alertness, night.energy3pm)
                .takeIf { it.isNotEmpty() }
                ?.average()

        val sleepHours: Double? get() = night.minutes.takeIf { it > 0 }?.let { it / 60.0 }

        /** Hours between the last meal and falling asleep (spec 12.6). */
        val mealGapHours: Double?
            get() {
                val parts = (night.lastMeal ?: return null).split(":")
                if (parts.size != 2) return null
                val h = parts[0].toIntOrNull() ?: return null
                val m = parts[1].toIntOrNull() ?: return null
                val start = night.sleepStart.takeIf { it > 0 } ?: return null
                val asleep = Instant.ofEpochMilli(start).atZone(ZoneId.systemDefault()).toLocalTime()
                var gap = (asleep.hour * 60 + asleep.minute) - (h * 60 + m)
                if (gap < 0) gap += 24 * 60
                return gap / 60.0
            }
    }

    /** One row of the comparison table. */
    data class Comparison(
        val label: String,
        val unit: String,
        val best: Double?,
        val worst: Double?,
        val bestCount: Int,
        val worstCount: Int,
    ) {
        val difference: Double? get() = if (best != null && worst != null) best - worst else null
    }

    data class Means(
        val sleepHours: Double?,
        val alertness: Double?,
        val energy: Double?,
        val restingHr: Double?,
        val caffeineMg: Double?,
        val wakeups: Double?,
    )

    private data class Input(
        val label: String,
        val unit: String,
        val extract: (Sample) -> Double?,
    )

    /** The inputs the table compares. Any of them may be absent on a night. */
    private val INPUTS = listOf(
        Input("Sleep", "h") { it.sleepHours },
        Input("Caffeine, day total", "mg") { it.caffeineMg.toDouble() },
        Input("Caffeine at bedtime", "mg") { it.bedtimeMg.toDouble() },
        Input("Resting heart rate", "bpm") { it.night.restingHr?.toDouble() },
        Input("Deep sleep", "min") { it.night.deepMin?.toDouble() },
        Input("REM sleep", "min") { it.night.remMin?.toDouble() },
        Input("Wake-ups", "") { it.night.wakeups?.toDouble() },
        Input("Blood oxygen", "%") { it.night.spo2 },
        Input("Alcohol", "units") { it.night.alcoholUnits },
        Input("Room temperature", "°C") { it.night.roomTempC },
        Input("Last meal to sleep", "h") { it.mealGapHours },
    )

    fun means(samples: List<Sample>): Means {
        fun mean(values: List<Double>): Double? = values.takeIf { it.isNotEmpty() }?.average()
        return Means(
            sleepHours = mean(samples.mapNotNull { it.sleepHours }),
            alertness = mean(samples.mapNotNull { it.night.alertness?.toDouble() }),
            energy = mean(samples.mapNotNull { it.night.energy3pm?.toDouble() }),
            restingHr = mean(samples.mapNotNull { it.night.restingHr?.toDouble() }),
            caffeineMg = mean(samples.map { it.caffeineMg.toDouble() }),
            wakeups = mean(samples.mapNotNull { it.night.wakeups?.toDouble() }),
        )
    }

    /**
     * The best third against the worst third (spec 5.3).
     *
     * Empty below [MINIMUM_RATED_NIGHTS], where the caller shows the count and
     * the target instead of a table that would read as findings when it is
     * still noise.
     */
    fun comparison(samples: List<Sample>): List<Comparison> {
        val rated = samples.filter { it.score != null }.sortedByDescending { it.score }
        if (rated.size < MINIMUM_RATED_NIGHTS) return emptyList()

        val third = (rated.size / 3).coerceAtLeast(1)
        val best = rated.take(third)
        val worst = rated.takeLast(third)

        return INPUTS.mapNotNull { input ->
            val bestValues = best.mapNotNull(input.extract)
            val worstValues = worst.mapNotNull(input.extract)
            // An input nobody recorded is left out rather than shown blank.
            if (bestValues.isEmpty() && worstValues.isEmpty()) return@mapNotNull null
            Comparison(
                label = input.label,
                unit = input.unit,
                best = bestValues.takeIf { it.isNotEmpty() }?.average(),
                worst = worstValues.takeIf { it.isNotEmpty() }?.average(),
                bestCount = bestValues.size,
                worstCount = worstValues.size,
            )
        }
    }

    fun ratedCount(samples: List<Sample>): Int = samples.count { it.score != null }
}
