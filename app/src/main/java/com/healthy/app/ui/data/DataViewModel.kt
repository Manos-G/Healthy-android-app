package com.healthy.app.ui.data

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.healthy.app.data.HealthyDatabase
import com.healthy.app.data.SettingsStore
import com.healthy.app.data.export.Csv
import com.healthy.app.data.export.JsonBackup
import com.healthy.app.health.HealthReader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class ExportKind(val fileName: String, val mime: String, val label: String) {
    NightsCsv("healthy-nights.csv", "text/csv", "Nights, CSV"),
    DrinksCsv("healthy-drinks.csv", "text/csv", "Drinks, CSV"),
    NotesCsv("healthy-notes.csv", "text/csv", "Notes, CSV"),
    EverythingJson("healthy-backup.json", "application/json", "Everything, JSON"),
}

/**
 * Export (spec 5.4), pulled forward from step 17.
 *
 * The reason it is here early: from step 3 the app holds real logged days, and
 * a device test run uninstalls the app and takes the database with it. Until an
 * export exists there is no way back from that.
 *
 * Writing goes through the Storage Access Framework, so the app never picks a
 * folder and needs no storage permission (spec 5.4).
 */
class DataViewModel(app: Application) : AndroidViewModel(app) {

    private val db = HealthyDatabase.get(app)
    private val settingsStore = SettingsStore(app)

    private val _status = MutableStateFlow<String?>(null)
    val status: StateFlow<String?> = _status.asStateFlow()

    private val _counts = MutableStateFlow(Counts())
    val counts: StateFlow<Counts> = _counts.asStateFlow()

    data class Counts(
        val nights: Int = 0,
        val drinks: Int = 0,
        val notes: Int = 0,
        val weights: Int = 0,
    )

    init {
        refreshCounts()
    }

    fun refreshCounts() {
        viewModelScope.launch {
            _counts.value = withContext(Dispatchers.IO) {
                Counts(
                    nights = db.nightDao().allForExport().size,
                    drinks = db.drinkDao().allForExport().size,
                    notes = db.noteDao().allForExport().size,
                    weights = db.weightDao().allForExport().size,
                )
            }
        }
    }

    fun export(kind: ExportKind, target: Uri) {
        viewModelScope.launch {
            val result = runCatching {
                val payload = withContext(Dispatchers.IO) { render(kind) }
                withContext(Dispatchers.IO) {
                    getApplication<Application>().contentResolver
                        .openOutputStream(target, "wt")
                        ?.use { it.write(payload.toByteArray()) }
                        ?: error("could not open the chosen file")
                }
                payload.length
            }
            _status.value = result.fold(
                onSuccess = { bytes -> "Wrote ${kind.fileName}, ${bytes / 1024 + 1} KB." },
                onFailure = { "Export failed: ${it.message}" },
            )
            refreshCounts()
        }
    }

    private suspend fun render(kind: ExportKind): String = when (kind) {
        ExportKind.NightsCsv -> Csv.nights(db.nightDao().allForExport())
        ExportKind.DrinksCsv -> Csv.drinks(db.drinkDao().allForExport())
        ExportKind.NotesCsv -> Csv.notes(db.noteDao().allForExport())
        ExportKind.EverythingJson -> JsonBackup.build(
            data = JsonBackup.Everything(
                nights = db.nightDao().allForExport(),
                stageBlocks = db.nightDao().allForExport().flatMap { db.nightDao().stageBlocks(it.date) },
                drinks = db.drinkDao().allForExport(),
                customDrinks = db.customDrinkDao().allForExport(),
                weights = db.weightDao().allForExport(),
                products = db.productDao().allForExport(),
                meals = db.mealDao().allForExport(),
                recipes = db.recipeDao().allForExport(),
                recipeItems = db.recipeDao().allItemsForExport(),
                notes = db.noteDao().allForExport(),
                settings = settingsStore.settings.first(),
            ),
            exportedAt = System.currentTimeMillis(),
            dbVersion = HealthyDatabase.VERSION,
        )
    }

    private val _survey = MutableStateFlow<String?>(null)
    val survey: StateFlow<String?> = _survey.asStateFlow()

    /**
     * Reads a week of Health Connect and reports what is actually there.
     *
     * START-HERE asks questions the code cannot answer by reasoning: whether
     * the watch writes awake blocks at all, and how recent the heart rate is.
     * This answers them from the device.
     */
    fun runSurvey() {
        viewModelScope.launch {
            _survey.value = "Reading…"
            val result = HealthReader(getApplication()).survey(days = 7)
            _survey.value = when (result) {
                null -> "Health Connect is unavailable or permission is not granted."
                else -> buildString {
                    append("Last 7 days\n")
                    append("Sleep sessions: ${result.sessions}")
                    append(", with stages: ${result.sessionsWithStages}\n")
                    append("Stage types seen: ")
                    append(if (result.stageTypesSeen.isEmpty()) "none" else result.stageTypesSeen.sorted().joinToString(", "))
                    append("\nAwake blocks: ${result.awakeBlockCount}")
                    if (result.awakeBlockCount == 0) append("  (so wake-ups read \"not reported\")")
                    append("\nHeart rate samples: ${result.heartRateSamples}\n")
                    append("Most recent heart rate: ")
                    append(
                        result.mostRecentHeartRate?.let {
                            java.time.Instant.ofEpochMilli(it)
                                .atZone(java.time.ZoneId.systemDefault())
                                .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
                        } ?: "none in this window"
                    )
                    append("\nBlood oxygen records: ${result.spo2Samples}")
                    append("\nSleep written by: ")
                    append(sourcesOf(result.sleepSources))
                    append("\nHeart rate written by: ")
                    append(sourcesOf(result.heartRateSources))
                }
            }
        }
    }

    private fun sourcesOf(packages: Set<String>): String =
        if (packages.isEmpty()) "nothing" else packages.joinToString(", ") { it.substringAfterLast('.') }

    fun clearStatus() {
        _status.value = null
    }
}
