package com.healthy.app.analysis

import com.healthy.app.data.entity.MealEntry
import com.healthy.app.data.entity.Product

/**
 * Portions and nutrient totals (spec 12).
 *
 * The purpose is correlation with sleep and alertness, not a calorie budget
 * (spec 12.1). Nothing here computes a verdict, colours a number, or compares
 * against a target — it adds up what was eaten so the comparison table has
 * something to compare.
 */
object Nutrition {

    /**
     * How the user said how much they ate (spec 12.3).
     *
     * A barcode gives values for 100 g and says nothing about the portion, so
     * the app has to ask. Weighing is the only accurate answer; the other two
     * are conveniences for when the pack is the portion.
     */
    sealed interface Portion {
        val grams: Double

        data class WholePack(override val grams: Double) : Portion
        data class OneServing(override val grams: Double) : Portion
        data class Weighed(override val grams: Double) : Portion
    }

    /** The options to offer for a product; a serving is hidden when unknown. */
    fun portionsFor(product: Product): List<Portion> = buildList {
        product.packGrams?.takeIf { it > 0 }?.let { add(Portion.WholePack(it.toDouble())) }
        product.servingGrams?.takeIf { it > 0 }?.let { add(Portion.OneServing(it.toDouble())) }
    }

    data class Totals(
        val kcal: Double = 0.0,
        val protein: Double = 0.0,
        val carbs: Double = 0.0,
        val sugar: Double = 0.0,
        val fat: Double = 0.0,
        val saturatedFat: Double = 0.0,
        val fibre: Double = 0.0,
        val salt: Double = 0.0,
        val magnesium: Double = 0.0,
        val vitaminD: Double = 0.0,
    ) {
        operator fun plus(other: Totals) = Totals(
            kcal + other.kcal, protein + other.protein, carbs + other.carbs,
            sugar + other.sugar, fat + other.fat, saturatedFat + other.saturatedFat,
            fibre + other.fibre,
            salt + other.salt, magnesium + other.magnesium, vitaminD + other.vitaminD,
        )
    }

    /**
     * Scales a product's per-100 g values to what was actually eaten.
     *
     * An absent value stays absent by contributing nothing, rather than being
     * counted as a zero — a product with no fibre figure must not drag a
     * day's fibre total down.
     */
    fun forGrams(product: Product, grams: Double): Totals {
        val factor = grams / 100.0
        fun of(value: Double?) = (value ?: 0.0) * factor
        return Totals(
            kcal = of(product.kcal100),
            protein = of(product.protein100),
            carbs = of(product.carbs100),
            sugar = of(product.sugar100),
            fat = of(product.fat100),
            saturatedFat = of(product.saturatedFat100),
            fibre = of(product.fibre100),
            salt = of(product.salt100),
            // Spec 12.2: these two have a plausible link to sleep, which is
            // why they reach the comparison table and the rest do not.
            magnesium = of(product.magnesium100),
            vitaminD = of(product.vitaminD100),
        )
    }

    fun totalFor(entries: List<MealEntry>, products: Map<String, Product>): Totals =
        entries.fold(Totals()) { running, entry ->
            val product = entry.barcode?.let(products::get) ?: return@fold running
            running + forGrams(product, entry.grams)
        }

    /** A barcode the user typed in themselves rather than scanned (spec 12.4). */
    fun manualBarcode(name: String): String =
        Product.MANUAL_PREFIX + name.lowercase().filter { it.isLetterOrDigit() }.take(24) +
            "-" + System.currentTimeMillis().toString().takeLast(6)
}
