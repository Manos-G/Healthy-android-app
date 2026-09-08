package com.healthy.app.ui.recipe

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.healthy.app.analysis.Nutrition
import com.healthy.app.analysis.Recipes
import com.healthy.app.data.HealthyDatabase
import com.healthy.app.data.entity.MealEntry
import com.healthy.app.data.entity.Product
import com.healthy.app.data.entity.Recipe
import com.healthy.app.data.entity.RecipeItem
import com.healthy.app.health.HealthWriter
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** A recipe with its nutrients worked out, ready to show or log. */
data class RecipeCard(
    val recipe: Recipe,
    val items: List<RecipeItem>,
    val resolved: Recipes.Resolved?,
    val problem: Recipes.Problem?,
)

/** An ingredient being added, before the recipe is saved. */
data class DraftItem(
    val name: String,
    val grams: Double,
    val barcode: String? = null,
    val childRecipeId: Long? = null,
)

@OptIn(ExperimentalCoroutinesApi::class)
class RecipeViewModel(app: Application) : AndroidViewModel(app) {

    private val db = HealthyDatabase.get(app)
    private val recipes = db.recipeDao()
    private val writer = HealthWriter(app)

    private val _draft = MutableStateFlow<List<DraftItem>>(emptyList())
    val draft: StateFlow<List<DraftItem>> = _draft.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    private val refresh = MutableStateFlow(0)

    /** Most recent first, so a repeated meal is one tap away (spec 13.6). */
    val cards: StateFlow<List<RecipeCard>> =
        combine(recipes.observeAll(), refresh) { list, _ -> list }
            .map { list ->
                val products = db.productDao().allForExport().associateBy { it.barcode }
                val all = list.associateWith { recipes.items(it.id) }
                val children = all.entries.associate { (r, i) -> r.id to (r to i) }

                list.map { recipe ->
                    val items = all[recipe].orEmpty()
                    val result = Recipes.resolve(recipe, items, products, children)
                    RecipeCard(
                        recipe = recipe,
                        items = items,
                        resolved = result.getOrNull(),
                        problem = (result.exceptionOrNull() as? com.healthy.app.analysis.RecipeError)?.problem,
                    )
                }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun addDraftItem(item: DraftItem) {
        _draft.value = _draft.value + item
    }

    fun removeDraftItem(index: Int) {
        _draft.value = _draft.value.filterIndexed { i, _ -> i != index }
    }

    fun clearDraft() {
        _draft.value = emptyList()
    }

    /** The raw weight of everything added so far, for the cooked-weight prompt. */
    fun draftRawGrams(): Double = _draft.value.sumOf { it.grams }

    /**
     * What the draft adds up to, and a sentence saying so.
     *
     * Resolved against the same product table the saved recipe will use, so
     * the figure shown while building is the figure that will be stored. An
     * ingredient with nothing behind it is named rather than quietly counted
     * as nothing, which is the whole of the bug this answers.
     */
    suspend fun draftTotals(): Pair<Double, String> {
        val items = _draft.value
        if (items.isEmpty()) return 0.0 to ""
        val products = db.productDao().allForExport().associateBy { it.barcode }
        var totals = Nutrition.Totals()
        val unknown = mutableListOf<String>()
        for (item in items) {
            val product = item.barcode?.let(products::get)
            if (product?.kcal100 == null) {
                unknown += item.name
                continue
            }
            totals += Nutrition.forGrams(product, item.grams)
        }
        val grams = items.sumOf { it.grams }
        val sentence = buildString {
            append("${grams.toInt()} g of ingredients, ${totals.kcal.toInt()} kcal")
            append(" · P ${totals.protein.toInt()} C ${totals.carbs.toInt()} F ${totals.fat.toInt()}")
            if (unknown.isNotEmpty()) {
                append(". No values for ")
                append(unknown.distinct().joinToString(", "))
                append(" — that part counts as zero.")
            } else {
                append(".")
            }
        }
        return totals.kcal to sentence
    }

    /**
     * Saves a recipe (spec 13.3, 13.4).
     *
     * The cooked weight is required, not optional: without it every portion
     * logged from this recipe afterwards is wrong by however much water the
     * dish lost or gained.
     */
    fun save(name: String, cookedGrams: Double, portions: Int, onDone: () -> Unit = {}) {
        if (name.isBlank() || cookedGrams <= 0) {
            _message.value = "A recipe needs a name and the weight of the finished dish."
            return
        }
        viewModelScope.launch {
            val id = recipes.saveRecipe(
                Recipe(name = name.trim(), cookedGrams = cookedGrams, portions = portions),
                _draft.value.map {
                    RecipeItem(
                        recipeId = 0,
                        barcode = it.barcode,
                        childRecipeId = it.childRecipeId,
                        name = it.name,
                        grams = it.grams,
                    )
                },
            )
            _draft.value = emptyList()
            _message.value = "Saved “${name.trim()}”. It is one tap from now on."
            refresh.value++
            onDone()
        }
    }

    /**
     * Logs a portion of a dish (spec 13.5), writing it to Health Connect like
     * any other meal so other apps see it too.
     */
    fun logPortion(card: RecipeCard, grams: Double, mealType: String = MealEntry.DINNER) {
        val resolved = card.resolved ?: return
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            db.mealDao().insert(
                MealEntry(
                    timestamp = now,
                    mealType = mealType,
                    recipeId = card.recipe.id,
                    grams = grams,
                )
            )
            val totals = Recipes.nutrientsFor(resolved, grams)
            writer.writeNutrition(
                at = now,
                mealType = androidx.health.connect.client.records.MealType.MEAL_TYPE_DINNER,
                name = card.recipe.name,
                kcal = totals.kcal,
                proteinG = totals.protein,
                carbsG = totals.carbs,
                fatG = totals.fat,
            )
            _message.value = "Logged ${grams.toInt()} g of ${card.recipe.name}, " +
                "${totals.kcal.toInt()} kcal."
        }
    }

