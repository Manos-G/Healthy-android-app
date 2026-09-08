package com.healthy.app.core

import com.healthy.app.analysis.Nutrition
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class IngredientCatalogTest {

    @Test
    fun `the catalog carries at least a hundred ingredients`() {
        assertTrue("only ${IngredientCatalog.ALL.size}", IngredientCatalog.ALL.size >= 100)
    }

    /** The four the issue named by hand. */
    @Test
    fun `the ingredients asked for are present with real values`() {
        listOf("Egg, whole", "Flour, plain", "Banana", "Cocoa powder").forEach { name ->
            val found = IngredientCatalog.ALL.firstOrNull { it.name == name }
            assertNotNull("$name is missing", found)
            assertTrue("$name has no energy", found!!.kcal > 0)
        }
    }

    @Test
    fun `every ingredient has an energy value, or it is no use in a recipe`() {
        IngredientCatalog.ALL.forEach {
            assertTrue(it.name, it.kcal >= 0.0)
            assertTrue("${it.name} has no macros at all", it.kcal > 0 || it.salt > 0 || it.name == "Water")
        }
    }

    @Test
    fun `codes are unique, so an ingredient is one row however often it is used`() {
        val codes = IngredientCatalog.ALL.map { it.barcode }
        assertEquals(codes.size, codes.toSet().size)
    }

    @Test
    fun `a catalog ingredient becomes a product the nutrition code already understands`() {
        val egg = IngredientCatalog.ALL.first { it.name == "Egg, whole" }.toProduct()
        assertEquals("builtin-egg-whole", egg.barcode)
        // Two eggs, about 100 g.
        val totals = Nutrition.forGrams(egg, 100.0)
        assertEquals(143.0, totals.kcal, 0.01)
        assertEquals(12.6, totals.protein, 0.01)
    }

    @Test
    fun `search puts a prefix match first`() {
        val results = IngredientCatalog.search("ba")
        assertTrue(results.first().name.lowercase().startsWith("ba"))
        assertTrue(results.any { it.name == "Banana" })
    }
}
