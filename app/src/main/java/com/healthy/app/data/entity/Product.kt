package com.healthy.app.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A scanned or hand-entered product (spec 11.2).
 *
 * One table holds drinks and foods. `kind` decides which dialog a scan opens.
 *
 * This table is the reason the app works in flight mode. A scan reads it first
 * and only reaches the network on a miss (spec 11.5), so a barcode seen once is
 * never looked up again.
 *
 * A product with no barcode gets a synthetic key `manual-<something>` (spec
 * 12.4) so that hand-entered foods live in the same table and the same search.
 *
 * The micronutrient columns are not in the 11.2 list. Section 12.2 requires
 * magnesium and vitamin D to reach the comparison table, and reads the other
 * four from the same response. Flagged for review.
 */
@Entity(
    tableName = "product",
    indices = [Index("name"), Index("kind")],
)
data class Product(
    @PrimaryKey
    @ColumnInfo(name = "barcode") val barcode: String,

    /** drink or food. */
    @ColumnInfo(name = "kind") val kind: String,
    @ColumnInfo(name = "name") val name: String,
    @ColumnInfo(name = "brand") val brand: String? = null,

    // Drinks only.
    @ColumnInfo(name = "mg") val mg: Int? = null,
    @ColumnInfo(name = "volumeMl") val volumeMl: Int? = null,

    // Foods only.
    @ColumnInfo(name = "packGrams") val packGrams: Int? = null,
    @ColumnInfo(name = "servingGrams") val servingGrams: Int? = null,

    // Macronutrients, each for 100 g.
    @ColumnInfo(name = "kcal100") val kcal100: Double? = null,
    @ColumnInfo(name = "protein100") val protein100: Double? = null,
    @ColumnInfo(name = "carbs100") val carbs100: Double? = null,
    @ColumnInfo(name = "sugar100") val sugar100: Double? = null,
    @ColumnInfo(name = "fat100") val fat100: Double? = null,
    @ColumnInfo(name = "fibre100") val fibre100: Double? = null,
    @ColumnInfo(name = "salt100") val salt100: Double? = null,

    // Micronutrients, each for 100 g (spec 12.2).
    @ColumnInfo(name = "calcium100") val calcium100: Double? = null,
    @ColumnInfo(name = "iron100") val iron100: Double? = null,
    @ColumnInfo(name = "potassium100") val potassium100: Double? = null,
    @ColumnInfo(name = "magnesium100") val magnesium100: Double? = null,
    @ColumnInfo(name = "vitaminD100") val vitaminD100: Double? = null,
    @ColumnInfo(name = "vitaminB12100") val vitaminB12100: Double? = null,

    /** user or off. Marks whether a human or Open Food Facts supplied the values. */
    @ColumnInfo(name = "source") val source: String = USER,
) {
    companion object {
        const val KIND_DRINK = "drink"
        const val KIND_FOOD = "food"
        const val USER = "user"
        const val OFF = "off"
        const val MANUAL_PREFIX = "manual-"
    }
}
