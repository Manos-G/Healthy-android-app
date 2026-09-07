package com.healthy.app.ui.morning

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.healthy.app.core.HealthyDay
import com.healthy.app.data.HealthyDatabase
import com.healthy.app.data.entity.Night
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

/**
 * The form the user fills in each morning (spec 5.2).
 *
 * Step 3 is manual entry only. The sync button and the read-only Health
 * Connect styling arrive in step 5; until then every field is typed.
 *
 * Times are held as `HH:mm` strings rather than instants because that is what
 * the user enters. They become epoch millis only on save, where the 04:00
 * boundary decides which night they belong to.
 */
data class MorningForm(
    val date: String = HealthyDay.lastNight(),
    val sleepStart: String? = null,
    val sleepEnd: String? = null,
    val wakeups: String = "",
    val restingHr: String = "",
    val spo2: String = "",
    val alertness: Int? = null,
    val alcoholUnits: String = "",
    val lastMeal: String? = null,
    val exercise: String? = null,
    val roomTempC: String = "",
    val notes: String = "",
    /** Derived from the drink table; the user never types this (spec 5.2). */
    val caffeineMg: Int = 0,
    val caffeineCount: Int = 0,
    val existing: Boolean = false,
    val savedAt: Long? = null,
) {
    /**
     * Sleep duration in minutes, handling a night that crosses midnight and one
     * that ends in the afternoon. A wake time at or before the sleep time means
     * the night ran past midnight, so a day is added.
     */
    val minutes: Int?
        get() {
            val start = sleepStart?.toLocalTimeOrNull() ?: return null
            val end = sleepEnd?.toLocalTimeOrNull() ?: return null
            var span = Duration.between(start, end).toMinutes()
            if (span <= 0) span += 24 * 60
            return span.toInt()
        }

    val durationLabel: String
        get() = minutes?.let { "${it / 60} h ${it % 60} m" } ?: "—"

    /** A night is worth saving once it has times or a rating. */
    val canSave: Boolean
        get() = minutes != null || alertness != null
}

private val HHMM: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

internal fun String.toLocalTimeOrNull(): LocalTime? =
    runCatching { LocalTime.parse(this, HHMM) }.getOrNull()

@OptIn(ExperimentalCoroutinesApi::class)
class MorningViewModel(app: Application) : AndroidViewModel(app) {

    private val db = HealthyDatabase.get(app)
    private val nights = db.nightDao()
    private val drinks = db.drinkDao()

    private val _form = MutableStateFlow(MorningForm())
    val form: StateFlow<MorningForm> = _form.asStateFlow()

    private val selectedDate = MutableStateFlow(HealthyDay.lastNight())

    /** Nights already stored, so the date picker can mark which are done. */
    val savedDates: StateFlow<Set<String>> =
        nights.observeAll().map { list -> list.map { it.date }.toSet() }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    init {
        viewModelScope.launch {
            selectedDate.flatMapLatest { date -> nights.observe(date) }.collect { night ->
                load(night, selectedDate.value)
            }
        }
    }

    private suspend fun load(night: Night?, date: String) {
        val from = HealthyDay.startOf(date)
        val to = HealthyDay.endOf(date)
        val dayDrinks = drinks.between(from, to)

        _form.value = if (night == null) {
            MorningForm(
                date = date,
                caffeineMg = dayDrinks.sumOf { it.mg },
                caffeineCount = dayDrinks.size,
                existing = false,
            )
        } else {
            MorningForm(
                date = date,
                sleepStart = night.sleepStart.takeIf { it > 0 }?.asClockString(),
                sleepEnd = night.sleepEnd.takeIf { it > 0 }?.asClockString(),
                wakeups = night.wakeups?.toString().orEmpty(),
                restingHr = night.restingHr?.toString().orEmpty(),
                spo2 = night.spo2?.let { trimNumber(it) }.orEmpty(),
                alertness = night.alertness,
                alcoholUnits = night.alcoholUnits?.let { trimNumber(it) }.orEmpty(),
                lastMeal = night.lastMeal,
                exercise = night.exercise,
                roomTempC = night.roomTempC?.let { trimNumber(it) }.orEmpty(),
                notes = night.notes,
                caffeineMg = dayDrinks.sumOf { it.mg },
                caffeineCount = dayDrinks.size,
                existing = true,
            )
        }
    }

    fun selectDate(date: String) {
        selectedDate.value = date
    }

    fun shiftDate(days: Long) {
        selectedDate.value = HealthyDay.plusDays(selectedDate.value, days)
    }

    fun update(block: (MorningForm) -> MorningForm) {
        _form.value = block(_form.value)
    }

    /**
     * Saves the night (spec 5.2). `energy3pm` is deliberately untouched: the
     * user cannot know their 15:00 energy in the morning, so the Today screen
     * collects it after 15:00 instead. A re-save must not wipe a rating that
     * was already given that way.
     */
    fun save(onDone: () -> Unit = {}) {
        val f = _form.value
        if (!f.canSave) return
        viewModelScope.launch {
            val existing = nights.byDate(f.date)
            val startMillis = f.sleepStart?.toEpochOn(f.date)
            val endMillis = f.sleepEnd?.toEpochOn(f.date, after = startMillis)

            nights.upsert(
                Night(
                    date = f.date,
                    sleepStart = startMillis ?: existing?.sleepStart ?: 0L,
                    sleepEnd = endMillis ?: existing?.sleepEnd ?: 0L,
                    minutes = f.minutes ?: existing?.minutes ?: 0,
                    deepMin = existing?.deepMin,
                    lightMin = existing?.lightMin,
                    remMin = existing?.remMin,
                    awakeMin = existing?.awakeMin,
                    wakeups = f.wakeups.toIntOrNull(),
                    restingHr = f.restingHr.toIntOrNull(),
                    spo2 = f.spo2.toDoubleOrNull(),
                    alertness = f.alertness,
                    energy3pm = existing?.energy3pm,
                    alcoholUnits = f.alcoholUnits.toDoubleOrNull(),
                    lastMeal = f.lastMeal,
                    exercise = f.exercise,
                    roomTempC = f.roomTempC.toDoubleOrNull(),
                    notes = f.notes,
                    editedFields = existing?.editedFields.orEmpty(),
                )
            )
            _form.value = _form.value.copy(existing = true, savedAt = System.currentTimeMillis())
            onDone()
        }
    }

    /**
     * Turns `HH:mm` into an instant on the logical day [date].
     *
     * A time before 04:00 belongs to the next calendar day, because the logical
     * day runs 04:00 to 04:00. This is what lets a night start at 02:38 and
     * still record against the day it started (spec 4.4, acceptance test 5).
     */
    private fun String.toEpochOn(date: String, after: Long? = null): Long? {
        val time = toLocalTimeOrNull() ?: return null
        val zone = ZoneId.systemDefault()
        val base = LocalDate.parse(date)
        val day = if (time.hour < HealthyDay.BOUNDARY_HOUR) base.plusDays(1) else base
        var millis = ZonedDateTime.of(day, time, zone).toInstant().toEpochMilli()
        // A wake time that lands before the sleep time means the night ran on
        // into the following day.
        if (after != null && millis <= after) {
            millis = ZonedDateTime.of(day.plusDays(1), time, zone).toInstant().toEpochMilli()
        }
        return millis
    }

    private fun Long.asClockString(): String =
        java.time.Instant.ofEpochMilli(this).atZone(ZoneId.systemDefault())
            .toLocalTime().format(HHMM)

    private fun trimNumber(value: Double): String =
        if (value == value.toLong().toDouble()) value.toLong().toString() else value.toString()
}
