package com.healthy.app.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.healthy.app.core.Caffeine
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * User settings (spec 5.4).
 *
 * These live in DataStore rather than Room. They are a handful of scalars with
 * no relationships and no history, so a table would add a migration burden for
 * nothing. The JSON export in step 17 reads them from here.
 */
data class HealthySettings(
    /** `HH:mm`. */
    val targetBedtime: String = DEFAULT_BEDTIME,
    val halfLifeHours: Double = Caffeine.DEFAULT_HALF_LIFE_HOURS,
    val bedtimeLimitMg: Int = Caffeine.DEFAULT_BEDTIME_LIMIT_MG,
) {
    companion object {
        const val DEFAULT_BEDTIME = "23:30"
    }
}

private val Context.dataStore by preferencesDataStore(name = "healthy_settings")

class SettingsStore(private val context: Context) {

    val settings: Flow<HealthySettings> = context.dataStore.data.map { prefs ->
        HealthySettings(
            targetBedtime = prefs[KEY_BEDTIME] ?: HealthySettings.DEFAULT_BEDTIME,
            halfLifeHours = prefs[KEY_HALF_LIFE] ?: Caffeine.DEFAULT_HALF_LIFE_HOURS,
            bedtimeLimitMg = prefs[KEY_LIMIT] ?: Caffeine.DEFAULT_BEDTIME_LIMIT_MG,
        )
    }

    suspend fun setTargetBedtime(value: String) = edit { it[KEY_BEDTIME] = value }

    suspend fun setHalfLifeHours(value: Double) = edit { it[KEY_HALF_LIFE] = value }

    suspend fun setBedtimeLimitMg(value: Int) = edit { it[KEY_LIMIT] = value }

    private suspend fun edit(block: (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        context.dataStore.edit(block)
    }

    private companion object {
        val KEY_BEDTIME: Preferences.Key<String> = stringPreferencesKey("target_bedtime")
        val KEY_HALF_LIFE: Preferences.Key<Double> = doublePreferencesKey("half_life_hours")
        val KEY_LIMIT: Preferences.Key<Int> = intPreferencesKey("bedtime_limit_mg")
    }
}
