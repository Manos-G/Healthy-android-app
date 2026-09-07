package com.healthy.app.data

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.healthy.app.data.migration.Migrations
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException

/**
 * Walks the migration chain against the schema JSON committed in `app/schemas`.
 *
 * At version 1 this proves only that the schema was exported and that the
 * database opens from it. From step 2 onward, each new version appends a case
 * here, so an upgrade path is never shipped untested. This is the test that
 * makes "no destructive fallback" a safe policy rather than a slogan.
 */
@RunWith(AndroidJUnit4::class)
class MigrationTest {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        HealthyDatabase::class.java,
    )

    @Test
    @Throws(IOException::class)
    fun schemaAtVersion1Opens() {
        helper.createDatabase(TEST_DB, 1).close()
    }

    /** Step 2 adds `custom_drink`. The upgrade must keep existing rows. */
    @Test
    @Throws(IOException::class)
    fun migration1To2AddsCustomDrinkAndKeepsData() {
        helper.createDatabase(TEST_DB, 1).apply {
            execSQL(
                "INSERT INTO drink (name, mg, timestamp, volumeMl) " +
                    "VALUES ('Freddo espresso', 125, 1772000000000, 200)"
            )
            close()
        }

        val db = helper.runMigrationsAndValidate(TEST_DB, 2, true, Migrations.MIGRATION_1_2)

        db.query("SELECT name, mg FROM drink").use { c ->
            assertTrue("the pre-migration drink must survive", c.moveToFirst())
            assertEquals("Freddo espresso", c.getString(0))
            assertEquals(125, c.getInt(1))
        }
        db.query("SELECT COUNT(*) FROM custom_drink").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals(0, c.getInt(0))
        }
        db.close()
    }

    /** Step 7 adds drink.alcoholUnits. Existing drinks must read as zero. */
    @Test
    @Throws(IOException::class)
    fun migration2To3AddsAlcoholUnitsWithoutLosingDrinks() {
        helper.createDatabase(TEST_DB, 1).apply {
            execSQL(
                "INSERT INTO drink (name, mg, timestamp, volumeMl) " +
                    "VALUES ('Freddo espresso', 125, 1772000000000, 200)"
            )
            close()
        }
        helper.runMigrationsAndValidate(TEST_DB, 2, true, Migrations.MIGRATION_1_2).close()
        val db = helper.runMigrationsAndValidate(TEST_DB, 3, true, Migrations.MIGRATION_2_3)

        db.query("SELECT name, mg, alcoholUnits FROM drink").use { c ->
            assertTrue("the drink must survive both migrations", c.moveToFirst())
            assertEquals("Freddo espresso", c.getString(0))
            assertEquals(125, c.getInt(1))
            assertEquals("a coffee has no alcohol", 0.0, c.getDouble(2), 0.0001)
        }
        db.close()
    }

    /**
     * Opens the current schema through the real builder and runs every
     * migration in [Migrations.ALL].
     */
    @Test
    @Throws(IOException::class)
    fun migratesToLatest() {
        helper.createDatabase(TEST_DB, 1).close()
        helper.runMigrationsAndValidate(
            TEST_DB,
            HealthyDatabase.VERSION,
            true,
            *Migrations.ALL,
        ).close()
    }

    private companion object {
        const val TEST_DB = "migration-test.db"
    }
}
