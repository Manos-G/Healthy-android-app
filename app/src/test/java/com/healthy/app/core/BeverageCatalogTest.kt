package com.healthy.app.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BeverageCatalogTest {

    /**
     * Spec 7 states twenty drinks as a milligram figure against one volume.
     * The catalog holds strength per 100 ml instead, so that a volume the user
     * picks scales the caffeine with it. At the default volume the two must
     * still agree, or the app quietly changed the spec's numbers.
     */
    @Test
    fun `every spec 7 drink still gives its stated milligrams at its stated volume`() {
        val spec = mapOf(
            "Freddo espresso" to (125 to 200),
            "Freddo cappuccino" to (125 to 250),
            "Frappé" to (70 to 250),
            "Greek coffee" to (60 to 60),
            "Espresso" to (63 to 30),
            "Double espresso" to (125 to 60),
            "Cold brew" to (200 to 300),
            "Filter coffee" to (95 to 240),
            "Cappuccino" to (75 to 180),
            "Instant coffee" to (60 to 200),
            "Hell" to (80 to 250),
            "Hell, big can" to (160 to 500),
            "Red Bull" to (80 to 250),
            "Red Bull, big can" to (114 to 355),
            "Monster" to (160 to 500),
            "Black tea" to (47 to 240),
            "Green tea" to (28 to 240),
            "Coca-Cola" to (32 to 330),
            "Coke Zero" to (34 to 330),
            "Dark chocolate" to (40 to 50),
        )
        spec.forEach { (name, expected) ->
            val (mg, ml) = expected
            val drink = BeverageCatalog.byName(name)
            assertNotNull("$name is missing from the catalog", drink)
            assertEquals("$name default volume", ml, drink!!.defaultMl)
            assertEquals("$name at $ml", mg, drink.caffeineMgFor(ml))
        }
    }

    @Test
    fun `each category carries about a hundred drinks`() {
        BeverageCategory.entries.forEach { category ->
            val size = BeverageCatalog.of(category).size
            assertTrue("${category.label} has only $size", size >= 100)
        }
    }

    @Test
    fun `no name appears twice, or the recent list would show it twice`() {
        val names = BeverageCatalog.ALL.map { it.name }
        assertEquals(names.size, names.toSet().size)
    }

    /** Water++ is defined by what it does not contain. */
    @Test
    fun `nothing in Water++ carries caffeine or alcohol`() {
        BeverageCatalog.of(BeverageCategory.Water).forEach {
            assertEquals(it.name, 0.0, it.caffeinePer100, 0.0)
            assertEquals(it.name, 0.0, it.abv, 0.0)
        }
    }

    @Test
    fun `everything in Alcohol has a strength`() {
        BeverageCatalog.of(BeverageCategory.Alcohol).forEach {
            assertTrue(it.name, it.abv > 0.0)
        }
    }

    /** A solid carries caffeine but must not count as fluid (spec 9.1). */
    @Test
    fun `dark chocolate adds caffeine and no fluid`() {
        val chocolate = BeverageCatalog.byName("Dark chocolate")!!
        assertEquals(40, chocolate.caffeineMgFor(50))
        assertEquals(0, chocolate.fluidMlFor(50))
        assertEquals("g", chocolate.unit)
    }

    /** Doubling the can doubles the dose; that is the point of the rebuild. */
    @Test
    fun `caffeine scales with the volume actually drunk`() {
        val hell = BeverageCatalog.byName("Hell")!!
        assertEquals(80, hell.caffeineMgFor(250))
        assertEquals(160, hell.caffeineMgFor(500))
        assertEquals(32, hell.caffeineMgFor(100))
    }

    @Test
    fun `a search matches inside a name and puts the prefix first`() {
        val results = BeverageCatalog.search(BeverageCategory.Caffeine, "co")
        assertTrue(results.first().name.lowercase().startsWith("co"))
        assertTrue(results.any { it.name == "Filter coffee" })
    }
}
