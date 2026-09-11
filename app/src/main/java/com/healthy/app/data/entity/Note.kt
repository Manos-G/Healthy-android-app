package com.healthy.app.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Fts4
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A short health note attached to a date (spec 15.2).
 *
 * A note such as "sore throat" explains a bad night better than caffeine does,
 * so the text reaches the CSV export and the comparison analysis.
 */
@Entity(
    tableName = "note",
    indices = [Index("date")],
)
data class Note(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id") val id: Long = 0,

    /** `YYYY-MM-DD`, on the logical day boundary. */
    @ColumnInfo(name = "date") val date: String,
    @ColumnInfo(name = "text") val text: String,
    @ColumnInfo(name = "createdAt") val createdAt: Long,
)

/**
 * Full-text index over [Note.text] (spec 15.2), so the notes screen can search
 * as the user types.
 *
 * `contentEntity` makes this an external-content FTS table: the text is not
 * stored twice, and the index reads through to `note`. Room keeps the two in
 * step on insert, update and delete.
 */
@Fts4(contentEntity = Note::class)
@Entity(tableName = "note_fts")
data class NoteFts(
    @ColumnInfo(name = "text") val text: String,
)