    fun delete(card: RecipeCard) {
        viewModelScope.launch {
            recipes.deleteRecipe(card.recipe)
            refresh.value++
        }
    }

    fun clearMessage() {
        _message.value = null
    }

    /**
     * Ingredients to choose from: the built-in catalog and everything the user
     * has scanned or typed before, in one list.
     *
     * The catalog is offered as [Product] so the picker has one kind of thing
     * to show and the recipe has one kind of thing to store. A stored product
     * of the same code wins, because that is either a correction the user made
     * or a real package they scanned.
     */
    suspend fun searchIngredients(term: String): List<Product> {
        val stored = db.productDao().allForExport().filter { it.kind == Product.KIND_FOOD }
        val storedCodes = stored.map { it.barcode }.toSet()
        val builtIn = com.healthy.app.core.IngredientCatalog.PRODUCTS
            .filterNot { it.barcode in storedCodes }
        val needle = term.trim()
        val all = stored + builtIn
        val matches = if (needle.isEmpty()) {
            all
        } else {
            all.filter { it.name.contains(needle, ignoreCase = true) }
        }
        // Prefix first, so "ba" reaches Banana before Strawberries.
        val (starts, rest) = matches.partition {
            needle.isNotEmpty() && it.name.startsWith(needle, ignoreCase = true)
        }
        return (starts + rest).take(60)
    }

    /**
     * Adds a chosen ingredient, storing the product first.
     *
     * A built-in ingredient has to exist in the `product` table before the
     * recipe can point at it, or the resolver will look up its barcode and
     * find nothing — which is the shape of the bug this fixes.
     */
    fun addIngredient(product: Product, grams: Double) {
        viewModelScope.launch {
            if (db.productDao().byBarcode(product.barcode) == null) {
                db.productDao().upsert(product)
            }
            addDraftItem(DraftItem(product.name, grams, barcode = product.barcode))
            refresh.value++
        }
    }

    /** A food with no barcode and not in the catalog (spec 12.4). */
    fun addManualIngredient(
        name: String,
        grams: Double,
        kcal100: Double?,
        protein100: Double?,
        carbs100: Double?,
        fat100: Double?,
        fibre100: Double?,
    ) {
        viewModelScope.launch {
            val product = Product(
                barcode = Nutrition.manualBarcode(name),
                kind = Product.KIND_FOOD,
                name = name.trim(),
                kcal100 = kcal100,
                protein100 = protein100,
                carbs100 = carbs100,
                fat100 = fat100,
                fibre100 = fibre100,
                source = Product.USER,
            )
            db.productDao().upsert(product)
            addDraftItem(DraftItem(product.name, grams, barcode = product.barcode))
            refresh.value++
        }
    }

    /** The products a recipe's items point at, for sharing them alongside it. */
    suspend fun productsFor(items: List<RecipeItem>): Map<String, Product> {
        val codes = items.mapNotNull { it.barcode }.toSet()
        if (codes.isEmpty()) return emptyMap()
        return db.productDao().allForExport().filter { it.barcode in codes }
            .associateBy { it.barcode }
    }

    /** After a scan: the product is already stored, so only the weight is left. */
    suspend fun productFor(barcode: String): Product? = db.productDao().byBarcode(barcode)
}
