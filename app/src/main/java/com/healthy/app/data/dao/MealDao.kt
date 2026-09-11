package com.healthy.app.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import com.healthy.app.data.entity.MealEntry
import kotlinx.coroutines.flow.Flow

@Dao
interface MealDao {

    @Insert
    suspend fun insert(entry: MealEntry): Long

    @Insert
    suspend fun insertAll(entries: List<MealEntry>)

    @Delete
    suspend fun delete(entry: MealEntry)

    /** Correcting a portion or a meal already logged. */
    @androidx.room.Update
    suspend fun update(entry: MealEntry)

    @Query("SELECT * FROM meal_entry WHERE timestamp >= :from AND timestamp < :to ORDER BY timestamp ASC")
    fun observeBetween(from: Long, to: Long): Flow<List<MealEntry>>

    @Query("SELECT * FROM meal_entry WHERE timestamp >= :from AND timestamp < :to ORDER BY timestamp ASC")
    suspend fun between(from: Long, to: Long): List<MealEntry>

    /**
     * Fills the "last meal" field on the morning screen, so the user does not
     * type a time the app already knows (spec 12.6). The gap between this and
     * the sleep start is a strong input for sleep quality.
     */
    @Query("SELECT MAX(timestamp) FROM meal_entry WHERE timestamp >= :from AND timestamp < :to")
    suspend fun lastMealTime(from: Long, to: Long): Long?

    @Query("SELECT * FROM meal_entry ORDER BY timestamp ASC")
    suspend fun allForExport(): List<MealEntry>
}
