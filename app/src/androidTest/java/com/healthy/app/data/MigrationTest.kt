package com.healthy.app.data

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.healthy.app.data.migration.Migrations
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

    /**
     * Opens the current schema through the real builder and runs every
     * migration in [Migrations.ALL]. Empty today; the guard matters later.
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
