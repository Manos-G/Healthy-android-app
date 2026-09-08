package com.healthy.app.analysis

import com.healthy.app.data.entity.Product
import com.healthy.app.data.entity.Recipe
import com.healthy.app.data.entity.RecipeItem

/**
 * Recipes (spec 13).
 *
 * A user eats the same meals many times, and without this the app is a form to
 * fill in twice a day until they stop. The point is that a dish becomes one
 * tap.
 */
object Recipes {

    /** Spec 13.6: a sauce inside a pasta dish, and no deeper. */
    const val MAX_DEPTH = 2

    data class Resolved(
        val recipe: Recipe,
        /** Nutrients for the whole finished dish. */
        val totals: Nutrition.Totals,
        /** The same, per 100 g of finished dish. */
        val per100g: Nutrition.Totals,
        val ingredientGrams: Double,
        /** Water lost or absorbed: cooked weight less raw weight. */
        val weightChangeGrams: Double,
        /**
         * Ingredients that contributed weight but no nutrients.
         *
         * Reported because the alternative was silence: an ingredient with no
         * product behind it was skipped, so a whole recipe could total zero
         * calories with nothing on screen to say why. A dish is only as
         * complete as this list is empty.
         */
        val unknownIngredients: List<String> = emptyList(),
    )

    sealed interface Problem {
        data object NoCookedWeight : Problem
        data class BothSources(val itemName: String) : Problem
        data class TooDeep(val recipeName: String) : Problem
        data class Circular(val recipeName: String) : Problem
    }

    /**
     * An item points at a product or at another recipe, never both (spec 13.2).
     */
    fun validate(items: List<RecipeItem>): Problem? =
        items.firstOrNull { it.barcode != null && it.childRecipeId != null }
            ?.let { Problem.BothSources(it.name) }

    /**
     * Nutrients for a finished dish, and for 100 g of it.
     *
     * ```
     * value100(dish) = sum of all ingredient values / cookedGrams x 100
     * ```
     *
     * The cooked weight is what makes this correct and cannot be skipped
     * (spec 13.4). A stew loses water and gets lighter; rice absorbs it and
     * gets heavier. Without weighing the finished dish, every portion logged
     * from the recipe afterwards is wrong — not slightly, but by whatever
     * fraction of the weight was water.
     */
    fun resolve(
        recipe: Recipe,
        items: List<RecipeItem>,
        products: Map<String, Product>,
        childRecipes: Map<Long, Pair<Recipe, List<RecipeItem>>> = emptyMap(),
        depth: Int = 1,
    ): Result<Resolved> {
        if (recipe.cookedGrams <= 0) return Result.failure(RecipeError(Problem.NoCookedWeight))
        validate(items)?.let { return Result.failure(RecipeError(it)) }

        var totals = Nutrition.Totals()
        var rawGrams = 0.0
        val unknown = mutableListOf<String>()

        for (item in items) {
            rawGrams += item.grams
            when {
                item.barcode != null -> {
                    val product = products[item.barcode]
                    if (product == null) {
                        unknown += item.name
                        continue
                    }
                    if (product.kcal100 == null) unknown += item.name
                    totals += Nutrition.forGrams(product, item.grams)
                }

                // Neither a product nor a child recipe: a name and a weight,
                // which is what the free-text ingredient box used to produce.
                item.childRecipeId == null -> unknown += item.name

                else -> {
                    if (depth >= MAX_DEPTH) {
                        return Result.failure(RecipeError(Problem.TooDeep(recipe.name)))
                    }
                    if (item.childRecipeId == recipe.id) {
                        return Result.failure(RecipeError(Problem.Circular(recipe.name)))
                    }
                    val (child, childItems) = childRecipes[item.childRecipeId] ?: run {
                        unknown += item.name
                        continue
                    }
                    val resolvedChild = resolve(
                        recipe = child,
                        items = childItems,
                        products = products,
                        childRecipes = childRecipes,
                        depth = depth + 1,
                    ).getOrElse { return Result.failure(it) }
                    // The child contributes by weight used, from its own
                    // per-100 g values — which already account for its cooking.
                    totals += scale(resolvedChild.per100g, item.grams / 100.0)
                }
            }
        }

        return Result.success(
            Resolved(
                recipe = recipe,
                totals = totals,
                per100g = scale(totals, 100.0 / recipe.cookedGrams),
                ingredientGrams = rawGrams,
                weightChangeGrams = recipe.cookedGrams - rawGrams,
                unknownIngredients = unknown.distinct(),
            )
        )
    }

    /** The three ways to log a portion (spec 13.5). */
    sealed interface Portion {
        val grams: Double

        /** Correct, because a scale measured it. */
        data class Weighed(override val grams: Double) : Portion

        /** A fraction of the dish, such as a quarter. */
        data class Share(val fraction: Double, override val grams: Double) : Portion

        /** The dish divided by however many portions it was said to make. */
        data class OnePortion(override val grams: Double) : Portion
    }

    fun shareOf(recipe: Recipe, fraction: Double): Portion.Share =
        Portion.Share(fraction, recipe.cookedGrams * fraction)

    fun onePortionOf(recipe: Recipe): Portion.OnePortion? =
        recipe.portions.takeIf { it > 0 }
            ?.let { Portion.OnePortion(recipe.cookedGrams / it) }

    fun nutrientsFor(resolved: Resolved, grams: Double): Nutrition.Totals =
        scale(resolved.per100g, grams / 100.0)

    private fun scale(t: Nutrition.Totals, factor: Double) = Nutrition.Totals(
        kcal = t.kcal * factor,
        protein = t.protein * factor,
        carbs = t.carbs * factor,
        sugar = t.sugar * factor,
        fat = t.fat * factor,
        saturatedFat = t.saturatedFat * factor,
        fibre = t.fibre * factor,
        salt = t.salt * factor,
        magnesium = t.magnesium * factor,
        vitaminD = t.vitaminD * factor,
    )
}

class RecipeError(val problem: Recipes.Problem) : Exception(problem.toString())
