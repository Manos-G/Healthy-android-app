package com.healthy.app.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One logged caffeine dose (spec 4.3).
 *
 * `volumeMl` is not in the section 4.3 column list. It is required by section
 * 9.1: one tap on a catalog button logs the caffeine and the fluid together,
 * and the user must not have to log the same drink twice. Without a volume on
 * this row the fluid total cannot be derived. Flagged for review.
 *
 * The row keeps its own `mg` and `volumeMl` rather than pointing at a catalog
 * entry, so that editing a catalog drink later cannot silently rewrite history.
 */
@Entity(
    tableName = "drink",
    indices = [Index("timestamp")],
)
data class Drink(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id") val id: Long = 0,

    @ColumnInfo(name = "name") val name: String,
    @ColumnInfo(name = "mg") val mg: Int,
    @ColumnInfo(name = "timestamp") val timestamp: Long,

    /** Fluid volume in millilitres. Zero for a solid, such as dark chocolate. */
    @ColumnInfo(name = "volumeMl") val volumeMl: Int = 0,
)
