package com.healthy.app.ui.food

import android.app.Application
import androidx.health.connect.client.records.MealType
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.healthy.app.analysis.Nutrition
import com.healthy.app.core.HealthyDay
import com.healthy.app.core.LogWindow
import com.healthy.app.data.HealthyDatabase
import com.healthy.app.data.entity.MealEntry
import com.healthy.app.data.entity.Product
import com.healthy.app.health.HealthWriter
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class LoggedItem(
    val entry: MealEntry,
    val product: Product?,
    val totals: Nutrition.Totals,
    /**
     * What to show. A portion of a recipe has no product, and reading the name
     * off the product alone is what put "Unknown" against every dish logged.
     */
    val name: String = product?.name ?: "Unknown",
)

data class FoodState(
    /** What the list shows, which follows [window] and may cross the boundary. */
    val listed: List<LoggedItem> = emptyList(),
    /**
     * Totals for one logical day, always.
     *
     * The energy target is a daily figure, so a percentage of a rolling
     * 24 hours would be a percentage of nothing in particular. When the list
     * is rolling these are today's; when a past day is selected they are that
     * day's, so the card always describes a real day.
     */
    val totals: Nutrition.Totals = Nutrition.Totals(),
    val window: LogWindow = LogWindow.Rolling,
    /**
     * Real values in the defaults, because Compose draws this state once
     * before the first emission arrives and a screen must not depend on
     * never being shown an initial value.
     */
    val totalsDay: String = HealthyDay.today(),
    val nowMillis: Long = System.currentTimeMillis(),
    val searchResults: List<Product> = emptyList(),
    /** The last seven logged days, for the mean beside today (spec 16.5). */
    val recentDays: List<Nutrition.Totals> = emptyList(),
)

@OptIn(ExperimentalCoroutinesApi::class)
class FoodViewModel(app: Application) : AndroidViewModel(app) {

    private val db = HealthyDatabase.get(app)
    private val meals = db.mealDao()
    private val products = db.productDao()
    private val writer = HealthWriter(app)

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    /** A product waiting for the user to say how much of it they ate. */
    private val _pendingPortion = MutableStateFlow<Product?>(null)
    val pendingPortion: StateFlow<Product?> = _pendingPortion.asStateFlow()

    private val window = MutableStateFlow<LogWindow>(LogWindow.Rolling)

    /** Re-read on each change so "the last 24 hours" moves with the clock. */
    private val nowAtChange = MutableStateFlow(System.currentTimeMillis())

    fun setWindow(value: LogWindow) {
        nowAtChange.value = System.currentTimeMillis()
        window.value = value
    }

    val state: StateFlow<FoodState> =
        combine(
            combine(window, nowAtChange) { w, now -> w to now }.flatMapLatest { (w, now) ->
                val day = w.dayForTotals(now)
                // Wide enough for both the list's window and the day the
                // totals are measured over, which are not the same stretch.
                val from = minOf(w.startMillis(now), HealthyDay.startOf(day))
                val to = maxOf(w.endMillis(now), HealthyDay.endOf(day))
                meals.observeBetween(from, to).map { Triple(it, w, now) }
            },
            _query.flatMapLatest { q ->
                if (q.isBlank()) kotlinx.coroutines.flow.flowOf(emptyList())
                else products.search(q)
            },
        ) { (entries, w, now), results ->
            val byBarcode = entries.mapNotNull { it.barcode }.distinct()
                .mapNotNull { code -> products.byBarcode(code)?.let { code to it } }
                .toMap()
            // A logged portion of a dish resolves through the recipe, not a
            // barcode, and nothing here used to know that.
            val dishes = com.healthy.app.analysis.Dishes
                .per100g(db, products.allForExport().associateBy { it.barcode })
            val dishNames = com.healthy.app.analysis.Dishes.names(db)

            val items = entries.map { entry ->
                val product = entry.barcode?.let(byBarcode::get)
                LoggedItem(
                    entry = entry,
                    product = product,
                    totals = Nutrition.forEntry(entry, byBarcode, dishes),
                    name = product?.name
                        ?: entry.recipeId?.let(dishNames::get)
                        ?: "Unknown",
                )
            }
            val day = w.dayForTotals(now)
            val dayStart = HealthyDay.startOf(day)
            val dayEnd = HealthyDay.endOf(day)
            val listStart = w.startMillis(now)
            val listEnd = w.endMillis(now)

            FoodState(
                listed = items
                    .filter { it.entry.timestamp in listStart until listEnd }
                    .sortedByDescending { it.entry.timestamp },
                totals = items
                    .filter { it.entry.timestamp in dayStart until dayEnd }
                    .fold(Nutrition.Totals()) { acc, i -> acc + i.totals },
                window = w,
                totalsDay = day,
                nowMillis = now,
                searchResults = results,
                recentDays = recentDayTotals(),
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), FoodState())

