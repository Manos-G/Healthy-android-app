package com.healthy.app.ui.morning

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.healthy.app.core.HealthyDay
import com.healthy.app.data.HealthyDatabase
import com.healthy.app.data.entity.Night
import com.healthy.app.health.HealthConnect
import com.healthy.app.health.HealthReader
import com.healthy.app.health.SleepAnalysis
import com.healthy.app.ui.health.HealthConnectUiState
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
 * the user enters. They become epoch millis only on save, where the day
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
    /**
     * Sync-owned fields the user has typed over. A second sync must not
     * overwrite these (spec 3.4, acceptance test 7).
     */
    val editedFields: Set<String> = emptySet(),
    val syncedAt: Long? = null,
    val syncMessage: String? = null,
    val stageSummary: String? = null,
    /** Set after a sync, for the chart in spec 18.4. */
    val hypnogram: com.healthy.app.analysis.Hypnogram.Result? = null,
    val stageBlocks: List<com.healthy.app.data.entity.StageBlock> = emptyList(),
    val sleepStartMillis: Long = 0,
    /**
     * Time actually asleep, as measured, when that is not the span between
     * falling asleep and waking.
     *
     * A day with a night and a nap has two sleeps and one span, and the span
     * includes the waking hours between them. The reader adds the sleeps up;
     * without somewhere to put that figure the form recomputed the span from
     * the two clock times and threw the measurement away — which is why
     * counting both sleeps changed nothing on screen.
     *
     * Cleared the moment the user types a time, because then the times they
     * typed are the better answer.
     */
    val measuredMinutes: Int? = null,
    /** How many separate sleeps that measurement covers. */
    val sleepCount: Int = 1,
    /** The sleep the two clock times describe, when the day held more than one. */
    val mainSleepMinutes: Int? = null,
    val sleepEndMillis: Long = 0,
    /** Both cycle lengths, labelled by source (spec 18.5). */
    val watchCycleMinutes: Int? = null,
    val heartCycleMinutes: Int? = null,
    /** none, light, medium or heavy. Hidden while the toggle is off (spec 10). */
    val flow: String? = null,
    val trackCycle: Boolean = false,
    val cycleDay: Int? = null,
) {
    /**
     * Sleep duration in minutes, handling a night that crosses midnight and one
     * that ends in the afternoon. A wake time at or before the sleep time means
     * the night ran past midnight, so a day is added.
     */
    val minutes: Int?
        get() {
            measuredMinutes?.let { return it }
            val start = sleepStart?.toLocalTimeOrNull() ?: return null
            val end = sleepEnd?.toLocalTimeOrNull() ?: return null
            var span = Duration.between(start, end).toMinutes()
            if (span <= 0) span += 24 * 60
            return span.toInt()
        }

    /**
     * The duration, said so it cannot contradict the times beside it.
     *
     * With a night and a nap the two clock times describe the longest sleep
     * only, and the total covers both. Printing the total alone next to those
     * times claimed a sleep of a length nobody had.
     */
    val durationLabel: String
        get() {
            val total = minutes ?: return "—"
            val main = mainSleepMinutes
            if (sleepCount <= 1 || main == null || main == total) {
                return "${total / 60} h ${total % 60} m"
            }
            val rest = (total - main).coerceAtLeast(0)
            return "${main / 60} h ${main % 60} m here, " +
                "plus ${rest / 60} h ${rest % 60} m in " +
                (if (sleepCount == 2) "another sleep" else "${sleepCount - 1} other sleeps") +
                " — ${total / 60} h ${total % 60} m in all"
        }

    /** A night is worth saving once it has times or a rating. */
    val canSave: Boolean
        get() = minutes != null || alertness != null
}

/** The sync-owned field names, matching the `night` columns. */
object SyncedField {
    const val SLEEP_START = "sleepStart"
    const val SLEEP_END = "sleepEnd"
    const val WAKEUPS = "wakeups"
    const val RESTING_HR = "restingHr"
    const val SPO2 = "spo2"
}

private val HHMM: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

internal fun String.toLocalTimeOrNull(): LocalTime? =
    runCatching { LocalTime.parse(this, HHMM) }.getOrNull()

