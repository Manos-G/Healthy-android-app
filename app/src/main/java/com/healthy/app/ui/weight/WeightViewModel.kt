package com.healthy.app.ui.weight

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.healthy.app.analysis.WeightTrend
import com.healthy.app.core.HealthyDay
import com.healthy.app.data.HealthyDatabase
import com.healthy.app.data.entity.Weight
import com.healthy.app.data.export.OpenScaleCsv
import com.healthy.app.health.HealthWriter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
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
)

class WeightViewModel(app: Application) : AndroidViewModel(app) {

    private val dao = HealthyDatabase.get(app).weightDao()
    private val writer = HealthWriter(app)

    val state: StateFlow<WeightState> = dao.observeAscending().map { weights ->
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
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), WeightState())

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
