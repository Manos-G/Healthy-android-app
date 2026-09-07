package com.healthy.app.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One item eaten at one time (spec 12.5).
 *
 * Either `barcode` or `recipeId` is set, never both. No foreign key is declared
 * on `barcode`: a product the user later deletes from the catalog must not
 * cascade away a meal that was actually eaten.
 */
@Entity(
    tableName = "meal_entry",
    indices = [Index("timestamp"), Index("barcode"), Index("recipeId")],
)
data class MealEntry(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id") val id: Long = 0,

    @ColumnInfo(name = "timestamp") val timestamp: Long,
    /** breakfast, lunch, dinner or snack. */
    @ColumnInfo(name = "mealType") val mealType: String,

    @ColumnInfo(name = "barcode") val barcode: String? = null,
    @ColumnInfo(name = "recipeId") val recipeId: Long? = null,

    @ColumnInfo(name = "grams") val grams: Double,
) {
    companion object {
        const val BREAKFAST = "breakfast"
        const val LUNCH = "lunch"
        const val DINNER = "dinner"
        const val SNACK = "snack"
    }
}