@OptIn(ExperimentalCoroutinesApi::class)
class MorningViewModel(app: Application) : AndroidViewModel(app) {

    private val db = HealthyDatabase.get(app)
    private val nights = db.nightDao()
    private val drinks = db.drinkDao()
    private val reader = HealthReader(app)
    private val settingsStore = com.healthy.app.data.SettingsStore(app)

    /** Held between a sync and the save that writes them (spec 4.2). */
    private var pendingStageBlocks: List<com.healthy.app.data.entity.StageBlock> = emptyList()

    private val _health = MutableStateFlow(
        HealthConnectUiState(HealthConnect.availability(app), isGranted = false)
    )
    val health: StateFlow<HealthConnectUiState> = _health.asStateFlow()

    private val _form = MutableStateFlow(MorningForm())
    val form: StateFlow<MorningForm> = _form.asStateFlow()

    private val selectedDate = MutableStateFlow(HealthyDay.lastNight())

    /** Nights already stored, so the date picker can mark which are done. */
    val savedDates: StateFlow<Set<String>> =
        nights.observeAll().map { list -> list.map { it.date }.toSet() }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    init {
        viewModelScope.launch {
            settingsStore.settings.collect { s ->
                _form.value = _form.value.copy(trackCycle = s.trackCycle)
            }
        }
        refreshHealthConnect()
        viewModelScope.launch {
            selectedDate.flatMapLatest { date -> nights.observe(date) }.collect { night ->
                load(night, selectedDate.value)
            }
        }
    }

    /** Minutes between two stored instants, or null when either is missing. */
    private fun spanMinutes(start: Long, end: Long): Int? =
        if (start > 0 && end > start) ((end - start) / 60_000L).toInt() else null

    private suspend fun load(night: Night?, date: String) {
        val from = HealthyDay.startOf(date)
        val to = HealthyDay.endOf(date)
        val dayDrinks = drinks.between(from, to)
        // Spec 9.4: a beer or a wine already recorded its units, so the form
        // shows the total instead of asking for it again.
        val loggedAlcohol = dayDrinks.sumOf { it.alcoholUnits }
        // Spec 12.6: the app already knows when the last meal was, so it does
        // not ask. The gap between it and sleep is a strong input for sleep
        // quality, which is why the comparison table carries it.
        val lastMealFromLog = db.mealDao().lastMealTime(from, to)?.let { it.asClockString() }

        _form.value = if (night == null) {
            MorningForm(
                date = date,
                alcoholUnits = if (loggedAlcohol > 0) trimNumber(loggedAlcohol) else "",
                lastMeal = lastMealFromLog,
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
                alcoholUnits = night.alcoholUnits?.let { trimNumber(it) }
                    ?: loggedAlcohol.takeIf { it > 0 }?.let { trimNumber(it) }.orEmpty(),
                lastMeal = night.lastMeal ?: lastMealFromLog,
                exercise = night.exercise,
                roomTempC = night.roomTempC?.let { trimNumber(it) }.orEmpty(),
                notes = night.notes,
                editedFields = night.editedFields,
                // The stored measurement, not the span between the two clock
                // times: reopening a saved night must not recompute a total
                // that two sleeps made smaller than the stretch they cover.
                measuredMinutes = night.minutes.takeIf { it > 0 },
                sleepCount = night.sleepCount,
                // Derived from the stored times, which describe the main sleep.
                mainSleepMinutes = spanMinutes(night.sleepStart, night.sleepEnd),
                stageSummary = stageSummary(night),
                caffeineMg = dayDrinks.sumOf { it.mg },
                caffeineCount = dayDrinks.size,
                existing = true,
            )
        }
    }

    /** Re-reads availability and granted permissions (spec 3.1, 3.2). */
    fun refreshHealthConnect() {
        val app = getApplication<Application>()
        viewModelScope.launch {
            val availability = HealthConnect.availability(app)
            val granted = runCatching { HealthConnect.hasAllReadPermissions(app) }.getOrDefault(false)
            _health.value = HealthConnectUiState(availability, granted)
        }
    }

    fun selectDate(date: String) {
        selectedDate.value = date
    }

