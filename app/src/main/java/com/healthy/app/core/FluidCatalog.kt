package com.healthy.app.core

/**
 * A drink with no caffeine (spec 9.2).
 *
 * [alcoholUnitsKey] names which setting supplies the units, because a unit
 * means different things in different countries (spec 9.4).
 */
data class FluidDrink(
    val name: String,
    val volumeMl: Int,
    val alcoholUnitsKey: AlcoholKind = AlcoholKind.None,
)

enum class AlcoholKind { None, Beer, Wine }

object FluidCatalog {

    val BUILT_IN: List<FluidDrink> = listOf(
        FluidDrink("Water, glass", 250),
        FluidDrink("Water, bottle", 500),
        FluidDrink("Water, large bottle", 1000),
        FluidDrink("Juice", 200),
        FluidDrink("Beer, 330 ml", 330, AlcoholKind.Beer),
        FluidDrink("Wine, glass", 150, AlcoholKind.Wine),
    )
}
