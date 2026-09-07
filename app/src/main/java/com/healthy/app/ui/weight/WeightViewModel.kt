package com.healthy.app.ui.weight

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.healthy.app.analysis.WeightTrend
import com.healthy.app.core.HealthyDay
import com.healthy.app.data.HealthyDatabase
import com.healthy.app.data.entity.Weight
import com.healthy.app.health.HealthWriter
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class WeightState(
    val points: List<WeightTrend.Point> = emptyList(),
    val latest: Weight? = null,
    val changeOver30Days: Double? = null,
    val todayLogged: Boolean = false,
)

class WeightViewModel(app: Application) : AndroidViewModel(app) {

    private val dao = HealthyDatabase.get(app).weightDao()
    private val writer = HealthWriter(app)

    val state: StateFlow<WeightState> = dao.observeAscending().map { weights ->
        val points = WeightTrend.series(weights)
        WeightState(
            points = points,
            latest = weights.lastOrNull(),
            changeOver30Days = WeightTrend.changeOverDays(points, days = 30),
            todayLogged = weights.any { it.date == HealthyDay.today() },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), WeightState())

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
