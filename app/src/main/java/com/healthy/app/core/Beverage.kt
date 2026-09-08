package com.healthy.app.core

/**
 * One drink that can be logged, in any volume (spec 7, extended).
 *
 * The spec's catalog gives a milligram figure against one fixed volume. That
 * cannot survive a volume the user chooses: a 500 ml Red Bull is not 80 mg.
 * So the strength is held per 100 ml and the amount is a separate question,
 * which is what the carousel asks.
 *
 * Every entry from spec 7 keeps its exact figures at [defaultMl]: 30 ml of
 * espresso at 210 mg per 100 ml is 63 mg, the number the spec states. Tapping
 * a drink and not moving the wheel logs precisely what the old catalog did.
 *
 * [abv] is alcohol by volume as a percentage, so units also scale with the
 * amount poured. [solid] marks something eaten rather than drunk — dark
 * chocolate carries caffeine but must not count towards the fluid total
 * (spec 9.1), and its carousel counts grams.
 */
data class Beverage(
    val name: String,
    val category: BeverageCategory,
    val defaultMl: Int,
    val caffeinePer100: Double = 0.0,
    val abv: Double = 0.0,
    val solid: Boolean = false,
) {
    /** Caffeine in [ml] of this drink, rounded the way a label would be. */
    fun caffeineMgFor(ml: Int): Int = Math.round(caffeinePer100 * ml / 100.0).toInt()

    /** Fluid counted towards the day's total. A solid contributes none. */
    fun fluidMlFor(ml: Int): Int = if (solid) 0 else ml

    /** The unit the carousel counts in. */
    val unit: String get() = if (solid) "g" else "ml"
}

enum class BeverageCategory(val label: String) {
    /** Water and everything else with neither caffeine nor alcohol. */
    Water("Water++"),
    Caffeine("Caffeine"),
    Alcohol("Alcohol"),
}
