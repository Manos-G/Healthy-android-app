package com.healthy.app.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * One weight reading for one logical day (spec 8).
 *
 * The primary key is the date, not an id. Section 8.4 requires an OpenScale
 * CSV import to skip a row whose date is already stored, and a date key makes
 * that a plain insert-ignore rather than a scan.
 *
 * Every body composition column is nullable (spec 8.4). A dumb scale fills only
 * `weightKg`. The UI hides a field with no data and never renders a zero, since
 * a zero body fat percentage is a false measurement rather than a missing one.
 */
@Entity(tableName = "weight")
data class Weight(
    @PrimaryKey
    @ColumnInfo(name = "date") val date: String,

    @ColumnInfo(name = "weightKg") val weightKg: Double,
    /** When the reading was taken. The date column is the logical day it counts for. */
    @ColumnInfo(name = "timestamp") val timestamp: Long,

    @ColumnInfo(name = "bodyFatPct") val bodyFatPct: Double? = null,
    @ColumnInfo(name = "waterPct") val waterPct: Double? = null,
    @ColumnInfo(name = "musclePct") val musclePct: Double? = null,
    @ColumnInfo(name = "boneKg") val boneKg: Double? = null,
    @ColumnInfo(name = "visceralFat") val visceralFat: Double? = null,

    /** manual, healthconnect or openscale. */
    @ColumnInfo(name = "source") val source: String = MANUAL,
) {
    companion object {
        const val MANUAL = "manual"
        const val HEALTH_CONNECT = "healthconnect"
        const val OPENSCALE = "openscale"
    }
}
