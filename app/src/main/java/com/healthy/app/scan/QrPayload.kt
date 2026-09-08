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

    /**
     * A link that carries the whole item, for sending through a messenger.
     *
     * A QR code only works when both people are in the same room: a square
     * sent through Messenger arrives on the reader's own screen, and a phone
     * cannot scan itself. So the same payload also travels as a link, which
     * opens the app and imports what it holds.
     *
     * The payload is deflated before base64 because JSON of this shape
     * compresses to roughly a third, and the difference decides whether a
     * dozen ingredients fit in something a person is willing to paste.
     *
     * `https` rather than a private scheme so it stays a tappable link
     * everywhere; the host is never contacted, and nothing is sent to it.
     */
    const val LINK_HOST = "healthy.app"
    private const val LINK_PREFIX = "https://$LINK_HOST/i#"

    fun toLink(payload: String): String {
        val deflater = java.util.zip.Deflater(java.util.zip.Deflater.BEST_COMPRESSION)
        val bytes = payload.toByteArray(Charsets.UTF_8)
        deflater.setInput(bytes)
        deflater.finish()
        val out = java.io.ByteArrayOutputStream(bytes.size)
        val buffer = ByteArray(4096)
        while (!deflater.finished()) out.write(buffer, 0, deflater.deflate(buffer))
        deflater.end()
        // java.util.Base64, not android.util: available from API 26, which is
        // minSdk, and it keeps this whole class testable on a plain JVM.
        val encoded = java.util.Base64.getUrlEncoder().withoutPadding()
            .encodeToString(out.toByteArray())
        return LINK_PREFIX + encoded
    }

    /** The payload back out of a link, or null if it is not one of ours. */
    fun fromLink(link: String): String? {
        val marker = link.indexOf("#")
        if (marker < 0 || !link.startsWith("https://$LINK_HOST/")) return null
        val encoded = link.substring(marker + 1).trim()
        if (encoded.isEmpty()) return null
        return runCatching {
            val raw = java.util.Base64.getUrlDecoder().decode(encoded)
            val inflater = java.util.zip.Inflater()
            inflater.setInput(raw)
            val out = java.io.ByteArrayOutputStream(raw.size * 4)
            val buffer = ByteArray(4096)
            while (!inflater.finished()) {
                val n = inflater.inflate(buffer)
                if (n == 0 && inflater.needsInput()) break
                out.write(buffer, 0, n)
            }
            inflater.end()
            out.toString(Charsets.UTF_8.name())
        }.getOrNull()
    }

    /**
     * Accepts either form: a raw payload from a scanned square, or a link
     * pasted out of a chat. The user should not have to know the difference.
     */
    fun decodeAny(text: String): Decoded {
        val trimmed = text.trim()
        val fromLink = trimmed.lineSequence()
            .map { it.trim() }
            .firstOrNull { it.startsWith("https://$LINK_HOST/") }
            ?.let(::fromLink)
        return decode(fromLink ?: trimmed)
    }

    sealed interface Decoded {
        data class Food(val product: Product) : Decoded

        /**
         * [products] are the ingredients' own values, carried along.
         *
         * Without them the receiving phone gets a list of barcodes it has
         * never seen and the dish resolves to zero — the same emptiness the
         * recipe builder used to produce, only across two devices. Built-in
         * ingredients are the exception: both phones compile the same catalog,
         * so those travel as a code alone.
         */
        data class Dish(
            val recipe: Recipe,
            val items: List<RecipeItem>,
            val products: List<Product> = emptyList(),
        ) : Decoded

        data class NotOurs(val reason: String) : Decoded
    }

    fun encode(product: Product): String =
        productJson(product).apply {
            put("a", MAGIC)
            put("t", "p")
        }.toString()

    private fun productJson(product: Product): JSONObject = JSONObject().apply {
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
    }

    /**
     * [products] should hold every product the items point at. Those already
     * in the built-in catalog are dropped, because the other phone has them.
     */
    fun encode(
        recipe: Recipe,
        items: List<RecipeItem>,
        products: Map<String, Product> = emptyMap(),
    ): String = JSONObject().apply {
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

        val carried = items.mapNotNull { it.barcode }
            .distinct()
            .filterNot { it.startsWith(com.healthy.app.core.CatalogIngredient.BUILTIN_PREFIX) }
            .mapNotNull(products::get)
        if (carried.isNotEmpty()) {
            put("pd", JSONArray().also { array -> carried.forEach { array.put(productJson(it)) } })
        }
    }.toString()

    fun decode(text: String): Decoded = runCatching {
        val root = JSONObject(text)
        if (root.optString("a") != MAGIC) {
            return Decoded.NotOurs("That code is not a Healthy item.")
        }
        when (root.optString("t")) {
            "p" -> Decoded.Food(productFrom(root))

            "r" -> {
                val array = root.optJSONArray("i") ?: JSONArray()
                val carried = root.optJSONArray("pd") ?: JSONArray()
                val items = (0 until array.length()).map { index ->
                    val item = array.getJSONObject(index)
                    RecipeItem(
                        recipeId = 0,
                        barcode = item.stringOrNull("b"),
                        name = item.optString("n"),
                        grams = item.optDouble("g", 0.0),
                    )
                }
                // Built-in ingredients are rebuilt from this phone's own copy
                // of the catalog rather than travelling in the code.
                val builtIn = items.mapNotNull { it.barcode }
                    .mapNotNull(com.healthy.app.core.IngredientCatalog::byBarcode)
                    .map { it.toProduct() }

                Decoded.Dish(
                    recipe = Recipe(
                        name = root.optString("n"),
                        cookedGrams = root.optDouble("cg", 0.0),
                        portions = root.optInt("po", 1),
                    ),
                    items = items,
                    products = builtIn +
                        (0 until carried.length()).map { productFrom(carried.getJSONObject(it)) },
                )
            }

            else -> Decoded.NotOurs("That code is a Healthy item of a kind this version cannot read.")
        }
    }.getOrElse { Decoded.NotOurs("That code could not be read as a Healthy item.") }

    private fun productFrom(root: JSONObject): Product = Product(
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

    private fun JSONObject.stringOrNull(key: String): String? =
        if (isNull(key)) null else optString(key).takeIf { it.isNotEmpty() }

    private fun JSONObject.intOrNull(key: String): Int? = if (isNull(key)) null else optInt(key)

    private fun JSONObject.doubleOrNull(key: String): Double? =
        if (isNull(key)) null else optDouble(key).takeIf { !it.isNaN() }
}
