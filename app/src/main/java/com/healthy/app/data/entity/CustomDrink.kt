package com.healthy.app.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A drink the user added themselves (spec 7).
 *
 * The built-in drinks are constants in
 * [com.healthy.app.core.BeverageCatalog]; only the user's own additions need
 * storage. Both appear together in the catalog grid.
 */
@Entity(tableName = "custom_drink")
data class CustomDrink(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id") val id: Long = 0,

    @ColumnInfo(name = "name") val name: String,
    @ColumnInfo(name = "mg") val mg: Int,
    @ColumnInfo(name = "volumeMl") val volumeMl: Int,
)
