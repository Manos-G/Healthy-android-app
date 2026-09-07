package com.healthy.app.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A dish the user cooks more than once (spec 13.2).
 *
 * `cookedGrams` is the weight of the finished dish, not the sum of the raw
 * ingredients. A stew loses water and a pot of rice gains it, so without the
 * cooked weight every portion logged from this recipe is wrong (spec 13.4).
 */
@Entity(tableName = "recipe")
data class Recipe(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id") val id: Long = 0,

    @ColumnInfo(name = "name") val name: String,
    @ColumnInfo(name = "cookedGrams") val cookedGrams: Double,
    @ColumnInfo(name = "portions") val portions: Int,
)
