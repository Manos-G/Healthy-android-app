package com.healthy.app.ui.today

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.healthy.app.core.Caffeine
import com.healthy.app.core.CatalogDrink
import com.healthy.app.core.AlcoholKind
import com.healthy.app.core.DrinkCatalog
import com.healthy.app.core.FluidCatalog
import com.healthy.app.core.FluidDrink
import com.healthy.app.core.HealthyDay
import com.healthy.app.data.HealthyDatabase
import com.healthy.app.data.HealthySettings
import com.healthy.app.data.SettingsStore
import com.healthy.app.data.entity.Drink
import com.healthy.app.data.entity.Night
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalTime
import java.time.format.DateTimeFormatter

data class TodayState(
    val nowMg: Int = 0,
    val bedtimeMg: Int = 0,
    val bedtimeLabel: String = HealthySettings.DEFAULT_BEDTIME,
    val isClear: Boolean = true,
    val curve: List<Double> = emptyList(),
    val curveMax: Double = 1.0,
    val dayStart: Long = 0,
    val dayEnd: Long = 0,
    val nowMillis: Long = 0,
    val bedtimeMillis: Long = 0,
    val limitMg: Int = Caffeine.DEFAULT_BEDTIME_LIMIT_MG,
    val entries: List<Drink> = emptyList(),
    /** Every dose still decaying into the window, needed to draw the curve. */
    val allDoses: List<Drink> = emptyList(),
    val halfLifeHours: Double = Caffeine.DEFAULT_HALF_LIFE_HOURS,
    val catalog: List<CatalogDrink> = DrinkCatalog.BUILT_IN,
    val totalMg: Int = 0,
    val totalMl: Int = 0,
    val fluidTargetMl: Int = HealthySettings.DEFAULT_FLUID_TARGET_ML,
    val alcoholUnits: Double = 0.0,
    val fluidCatalog: List<FluidDrink> = FluidCatalog.BUILT_IN,
)

@OptIn(ExperimentalCoroutinesApi::class)
class TodayViewModel(app: Application) : AndroidViewModel(app) {

    private val db = HealthyDatabase.get(app)
    private val drinks = db.drinkDao()
    private val customDrinks = db.customDrinkDao()
    private val settingsStore = SettingsStore(app)
    private val writer = com.healthy.app.health.HealthWriter(app)

    /**
     * Ticks so the hero numeral and the "now" line keep up with the clock.
     * A minute is fine: caffeine at a 5 h half-life moves about 0.2 % a minute.
     */
    private val tick = MutableStateFlow(System.currentTimeMillis())

