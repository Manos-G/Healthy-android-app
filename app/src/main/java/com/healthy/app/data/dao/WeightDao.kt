package com.healthy.app.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.healthy.app.data.entity.Weight
import kotlinx.coroutines.flow.Flow

@Dao
interface WeightDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(weight: Weight)

    /**
     * An OpenScale CSV import skips a date already stored (spec 8.4). IGNORE
     * rather than REPLACE, so a re-import cannot overwrite a value the user
     * has since corrected by hand.
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnoringExisting(weights: List<Weight>): List<Long>

    @Query("SELECT * FROM weight WHERE date = :date")
    suspend fun byDate(date: String): Weight?

    /** Seeds the entry wheel, which starts at the previous weight (spec 8.1). */
    @Query("SELECT * FROM weight ORDER BY date DESC LIMIT 1")
    suspend fun mostRecent(): Weight?

    @Query("SELECT * FROM weight ORDER BY date DESC LIMIT 1")
    fun observeMostRecent(): Flow<Weight?>

    /** Ascending, because the trend is a forward recurrence over the series (spec 8.2). */
    @Query("SELECT * FROM weight ORDER BY date ASC")
    fun observeAscending(): Flow<List<Weight>>

    @Query("SELECT * FROM weight WHERE date >= :from ORDER BY date ASC")
    fun observeSince(from: String): Flow<List<Weight>>

    @Query("SELECT * FROM weight ORDER BY date ASC")
    suspend fun allForExport(): List<Weight>

    @Query("SELECT COUNT(*) FROM weight")
    suspend fun count(): Int

    @Query("DELETE FROM weight WHERE date = :date")
    suspend fun delete(date: String)
}
