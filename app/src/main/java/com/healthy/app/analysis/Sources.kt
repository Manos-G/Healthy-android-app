package com.healthy.app.analysis

import com.healthy.app.core.Alcohol
import com.healthy.app.core.Caffeine
import com.healthy.app.core.SafeLimits

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
            item = "Daily caffeine limit",
            value = "${SafeLimits.CAFFEINE_DAILY_MG} mg a day",
            basis = "EFSA's 2015 opinion and the US FDA both put habitual intake " +
                "for a healthy adult at this figure. EFSA adds ${SafeLimits.CAFFEINE_SINGLE_DOSE_MG} mg " +
                "as a single dose.",
            limitation = "A population figure, not a measurement of you. It says " +
                "nothing about timing, and timing is what decides whether caffeine " +
                "reaches your bedtime — that is the separate line on the curve.",
        ),
        Entry(
            item = "Weekly alcohol limit",
            value = "${SafeLimits.ALCOHOL_WEEKLY_UNITS.toInt()} units a week",
            basis = "The UK Chief Medical Officers' guideline, which also says to " +
                "spread it across three days or more rather than save it up.",
            limitation = "There is no amount known to be free of risk; this is the " +
                "level below which the risk is described as low. One unit is " +
                "${Alcohol.DEFAULT_ML_PER_UNIT.toInt()} ml of pure alcohol here, and " +
                "that definition changes between countries.",
        ),
        Entry(
            item = "Daily fluid target",
            value = "${com.healthy.app.data.HealthySettings.DEFAULT_FLUID_TARGET_ML} ml a day",
            basis = "The default this app was specified with. It sits close to what " +
                "EFSA calls adequate intake — 2.0 litres of total water a day for " +
                "women, 2.5 for men — once the fifth or so that comes from food is " +
                "taken off, leaving roughly 1.6 to 2.0 litres of drinks.",
            limitation = "A round number, not a requirement, and not measured for " +
                "you. Real need moves with heat, exercise and body size, and thirst " +
                "tracks it better than any fixed figure. Change it in settings; " +
                "nothing is sent when you are under it.",
        ),
        Entry(
            item = "Fluid caution level",
            value = "${SafeLimits.FLUID_CAUTION_ML} ml a day",
            basis = "Healthy kidneys clear roughly 0.8 to 1.0 litres an hour. This " +
                "is the point past which more water stops helping.",
            limitation = "Not a danger line. Water intoxication needs far more, far " +
                "faster, and usually heavy sweating with no salt replaced. Your own " +
                "need rises with heat and exercise.",
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
            item = "Protein target",
            value = "${NutrientTargets.PROTEIN_G_PER_KG} g per kg",
            basis = "A floor, scaled to body weight, in the range commonly given " +
                "for people who train.",
            limitation = "It is a floor rather than a goal, and body weight is a crude " +
                "scale: lean mass is what needs the protein, and this app does not " +
                "know yours.",
        ),
        Entry(
            item = "Fibre target",
            value = "${NutrientTargets.FIBRE_G.toInt()} g",
            basis = "A common daily recommendation for adults.",
            limitation = "A population figure. It says nothing about what your own gut " +
                "is comfortable with.",
        ),
        Entry(
            item = "Sugar and saturated fat ceilings",
            value = "${NutrientTargets.SUGAR_PERCENT_OF_ENERGY.toInt()} percent of energy",
            basis = "Each expressed as a share of your energy target, so both move " +
                "when the target does.",
            limitation = "Sugar here is whatever the label counted, which lumps the sugar " +
                "in fruit together with the sugar in a biscuit.",
        ),
        Entry(
            item = "Salt ceiling",
            value = "${NutrientTargets.SALT_G.toInt()} g",
            basis = "A common daily upper limit.",
            limitation = "Salt on food after it is cooked is invisible to this app, so " +
                "the figure is a floor on what you actually ate.",
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

    /** Not a const: the boundaries are read from the one place that defines them. */
    val DAY_BOUNDARY_NOTE: String =
        "There are two days here. Eating and drinking run " +
            "${com.healthy.app.core.HealthyDay.BOUNDARY_LABEL} to " +
            "${com.healthy.app.core.HealthyDay.BOUNDARY_LABEL}, so a coffee at 02:00 " +
            "counts against the day still being lived rather than the one about to " +
            "start. Sleep runs midnight to midnight, so a night carries the calendar " +
            "date it began on. Where the two are compared, a night is matched to the " +
            "eating day that was running when its sleep started."
}
