package com.healthy.app.analysis

import com.healthy.app.data.entity.MealEntry
import com.healthy.app.data.entity.Product
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Portions and nutrient totals (spec 12). */
class NutritionTest {

    private val oats = Product(
        barcode = "1", kind = Product.KIND_FOOD, name = "Oats",
        packGrams = 500, servingGrams = 40,
        kcal100 = 370.0, protein100 = 13.5, carbs100 = 58.7,
        sugar100 = 1.1, fat100 = 6.5, fibre100 = 10.1, salt100 = 0.02,
        magnesium100 = 0.177,
    )

    @Test
    fun `values scale from 100 grams to what was eaten`() {
        val t = Nutrition.forGrams(oats, 40.0)
        assertEquals(148.0, t.kcal, 0.01)
        assertEquals(5.4, t.protein, 0.01)
        assertEquals(4.04, t.fibre, 0.01)
    }

    @Test
    fun `100 grams is the values themselves`() {
        val t = Nutrition.forGrams(oats, 100.0)
        assertEquals(370.0, t.kcal, 0.001)
        assertEquals(13.5, t.protein, 0.001)
    }

    /** An absent field contributes nothing rather than dragging a total down. */
    @Test
    fun `a product with no fibre figure adds no fibre and no negative`() {
        val bread = Product(barcode = "2", kind = Product.KIND_FOOD, name = "Bread", kcal100 = 250.0)
        val t = Nutrition.forGrams(bread, 100.0)
        assertEquals(250.0, t.kcal, 0.001)
        assertEquals(0.0, t.fibre, 0.001)
    }

    @Test
    fun `a serving is offered only when the field exists`() {
        assertEquals(2, Nutrition.portionsFor(oats).size)

        val noServing = oats.copy(servingGrams = null)
        val options = Nutrition.portionsFor(noServing)
        assertEquals(1, options.size)
        assertTrue(options.single() is Nutrition.Portion.WholePack)
    }

    @Test
    fun `a product with neither pack nor serving leaves only weighing`() {
        val bare = Product(barcode = "3", kind = Product.KIND_FOOD, name = "Apple")
        assertTrue(Nutrition.portionsFor(bare).isEmpty())
    }

    @Test
    fun `totals add across a day`() {
        val entries = listOf(
            MealEntry(id = 1, timestamp = 1L, mealType = MealEntry.BREAKFAST, barcode = "1", grams = 40.0),
            MealEntry(id = 2, timestamp = 2L, mealType = MealEntry.SNACK, barcode = "1", grams = 60.0),
        )
        val total = Nutrition.totalFor(entries, mapOf("1" to oats))
        assertEquals(370.0, total.kcal, 0.01)
        assertEquals(13.5, total.protein, 0.01)
    }

    @Test
    fun `an entry whose product is missing is skipped rather than counted as zero`() {
        val entries = listOf(
            MealEntry(id = 1, timestamp = 1L, mealType = MealEntry.SNACK, barcode = "1", grams = 100.0),
            MealEntry(id = 2, timestamp = 2L, mealType = MealEntry.SNACK, barcode = "gone", grams = 100.0),
        )
        assertEquals(370.0, Nutrition.totalFor(entries, mapOf("1" to oats)).kcal, 0.01)
    }

    /** Spec 12.4: a hand-typed food gets a manual- code, not a real barcode. */
    @Test
    fun `a manual food gets a distinguishable code`() {
        val code = Nutrition.manualBarcode("Bakery bread")
        assertTrue(code.startsWith(Product.MANUAL_PREFIX))
        assertTrue(code.contains("bakerybread"))
    }

    @Test
    fun `two manual foods with the same name do not collide`() {
        val a = Nutrition.manualBarcode("Apple")
        Thread.sleep(2)
        val b = Nutrition.manualBarcode("Apple")
        assertTrue("codes must differ: $a and $b", a != b)
    }

    /**
     * Reported from the phone: logging 222 g of a saved recipe showed
     * "Unknown, 0 kcal" on the food list and added nothing to the day, while
     * the recipe screen had just said 264 kcal.
     *
     * A meal entry points at a product or at a recipe. Everything that added
     * entries up only knew the first kind.
     */
    @Test
    fun `a portion logged from a recipe counts, and is not Unknown`() {
        val dish = 3L
        // 119 kcal per 100 g of finished dish, as the recipe card showed.
        val per100g = mapOf(dish to Nutrition.Totals(kcal = 119.0, protein = 6.0))
        val entry = MealEntry(
            timestamp = 0L,
            mealType = MealEntry.DINNER,
            recipeId = dish,
            grams = 222.0,
        )

        val totals = Nutrition.forEntry(entry, emptyMap(), per100g)
        assertEquals(264.2, totals.kcal, 0.5)
        assertEquals(13.3, totals.protein, 0.1)
    }

    /** A recipe the app cannot resolve stays at zero rather than inventing one. */
    @Test
    fun `an unresolvable recipe contributes nothing`() {
        val entry = MealEntry(timestamp = 0L, mealType = MealEntry.DINNER, recipeId = 9L, grams = 200.0)
        assertEquals(0.0, Nutrition.forEntry(entry, emptyMap(), emptyMap()).kcal, 0.001)
    }

    /** A day of both kinds adds up to both. */
    @Test
    fun `a day mixing scanned food and a home-cooked dish totals both`() {
        val crisps = Product(
            barcode = "1",
            kind = Product.KIND_FOOD,
            name = "Crisps",
            kcal100 = 517.0,
        )
        val entries = listOf(
            MealEntry(timestamp = 0L, mealType = MealEntry.SNACK, barcode = "1", grams = 53.0),
            MealEntry(timestamp = 1L, mealType = MealEntry.DINNER, recipeId = 3L, grams = 222.0),
        )
        val totals = Nutrition.totalFor(
            entries,
            mapOf("1" to crisps),
            mapOf(3L to Nutrition.Totals(kcal = 119.0)),
        )
        assertEquals(274.0 + 264.0, totals.kcal, 1.0)
    }
}
