package com.healthy.app.ui.weight

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.healthy.app.analysis.WeightGoal
import com.healthy.app.analysis.WeightTrend
import com.healthy.app.core.HealthyDay
import com.healthy.app.data.HealthyDatabase
import com.healthy.app.data.HealthySettings
import com.healthy.app.data.SettingsStore
import com.healthy.app.data.entity.Weight
import com.healthy.app.health.HealthWriter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class WeightState(
    val points: List<WeightTrend.Point> = emptyList(),
    val latest: Weight? = null,
    val changeOver30Days: Double? = null,
    val todayLogged: Boolean = false,
    /** Smoothed body fat, empty when the scale never measured it (spec 8.4). */
    val bodyFatPoints: List<WeightTrend.Point> = emptyList(),
    val goalMode: String = HealthySettings.GOAL_NONE,
    val holdTargetKg: Double? = null,
    val holdStatus: WeightGoal.HoldStatus? = null,
    val rateKgPerWeek: Double? = null,
    val progress: WeightGoal.Progress? = null,
    val maxRateKgPerWeek: Double? = null,
    val rateRefusal: String? = null,
    val goalTargetKg: Double? = null,
    /** Where the current rate leads, week by week, from today's trend. */
    val projection: List<Double> = emptyList(),
    /**
     * Where the chosen rate said the weight would be, by date.
     *
     * Keyed by date rather than parallel to the points, because the chart
     * shows only a window of them and a list would have to be sliced in step.
     */
    val plannedKg: Map<String, Double> = emptyMap(),
    val dailyTargetKcal: Int? = null,
    /** Newest first, for the history list. */
    val history: List<Weight> = emptyList(),
    /** How far along the goal is, and the last milestone worth saying so for. */
    val goalProgressPercent: Int? = null,
    val celebrate: Int? = null,
)

class WeightViewModel(app: Application) : AndroidViewModel(app) {

    private val dao = HealthyDatabase.get(app).weightDao()
    private val _refusal = MutableStateFlow<String?>(null)
    private val writer = HealthWriter(app)
    private val reader = com.healthy.app.health.HealthReader(app)

    private val settingsStore = SettingsStore(app)

    val state: StateFlow<WeightState> = combine(
        dao.observeAscending(),
        settingsStore.settings,
        _refusal,
    ) { weights, settings, refusal ->
        val points = WeightTrend.series(weights)
        // Where the goal started. Stored once when the goal was set; for a
        // goal that predates that being recorded, recovered from the trend on
        // the day it began, and failing that from the first reading there is.
        // Otherwise every existing goal would show no progress forever.
        val startKg = settings.goalStartKg
            ?: settings.goalStartedOn?.let { began ->
                points.firstOrNull { it.date >= began }?.trendKg
            }
            ?: points.firstOrNull()?.trendKg
        val percent = WeightGoal.percentOfGoal(
            startKg = startKg,
            currentKg = points.lastOrNull()?.trendKg,
            targetKg = settings.goalTargetKg,
        )
        // Body fat gets the same smoothing, over only the days that measured
        // it: a dumb scale in the middle of the series must not read as zero.
        val withFat = weights.filter { it.bodyFatPct != null }
        WeightState(
            points = points,
            latest = weights.lastOrNull(),
            changeOver30Days = WeightTrend.changeOverDays(points, days = 30),
            todayLogged = weights.any { it.date == HealthyDay.today() },
            bodyFatPoints = WeightTrend.series(
                withFat.map { it.copy(weightKg = it.bodyFatPct!!) }
            ),
            goalMode = settings.goalMode,
            holdTargetKg = settings.goalHoldKg,
            holdStatus = settings.goalHoldKg?.let { WeightGoal.holdStatus(points, it) },
            rateKgPerWeek = settings.goalRateKgPerWeek,
            progress = settings.goalRateKgPerWeek?.let { rate ->
                // Only the points from the day the goal started: the target
                // line begins at the trend on that day (spec 8.6).
                val since = settings.goalStartedOn
                val window = if (since == null) points else points.filter { it.date >= since }
                WeightGoal.progress(window, rate)
            },
            maxRateKgPerWeek = weights.lastOrNull()?.let { WeightGoal.maximumRate(it.weightKg) },
            rateRefusal = refusal,
            goalTargetKg = settings.goalTargetKg,
            plannedKg = plannedFor(points, settings),
            history = weights.asReversed(),
            goalProgressPercent = percent,
            celebrate = percent
                ?.let { WeightGoal.milestoneReached(it, settings.goalCelebratedPercent) },
            projection = projectionFor(points, settings),
            dailyTargetKcal = settings.maintenanceKcal?.let { maintenance ->
                com.healthy.app.analysis.Energy.dailyTarget(
                    maintenanceKcal = maintenance,
                    rateKgPerWeek = settings.goalRateKgPerWeek ?: 0.0,
                    basalKcal = 0,
                ).kcal
            },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), WeightState())