    fun shiftDate(days: Long) {
        selectedDate.value = HealthyDay.plusDays(selectedDate.value, days)
    }

    fun update(block: (MorningForm) -> MorningForm) {
        val before = _form.value
        val after = block(before)
        // A typed time replaces a measurement: the user is correcting it, and
        // the span between what they typed is then the honest answer.
        _form.value = if (
            after.sleepStart != before.sleepStart || after.sleepEnd != before.sleepEnd
        ) {
            after.copy(measuredMinutes = null, sleepCount = 1)
        } else {
            after
        }
    }

    /**
     * Updates a sync-owned field and records that the user typed it.
     *
     * This is what makes acceptance test 7 hold: a manual edit survives a
     * second sync, because [sync] skips every field named here (spec 3.4).
     */
    fun edit(field: String, block: (MorningForm) -> MorningForm) {
        val current = _form.value
        _form.value = block(current).copy(editedFields = current.editedFields + field)
    }

    /** Lets the user hand a field back to the sync. */
    fun clearEdit(field: String) {
        val current = _form.value
        _form.value = current.copy(editedFields = current.editedFields - field)
    }

    /**
     * Reads the selected night from Health Connect and fills the form
     * (spec 3.4). Fields the user has edited are left alone.
     */
    fun sync() {
        val date = _form.value.date
        viewModelScope.launch {
            _form.value = _form.value.copy(syncMessage = "Reading Health Connect…")
            when (val result = reader.readNight(date)) {
                is HealthReader.Result.NoSession ->
                    _form.value = _form.value.copy(
                        syncMessage = "No sleep session recorded for $date.",
                    )

                is HealthReader.Result.Failed ->
                    _form.value = _form.value.copy(
                        syncMessage = "Could not read Health Connect: ${result.reason}",
                    )

                is HealthReader.Result.Found -> {
                    pendingStageBlocks = result.data.stageBlocks
                    val hypnogram = com.healthy.app.analysis.Hypnogram.analyse(
                        samples = result.data.heartRateSamples,
                        sleepStart = result.data.sleepStart,
                        sleepEnd = result.data.sleepEnd,
                    )
                    val f = _form.value
                    val edited = f.editedFields
                    val t = result.data.totals

                    _form.value = f.copy(
                        sleepStart = if (SyncedField.SLEEP_START in edited) f.sleepStart
                        else result.data.sleepStart.asClockString(),
                        sleepEnd = if (SyncedField.SLEEP_END in edited) f.sleepEnd
                        else result.data.sleepEnd.asClockString(),
                        wakeups = if (SyncedField.WAKEUPS in edited) f.wakeups
                        else t.wakeups?.toString().orEmpty(),
                        restingHr = if (SyncedField.RESTING_HR in edited) f.restingHr
                        else result.data.restingHr?.toString().orEmpty(),
                        spo2 = if (SyncedField.SPO2 in edited) f.spo2
                        else result.data.spo2?.let { "%.1f".format(it) }.orEmpty(),
                        // The measured total, which for two sleeps is their
                        // sum and not the span the clock times imply. Dropped
                        // if the user has typed either time themselves.
                        measuredMinutes = result.data.minutes.takeIf {
                            SyncedField.SLEEP_START !in edited && SyncedField.SLEEP_END !in edited
                        },
                        sleepCount = result.data.sleepCount,
                        mainSleepMinutes = result.data.mainSleepMinutes,
                        hypnogram = hypnogram,
                        stageBlocks = result.data.stageBlocks,
                        sleepStartMillis = result.data.sleepStart,
                        sleepEndMillis = result.data.sleepEnd,
                        watchCycleMinutes = SleepAnalysis
                            .cycleLengthFromDeepBlocks(result.data.stageBlocks),
                        heartCycleMinutes = (hypnogram as? com.healthy.app.analysis.Hypnogram.Result.Found)
                            ?.cycleLengthMinutes,
                        syncedAt = System.currentTimeMillis(),
                        stageSummary = describeStages(
                            t,
                            result.data.heartRateSampleCount,
                            result.data.source,
                            result.data.competingSessions,
                            result.data.heartRateSource,
                        ),
                        syncMessage = buildString {
                            append("Read ")
                            append(result.data.minutes / 60)
                            append(" h ")
                            append(result.data.minutes % 60)
                            append(" m")
                            // Two sleeps in a day used to record only one of
                            // them, so when both are counted it should say so
                            // rather than quietly show a larger number.
                            if (result.data.sleepCount > 1) {
                                append(" across ${result.data.sleepCount} sleeps")
                            }
                            if (edited.isNotEmpty()) {
                                append(". Kept your edits to ")
                                append(edited.sorted().joinToString(", "))
                            }
                            append(".")
                        },
                    )
                }
            }
        }
    }

