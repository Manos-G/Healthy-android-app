package com.healthy.app.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import com.healthy.app.data.entity.CustomDrink
import kotlinx.coroutines.flow.Flow

@Dao
interface CustomDrinkDao {

    @Insert
    suspend fun insert(drink: CustomDrink): Long

    @Delete
    suspend fun delete(drink: CustomDrink)

    @Query("SELECT * FROM custom_drink ORDER BY name ASC")
    fun observeAll(): Flow<List<CustomDrink>>

    @Query("SELECT * FROM custom_drink ORDER BY name ASC")
    suspend fun allForExport(): List<CustomDrink>
}
