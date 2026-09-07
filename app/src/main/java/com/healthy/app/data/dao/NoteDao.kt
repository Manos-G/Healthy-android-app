package com.healthy.app.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.healthy.app.data.entity.Note
import kotlinx.coroutines.flow.Flow

@Dao
interface NoteDao {

    @Insert
    suspend fun insert(note: Note): Long

    @Update
    suspend fun update(note: Note)

    @Delete
    suspend fun delete(note: Note)

    @Query("SELECT * FROM note ORDER BY date DESC, createdAt DESC")
    fun observeAll(): Flow<List<Note>>

    @Query("SELECT * FROM note WHERE date = :date ORDER BY createdAt DESC")
    fun observeByDate(date: String): Flow<List<Note>>

    /** Feeds the calendar dots. One row for each day that holds a note (spec 15.4). */
    @Query("SELECT DISTINCT date FROM note WHERE date BETWEEN :from AND :to")
    fun observeDatesWithNotes(from: String, to: String): Flow<List<String>>

    /**
     * Full-text search, run as the user types (spec 15.4).
     *
     * The MATCH runs against the FTS index and the join pulls the real rows,
     * so the result carries the id and date the carousel needs.
     */
    @Query(
        """
        SELECT note.* FROM note
        JOIN note_fts ON note.id = note_fts.rowid
        WHERE note_fts MATCH :query
        ORDER BY note.date DESC, note.createdAt DESC
        """
    )
    fun search(query: String): Flow<List<Note>>

    @Query("SELECT * FROM note ORDER BY date ASC, createdAt ASC")
    suspend fun allForExport(): List<Note>
}
