package com.healthy.app.analysis

import com.healthy.app.data.entity.Product
import com.healthy.app.data.entity.Recipe
import com.healthy.app.data.entity.RecipeItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Recipes (spec 13, acceptance tests 17 and 18). */
class RecipesTest {

    private val lentils = Product(
        barcode = "lentils", kind = Product.KIND_FOOD, name = "Lentils",
        kcal100 = 350.0, protein100 = 25.0, carbs100 = 60.0, fibre100 = 11.0,
    )
    private val oil = Product(
        barcode = "oil", kind = Product.KIND_FOOD, name = "Olive oil",
        kcal100 = 900.0, fat100 = 100.0,
    )
    private val products = mapOf("lentils" to lentils, "oil" to oil)

    /**
     * Acceptance test 17, exactly as written: 1000 g of ingredients cooked
     * down to 700 g gives correct values for 100 g.
     */
    @Test
    fun `a kilo of ingredients cooked to 700 grams scales correctly`() {
        val recipe = Recipe(id = 1, name = "Lentil soup", cookedGrams = 700.0, portions = 3)
        val items = listOf(
            RecipeItem(id = 1, recipeId = 1, barcode = "lentils", name = "Lentils", grams = 900.0),
            RecipeItem(id = 2, recipeId = 1, barcode = "oil", name = "Oil", grams = 100.0),
        )
        val resolved = Recipes.resolve(recipe, items, products).getOrThrow()

        // 900 g lentils at 350/100 = 3150 kcal, plus 100 g oil at 900 = 900.
        assertEquals(4050.0, resolved.totals.kcal, 0.01)
        assertEquals(1000.0, resolved.ingredientGrams, 0.01)
        // Cooked to 700 g, so per 100 g is 4050 / 700 * 100.
        assertEquals(578.57, resolved.per100g.kcal, 0.01)
        // The dish lost 300 g of water.
        assertEquals(-300.0, resolved.weightChangeGrams, 0.01)
    }

    @Test
    fun `rice that absorbs water reports a heavier dish and a lower density`() {
        val rice = Product(barcode = "rice", kind = Product.KIND_FOOD, name = "Rice", kcal100 = 360.0)
        val recipe = Recipe(id = 2, name = "Rice", cookedGrams = 300.0, portions = 2)
        val items = listOf(RecipeItem(id = 1, recipeId = 2, barcode = "rice", name = "Rice", grams = 100.0))
        val resolved = Recipes.resolve(recipe, items, mapOf("rice" to rice)).getOrThrow()

        assertEquals(360.0, resolved.totals.kcal, 0.01)
        assertEquals(120.0, resolved.per100g.kcal, 0.01)
        assertEquals(200.0, resolved.weightChangeGrams, 0.01)
    }

    /** Spec 13.4: without the cooked weight every later portion is wrong. */
    @Test
    fun `a recipe with no cooked weight is refused`() {
        val recipe = Recipe(id = 3, name = "Half a plan", cookedGrams = 0.0, portions = 2)
        val result = Recipes.resolve(recipe, emptyList(), products)
        assertTrue(result.isFailure)
        assertEquals(
            Recipes.Problem.NoCookedWeight,
            (result.exceptionOrNull() as RecipeError).problem,
        )
    }

    /** Spec 13.2: an item points at a product or a recipe, never both. */
    @Test
    fun `an item with both a barcode and a child recipe is refused`() {
        val items = listOf(
            RecipeItem(id = 1, recipeId = 1, barcode = "lentils", childRecipeId = 9, name = "Muddle", grams = 100.0),
        )
        assertNotNull(Recipes.validate(items))
        val result = Recipes.resolve(Recipe(1, "x", 500.0, 2), items, products)
        assertTrue(result.isFailure)
    }

