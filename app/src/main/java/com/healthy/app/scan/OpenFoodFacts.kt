package com.healthy.app.scan

import com.healthy.app.data.entity.Product
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * The one network call this app ever makes (spec 11.3, 11.5).
 *
 * It happens only after a barcode scan that missed the local table, and it
 * reaches `world.openfoodfacts.org` and nowhere else. Everything else in the
 * app works with no network at all.
 */
object OpenFoodFacts {

    const val HOST = "world.openfoodfacts.org"

    private const val FIELDS = "product_name,brands,quantity,product_quantity,serving_size,nutriments"

    /**
     * Open Food Facts refuses a request with a default User-Agent, and asks
     * that apps identify themselves with a contact address.
     */
    private const val USER_AGENT = "Healthy/0.1 (Android; open source; github.com/Manos-G)"

    sealed interface Result {
        data class Found(val product: Product) : Result

        /** The service answered, and has never heard of this barcode. */
        data object Unknown : Result

        /** No network, a timeout, or the service failed. */
        data class Failed(val reason: String) : Result
    }

    fun lookup(barcode: String, kind: String = Product.KIND_DRINK): Result {
        val url = URL("https://$HOST/api/v2/product/$barcode.json?fields=$FIELDS")
        return runCatching {
            val connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                setRequestProperty("User-Agent", USER_AGENT)
                connectTimeout = 10_000
                readTimeout = 10_000
            }
            connection.use { body ->
                if (connection.responseCode == 404) return Result.Unknown
                if (connection.responseCode !in 200..299) {
                    return Result.Failed("the service answered ${connection.responseCode}")
                }
                parse(barcode, kind, body)
            }
        }.getOrElse { Result.Failed(it.message ?: "no network") }
    }

    private inline fun <T> HttpURLConnection.use(block: (String) -> T): T =
        try {
            block(inputStream.bufferedReader().use { it.readText() })
        } finally {
            disconnect()
        }

    fun parse(barcode: String, kind: String, json: String): Result {
        val root = JSONObject(json)
        if (root.optInt("status", 1) == 0) return Result.Unknown
        val product = root.optJSONObject("product") ?: return Result.Unknown

        val nutriments = product.optJSONObject("nutriments") ?: JSONObject()
        val name = product.optString("product_name").takeIf { it.isNotBlank() }
        val brand = product.optString("brands").takeIf { it.isNotBlank() }

        return Result.Found(
            Product(
                barcode = barcode,
                kind = kind,
                name = name ?: "Unknown product",
                brand = brand,
                // caffeine_100g is in grams per 100 ml; x10 gives mg per 100 ml
                // (spec 11.3). Often absent, and then the user is asked once.
                mg = nutriments.doubleOrNull("caffeine_100g")?.let { grams ->
                    val volume = parseQuantityMl(product.optString("quantity"))
                    val mgPer100 = grams * 10_000
                    if (volume != null) (mgPer100 * volume / 100.0).toInt() else mgPer100.toInt()
                },
                volumeMl = parseQuantityMl(product.optString("quantity")),
                packGrams = product.doubleOrNull("product_quantity")?.toInt(),
                servingGrams = parseQuantityMl(product.optString("serving_size")),
                kcal100 = nutriments.doubleOrNull("energy-kcal_100g"),
                protein100 = nutriments.doubleOrNull("proteins_100g"),
                carbs100 = nutriments.doubleOrNull("carbohydrates_100g"),
                sugar100 = nutriments.doubleOrNull("sugars_100g"),
                fat100 = nutriments.doubleOrNull("fat_100g"),
                fibre100 = nutriments.doubleOrNull("fiber_100g"),
                salt100 = nutriments.doubleOrNull("salt_100g"),
                calcium100 = nutriments.doubleOrNull("calcium_100g"),
                iron100 = nutriments.doubleOrNull("iron_100g"),
                potassium100 = nutriments.doubleOrNull("potassium_100g"),
                magnesium100 = nutriments.doubleOrNull("magnesium_100g"),
                vitaminD100 = nutriments.doubleOrNull("vitamin-d_100g"),
                vitaminB12100 = nutriments.doubleOrNull("vitamin-b12_100g"),
                source = Product.OFF,
            )
        )
    }

    /**
     * Pulls the number out of "250 ml", "1,5 L", "30 g" and similar. The field
     * is free text, so anything unrecognised gives null rather than a guess.
     */
    fun parseQuantityMl(raw: String?): Int? {
        val text = raw?.trim()?.lowercase() ?: return null
        if (text.isEmpty()) return null
        val match = Regex("([0-9]+(?:[.,][0-9]+)?)\\s*(ml|cl|l|g|kg)?").find(text) ?: return null
        val value = match.groupValues[1].replace(',', '.').toDoubleOrNull() ?: return null
        return when (match.groupValues[2]) {
            "l", "kg" -> (value * 1000).toInt()
            "cl" -> (value * 10).toInt()
            else -> value.toInt()
        }.takeIf { it > 0 }
    }

    private fun JSONObject.doubleOrNull(key: String): Double? =
        if (isNull(key)) null else optDouble(key).takeIf { !it.isNaN() }
}
