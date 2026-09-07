package com.healthy.app.core

/**
 * A drink the user can log with one tap.
 *
 * [volumeMl] is zero for a solid such as dark chocolate: it carries caffeine
 * but must not count towards the fluid total (spec 9.1).
 */
data class CatalogDrink(
    val name: String,
    val mg: Int,
    val volumeMl: Int,
)

/**
 * The built-in catalog (spec 7).
 *
 * These twenty are fixed values from the specification, so they live in code
 * rather than in a seeded table: a static list cannot drift out of step with
 * the spec, needs no migration, and cannot be corrupted by a bad import. The
 * user's own additions go in the `custom_drink` table instead.
 */
object DrinkCatalog {

    val BUILT_IN: List<CatalogDrink> = listOf(
        CatalogDrink("Freddo espresso", 125, 200),
        CatalogDrink("Freddo cappuccino", 125, 250),
        CatalogDrink("Frappé", 70, 250),
        CatalogDrink("Greek coffee", 60, 60),
        CatalogDrink("Espresso", 63, 30),
        CatalogDrink("Double espresso", 125, 60),
        CatalogDrink("Cold brew, 300 ml", 200, 300),
        CatalogDrink("Filter coffee", 95, 240),
        CatalogDrink("Cappuccino", 75, 180),
        CatalogDrink("Instant coffee", 60, 200),
        CatalogDrink("Hell 250 ml", 80, 250),
        CatalogDrink("Hell 500 ml", 160, 500),
        CatalogDrink("Red Bull 250 ml", 80, 250),
        CatalogDrink("Red Bull 355 ml", 114, 355),
        CatalogDrink("Monster 500 ml", 160, 500),
        CatalogDrink("Black tea", 47, 240),
        CatalogDrink("Green tea", 28, 240),
        CatalogDrink("Coca-Cola 330 ml", 32, 330),
        CatalogDrink("Coke Zero 330 ml", 34, 330),
        CatalogDrink("Dark chocolate 50 g", 40, 0),
    )
}
