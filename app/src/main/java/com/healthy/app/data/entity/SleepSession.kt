package com.healthy.app.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One stretch of actual sleep within a night (spec 4.2, extended).
 *
 * A night can hold more than one: a night and an afternoon nap are both real
 * sleep and do not overlap. The `night` row keeps one pair of times because a
 * row has one of everything, and those describe the longest sleep — so without
 * this table the shorter ones existed only as a number in a total, and the
 * screen could show the times of one sleep while claiming the duration of two.
 *
 * Deleted with the night, like the stage blocks, so a re-sync cannot leave a
 * sleep behind that no longer happened.
 */
@Entity(
    tableName = "sleep_session",
    foreignKeys = [
        ForeignKey(
            entity = Night::class,
            parentColumns = ["date"],
            childColumns = ["nightDate"],
            onDelete = ForeignKey.CASCADE,
        )
    ],
    indices = [Index("nightDate")],
)
data class SleepSession(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id") val id: Long = 0,

    @ColumnInfo(name = "nightDate") val nightDate: String,
    @ColumnInfo(name = "startTime") val startTime: Long,
    @ColumnInfo(name = "endTime") val endTime: Long,
) {
    val minutes: Int get() = ((endTime - startTime).coerceAtLeast(0L) / 60_000L).toInt()
}
