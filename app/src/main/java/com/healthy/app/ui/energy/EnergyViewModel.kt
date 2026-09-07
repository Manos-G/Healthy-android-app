package com.healthy.app.ui.energy

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.healthy.app.analysis.Energy
import com.healthy.app.analysis.IntakeHistory
import com.healthy.app.analysis.WeightTrend
import com.healthy.app.core.HealthyDay
import com.healthy.app.data.HealthyDatabase
import com.healthy.app.data.HealthySettings
import com.healthy.app.data.SettingsStore
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class EnergyState(
    val plan: Energy.Plan? = null,
    val consumedTodayKcal: Int = 0,
    val percentOfTarget: Int? = null,
    /** Days of complete logging behind the measured figure. */
    val loggedDays: Int = 0,
    val neededDays: Int = Energy.WINDOW_DAYS,
    val calculatedAt: Long? = null,
    val message: String? = null,
    val canMeasure: Boolean = false,
    val missingBody: Boolean = false,
)

@OptIn(ExperimentalCoroutinesApi::class)
class EnergyViewModel(app: Application) : AndroidViewModel(app) {

    private val db = HealthyDatabase.get(app)
    private val settingsStore = SettingsStore(app)
    private val _message = MutableStateFlow<String?>(null)
    private val recomputed = MutableStateFlow(0)

    val state: StateFlow<EnergyState> = combine(
        settingsStore.settings,
        db.weightDao().observeAscending(),
        _message,
        recomputed,
    ) { settings, weights, message, _ ->
        val currentKg = weights.lastOrNull()?.weightKg
        val basal = basalFor(settings, currentKg)

        val stored = settings.maintenanceKcal?.let {
            Energy.Maintenance(
                kcal = it,
                source = if (settings.maintenanceMeasured) Energy.Source.Measured else Energy.Source.Estimated,
                days = settings.maintenanceDays,
            )
        }

        val rate = if (settings.goalMode == HealthySettings.GOAL_CHANGE) {
            settings.goalRateKgPerWeek ?: 0.0
        } else {
            0.0
        }

        val plan = if (stored != null && basal != null) {
            Energy.plan(stored, rate, basal, currentKg, settings.goalTargetKg)
        } else {
            null
        }

        val todayKcal = todayKcal()
        EnergyState(
            plan = plan,
            consumedTodayKcal = todayKcal,
            percentOfTarget = Energy.percentOfTarget(todayKcal.toDouble(), plan?.targetKcal),
            loggedDays = completeDays().size,
            calculatedAt = settings.maintenanceCalculatedAt,
            message = message,
            canMeasure = completeDays().size >= Energy.WINDOW_DAYS && weights.size >= 2,
            missingBody = settings.heightCm == null || settings.ageYears == null || settings.sexMale == null,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), EnergyState())

    /**
     * Recalculates maintenance from the user's own data (spec 16.1).
     *
     * Measured wherever the window allows it, and clearly an estimate where it
     * does not. The two are never mixed: one number is shown and it says where
     * it came from.
     */
    fun recalculate() {
        viewModelScope.launch {
            val settings = settingsStore.settings.first()
            val weights = db.weightDao().observeAscending().first()
            val days = completeDays()

            val points = WeightTrend.series(weights)
            val trendChange = WeightTrend.changeOverDays(points, Energy.WINDOW_DAYS)

            val measured = if (days.size >= Energy.WINDOW_DAYS && trendChange != null) {
                Energy.measuredMaintenance(days.map { it.kcal }, trendChange)
            } else {
                null
            }

            if (measured != null) {
                settingsStore.setMaintenance(measured.kcal, measured = true, days = measured.days)
                _message.value = "Measured from your last ${measured.days} days: " +
                    "${measured.kcal} kcal to hold your weight."
                return@launch
            }

            val currentKg = weights.lastOrNull()?.weightKg
            if (currentKg == null || settings.heightCm == null ||
                settings.ageYears == null || settings.sexMale == null
            ) {
                _message.value = "Add your height, age and sex below, and log a weight, " +
                    "so the app has a starting estimate to use until it can measure."
                return@launch
            }

            val estimate = Energy.mifflinStJeor(
                weightKg = currentKg,
                heightCm = settings.heightCm,
                ageYears = settings.ageYears,
                sex = if (settings.sexMale) Energy.Sex.Male else Energy.Sex.Female,
            )
            settingsStore.setMaintenance(estimate.kcal, measured = false, days = 0)
            _message.value = "Estimated at ${estimate.kcal} kcal from the Mifflin-St Jeor " +
                "formula, which is often 300 kcal out. Log ${Energy.WINDOW_DAYS} full days " +
                "of food and it will be measured instead, from your own numbers."
        }
    }

    fun setBody(heightCm: Double?, ageYears: Int?, sexMale: Boolean?) {
        viewModelScope.launch {
            settingsStore.setBody(heightCm, ageYears, sexMale)
            recomputed.value++
        }
    }

    fun setGoalTarget(kg: Double) {
        viewModelScope.launch {
            settingsStore.setGoalTargetKg(kg)
            recomputed.value++
        }
    }

    fun clearMessage() {
        _message.value = null
    }

    private suspend fun basalFor(settings: HealthySettings, currentKg: Double?): Int? {
        if (currentKg == null || settings.heightCm == null ||
            settings.ageYears == null || settings.sexMale == null
        ) {
            // Without a basal rate there is no floor to protect the target, so
            // no plan is offered at all rather than one that could go too low.
            return null
        }
        return Energy.basalRate(
            weightKg = currentKg,
            heightCm = settings.heightCm,
            ageYears = settings.ageYears,
            sex = if (settings.sexMale) Energy.Sex.Male else Energy.Sex.Female,
        )
    }

    private suspend fun completeDays(): List<IntakeHistory.Day> {
        val from = HealthyDay.startOf(HealthyDay.plusDays(HealthyDay.today(), -(Energy.WINDOW_DAYS + 7L)))
        val meals = db.mealDao().between(from, System.currentTimeMillis())
        val products = db.productDao().allForExport().associateBy { it.barcode }
        return IntakeHistory.completeDays(IntakeHistory.byDay(meals, products))
            .takeLast(Energy.WINDOW_DAYS)
    }

    private suspend fun todayKcal(): Int {
        val today = HealthyDay.today()
        val meals = db.mealDao().between(HealthyDay.startOf(today), HealthyDay.endOf(today))
        val products = db.productDao().allForExport().associateBy { it.barcode }
        return com.healthy.app.analysis.Nutrition.totalFor(meals, products).kcal.toInt()
    }
}
