package com.healthy.app.ui.fluids

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.healthy.app.core.Alcohol
import com.healthy.app.core.Beverage
import com.healthy.app.core.BeverageCatalog
import com.healthy.app.core.BeverageCategory
import com.healthy.app.core.Caffeine
import com.healthy.app.core.HealthyDay
import com.healthy.app.core.LogWindow
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

data class FluidsState(
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
    /** The rows the list is showing, which is [window], not necessarily today. */
    val entries: List<Drink> = emptyList(),
    val window: LogWindow = LogWindow.Rolling,
    /** Where the 04:00 boundary falls in the list, so it can be marked. */
    val boundaryMillis: Long? = null,
    /** Every dose still decaying into the window, needed to draw the curve. */
    val allDoses: List<Drink> = emptyList(),
    val halfLifeHours: Double = Caffeine.DEFAULT_HALF_LIFE_HOURS,
    val totalMg: Int = 0,
    val totalMl: Int = 0,
    val fluidTargetMl: Int = HealthySettings.DEFAULT_FLUID_TARGET_ML,
    val alcoholUnits: Double = 0.0,
    /**
     * The last five in each category, which is the whole point of the card:
     * a person drinks the same handful of things and should not hunt for them
     * among three hundred.
     */
    val recent: Map<BeverageCategory, List<Beverage>> = emptyMap(),
    /** The user's own drinks, offered in every category's search. */
    val custom: List<Beverage> = emptyList(),
    val mlPerUnit: Double = Alcohol.DEFAULT_ML_PER_UNIT,
    /** Seven logical days including today, against the weekly guideline. */
    val weekAlcoholUnits: Double = 0.0,
)

@OptIn(ExperimentalCoroutinesApi::class)
class FluidsViewModel(app: Application) : AndroidViewModel(app) {

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
     * Which stretch of the log the list shows. Defaults to the rolling day
     * because that is the question being asked when the app is opened; the
     * whole-day views are a step away for correcting an earlier entry.
     */
    private val window = MutableStateFlow<LogWindow>(LogWindow.Rolling)

    fun setWindow(value: LogWindow) {
        window.value = value
    }

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