    /** Totals for each of the last seven logged days, oldest first. */
    private suspend fun recentDayTotals(): List<Nutrition.Totals> {
        val from = HealthyDay.startOf(HealthyDay.plusDays(HealthyDay.today(), -7))
        val entries = meals.between(from, System.currentTimeMillis())
        val byBarcode = products.allForExport().associateBy { it.barcode }
        val dishes = com.healthy.app.analysis.Dishes.per100g(db, byBarcode)
        return entries.groupBy { HealthyDay.dayOf(it.timestamp) }
            .toSortedMap()
            .map { (_, dayEntries) -> Nutrition.totalFor(dayEntries, byBarcode, dishes) }
    }

    fun setQuery(value: String) {
        _query.value = value
    }

    fun choosePortionFor(product: Product) {
        _pendingPortion.value = product
    }

    fun cancelPortion() {
        _pendingPortion.value = null
    }

    /**
     * Records a portion and mirrors it into Health Connect (spec 12.5), so
     * other apps can read what was eaten here.
     */
    fun log(product: Product, grams: Double, mealType: String, minutesAgo: Int = 0) {
        viewModelScope.launch {
            val now = System.currentTimeMillis() - minutesAgo * 60_000L
            meals.insert(
                MealEntry(
                    timestamp = now,
                    mealType = mealType,
                    barcode = product.barcode,
                    grams = grams,
                )
            )
            val totals = Nutrition.forGrams(product, grams)
            writer.writeNutrition(
                at = now,
                mealType = mealType.toHealthConnectMealType(),
                name = product.name,
                kcal = totals.kcal,
                proteinG = totals.protein,
                carbsG = totals.carbs,
                fatG = totals.fat,
            )
            _pendingPortion.value = null
        }
    }

    /** A food with no barcode: fruit, bread from a bakery (spec 12.4). */
    fun saveManualFood(
        name: String,
        kcal100: Double?,
        protein100: Double?,
        carbs100: Double?,
        fat100: Double?,
        fibre100: Double?,
        grams: Double,
        mealType: String,
        minutesAgo: Int = 0,
    ) {
        viewModelScope.launch {
            val product = Product(
                barcode = Nutrition.manualBarcode(name),
                kind = Product.KIND_FOOD,
                name = name,
                kcal100 = kcal100,
                protein100 = protein100,
                carbs100 = carbs100,
                fat100 = fat100,
                fibre100 = fibre100,
                source = Product.USER,
            )
            products.upsert(product)
            log(product, grams, mealType, minutesAgo)
        }
    }

    /**
     * Stores an item another phone shared as a QR code (spec 5.4).
     *
     * A shared food joins the product table like any other, so it is
     * searchable and one tap away afterwards. A shared recipe arrives with its
     * ingredients intact.
     */
    fun receiveShared(decoded: com.healthy.app.scan.QrPayload.Decoded) {
        viewModelScope.launch {
            when (decoded) {
                is com.healthy.app.scan.QrPayload.Decoded.Food -> {
                    products.upsert(decoded.product)
                    _shareMessage.value = "Added ${decoded.product.name}. It is in your search now."
                }

                is com.healthy.app.scan.QrPayload.Decoded.Dish -> {
                    // The ingredients first. Saving the recipe alone would
                    // leave it pointing at barcodes this phone has never seen,
                    // and it would read as zero calories.
                    decoded.products.forEach { product ->
                        if (products.byBarcode(product.barcode) == null) products.upsert(product)
                    }
                    db.recipeDao().saveRecipe(decoded.recipe, decoded.items)

                    val known = decoded.items.mapNotNull { it.barcode }.toSet()
                    val missing = decoded.items.count {
                        it.barcode == null || it.barcode !in known
                    }
                    _shareMessage.value = buildString {
                        append("Added the recipe “${decoded.recipe.name}”")
                        append(" with ${decoded.items.size} ingredient")
                        if (decoded.items.size != 1) append("s")
                        append(". It is in your recipes now.")
                        if (missing > 0) {
                            append(" $missing of them arrived without values.")
                        }
                    }
                }

                is com.healthy.app.scan.QrPayload.Decoded.NotOurs ->
                    _shareMessage.value = decoded.reason
            }
        }
    }

    private val _shareMessage = kotlinx.coroutines.flow.MutableStateFlow<String?>(null)
    val shareMessage: kotlinx.coroutines.flow.StateFlow<String?> = _shareMessage

    fun clearShareMessage() {
        _shareMessage.value = null
    }

    fun delete(entry: MealEntry) {
        viewModelScope.launch { meals.delete(entry) }
    }
}

private fun String.toHealthConnectMealType(): Int = when (this) {
    MealEntry.BREAKFAST -> MealType.MEAL_TYPE_BREAKFAST
    MealEntry.LUNCH -> MealType.MEAL_TYPE_LUNCH
    MealEntry.DINNER -> MealType.MEAL_TYPE_DINNER
    else -> MealType.MEAL_TYPE_SNACK
}
