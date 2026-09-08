package com.healthy.app.scan

import com.healthy.app.data.entity.Product
import com.healthy.app.data.entity.Recipe
import com.healthy.app.data.entity.RecipeItem
import org.json.JSONArray
import org.json.JSONObject

/**
 * One food or one recipe, small enough to fit in a QR code (spec 5.4).
 *
 * The point is a transfer that needs no server and no account: one phone shows
 * a square, another reads it, and the item exists on both. Nothing leaves
 * either device except as light.
 *
 * The keys are one letter each because a QR code holds about 2.9 kB and a
 * recipe with a dozen ingredients has to fit beside its name.
 */
object QrPayload {

    /** Marks the payload as this app's, so a random QR is rejected politely. */
    const val MAGIC = "healthy"

    sealed interface Decoded {
        data class Food(val product: Product) : Decoded
        data class Dish(val recipe: Recipe, val items: List<RecipeItem>) : Decoded
        data class NotOurs(val reason: String) : Decoded
    }

    fun encode(product: Product): String = JSONObject().apply {
        put("a", MAGIC)
        put("t", "p")
        put("b", product.barcode)
        put("n", product.name)
        product.brand?.let { put("br", it) }
        product.kind.let { put("k", it) }
        product.mg?.let { put("mg", it) }
        product.volumeMl?.let { put("v", it) }
        product.packGrams?.let { put("pk", it) }
        product.servingGrams?.let { put("sv", it) }
        product.kcal100?.let { put("e", it) }
        product.protein100?.let { put("pr", it) }
        product.carbs100?.let { put("c", it) }
        product.sugar100?.let { put("su", it) }
        product.fat100?.let { put("f", it) }
        product.saturatedFat100?.let { put("sf", it) }
        product.fibre100?.let { put("fi", it) }
        product.salt100?.let { put("sa", it) }
        product.magnesium100?.let { put("mg100", it) }
        product.vitaminD100?.let { put("vd", it) }
    }.toString()

    fun encode(recipe: Recipe, items: List<RecipeItem>): String = JSONObject().apply {
        put("a", MAGIC)
        put("t", "r")
        put("n", recipe.name)
        put("cg", recipe.cookedGrams)
        put("po", recipe.portions)
        put(
            "i",
            JSONArray().also { array ->
                items.forEach { item ->
                    array.put(
                        JSONObject().apply {
                            put("n", item.name)
                            put("g", item.grams)
                            item.barcode?.let { put("b", it) }
                        }
                    )
                }
            },
        )
    }.toString()

    fun decode(text: String): Decoded = runCatching {
        val root = JSONObject(text)
        if (root.optString("a") != MAGIC) {
            return Decoded.NotOurs("That code is not a Healthy item.")
        }
        when (root.optString("t")) {
            "p" -> Decoded.Food(
                Product(
                    barcode = root.optString("b").ifBlank { "shared-" + System.currentTimeMillis() },
                    kind = root.optString("k").ifBlank { Product.KIND_FOOD },
                    name = root.optString("n"),
                    brand = root.stringOrNull("br"),
                    mg = root.intOrNull("mg"),
                    volumeMl = root.intOrNull("v"),
                    packGrams = root.intOrNull("pk"),
                    servingGrams = root.intOrNull("sv"),
                    kcal100 = root.doubleOrNull("e"),
                    protein100 = root.doubleOrNull("pr"),
                    carbs100 = root.doubleOrNull("c"),
                    sugar100 = root.doubleOrNull("su"),
                    fat100 = root.doubleOrNull("f"),
                    saturatedFat100 = root.doubleOrNull("sf"),
                    fibre100 = root.doubleOrNull("fi"),
                    salt100 = root.doubleOrNull("sa"),
                    magnesium100 = root.doubleOrNull("mg100"),
                    vitaminD100 = root.doubleOrNull("vd"),
                    // Shared by a person, not fetched from the database.
                    source = Product.USER,
                )
            )

            "r" -> {
                val array = root.optJSONArray("i") ?: JSONArray()
                Decoded.Dish(
                    recipe = Recipe(
                        name = root.optString("n"),
                        cookedGrams = root.optDouble("cg", 0.0),
                        portions = root.optInt("po", 1),
                    ),
                    items = (0 until array.length()).map { index ->
                        val item = array.getJSONObject(index)
                        RecipeItem(
                            recipeId = 0,
                            barcode = item.stringOrNull("b"),
                            name = item.optString("n"),
                            grams = item.optDouble("g", 0.0),
                        )
                    },
                )
            }

            else -> Decoded.NotOurs("That code is a Healthy item of a kind this version cannot read.")
        }
    }.getOrElse { Decoded.NotOurs("That code could not be read as a Healthy item.") }

    private fun JSONObject.stringOrNull(key: String): String? =
        if (isNull(key)) null else optString(key).takeIf { it.isNotEmpty() }

    private fun JSONObject.intOrNull(key: String): Int? = if (isNull(key)) null else optInt(key)

    private fun JSONObject.doubleOrNull(key: String): Double? =
        if (isNull(key)) null else optDouble(key).takeIf { !it.isNaN() }
}