    /**
     * The plan, laid over what actually happened.
     *
     * Starts at the trend on the day the goal began and moves at the chosen
     * rate, so the distance between this line and the real one is how far
     * ahead or behind the plan is running. It stops at the goal weight: past
     * that the plan is finished and a line continuing beyond it would be
     * asking for something nobody agreed to.
     */
    private fun plannedFor(
        points: List<WeightTrend.Point>,
        settings: HealthySettings,
    ): Map<String, Double> {
        if (settings.goalMode != HealthySettings.GOAL_CHANGE) return emptyMap()
        val rate = settings.goalRateKgPerWeek?.takeIf { it != 0.0 } ?: return emptyMap()
        val began = settings.goalStartedOn ?: points.firstOrNull()?.date ?: return emptyMap()
        val from = points.filter { it.date >= began }
        if (from.isEmpty()) return emptyMap()

        val start = settings.goalStartKg ?: from.first().trendKg
        val target = settings.goalTargetKg
        val firstDay = java.time.LocalDate.parse(from.first().date).toEpochDay()

        return from.associate { point ->
            val days = java.time.LocalDate.parse(point.date).toEpochDay() - firstDay
            val planned = start + rate * days / 7.0
            point.date to when {
                target == null -> planned
                rate < 0 -> planned.coerceAtLeast(target)
                else -> planned.coerceAtMost(target)
            }
        }
    }

    /**
     * Where the chosen rate leads, from today's trend to the goal weight.
     *
     * One point per day so it continues the same axis as the trend, and it
     * stops at the goal rather than running on forever: the line is the plan,
     * and the plan ends when it arrives.
     */
    private fun projectionFor(
        points: List<WeightTrend.Point>,
        settings: HealthySettings,
    ): List<Double> {
        if (settings.goalMode != HealthySettings.GOAL_CHANGE) return emptyList()
        val rate = settings.goalRateKgPerWeek ?: return emptyList()
        val target = settings.goalTargetKg ?: return emptyList()
        val start = points.lastOrNull()?.trendKg ?: return emptyList()
        if (rate == 0.0) return emptyList()
        // A rate pointing away from the goal has nothing to project.
        if ((target - start > 0) != (rate > 0)) return emptyList()

        val perDay = rate / 7.0
        val days = Math.ceil(kotlin.math.abs((target - start) / perDay)).toInt()
            .coerceIn(1, MAX_PROJECTION_DAYS)
        return (0..days).map { day -> start + perDay * day }
    }

    /**
     * Goal changes (spec 8.5).
     *
     * A rate above 1 percent of body weight is refused and the limit stated,
     * rather than silently clamped: the user asked for something the app will
     * not do, and needs to know that rather than wonder why the target looks
     * wrong.
     */
    fun setNoGoal() {
        viewModelScope.launch { settingsStore.setNoGoal(); _refusal.value = null }
    }

    fun setHoldGoal(targetKg: Double) {
        viewModelScope.launch {
            settingsStore.setHoldGoal(targetKg, HealthyDay.today())
            _refusal.value = null
        }
    }

