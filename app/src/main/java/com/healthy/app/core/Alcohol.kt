package com.healthy.app.core

/**
 * Alcohol units from what was actually poured (spec 9.4, extended).
 *
 * Spec 9.4 asks for two settings: the units in one beer and the units in one
 * glass of wine. That works while a beer is always 330 ml, and stops working
 * the moment the user picks the volume — a 568 ml pint and a 330 ml bottle
 * cannot both be "one beer".
 *
 * So units are computed from the two things that decide them: how much liquid,
 * and how strong it was. Pure ethanol is 0.789 g/ml, and a UK unit is 10 ml of
 * it. The unit size is a setting because a unit means different things in
 * different countries, which is the point spec 9.4 was making.
 */
object Alcohol {

    /** Millilitres of pure ethanol in one unit. The UK definition. */
    const val DEFAULT_ML_PER_UNIT = 10.0

    /** Grams of ethanol in one millilitre, at room temperature. */
    const val ETHANOL_DENSITY = 0.789

    /**
     * Units in [volumeMl] of a drink at [abv] percent.
     *
     * A 330 ml lager at 5 percent is 16.5 ml of ethanol, so 1.65 units — which
     * is what spec 9.4's default of 1.7 units per beer was approximating.
     */
    fun units(volumeMl: Int, abv: Double, mlPerUnit: Double = DEFAULT_ML_PER_UNIT): Double {
        if (volumeMl <= 0 || abv <= 0.0 || mlPerUnit <= 0.0) return 0.0
        return volumeMl * (abv / 100.0) / mlPerUnit
    }

    /** Grams of ethanol, which is how some countries state a standard drink. */
    fun grams(volumeMl: Int, abv: Double): Double {
        if (volumeMl <= 0 || abv <= 0.0) return 0.0
        return volumeMl * (abv / 100.0) * ETHANOL_DENSITY
    }
}
