package com.healthy.app.ui.weight

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.healthy.app.analysis.WeightGoal
import com.healthy.app.analysis.WeightTrend
import com.healthy.app.core.HealthyDay
import com.healthy.app.data.HealthyDatabase
import com.healthy.app.data.HealthySettings
import com.healthy.app.data.SettingsStore
import com.healthy.app.data.entity.Weight
import com.healthy.app.data.export.OpenScaleCsv
import com.healthy.app.health.HealthWriter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
)

class WeightViewModel(app: Application) : AndroidViewModel(app) {

    private val dao = HealthyDatabase.get(app).weightDao()
    private val _refusal = MutableStateFlow<String?>(null)
    private val writer = HealthWriter(app)

    private val settingsStore = SettingsStore(app)

    val state: StateFlow<WeightState> = combine(
        dao.observeAscending(),
        settingsStore.settings,
        _refusal,
    ) { weights, settings, refusal ->
        val points = WeightTrend.series(weights)
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
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), WeightState())

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
     * Imports an OpenScale export (spec 8.4).
     *
     * Rows whose date is already stored are ignored rather than overwritten,
     * so a re-import cannot clobber a value the user has since corrected by
     * hand — the DAO uses INSERT OR IGNORE for exactly this.
     */
    fun importOpenScale(source: Uri) {
        viewModelScope.launch {
            val text = runCatching {
                withContext(Dispatchers.IO) {
                    getApplication<Application>().contentResolver
                        .openInputStream(source)?.bufferedReader()?.use { it.readText() }
                        ?: error("the file could not be opened")
                }
            }.getOrElse {
                _importStatus.value = "Import failed: ${it.message}"
                return@launch
            }

            val outcome = OpenScaleCsv.parse(text)
            if (outcome.problem != null) {
                _importStatus.value = "Import failed: ${outcome.problem}."
                return@launch
            }

            val before = dao.count()
            dao.insertIgnoringExisting(outcome.rows)
            val added = dao.count() - before
            val alreadyHad = outcome.rows.size - added

            _importStatus.value = buildString {
                append("Added $added reading")
                if (added != 1) append("s")
                if (alreadyHad > 0) append(", skipped $alreadyHad already stored")
                if (outcome.skipped > 0) append(", could not read ${outcome.skipped} row")
                if (outcome.skipped > 1) append("s")
                append(".")
            }
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
}
