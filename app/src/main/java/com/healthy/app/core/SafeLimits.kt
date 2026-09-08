package com.healthy.app.core

/**
 * The published safe limits for each category.
 *
 * These are intake figures — how much went in over a day or a week — and not
 * levels in the body. That distinction decides where they can honestly be
 * drawn: the caffeine curve plots milligrams still circulating, so a 400 mg
 * daily intake limit has no meaning on that axis and is not drawn there. It
 * belongs against the day's running total, which is what the category cards
 * show.
 *
 * Every figure is a population guideline, not a measurement of this user. They
 * are drawn as a marker on a bar and nothing more: no red, no notification and
 * no streak, in keeping with spec 12.1 and 16.5.
 */
object SafeLimits {

    /**
     * 400 mg of caffeine a day for a healthy adult.
     *
     * EFSA's 2015 opinion and the FDA both land here. EFSA adds a single-dose
     * figure of 200 mg, which is the more useful one for sleep: it is the
     * amount that reliably raises the level for hours afterwards.
     */
    const val CAFFEINE_DAILY_MG = 400
    const val CAFFEINE_SINGLE_DOSE_MG = 200

    /**
     * 14 units of alcohol a week, the UK Chief Medical Officers' figure.
     *
     * Stated for the week on purpose. The same guidance says not to save them
     * up for one night, so a daily line would be the wrong shape: it would
     * pass a person drinking two units every day and fail one drinking six on
     * a Saturday, when the second is the safer pattern by that guidance only
     * if the week's total is lower. The week's total is what is shown.
     */
    const val ALCOHOL_WEEKLY_UNITS = 14.0

    /**
     * The daily total above which drinking more water stops helping.
     *
     * Healthy kidneys clear roughly 0.8 to 1.0 litres an hour. Three and a
     * half litres in a day is well inside that for someone spreading it out,
     * and is the point past which the app stops encouraging more rather than
     * a danger line — hyponatremia needs far more, far faster, usually with
     * heavy sweating and no salt.
     */
    const val FLUID_CAUTION_ML = 3500

    /** Where a total sits against its limit, as a fraction for a bar. */
    fun fraction(value: Double, limit: Double): Float =
        if (limit <= 0.0) 0f else (value / limit).coerceIn(0.0, 1.0).toFloat()

    /** True once a total has gone past the guideline. Stated, never coloured red. */
    fun exceeded(value: Double, limit: Double): Boolean = limit > 0.0 && value > limit
}
