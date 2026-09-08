package com.healthy.app.data.entity

import androidx.room.ColumnInfo

/**
 * A drink the user reached for recently, with the amount they poured.
 *
 * Not a table — a projection over `drink`, so the recent list is always the
 * truth of what was logged rather than a second copy that can fall behind.
 */
data class RecentDrink(
    @ColumnInfo(name = "name") val name: String,
    @ColumnInfo(name = "volumeMl") val volumeMl: Int,
    @ColumnInfo(name = "mg") val mg: Int,
    @ColumnInfo(name = "alcoholUnits") val alcoholUnits: Double,
    @ColumnInfo(name = "lastAt") val lastAt: Long,
)
