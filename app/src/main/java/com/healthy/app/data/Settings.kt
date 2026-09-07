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
    /** Millilitres. No notification and no streak when below it (spec 9.3). */
    val fluidTargetMl: Int = DEFAULT_FLUID_TARGET_ML,
    /** A unit means different things by country, so both are settings (spec 9.4). */
    val unitsPerBeer: Double = DEFAULT_UNITS_PER_BEER,
    val unitsPerWine: Double = DEFAULT_UNITS_PER_WINE,
    /** none, hold or change (spec 8.5). The default is no goal. */
    val goalMode: String = GOAL_NONE,
    val goalHoldKg: Double? = null,
    val goalRateKgPerWeek: Double? = null,
    val goalStartedOn: String? = null,
) {
    companion object {
        const val DEFAULT_BEDTIME = "23:30"
        const val DEFAULT_FLUID_TARGET_ML = 2000
        const val DEFAULT_UNITS_PER_BEER = 1.7
        const val DEFAULT_UNITS_PER_WINE = 1.6
        const val GOAL_NONE = "none"
        const val GOAL_HOLD = "hold"
        const val GOAL_CHANGE = "change"
    }
}

private val Context.dataStore by preferencesDataStore(name = "healthy_settings")

class SettingsStore(private val context: Context) {

    val settings: Flow<HealthySettings> = context.dataStore.data.map { prefs ->
        HealthySettings(
            targetBedtime = prefs[KEY_BEDTIME] ?: HealthySettings.DEFAULT_BEDTIME,
            halfLifeHours = prefs[KEY_HALF_LIFE] ?: Caffeine.DEFAULT_HALF_LIFE_HOURS,
            bedtimeLimitMg = prefs[KEY_LIMIT] ?: Caffeine.DEFAULT_BEDTIME_LIMIT_MG,
            fluidTargetMl = prefs[KEY_FLUID_TARGET] ?: HealthySettings.DEFAULT_FLUID_TARGET_ML,
            unitsPerBeer = prefs[KEY_UNITS_BEER] ?: HealthySettings.DEFAULT_UNITS_PER_BEER,
            unitsPerWine = prefs[KEY_UNITS_WINE] ?: HealthySettings.DEFAULT_UNITS_PER_WINE,
            goalMode = prefs[KEY_GOAL_MODE] ?: HealthySettings.GOAL_NONE,
            goalHoldKg = prefs[KEY_GOAL_HOLD],
            goalRateKgPerWeek = prefs[KEY_GOAL_RATE],
            goalStartedOn = prefs[KEY_GOAL_STARTED],
        )
    }

    suspend fun setTargetBedtime(value: String) = edit { it[KEY_BEDTIME] = value }

    suspend fun setHalfLifeHours(value: Double) = edit { it[KEY_HALF_LIFE] = value }

    suspend fun setBedtimeLimitMg(value: Int) = edit { it[KEY_LIMIT] = value }

    suspend fun setFluidTargetMl(value: Int) = edit { it[KEY_FLUID_TARGET] = value }

    /** Clearing the goal removes its values rather than leaving them stale. */
    suspend fun setNoGoal() = edit {
        it[KEY_GOAL_MODE] = HealthySettings.GOAL_NONE
        it.remove(KEY_GOAL_HOLD)
        it.remove(KEY_GOAL_RATE)
        it.remove(KEY_GOAL_STARTED)
    }

    suspend fun setHoldGoal(targetKg: Double, startedOn: String) = edit {
        it[KEY_GOAL_MODE] = HealthySettings.GOAL_HOLD
        it[KEY_GOAL_HOLD] = targetKg
        it[KEY_GOAL_STARTED] = startedOn
        it.remove(KEY_GOAL_RATE)
    }

    suspend fun setChangeGoal(rateKgPerWeek: Double, startedOn: String) = edit {
        it[KEY_GOAL_MODE] = HealthySettings.GOAL_CHANGE
        it[KEY_GOAL_RATE] = rateKgPerWeek
        it[KEY_GOAL_STARTED] = startedOn
        it.remove(KEY_GOAL_HOLD)
    }

    private suspend fun edit(block: (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        context.dataStore.edit(block)
    }

    private companion object {
        val KEY_BEDTIME: Preferences.Key<String> = stringPreferencesKey("target_bedtime")
        val KEY_HALF_LIFE: Preferences.Key<Double> = doublePreferencesKey("half_life_hours")
        val KEY_LIMIT: Preferences.Key<Int> = intPreferencesKey("bedtime_limit_mg")
        val KEY_FLUID_TARGET: Preferences.Key<Int> = intPreferencesKey("fluid_target_ml")
        val KEY_UNITS_BEER: Preferences.Key<Double> = doublePreferencesKey("units_per_beer")
        val KEY_UNITS_WINE: Preferences.Key<Double> = doublePreferencesKey("units_per_wine")
        val KEY_GOAL_MODE: Preferences.Key<String> = stringPreferencesKey("goal_mode")
        val KEY_GOAL_HOLD: Preferences.Key<Double> = doublePreferencesKey("goal_hold_kg")
        val KEY_GOAL_RATE: Preferences.Key<Double> = doublePreferencesKey("goal_rate_kg_week")
        val KEY_GOAL_STARTED: Preferences.Key<String> = stringPreferencesKey("goal_started_on")
    }
}
