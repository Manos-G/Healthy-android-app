package com.healthy.app.ui.data

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.healthy.app.data.HealthyDatabase
import com.healthy.app.data.SettingsStore
import com.healthy.app.data.export.Csv
import com.healthy.app.data.export.JsonBackup
import com.healthy.app.data.export.JsonRestore
import com.healthy.app.health.HealthReader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import androidx.room.withTransaction
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
                null -> "Could not read Health Connect. Either it is unavailable, or one " +
                    "of the permissions has not been granted — reconnect from the Morning tab."
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
                    append("\nWeight records (1 year): ${result.weightRecords}")
                    append("\nWeight written by: ")
                    append(sourcesOf(result.weightSources))
                }
            }
        }
    }

    private fun sourcesOf(packages: Set<String>): String =
        if (packages.isEmpty()) "nothing" else packages.joinToString(", ") { it.substringAfterLast('.') }

    /**
     * Replaces everything on the device with the contents of a backup
     * (spec 5.4).
     *
     * Replace rather than merge, and in one transaction: a half-applied import
     * that mixed two histories would be worse than either. Nothing is deleted
     * until the file has parsed, so a bad file leaves the database untouched.
     */
    fun import(source: Uri) {
        viewModelScope.launch {
            _status.value = "Reading the file…"
            val text = runCatching {
                withContext(Dispatchers.IO) {
                    getApplication<Application>().contentResolver
                        .openInputStream(source)?.bufferedReader()?.use { it.readText() }
                        ?: error("the file could not be opened")
                }
            }.getOrElse {
                _status.value = "Import failed: ${it.message}"
                return@launch
            }

            when (val parsed = JsonRestore.parse(text)) {
                is JsonRestore.Result.Failed -> {
                    _status.value = "Import failed: ${parsed.reason} Nothing was changed."
                }
                is JsonRestore.Result.Ok -> {
                    runCatching { apply(parsed.data) }
                        .onSuccess {
                            _status.value = "Imported ${parsed.data.nights.size} nights, " +
                                "${parsed.data.drinks.size} drinks and " +
                                "${parsed.data.weights.size} weights."
                        }
                        .onFailure { _status.value = "Import failed while writing: ${it.message}" }
                    refreshCounts()
                }
            }
        }
    }

    private suspend fun apply(data: JsonBackup.Everything) = db.withTransaction {
        // One transaction, so a failure part way through rolls the whole
        // import back rather than leaving two histories mixed together.
        val m = db.maintenanceDao()
        m.clearStageBlocks(); m.clearRecipeItems(); m.clearNights(); m.clearDrinks()
        m.clearCustomDrinks(); m.clearWeights(); m.clearProducts(); m.clearMeals()
        m.clearRecipes(); m.clearNotes()

        data.nights.forEach { db.nightDao().upsert(it) }
        data.stageBlocks.groupBy { it.nightDate }.forEach { (date, blocks) ->
            db.nightDao().replaceStageBlocks(date, blocks)
        }
        data.drinks.forEach { db.drinkDao().insert(it) }
        data.customDrinks.forEach { db.customDrinkDao().insert(it) }
        data.weights.forEach { db.weightDao().upsert(it) }
        data.products.forEach { db.productDao().upsert(it) }
        db.mealDao().insertAll(data.meals)
        data.recipes.forEach { db.recipeDao().upsertRecipe(it) }
        db.recipeDao().insertItems(data.recipeItems)
        data.notes.forEach { db.noteDao().insert(it) }

        settingsStore.setTargetBedtime(data.settings.targetBedtime)
        settingsStore.setHalfLifeHours(data.settings.halfLifeHours)
        settingsStore.setBedtimeLimitMg(data.settings.bedtimeLimitMg)
        settingsStore.setFluidTargetMl(data.settings.fluidTargetMl)
    }

    fun clearStatus() {
        _status.value = null
    }
}
