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

    suspend fun searchProducts(term: String): List<Product> =
        db.productDao().allForExport().filter { it.name.contains(term, ignoreCase = true) }.take(20)
}
