package com.healthy.app.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * One row for each night (spec 4.1).
 *
 * The primary key is the date the sleep *started*, as `YYYY-MM-DD`. A session
 * that runs 02:38 to 13:49 belongs to the day that began at the boundary before
 * afternoon, so the key comes from [com.healthy.app.core.HealthyDay.dayOf] and
 * never from the raw calendar date of the start instant.
 *
 * Nullable sensor columns mean "not reported", which is not the same as zero.
 * A wrist device can write stage data with no awake blocks at all; the UI shows
 * "not reported" for a null and a number for a zero (spec 3.3).
 */
@Entity(tableName = "night")
data class Night(
    @PrimaryKey
    @ColumnInfo(name = "date")
    val date: String,

    @ColumnInfo(name = "sleepStart") val sleepStart: Long,
    @ColumnInfo(name = "sleepEnd") val sleepEnd: Long,
    @ColumnInfo(name = "minutes") val minutes: Int,

    /**
     * How many separate sleeps [minutes] was added up from.
     *
     * More than one means a nap as well as a night, and that [minutes] is
     * their sum rather than the span from [sleepStart] to [sleepEnd] — which
     * would count the waking hours in between.
     */
    @ColumnInfo(name = "sleepCount", defaultValue = "1") val sleepCount: Int = 1,

    // From Health Connect. Null when the night has no stage list at all.
    @ColumnInfo(name = "deepMin") val deepMin: Int? = null,
    @ColumnInfo(name = "lightMin") val lightMin: Int? = null,
    @ColumnInfo(name = "remMin") val remMin: Int? = null,
    @ColumnInfo(name = "awakeMin") val awakeMin: Int? = null,

    /** Count of awake blocks. Null means the device reported none at all. */
    @ColumnInfo(name = "wakeups") val wakeups: Int? = null,

    /** 5th percentile of the beats-per-minute samples inside the window. */
    @ColumnInfo(name = "restingHr") val restingHr: Int? = null,
    @ColumnInfo(name = "spo2") val spo2: Double? = null,

    // Typed by the user. Null until the night is rated.
    @ColumnInfo(name = "alertness") val alertness: Int? = null,
    @ColumnInfo(name = "energy3pm") val energy3pm: Int? = null,

    @ColumnInfo(name = "alcoholUnits") val alcoholUnits: Double? = null,
    /** `HH:mm`. Filled from the previous day's last meal entry (spec 12.6). */
    @ColumnInfo(name = "lastMeal") val lastMeal: String? = null,
    /** none, light or hard. */
    @ColumnInfo(name = "exercise") val exercise: String? = null,
    @ColumnInfo(name = "roomTempC") val roomTempC: Double? = null,
    @ColumnInfo(name = "notes") val notes: String = "",

    /** Field names the user edited after a sync. A sync must not overwrite these. */
    @ColumnInfo(name = "editedFields") val editedFields: Set<String> = emptySet(),
)
