package com.healthy.app.analysis

import com.healthy.app.data.HealthyDatabase
import com.healthy.app.data.entity.Product

/**
 * Every saved recipe's finished values for 100 g, keyed by recipe id.
 *
 * One place, because the same lookup is needed wherever a meal entry is turned
 * into nutrients, and it was missing from all of them: a logged portion of a
 * dish carries a `recipeId` and no barcode, so the food list called it
 * "Unknown, 0 kcal" and the day's energy did not count it at all.
 *
 * Anything that cannot be resolved — no cooked weight, an ingredient with no
 * product behind it — is simply absent from the map rather than present as
 * zero, so a caller can tell "nothing known" from "known to be nothing".
 */
object Dishes {

    suspend fun per100g(
        db: HealthyDatabase,
        products: Map<String, Product>,
    ): Map<Long, Nutrition.Totals> {
        val dao = db.recipeDao()
        val recipes = dao.allForExport()
        if (recipes.isEmpty()) return emptyMap()

        val items = recipes.associateWith { dao.items(it.id) }
        val children = items.entries.associate { (recipe, list) -> recipe.id to (recipe to list) }

        return recipes.mapNotNull { recipe ->
            Recipes.resolve(recipe, items[recipe].orEmpty(), products, children)
                .getOrNull()
                ?.let { recipe.id to it.per100g }
        }.toMap()
    }

    /** The name to show against a logged portion, rather than "Unknown". */
    suspend fun names(db: HealthyDatabase): Map<Long, String> =
        db.recipeDao().allForExport().associate { it.id to it.name }
}
