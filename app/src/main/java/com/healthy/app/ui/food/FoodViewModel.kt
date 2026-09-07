package com.healthy.app.ui.food

import android.app.Application
import androidx.health.connect.client.records.MealType
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.healthy.app.analysis.Nutrition
import com.healthy.app.core.HealthyDay
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
)

data class FoodState(
    val today: List<LoggedItem> = emptyList(),
    val totals: Nutrition.Totals = Nutrition.Totals(),
    val searchResults: List<Product> = emptyList(),
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

    private val day = MutableStateFlow(HealthyDay.today())

    val state: StateFlow<FoodState> =
        combine(
            day.flatMapLatest { d ->
                meals.observeBetween(HealthyDay.startOf(d), HealthyDay.endOf(d))
            },
            _query.flatMapLatest { q ->
                if (q.isBlank()) kotlinx.coroutines.flow.flowOf(emptyList())
                else products.search(q)
            },
        ) { entries, results ->
            val byBarcode = entries.mapNotNull { it.barcode }.distinct()
                .mapNotNull { code -> products.byBarcode(code)?.let { code to it } }
                .toMap()

            val items = entries.map { entry ->
                val product = entry.barcode?.let(byBarcode::get)
                LoggedItem(
                    entry = entry,
                    product = product,
                    totals = product?.let { Nutrition.forGrams(it, entry.grams) } ?: Nutrition.Totals(),
                )
            }
            FoodState(
                today = items.sortedByDescending { it.entry.timestamp },
                totals = items.fold(Nutrition.Totals()) { acc, i -> acc + i.totals },
                searchResults = results,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), FoodState())

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
    fun log(product: Product, grams: Double, mealType: String) {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
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
            log(product, grams, mealType)
        }
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
