package com.healthy.app.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One block of one sleep stage, exactly as the watch wrote it (spec 4.2).
 *
 * The blocks are stored individually and never collapsed into totals. The
 * totals answer "how much deep sleep", which the watch is bad at. The block
 * times answer "when did the cycles fall", which is what section 18 needs to
 * lay the watch's guess against the measured heart rate curve.
 */
@Entity(
    tableName = "stage_block",
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
data class StageBlock(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id") val id: Long = 0,

    @ColumnInfo(name = "nightDate") val nightDate: String,
    /** deep, light, rem or awake. */
    @ColumnInfo(name = "type") val type: String,
    @ColumnInfo(name = "startTime") val startTime: Long,
    @ColumnInfo(name = "endTime") val endTime: Long,
) {
    companion object {
        const val DEEP = "deep"
        const val LIGHT = "light"
        const val REM = "rem"
        const val AWAKE = "awake"
    }
}
