package com.healthy.app.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.healthy.app.data.entity.Night
import com.healthy.app.data.entity.StageBlock
import kotlinx.coroutines.flow.Flow

@Dao
interface NightDao {

    /** A save replaces any entry with the same date (spec 5.2). */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(night: Night)

    @Update
    suspend fun update(night: Night)

    @Query("SELECT * FROM night WHERE date = :date")
    suspend fun byDate(date: String): Night?

    @Query("SELECT * FROM night WHERE date = :date")
    fun observe(date: String): Flow<Night?>

    @Query("SELECT * FROM night ORDER BY date DESC")
    fun observeAll(): Flow<List<Night>>

    @Query("SELECT * FROM night ORDER BY date DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<Night>>

    @Query("SELECT * FROM night ORDER BY date ASC")
    suspend fun allForExport(): List<Night>

    /**
     * Nights that carry both ratings. The comparison table in section 5.3
     * needs 6 of these before it will draw anything.
     */
    @Query(
        """
        SELECT * FROM night
        WHERE alertness IS NOT NULL AND energy3pm IS NOT NULL
        ORDER BY date DESC
        """
    )
    fun observeRated(): Flow<List<Night>>

    @Query("SELECT COUNT(*) FROM night WHERE alertness IS NOT NULL AND energy3pm IS NOT NULL")
    fun observeRatedCount(): Flow<Int>

    /**
     * The night waiting for its afternoon rating (spec 5.2).
     *
     * The user cannot know their 15:00 energy while it is still morning, so
     * the morning save leaves `energy3pm` empty and the Today screen collects
     * it later. This finds the most recent night that was rated in the morning
     * but never got its second rating.
     */
    @Query(
        """
        SELECT * FROM night
        WHERE alertness IS NOT NULL AND energy3pm IS NULL
        ORDER BY date DESC
        LIMIT 1
        """
    )
    fun observeAwaitingEnergyRating(): Flow<Night?>

    @Query("UPDATE night SET energy3pm = :energy WHERE date = :date")
    suspend fun setEnergy3pm(date: String, energy: Int)

    /** True when the notification job has already recorded this night (spec 14.2). */
    @Query("SELECT EXISTS(SELECT 1 FROM night WHERE date = :date)")
    suspend fun exists(date: String): Boolean

    @Query("DELETE FROM night WHERE date = :date")
    suspend fun delete(date: String)

    // --- stage blocks -----------------------------------------------------

    @Query("SELECT * FROM stage_block WHERE nightDate = :date ORDER BY startTime ASC")
    suspend fun stageBlocks(date: String): List<StageBlock>

    @Query("SELECT * FROM stage_block WHERE nightDate = :date ORDER BY startTime ASC")
    fun observeStageBlocks(date: String): Flow<List<StageBlock>>

    @Insert
    suspend fun insertStageBlocks(blocks: List<StageBlock>)

    @Query("DELETE FROM stage_block WHERE nightDate = :date")
    suspend fun deleteStageBlocks(date: String)

    /**
     * A sync deletes the blocks for that night and writes the new ones, in one
     * transaction, so a re-sync cannot leave duplicates behind (spec 4.2).
     */
    @Transaction
    suspend fun replaceStageBlocks(date: String, blocks: List<StageBlock>) {
        deleteStageBlocks(date)
        insertStageBlocks(blocks)
    }

    /** Writes the night and its blocks together. Used by the sync in step 5. */
    @Transaction
    suspend fun saveNightWithStages(night: Night, blocks: List<StageBlock>) {
        upsert(night)
        replaceStageBlocks(night.date, blocks)
    }
}