    val state: StateFlow<FluidsState> =
        combine(
            tick,
            settingsStore.settings,
            customDrinks.observeAll(),
            window,
        ) { now, settings, custom, window -> Inputs(now, settings, custom, window) }
            .flatMapLatest { (now, settings, custom, window) ->
                val dayStart = HealthyDay.startOf(HealthyDay.dayOf(now))
                val dayEnd = dayStart + DAY_MILLIS
                // Doses from before the window still decay into it, so the
                // query must reach roughly six half-lives back or the curve
                // starts the day at a false zero.
                val lookback = (settings.halfLifeHours * 6 * MILLIS_PER_HOUR).toLong()
                // Far enough back for the curve and for whichever window the
                // list is showing, whichever of the two reaches further.
                val from = minOf(dayStart - lookback, window.startMillis(now))
                val to = maxOf(dayEnd, window.endMillis(now))

                combine(
                    drinks.observeDecayWindow(from, to),
                    recentByCategory(),
                    drinks.observeAlcoholUnits(dayStart - WEEK_MILLIS + DAY_MILLIS, dayEnd),
                ) { all, recent, weekUnits ->
                    build(
                        now = now,
                        settings = settings,
                        custom = custom.map(::asBeverage),
                        recent = recent,
                        all = all,
                        dayStart = dayStart,
                        dayEnd = dayEnd,
                        weekAlcoholUnits = weekUnits,
                        window = window,
                    )
                }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), FluidsState())

    private data class Inputs(
        val now: Long,
        val settings: HealthySettings,
        val custom: List<com.healthy.app.data.entity.CustomDrink>,
        val window: LogWindow,
    )

    /**
     * Each category's recent five, as beverages ready to be logged again.
     *
     * A recent row names a drink; the catalog supplies its strength. When the
     * name is not in the catalog — a custom drink, or something scanned — the
     * strength is recovered from the row itself, so a scanned Hell comes back
     * with the figures the user corrected rather than a catalog guess.
     */
    private fun recentByCategory(): kotlinx.coroutines.flow.Flow<Map<BeverageCategory, List<Beverage>>> {
        val perCategory: List<kotlinx.coroutines.flow.Flow<Pair<BeverageCategory, List<Beverage>>>> =
            BeverageCategory.entries.map { category ->
                drinks.observeRecent(category.name.lowercase(), RECENT_COUNT).map { rows ->
                    category to rows.map { row -> asBeverage(row, category) }
                }
            }
        return combine(perCategory) { pairs -> pairs.toMap() }
    }

    private fun asBeverage(
        row: com.healthy.app.data.entity.RecentDrink,
        category: BeverageCategory,
    ): Beverage {
        val known = BeverageCatalog.byName(row.name)
        if (known != null) return known.copy(defaultMl = row.volumeMl.takeIf { it > 0 } ?: known.defaultMl)

        // Not in the catalog. The row holds a total, not a rate, so the rate
        // is recovered by dividing — and a zero volume means a solid, whose
        // figures are already per whatever it was.
        val ml = row.volumeMl
        return Beverage(
            name = row.name,
            category = category,
            defaultMl = ml.takeIf { it > 0 } ?: 100,
            caffeinePer100 = if (ml > 0) row.mg * 100.0 / ml else row.mg.toDouble(),
            abv = if (ml > 0 && row.alcoholUnits > 0) {
                row.alcoholUnits * Alcohol.DEFAULT_ML_PER_UNIT * 100.0 / ml
            } else {
                0.0
            },
            solid = ml <= 0 && row.mg > 0,
        )
    }

    private fun asBeverage(custom: com.healthy.app.data.entity.CustomDrink): Beverage =
        Beverage(
            name = custom.name,
            category = if (custom.mg > 0) BeverageCategory.Caffeine else BeverageCategory.Water,
            defaultMl = custom.volumeMl.takeIf { it > 0 } ?: 100,
            caffeinePer100 = if (custom.volumeMl > 0) {
                custom.mg * 100.0 / custom.volumeMl
            } else {
                custom.mg.toDouble()
            },
            solid = custom.volumeMl <= 0 && custom.mg > 0,
        )

    private fun build(
        now: Long,
        settings: HealthySettings,
        custom: List<Beverage>,
        recent: Map<BeverageCategory, List<Beverage>>,
        all: List<Drink>,
        dayStart: Long,
        dayEnd: Long,
        weekAlcoholUnits: Double,
        window: LogWindow,
    ): FluidsState {
        val bedtimeMillis = nextBedtime(now, settings.targetBedtime)
        val bedtimeMg = Caffeine.levelAt(all, bedtimeMillis, settings.halfLifeHours)
        val curve = Caffeine.curve(all, dayStart, dayEnd, settings.halfLifeHours)
        // Two different windows on purpose. The totals are measured against
        // daily guidelines and so must use the logical day; the list answers
        // "what have I had lately" and follows whatever the user selected.
        val today = all.filter { it.timestamp in dayStart until dayEnd }
        val listed = all.filter {
            it.timestamp >= window.startMillis(now) && it.timestamp < window.endMillis(now)
        }

        return FluidsState(
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
            entries = listed.sortedByDescending { it.timestamp },
            window = window,
            boundaryMillis = LogWindow.boundaryWithin(window, now),
            allDoses = all,
            halfLifeHours = settings.halfLifeHours,
            totalMg = today.sumOf { it.mg },
            totalMl = today.sumOf { it.volumeMl },
            fluidTargetMl = settings.fluidTargetMl,
            alcoholUnits = today.sumOf { it.alcoholUnits },
            recent = recent,
            custom = custom,
            mlPerUnit = settings.mlPerAlcoholUnit,
            weekAlcoholUnits = weekAlcoholUnits,
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
     * Logs [beverage] at the amount the carousel settled on.
     *
     * One row carries all three: the caffeine that feeds the curve, the fluid
     * that fills the bar, and the units the morning screen reads rather than
     * asking the user to type again (spec 9.1 and 9.4). Every figure scales
     * with the amount, which is the reason the carousel exists — a 500 ml can
     * is not a 250 ml can with a different label.
     *
     * [minutesAgo] backdates it. That matters most for caffeine: a dose two
     * hours old has already lost a quarter of itself, and stamping it as now
     * pushes the curve, and the bedtime figure, too high.
     */
    fun log(beverage: Beverage, amount: Int, minutesAgo: Int = 0) {
        viewModelScope.launch {
            val settings = settingsStore.settings.first()
            val now = System.currentTimeMillis() - minutesAgo * 60_000L
            val fluid = beverage.fluidMlFor(amount)
            val row = Drink(
                name = beverage.name,
                mg = beverage.caffeineMgFor(amount),
                timestamp = now,
                volumeMl = fluid,
                alcoholUnits = Alcohol.units(amount, beverage.abv, settings.mlPerAlcoholUnit),
            )
            val id = drinks.insert(row)
            _lastLogged.value = row.copy(id = id)
            // Mirror the fluid into Health Connect so other apps see it too.
            if (fluid > 0) writer.writeHydration(fluid, now, now)
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

    /**
     * Corrects a drink already logged.
     *
     * The amount is what changes, and everything the row carries follows from
     * it: the caffeine, the fluid and the units are all recomputed from the
     * drink's own strength rather than scaled, so a correction cannot drift
     * away from what that drink actually is.
     */
    fun editAmount(row: Drink, amount: Int) {
        viewModelScope.launch {
            val settings = settingsStore.settings.first()
            val beverage = BeverageCatalog.byName(row.name)
            val corrected = if (beverage != null) {
                row.copy(
                    mg = beverage.caffeineMgFor(amount),
                    volumeMl = beverage.fluidMlFor(amount),
                    alcoholUnits = Alcohol.units(amount, beverage.abv, settings.mlPerAlcoholUnit),
                )
            } else {
                // Not in the catalog — a scanned or custom drink. Its strength
                // is only knowable from the row itself, so scale by ratio.
                val was = row.volumeMl.takeIf { it > 0 } ?: amount
                val factor = amount.toDouble() / was
                row.copy(
                    mg = Math.round(row.mg * factor).toInt(),
                    volumeMl = amount,
                    alcoholUnits = row.alcoholUnits * factor,
                )
            }
            drinks.update(corrected)
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
        const val WEEK_MILLIS = 7 * DAY_MILLIS
        const val MILLIS_PER_HOUR = 3_600_000.0
        /** Spec 5.2 asks for energy "at approximately 15:00". */
        const val ENERGY_PROMPT_HOUR = 15

        /** How many drinks each category remembers. The user asked for five. */
        const val RECENT_COUNT = 5
    }
}