    /**
     * The goal weight, which the Food tab reads too.
     *
     * There is one goal. Setting it here and setting it there write the same
     * setting, so the energy target and the projection can never disagree.
     */
    fun setGoalWeight(targetKg: Double) {
        viewModelScope.launch {
            settingsStore.setGoalTargetKg(targetKg)
            // Where the goal started from, so progress can be a fraction of
            // the distance rather than a distance with no scale. Taken once,
            // when the goal is set, because a start weight that moved with the
            // current weight would make progress permanently zero.
            dao.mostRecent()?.weightKg?.let { settingsStore.setGoalStartKg(it) }
            _refusal.value = null
        }
    }

    /** Corrects or removes a reading already logged. */
    fun editWeight(original: Weight, weightKg: Double) {
        viewModelScope.launch {
            dao.upsert(original.copy(weightKg = weightKg, source = Weight.MANUAL))
            writer.writeWeight(weightKg, HealthyDay.startOf(original.date))
        }
    }

    fun deleteWeight(weight: Weight) {
        viewModelScope.launch { dao.delete(weight.date) }
    }

    /** Remembers a milestone so the congratulation is shown once, not forever. */
    fun acknowledgeCelebration(percent: Int) {
        viewModelScope.launch { settingsStore.setGoalCelebratedPercent(percent) }
    }

    fun setChangeGoal(rateKgPerWeek: Double) {
        viewModelScope.launch {
            val bodyWeight = dao.mostRecent()?.weightKg
            if (bodyWeight == null) {
                _refusal.value = "Log a weight first, so the app knows what 1 percent of it is."
                return@launch
            }
            when (val check = WeightGoal.checkRate(rateKgPerWeek, bodyWeight)) {
                is WeightGoal.RateCheck.TooFast -> _refusal.value = check.message
                WeightGoal.RateCheck.Allowed -> {
                    settingsStore.setChangeGoal(rateKgPerWeek, HealthyDay.today())
                    _refusal.value = null
                }
            }
        }
    }

    private val _importStatus = MutableStateFlow<String?>(null)
    val importStatus: StateFlow<String?> = _importStatus

    /**
     * Pulls weights other apps wrote to Health Connect (spec 8.4, first in the
     * order it gives). A date already stored is left alone, so this can be run
     * repeatedly and never overwrites a hand-corrected value.
     */
    fun syncFromHealthConnect() {
        viewModelScope.launch {
            val external = reader.readWeights()
            if (external.isEmpty()) {
                _importStatus.value =
                    "No weight from other apps in Health Connect. Nothing else is writing it."
                return@launch
            }
            val rows = external
                .groupBy { HealthyDay.dayOf(it.atMillis) }
                .map { (day, sameDay) ->
                    val latest = sameDay.maxBy { it.atMillis }
                    Weight(
                        date = day,
                        weightKg = latest.kilograms,
                        timestamp = latest.atMillis,
                        source = Weight.HEALTH_CONNECT,
                    )
                }
            val before = dao.count()
            dao.insertIgnoringExisting(rows)
            val added = dao.count() - before
            val sources = external.map { it.source.substringAfterLast('.') }.toSet()
            _importStatus.value =
                "Added $added reading${if (added == 1) "" else "s"} from " +
                    "${sources.joinToString(", ")}, skipped ${rows.size - added} already stored."
        }
    }

    /**
     * Saves a reading and mirrors it into Health Connect (spec 3.3), so other
     * apps can read what the user records here. A failed write never blocks
     * the local save.
     */
    fun save(kilograms: Double, onDone: (Boolean) -> Unit = {}) {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            dao.upsert(
                Weight(
                    date = HealthyDay.today(),
                    weightKg = kilograms,
                    timestamp = now,
                    source = Weight.MANUAL,
                )
            )
            onDone(writer.writeWeight(kilograms, now))
        }
    }

    private companion object {
        /** A year. Beyond that a projection is a fiction, not a plan. */
        const val MAX_PROJECTION_DAYS = 365
    }
}
