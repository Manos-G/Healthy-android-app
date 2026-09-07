package com.healthy.app.data.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * The migration strategy for this app.
 *
 * The rules, in order of importance:
 *
 * 1. **Never destroy data.** `fallbackToDestructiveMigration` is not called
 *    anywhere. START-HERE requires that export works at every stage of the
 *    build, and the user owns the data. A schema change that has no migration
 *    must fail loudly at open time, not silently wipe the phone.
 *
 * 2. **The schema JSON is committed.** `room.schemaLocation` writes
 *    `app/schemas/<version>.json` on every build. A change to an entity that
 *    the author forgot to migrate shows up as an uncommitted schema diff.
 *
 * 3. **One version for each build step.** The build order in section 21 adds
 *    tables step by step. Each step that changes the schema bumps
 *    [com.healthy.app.data.HealthyDatabase.VERSION] by one and appends a
 *    [Migration] to [ALL]. Migrations are additive: new tables and new
 *    nullable columns. A column is not dropped and not renamed, because both
 *    require a table rebuild that can lose data on a partial failure.
 *
 * 4. **Every migration gets a test.** `MigrationTest` walks the whole chain
 *    against the exported schemas before the build is called done.
 */
object Migrations {

    /**
     * Step 2 adds the `custom_drink` table (spec 7). Purely additive: no
     * existing table is touched, so no data can be lost.
     */
    val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `custom_drink` (
                    `id` INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
                    `name` TEXT NOT NULL,
                    `mg` INTEGER NOT NULL,
                    `volumeMl` INTEGER NOT NULL
                )
                """.trimIndent()
            )
        }
    }

    val ALL: Array<Migration> = arrayOf(MIGRATION_1_2)
}
