package com.healthy.app.data.dao

import androidx.room.Dao
import androidx.room.Query

/**
 * Wholesale deletes, used only by the replace-all import (spec 5.4).
 *
 * Kept apart from the feature DAOs so that a destructive statement is never a
 * keystroke away from an ordinary query, and so the one place that can empty
 * the database is easy to find.
 */
@Dao
interface MaintenanceDao {

    // Children first: stage_block and recipe_item hang off their parents.
    @Query("DELETE FROM stage_block") suspend fun clearStageBlocks()
    @Query("DELETE FROM recipe_item") suspend fun clearRecipeItems()
    @Query("DELETE FROM night") suspend fun clearNights()
    @Query("DELETE FROM drink") suspend fun clearDrinks()
    @Query("DELETE FROM custom_drink") suspend fun clearCustomDrinks()
    @Query("DELETE FROM weight") suspend fun clearWeights()
    @Query("DELETE FROM product") suspend fun clearProducts()
    @Query("DELETE FROM meal_entry") suspend fun clearMeals()
    @Query("DELETE FROM recipe") suspend fun clearRecipes()
    @Query("DELETE FROM note") suspend fun clearNotes()
}
