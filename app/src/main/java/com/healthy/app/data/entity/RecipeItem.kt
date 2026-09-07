package com.healthy.app.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One ingredient of one recipe, at its weight before cooking (spec 13.2).
 *
 * `childRecipeId` is not in the 13.2 column list. Section 13.6 requires a
 * recipe to hold another recipe as an ingredient, to a depth of two levels:
 * a sauce is a recipe and a pasta dish uses the sauce. Without this column
 * that requirement has nowhere to live. Flagged for review.
 */
@Entity(
    tableName = "recipe_item",
    foreignKeys = [
        ForeignKey(
            entity = Recipe::class,
            parentColumns = ["id"],
            childColumns = ["recipeId"],
            onDelete = ForeignKey.CASCADE,
        )
    ],
    indices = [Index("recipeId"), Index("barcode"), Index("childRecipeId")],
)
data class RecipeItem(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id") val id: Long = 0,

    @ColumnInfo(name = "recipeId") val recipeId: Long,
    @ColumnInfo(name = "barcode") val barcode: String? = null,
    /** Another recipe used as an ingredient. Depth is capped at 2 levels. */
    @ColumnInfo(name = "childRecipeId") val childRecipeId: Long? = null,
    @ColumnInfo(name = "name") val name: String,
    @ColumnInfo(name = "grams") val grams: Double,
)
