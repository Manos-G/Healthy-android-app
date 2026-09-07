package com.healthy.app.data.migration

import androidx.room.migration.Migration

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
 *
 * Version 1 creates every table in one go, so the list below is empty. It
 * stays here so that step 2 has an obvious place to add the first entry.
 */
object Migrations {

    val ALL: Array<Migration> = arrayOf()
}