    /** Spec 13.6: a sauce inside a pasta dish, and no deeper. */
    @Test
    fun `a recipe can hold a recipe`() {
        val sauce = Recipe(id = 10, name = "Sauce", cookedGrams = 400.0, portions = 4)
        val sauceItems = listOf(
            RecipeItem(id = 1, recipeId = 10, barcode = "oil", name = "Oil", grams = 100.0),
        )
        val pasta = Recipe(id = 11, name = "Pasta", cookedGrams = 600.0, portions = 2)
        val pastaItems = listOf(
            RecipeItem(id = 2, recipeId = 11, childRecipeId = 10, name = "Sauce", grams = 200.0),
        )

        val resolved = Recipes.resolve(
            recipe = pasta,
            items = pastaItems,
            products = products,
            childRecipes = mapOf(10L to (sauce to sauceItems)),
        ).getOrThrow()

        // Sauce is 900 kcal in 400 g, so 225 per 100 g; 200 g of it is 450.
        assertEquals(450.0, resolved.totals.kcal, 0.01)
    }

    @Test
    fun `three levels of nesting are refused`() {
        val inner = Recipe(id = 20, name = "Inner", cookedGrams = 100.0, portions = 1)
        val innerItems = listOf(RecipeItem(id = 1, recipeId = 20, barcode = "oil", name = "Oil", grams = 50.0))
        val middle = Recipe(id = 21, name = "Middle", cookedGrams = 200.0, portions = 1)
        val middleItems = listOf(RecipeItem(id = 2, recipeId = 21, childRecipeId = 20, name = "Inner", grams = 100.0))
        val outer = Recipe(id = 22, name = "Outer", cookedGrams = 300.0, portions = 1)
        val outerItems = listOf(RecipeItem(id = 3, recipeId = 22, childRecipeId = 21, name = "Middle", grams = 100.0))

        val result = Recipes.resolve(
            recipe = outer,
            items = outerItems,
            products = products,
            childRecipes = mapOf(20L to (inner to innerItems), 21L to (middle to middleItems)),
        )
        assertTrue(result.isFailure)
        assertTrue((result.exceptionOrNull() as RecipeError).problem is Recipes.Problem.TooDeep)
    }

    @Test
    fun `a recipe holding itself is refused rather than looping forever`() {
        val recipe = Recipe(id = 30, name = "Ouroboros", cookedGrams = 100.0, portions = 1)
        val items = listOf(RecipeItem(id = 1, recipeId = 30, childRecipeId = 30, name = "Itself", grams = 50.0))
        val result = Recipes.resolve(recipe, items, products, mapOf(30L to (recipe to items)))
        assertTrue(result.isFailure)
        assertTrue((result.exceptionOrNull() as RecipeError).problem is Recipes.Problem.Circular)
    }

    /** Spec 13.5: the three ways to say how much of the dish was eaten. */
    @Test
    fun `a share and a portion both come off the cooked weight`() {
        val recipe = Recipe(id = 1, name = "Soup", cookedGrams = 700.0, portions = 4)
        assertEquals(175.0, Recipes.shareOf(recipe, 0.25).grams, 0.01)
        assertEquals(175.0, Recipes.onePortionOf(recipe)!!.grams, 0.01)
        assertEquals(350.0, Recipes.shareOf(recipe, 0.5).grams, 0.01)
    }

    @Test
    fun `a recipe that says it makes no portions offers none`() {
        assertNull(Recipes.onePortionOf(Recipe(1, "Soup", 700.0, portions = 0)))
    }

    @Test
    fun `logging a portion scales from the finished dish`() {
        val recipe = Recipe(id = 1, name = "Lentil soup", cookedGrams = 700.0, portions = 4)
        val items = listOf(
            RecipeItem(id = 1, recipeId = 1, barcode = "lentils", name = "Lentils", grams = 900.0),
            RecipeItem(id = 2, recipeId = 1, barcode = "oil", name = "Oil", grams = 100.0),
        )
        val resolved = Recipes.resolve(recipe, items, products).getOrThrow()
        val portion = Recipes.onePortionOf(recipe)!!

        // A quarter of the dish is a quarter of its energy.
        assertEquals(4050.0 / 4, Recipes.nutrientsFor(resolved, portion.grams).kcal, 0.01)
    }
}
