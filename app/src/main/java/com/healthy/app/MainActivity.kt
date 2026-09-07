package com.healthy.app

import android.app.Activity
import android.os.Bundle
import android.util.Log
import com.healthy.app.data.HealthyDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Step 1 has no user interface. The build order puts the Today screen in step 2.
 *
 * This activity exists so the APK installs and runs, which is what forces Room
 * to create the database file on the phone. It opens the database, reads the
 * schema back out of SQLite and writes it to logcat under the tag [TAG], so the
 * tables and columns can be checked on the real device rather than assumed.
 */
class MainActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        dumpSchema()
    }

    private fun dumpSchema() {
        CoroutineScope(Dispatchers.IO).launch {
            runCatching {
                val db = HealthyDatabase.get(applicationContext).openHelper.readableDatabase

                Log.i(TAG, "database=${HealthyDatabase.NAME} version=${db.version}")

                db.query(
                    "SELECT name FROM sqlite_master WHERE type='table' " +
                        "AND name NOT LIKE 'sqlite_%' AND name NOT LIKE 'android_%' " +
                        "AND name NOT LIKE 'room_%' ORDER BY name"
                ).use { tables ->
                    while (tables.moveToNext()) {
                        val table = tables.getString(0)
                        val columns = buildList {
                            db.query("PRAGMA table_info('$table')").use { info ->
                                // 1 = name, 2 = declared type, 3 = notnull, 5 = pk
                                while (info.moveToNext()) {
                                    val nn = if (info.getInt(3) == 1) " NOT NULL" else ""
                                    val pk = if (info.getInt(5) > 0) " PK" else ""
                                    add("${info.getString(1)}:${info.getString(2)}$nn$pk")
                                }
                            }
                        }
                        Log.i(TAG, "$table(${columns.joinToString(", ")})")
                    }
                }
            }.onFailure { Log.e(TAG, "schema dump failed", it) }
        }
    }

    companion object {
        const val TAG = "HealthySchema"
    }
}