    /**
     * The night that was rated this morning but still has no 15:00 energy,
     * surfaced only after 15:00 (spec 5.2). Before that hour the user cannot
     * answer, so the card must not appear.
     */
    val energyPrompt: StateFlow<Night?> =
        combine(tick, db.nightDao().observeAwaitingEnergyRating()) { now, night ->
            val hour = java.time.Instant.ofEpochMilli(now)
                .atZone(java.time.ZoneId.systemDefault()).hour
            if (hour >= ENERGY_PROMPT_HOUR) night else null
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun rateEnergy(date: String, energy: Int) {
        viewModelScope.launch {
            db.nightDao().setEnergy3pm(date, energy)
        }
    }

    /** Set after a log so the snackbar can offer an undo (spec 5.1). */
    private val _lastLogged = MutableStateFlow<Drink?>(null)
    val lastLogged: StateFlow<Drink?> = _lastLogged

    init {
        viewModelScope.launch {
            while (true) {
                kotlinx.coroutines.delay(60_000)
                tick.value = System.currentTimeMillis()
            }
        }
    }

    val state: StateFlow<TodayState> =
        combine(
            tick,
            settingsStore.settings,
            customDrinks.observeAll(),
        ) { now, settings, custom -> Inputs(now, settings, custom) }
            .flatMapLatest { (now, settings, custom) ->
                val dayStart = HealthyDay.startOf(HealthyDay.dayOf(now))
                val dayEnd = dayStart + DAY_MILLIS
                // Doses from before the window still decay into it, so the
                // query must reach roughly six half-lives back or the curve
                // starts the day at a false zero.
                val lookback = (settings.halfLifeHours * 6 * MILLIS_PER_HOUR).toLong()

                drinks.observeDecayWindow(dayStart - lookback, dayEnd).map { all ->
                    build(
                        now = now,
                        settings = settings,
                        custom = custom.map { CatalogDrink(it.name, it.mg, it.volumeMl) },
                        all = all,
                        dayStart = dayStart,
                        dayEnd = dayEnd,
                    )
                }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TodayState())

    private data class Inputs(
        val now: Long,
        val settings: HealthySettings,
        val custom: List<com.healthy.app.data.entity.CustomDrink>,
    )

    private fun build(
        now: Long,
        settings: HealthySettings,
        custom: List<CatalogDrink>,
        all: List<Drink>,
        dayStart: Long,
        dayEnd: Long,
    ): TodayState {
        val bedtimeMillis = nextBedtime(now, settings.targetBedtime)
        val bedtimeMg = Caffeine.levelAt(all, bedtimeMillis, settings.halfLifeHours)
        val curve = Caffeine.curve(all, dayStart, dayEnd, settings.halfLifeHours)
        val today = all.filter { it.timestamp in dayStart until dayEnd }

        return TodayState(
            nowMg = Caffeine.levelAt(all, now, settings.halfLifeHours).toInt(),
            bedtimeMg = Math.round(bedtimeMg).toInt(),
            bedtimeLabel = settings.targetBedtime,
            isClear = Math.round(bedtimeMg) <= settings.bedtimeLimitMg,
            curve = curve,
            // Keep the limit line and a sane floor on the axis, so a quiet day
            // does not magnify a single 30 mg tea into a mountain.
            curveMax = maxOf(curve.maxOrNull() ?: 0.0, settings.bedtimeLimitMg * 1.6, 60.0),
            dayStart = dayStart,
            dayEnd = dayEnd,
            nowMillis = now,
            bedtimeMillis = bedtimeMillis,
            limitMg = settings.bedtimeLimitMg,
            entries = today.sortedByDescending { it.timestamp },
            allDoses = all,
            halfLifeHours = settings.halfLifeHours,
            catalog = DrinkCatalog.BUILT_IN + custom,
            totalMg = today.sumOf { it.mg },
            totalMl = today.sumOf { it.volumeMl },
            fluidTargetMl = settings.fluidTargetMl,
            alcoholUnits = today.sumOf { it.alcoholUnits },
        )
    }

    /** The next occurrence of `HH:mm`; tomorrow if it has already passed today. */
    private fun nextBedtime(now: Long, hhmm: String): Long {
        val time = runCatching { LocalTime.parse(hhmm, DateTimeFormatter.ofPattern("HH:mm")) }
            .getOrElse { LocalTime.of(23, 30) }
        val zone = java.time.ZoneId.systemDefault()
        val today = java.time.Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        var candidate = java.time.ZonedDateTime.of(today, time, zone).toInstant().toEpochMilli()
        if (candidate <= now) {
            candidate = java.time.ZonedDateTime.of(today.plusDays(1), time, zone)
                .toInstant().toEpochMilli()
        }
        return candidate
    }

    /**
     * Logs a drink with no caffeine (spec 9.2). A beer or a wine also adds to
     * the day's alcohol units, which the morning screen then reads rather than
     * asking the user to type again (spec 9.4).
     */
    fun logFluid(drink: FluidDrink) {
        viewModelScope.launch {
            val settings = settingsStore.settings.first()
            val units = when (drink.alcoholUnitsKey) {
                AlcoholKind.None -> 0.0
                AlcoholKind.Beer -> settings.unitsPerBeer
                AlcoholKind.Wine -> settings.unitsPerWine
            }
            val now = System.currentTimeMillis()
            val row = Drink(
                name = drink.name,
                mg = 0,
                timestamp = now,
                volumeMl = drink.volumeMl,
                alcoholUnits = units,
            )
            val id = drinks.insert(row)
            _lastLogged.value = row.copy(id = id)
            writer.writeHydration(drink.volumeMl, now, now)
            tick.value = System.currentTimeMillis()
        }
    }

    /** One tap logs the caffeine and the fluid together (spec 9.1). */
    fun log(drink: CatalogDrink) {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val row = Drink(
                name = drink.name,
                mg = drink.mg,
                timestamp = now,
                volumeMl = drink.volumeMl,
            )
            val id = drinks.insert(row)
            _lastLogged.value = row.copy(id = id)
            // Mirror the fluid into Health Connect so other apps see it too.
            if (drink.volumeMl > 0) writer.writeHydration(drink.volumeMl, now, now)
            tick.value = System.currentTimeMillis()
        }
    }

    fun undoLast() {
        val row = _lastLogged.value ?: return
        viewModelScope.launch {
            drinks.deleteById(row.id)
            _lastLogged.value = null
            tick.value = System.currentTimeMillis()
        }
    }

    fun delete(drink: Drink) {
        viewModelScope.launch {
            drinks.delete(drink)
            tick.value = System.currentTimeMillis()
        }
    }

    fun clearSnackbar() {
        _lastLogged.value = null
    }

    private companion object {
        const val DAY_MILLIS = 24 * 60 * 60 * 1000L
        const val MILLIS_PER_HOUR = 3_600_000.0
        /** Spec 5.2 asks for energy "at approximately 15:00". */
        const val ENERGY_PROMPT_HOUR = 15
    }
}
