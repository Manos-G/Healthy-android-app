package com.healthy.app.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.healthy.app.data.entity.Recipe
import com.healthy.app.data.entity.RecipeItem
import kotlinx.coroutines.flow.Flow

@Dao
interface RecipeDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertRecipe(recipe: Recipe): Long

    @Update
    suspend fun updateRecipe(recipe: Recipe)

    @Delete
    suspend fun deleteRecipe(recipe: Recipe)

    @Query("SELECT * FROM recipe WHERE id = :id")
    suspend fun recipe(id: Long): Recipe?

    /** Most recent first, so a repeated meal is one tap away (spec 13.6). */
    @Query("SELECT * FROM recipe ORDER BY id DESC")
    fun observeAll(): Flow<List<Recipe>>

    @Query("SELECT * FROM recipe ORDER BY id ASC")
    suspend fun allForExport(): List<Recipe>

    // --- items ------------------------------------------------------------

    @Insert
    suspend fun insertItems(items: List<RecipeItem>)

    @Query("SELECT * FROM recipe_item WHERE recipeId = :recipeId ORDER BY id ASC")
    suspend fun items(recipeId: Long): List<RecipeItem>

    @Query("SELECT * FROM recipe_item WHERE recipeId = :recipeId ORDER BY id ASC")
    fun observeItems(recipeId: Long): Flow<List<RecipeItem>>

    @Query("SELECT * FROM recipe_item ORDER BY id ASC")
    suspend fun allItemsForExport(): List<RecipeItem>

    @Query("DELETE FROM recipe_item WHERE recipeId = :recipeId")
    suspend fun deleteItems(recipeId: Long)

    /** Saves a recipe and its ingredient list as one unit. */
    @Transaction
    suspend fun saveRecipe(recipe: Recipe, items: List<RecipeItem>): Long {
        val id = upsertRecipe(recipe)
        deleteItems(id)
        insertItems(items.map { it.copy(recipeId = id) })
        return id
    }
}
