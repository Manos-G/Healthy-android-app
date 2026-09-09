package com.healthy.app.ui.energy

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.healthy.app.analysis.Energy
import com.healthy.app.analysis.IntakeHistory
import com.healthy.app.analysis.Nutrition
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
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
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
    /** Needed for the protein floor, which is stated per kilogram. */
    val bodyWeightKg: Double? = null,
    /**
     * The one weight goal, shown here as well as on the Weight tab. It was
     * split across the two screens and neither showed the whole plan.
     */
    val goalTargetKg: Double? = null,
    val goalRateKgPerWeek: Double? = null,
    val heightCm: Double? = null,
    val ageYears: Int? = null,
    val sexMale: Boolean? = null,
)

@OptIn(ExperimentalCoroutinesApi::class)
class EnergyViewModel(app: Application) : AndroidViewModel(app) {

    private val db = HealthyDatabase.get(app)
    private val settingsStore = SettingsStore(app)
    private val _message = MutableStateFlow<String?>(null)
    private val recomputed = MutableStateFlow(0)

    /**
     * The logical day, re-checked on a slow tick.
     *
     * `distinctUntilChanged` means the queries downstream restart only when
     * the day actually rolls at 04:00, so the cost of the tick is a string
     * comparison a minute and nothing else.
     */
    private val day: kotlinx.coroutines.flow.Flow<String> = kotlinx.coroutines.flow.flow {
        while (true) {
            emit(HealthyDay.today())
            kotlinx.coroutines.delay(60_000)
        }
    }.distinctUntilChanged()

    /**
     * The card has to watch the meal table, not merely the settings.
     *
     * It did not, and the consequence was on screen: the totals card said
     * 121 kcal while this one still said 0, because `combine` had no reason to
     * re-run — logging a meal changes no setting and no weight. Observing the
     * day's meals is what makes the number live.
     */
    val state: StateFlow<EnergyState> = combine(
        settingsStore.settings,
        db.weightDao().observeAscending(),
        _message,
        recomputed,
        day,
    ) { settings, weights, message, _, today -> Sources(settings, weights, message, today) }
        .flatMapLatest { (settings, weights, message, today) ->
            db.mealDao()
                .observeBetween(HealthyDay.startOf(today), HealthyDay.endOf(today))
                .map { mealsToday -> build(settings, weights, message, today, mealsToday) }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), EnergyState())

    private data class Sources(
        val settings: HealthySettings,
        val weights: List<com.healthy.app.data.entity.Weight>,
        val message: String?,
        val today: String,
    )

    private suspend fun build(
        settings: HealthySettings,
        weights: List<com.healthy.app.data.entity.Weight>,
        message: String?,
        today: String,
        mealsToday: List<com.healthy.app.data.entity.MealEntry>,
    ): EnergyState {
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

        val products = db.productDao().allForExport().associateBy { it.barcode }
        // Portions of a recipe carry a recipeId and no barcode. Without this
        // the day's energy silently ignored every home-cooked meal.
        val dishes = com.healthy.app.analysis.Dishes.per100g(db, products)
        val todayKcal = Nutrition.totalFor(mealsToday, products, dishes).kcal.toInt()
        // Computed once. It was being queried twice per emission, for the
        // count and again for the comparison.
        val logged = completeDays().size

        return EnergyState(
            plan = plan,
            consumedTodayKcal = todayKcal,
            percentOfTarget = Energy.percentOfTarget(todayKcal.toDouble(), plan?.targetKcal),
            loggedDays = logged,
            calculatedAt = settings.maintenanceCalculatedAt,
            message = message,
            canMeasure = logged >= Energy.WINDOW_DAYS && weights.size >= 2,
            missingBody = settings.heightCm == null || settings.ageYears == null || settings.sexMale == null,
            bodyWeightKg = currentKg,
            goalTargetKg = settings.goalTargetKg,
            goalRateKgPerWeek = settings.goalRateKgPerWeek.takeIf {
                settings.goalMode == HealthySettings.GOAL_CHANGE
            },
            heightCm = settings.heightCm,
            ageYears = settings.ageYears,
            sexMale = settings.sexMale,
        )
    }

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

    /**
     * Saves the body figures and immediately recalculates.
     *
     * Without this the user filled three boxes, pressed Save, and nothing
     * appeared — the number only arrived after a separate Recalculate they had
     * no reason to expect.
     */
    fun setBody(heightCm: Double?, ageYears: Int?, sexMale: Boolean?) {
        viewModelScope.launch {
            settingsStore.setBody(heightCm, ageYears, sexMale)
            recomputed.value++
            recalculate()
        }
    }

    /**
     * The weight goal, which is one goal however it is reached.
     *
     * It was split between two screens: the rate lived on the Weight tab and
     * the goal weight on the Food tab, so neither screen showed the whole
     * plan. Both now write the same settings and both show the same result.
     */
    fun setGoal(rateKgPerWeek: Double?, targetKg: Double?) {
        viewModelScope.launch {
            val weights = db.weightDao()
            val bodyWeight = weights.mostRecent()?.weightKg
            targetKg?.let { settingsStore.setGoalTargetKg(it) }

            if (rateKgPerWeek != null) {
                if (bodyWeight == null) {
                    _message.value = "Log a weight first, so the app knows what 1 percent of it is."
                    return@launch
                }
                when (val check = com.healthy.app.analysis.WeightGoal.checkRate(rateKgPerWeek, bodyWeight)) {
                    is com.healthy.app.analysis.WeightGoal.RateCheck.TooFast -> {
                        _message.value = check.message
                        return@launch
                    }
                    com.healthy.app.analysis.WeightGoal.RateCheck.Allowed ->
                        settingsStore.setChangeGoal(rateKgPerWeek, HealthyDay.today())
                }
            }
            recomputed.value++
            recalculate()
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
        val dishes = com.healthy.app.analysis.Dishes.per100g(db, products)
        return IntakeHistory.completeDays(IntakeHistory.byDay(meals, products, dishes))
            .takeLast(Energy.WINDOW_DAYS)
    }

}
