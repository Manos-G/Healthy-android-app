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
}
