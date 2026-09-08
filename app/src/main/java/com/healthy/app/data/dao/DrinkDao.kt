package com.healthy.app.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.healthy.app.data.entity.Drink
import com.healthy.app.data.entity.RecentDrink
import kotlinx.coroutines.flow.Flow

/**
 * Queries are bounded by explicit epoch millis rather than by a date string,
 * because the logical day runs 04:00 to 04:00 (spec 4.4). The caller gets the
 * bounds from [com.healthy.app.core.HealthyDay].
 */
@Dao
interface DrinkDao {

    @Insert
    suspend fun insert(drink: Drink): Long

    @Delete
    suspend fun delete(drink: Drink)

    /** Correcting a logged drink in place, when the scanned values were wrong. */
    @Update
    suspend fun update(drink: Drink)

    @Query("SELECT * FROM drink WHERE id = :id")
    suspend fun byId(id: Long): Drink?

    @Query("DELETE FROM drink WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT * FROM drink WHERE timestamp >= :from AND timestamp < :to ORDER BY timestamp DESC")
    fun observeBetween(from: Long, to: Long): Flow<List<Drink>>

    @Query("SELECT * FROM drink WHERE timestamp >= :from AND timestamp < :to ORDER BY timestamp ASC")
    suspend fun between(from: Long, to: Long): List<Drink>

    /**
     * Every dose still decaying at [from]. The caffeine curve needs the doses
     * from before the window opened, or the level at 04:00 starts at zero when
     * it should not (spec 6).
     */
    @Query("SELECT * FROM drink WHERE timestamp >= :since AND timestamp <= :until ORDER BY timestamp ASC")
    fun observeDecayWindow(since: Long, until: Long): Flow<List<Drink>>

    @Query("SELECT COALESCE(SUM(mg), 0) FROM drink WHERE timestamp >= :from AND timestamp < :to")
    suspend fun totalMg(from: Long, to: Long): Int

    /** Fluid from caffeinated drinks. One tap logs both (spec 9.1). */
    @Query("SELECT COALESCE(SUM(volumeMl), 0) FROM drink WHERE timestamp >= :from AND timestamp < :to")
    fun observeVolumeMl(from: Long, to: Long): Flow<Int>

    @Query("SELECT * FROM drink ORDER BY timestamp ASC")
    suspend fun allForExport(): List<Drink>

    @Query("SELECT MAX(timestamp) FROM drink WHERE timestamp >= :from AND timestamp < :to")
    suspend fun lastDoseTime(from: Long, to: Long): Long?

    /**
     * The last few distinct drinks in one category, newest first.
     *
     * The category is read off the row rather than stored: a row with alcohol
     * units is a drink, a row with caffeine and none is a coffee, and a row
     * with neither is water. That keeps the recent lists working for every
     * drink already logged, with no migration and no back-fill.
     *
     * SQLite takes the bare columns from the row that supplied MAX(timestamp),
     * so the volume and strength returned are the ones last actually used.
     */
    @Query(
        """
        SELECT name, volumeMl, mg, alcoholUnits, MAX(timestamp) AS lastAt
        FROM drink
        WHERE CASE :category
            WHEN 'alcohol' THEN alcoholUnits > 0
            WHEN 'caffeine' THEN alcoholUnits <= 0 AND mg > 0
            ELSE alcoholUnits <= 0 AND mg <= 0
        END
        GROUP BY name
        ORDER BY lastAt DESC
        LIMIT :limit
        """
    )
    fun observeRecent(category: String, limit: Int): Flow<List<RecentDrink>>
}
