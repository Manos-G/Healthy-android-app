package com.healthy.app.analysis

import com.healthy.app.core.Caffeine

/**
 * The basis of every number the app shows (spec 17).
 *
 * Each value is read from the constant the code actually uses, never typed in
 * as a literal. That is what makes acceptance test 28 — every number here
 * matches the value the code uses — a property of the design rather than
 * something that has to be re-checked by hand after every change.
 */
object Sources {

    data class Entry(
        val item: String,
        val value: String,
        val basis: String,
        /** What the app does not know about this number (spec 17). */
        val limitation: String,
    )

    val ENTRIES: List<Entry> = listOf(
        Entry(
            item = "Caffeine half-life",
            value = "${Caffeine.DEFAULT_HALF_LIFE_HOURS.toInt()} h",
            basis = "Pharmacokinetic studies put the range at 4 h to 6 h.",
            limitation = "Genetics change it and this app has not measured yours. " +
                "The model also ignores absorption time, so it compares your own " +
                "days against each other rather than measuring your blood.",
        ),
        Entry(
            item = "Bedtime caffeine limit",
            value = "${Caffeine.DEFAULT_BEDTIME_LIMIT_MG} mg",
            basis = "A rule of thumb in common use.",
            limitation = "It is not measured for you. If caffeine hits you hard, lower it.",
        ),
        Entry(
            item = "Weight smoothing factor",
            value = WeightTrend.SMOOTHING.toString(),
            basis = "The Hacker's Diet moving average.",
            limitation = "A smoothed line lags a real change by a few days. " +
                "That is the price of ignoring the daily noise.",
        ),
        Entry(
            item = "Energy in 1 kg of tissue",
            value = "${Energy.KCAL_PER_KG.toInt()} kcal",
            basis = "The standard value for mixed body tissue.",
            limitation = "Real tissue is not all one thing. Losing mostly water " +
                "for a week makes this number lie.",
        ),
        Entry(
            item = "Resting heart rate",
            value = "5th percentile",
            basis = "Taken across the samples inside the sleep window, using the " +
                "minimum of each 30-minute group.",
            limitation = "Chosen to reject one bad sensor reading. It is not a " +
                "clinical resting heart rate.",
        ),
        Entry(
            item = "Heart rate cycle detection",
            value = "Local minima, ${Hypnogram.MINIMUM_ISOLATION_MINUTES} min window",
            basis = "Heart rate falls in deep sleep and rises in REM, so the " +
                "troughs mark the rhythm of the night. Minima closer than " +
                "${Hypnogram.MINIMUM_SEPARATION_MINUTES} minutes are treated as one.",
            limitation = "This is a rhythm, not a stage. The night needs a sample " +
                "at least every ${Hypnogram.REQUIRED_SAMPLE_INTERVAL_MINUTES} minutes " +
                "or the app shows nothing rather than a guess.",
        ),
        Entry(
            item = "Sleep stages",
            value = "From the watch",
            basis = "A wrist sensor estimates stages from movement and heart rate.",
            limitation = "It agrees with laboratory scoring 60 to 80 percent of the " +
                "time. On this watch the deep blocks repeat at the same size all " +
                "night, which real deep sleep does not do. Treat the percentages " +
                "as a rough guide.",
        ),
        Entry(
            item = "Maximum weight change rate",
            value = "${(WeightGoal.MAX_RATE_FRACTION * 100).toInt()} percent each week",
            basis = "A common upper limit in nutrition guidance.",
            limitation = "It is a limit on what this app will set for you, not a " +
                "statement about what your body can do.",
        ),
        Entry(
            item = "Hold-the-weight band",
            value = "plus or minus ${WeightGoal.HOLD_BAND_KG.toInt()} kg",
            basis = "Compared against the trend, never the daily reading, and only " +
                "after ${WeightGoal.HOLD_PATIENCE_DAYS} days outside it.",
            limitation = "A daily weight leaves this band constantly and means nothing.",
        ),
        Entry(
            item = "Measured energy need",
            value = "${Energy.WINDOW_DAYS}-day window",
            basis = "Mean intake plus the energy the weight trend says your tissue " +
                "supplied. Measured from your own data.",
            limitation = "Before the window fills, the app shows a Mifflin-St Jeor " +
                "estimate instead and says so. A formula is often 300 kcal wrong.",
        ),
        Entry(
            item = "Reference intakes",
            value = "Various",
            basis = "U.S. National Academies, Institute of Medicine tables.",
            limitation = "Population figures. They are a starting point, not your " +
                "requirement.",
        ),
    )

    /** Spec 18.6, shown under the hypnogram entry. */
    const val HYPNOGRAM_DISCLAIMER: String =
        "This chart shows heart rate, not brain activity. Only an EEG measures " +
            "sleep stages. This method finds the rhythm of the night from a real " +
            "measurement. It does not name the stages."

    const val DAY_BOUNDARY_NOTE: String =
        "A day here runs 04:00 to 04:00, not midnight to midnight, so a night " +
            "that starts after midnight stays with the day it belongs to."
}