    private fun describeStages(
        t: SleepAnalysis.StageTotals,
        hrSamples: Int,
        source: String,
        competing: Int,
        heartSource: String?,
    ): String = buildString {
        if (t.deepMin == null) {
            append("The watch reported no sleep stages for this night.")
        } else {
            append("Deep ${t.deepMin} m, light ${t.lightMin} m, REM ${t.remMin} m, ")
            append("awake ${t.awakeMin} m. ")
            append("Wake-ups: ${t.wakeups?.toString() ?: "not reported"}.")
        }
        append(" Heart rate samples: $hrSamples.")
        append("\nSleep from ${source.substringAfterLast('.')}")
        if (competing > 0) {
            append(", chosen over $competing other")
            if (competing > 1) append("s") else append(" one")
            append(" for having more detail")
        }
        if (heartSource != null && heartSource != source) {
            append("; heart rate from ${heartSource.substringAfterLast('.')}")
        }
        append(".")
    }

    private fun stageSummary(night: com.healthy.app.data.entity.Night): String? =
        if (night.deepMin == null) null
        else "Deep ${night.deepMin} m, light ${night.lightMin} m, REM ${night.remMin} m, " +
            "awake ${night.awakeMin} m. Wake-ups: ${night.wakeups?.toString() ?: "not reported"}."

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

            // A sync stages the blocks; the save writes them with the night so
            // the two cannot drift apart (spec 4.2).
            val blocks = pendingStageBlocks
            val totals = if (blocks.isNotEmpty()) {
                SleepAnalysis.stageTotals(blocks)
            } else {
                SleepAnalysis.StageTotals(
                    existing?.deepMin, existing?.lightMin, existing?.remMin,
                    existing?.awakeMin, existing?.wakeups,
                )
            }

            val night = Night(
                    date = f.date,
                    sleepStart = startMillis ?: existing?.sleepStart ?: 0L,
                    sleepEnd = endMillis ?: existing?.sleepEnd ?: 0L,
                    minutes = f.minutes ?: existing?.minutes ?: 0,
                    sleepCount = f.sleepCount,
                    deepMin = totals.deepMin,
                    lightMin = totals.lightMin,
                    remMin = totals.remMin,
                    awakeMin = totals.awakeMin,
                    // A typed wake-up count wins; otherwise the stage count,
                    // which is null rather than zero when none were reported.
                    wakeups = f.wakeups.toIntOrNull() ?: totals.wakeups,
                    restingHr = f.restingHr.toIntOrNull(),
                    spo2 = f.spo2.toDoubleOrNull(),
                    alertness = f.alertness,
                    energy3pm = existing?.energy3pm,
                    alcoholUnits = f.alcoholUnits.toDoubleOrNull(),
                    lastMeal = f.lastMeal,
                    exercise = f.exercise,
                    roomTempC = f.roomTempC.toDoubleOrNull(),
                    notes = f.notes,
                    editedFields = f.editedFields,
                )

            if (blocks.isNotEmpty()) {
                nights.saveNightWithStages(night, blocks)
            } else {
                nights.upsert(night)
            }
            pendingStageBlocks = emptyList()
            // The reminder has served its purpose once the night is saved.
            com.healthy.app.notify.MorningNotifier.clear(getApplication())
            _form.value = _form.value.copy(existing = true, savedAt = System.currentTimeMillis())
            onDone()
        }
    }

    /**
     * Turns `HH:mm` into an instant on the logical day [date].
     *
     * A time before the boundary belongs to the next calendar day, because the
     * logical day runs boundary to boundary. This lets a night start at 02:38 and
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
