package com.healthy.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.healthy.app.data.converter.EditedFieldsConverter
import com.healthy.app.data.dao.CustomDrinkDao
import com.healthy.app.data.dao.DrinkDao
import com.healthy.app.data.dao.MealDao
import com.healthy.app.data.dao.NightDao
import com.healthy.app.data.dao.NoteDao
import com.healthy.app.data.dao.ProductDao
import com.healthy.app.data.dao.RecipeDao
import com.healthy.app.data.dao.WeightDao
import com.healthy.app.data.entity.CustomDrink
import com.healthy.app.data.entity.Drink
import com.healthy.app.data.entity.MealEntry
import com.healthy.app.data.entity.Night
import com.healthy.app.data.entity.Note
import com.healthy.app.data.entity.NoteFts
import com.healthy.app.data.entity.Product
import com.healthy.app.data.entity.Recipe
import com.healthy.app.data.entity.RecipeItem
import com.healthy.app.data.entity.StageBlock
import com.healthy.app.data.entity.Weight
import com.healthy.app.data.migration.Migrations

@Database(
    entities = [
        Night::class,
        StageBlock::class,
        Drink::class,
        CustomDrink::class,
        Weight::class,
        Product::class,
        MealEntry::class,
        Recipe::class,
        RecipeItem::class,
        Note::class,
        NoteFts::class,
    ],
    version = HealthyDatabase.VERSION,
    exportSchema = true,
)
@TypeConverters(EditedFieldsConverter::class)
abstract class HealthyDatabase : RoomDatabase() {

    abstract fun nightDao(): NightDao
    abstract fun drinkDao(): DrinkDao
    abstract fun customDrinkDao(): CustomDrinkDao
    abstract fun weightDao(): WeightDao
    abstract fun productDao(): ProductDao
    abstract fun mealDao(): MealDao
    abstract fun recipeDao(): RecipeDao
    abstract fun noteDao(): NoteDao

    companion object {
        const val VERSION = 2
        const val NAME = "healthy.db"

        @Volatile
        private var instance: HealthyDatabase? = null

        fun get(context: Context): HealthyDatabase =
            instance ?: synchronized(this) {
                instance ?: build(context.applicationContext).also { instance = it }
            }

        private fun build(context: Context): HealthyDatabase =
            Room.databaseBuilder(context, HealthyDatabase::class.java, NAME)
                .addMigrations(*Migrations.ALL)
                // No fallbackToDestructiveMigration. A missing migration must
                // throw on open rather than delete the user's history.
                // Room turns on foreign key enforcement itself, so the CASCADE
                // from night to stage_block needs no pragma here.
                .build()
    }
}
