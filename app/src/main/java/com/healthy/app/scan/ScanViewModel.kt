package com.healthy.app.scan

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.healthy.app.data.HealthyDatabase
import com.healthy.app.data.entity.Drink
import com.healthy.app.data.entity.Product
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * What happens after a barcode is read (spec 11).
 *
 * The local table is consulted first and a hit logs the drink immediately with
 * no network request at all. That is what makes a scan work in flight mode
 * once the code has been seen, and it is the reason the table exists.
 */
class ScanViewModel(app: Application) : AndroidViewModel(app) {

    private val products = HealthyDatabase.get(app).productDao()
    private val drinks = HealthyDatabase.get(app).drinkDao()

    sealed interface State {
        data object Idle : State

        /** Known code, drink logged, no network touched. */
        data class Logged(val product: Product, val fromCache: Boolean) : State

        /**
         * A barcode the app has never seen. Spec 11.1: it asks which kind it
         * is, because a drink and a food lead to completely different
         * questions and guessing wrong wastes the user's time twice.
         */
        data class NeedsKind(val product: Product) : State

        /**
         * A food. The caller hands this to the food screen, which asks how
         * much was eaten (spec 12.3) rather than how much caffeine it holds.
         */
        data class NeedsPortion(val product: Product) : State

        /**
         * Found online but with no caffeine value; the user reads the can.
         *
         * [suggestion] is the closest drink in the built-in catalog by name,
         * offered as a starting point. It is a guess from a similar product,
         * never presented as this product's own figure.
         */
        data class NeedsCaffeine(
            val product: Product,
            val suggestion: CaffeineReference.Guess? = null,
        ) : State

        /** Not in the local table and not in Open Food Facts either. */
        data class Unknown(val barcode: String, val reason: String?) : State

        data class Working(val barcode: String) : State
    }

    private val _state = MutableStateFlow<State>(State.Idle)
    val state: StateFlow<State> = _state.asStateFlow()

    /**
     * [suggestedKind] is where the scan came from — the Today screen means a
     * drink is likely, the food screen a food. It only pre-selects the choice
     * for a barcode the app has never seen; a stored `kind` always wins.
     */
    fun onBarcode(barcode: String, suggestedKind: String = Product.KIND_DRINK) {
        viewModelScope.launch {
            _state.value = State.Working(barcode)

            val cached = products.byBarcode(barcode)
            if (cached != null) {
                // Spec 11.1: the stored kind decides which question is asked.
                route(cached)
                return@launch
            }

            // Only now, and only for this one host (spec 11.5).
            when (val result = withContext(Dispatchers.IO) { OpenFoodFacts.lookup(barcode, suggestedKind) }) {
                is OpenFoodFacts.Result.Found -> {
                    // Not stored yet: the user says what it is first, because
                    // the kind decides everything that follows.
                    _state.value = State.NeedsKind(result.product.copy(kind = suggestedKind))
                }

                OpenFoodFacts.Result.Unknown ->
                    _state.value = State.Unknown(barcode, null)

                // A failure shows the manual dialog rather than an error and a
                // dead end (spec 11.5).
                is OpenFoodFacts.Result.Failed ->
                    _state.value = State.Unknown(barcode, result.reason)
            }
        }
    }

    /** Sends a known product to the question its kind calls for (spec 11.1). */
    private suspend fun route(product: Product) {
        _state.value = when {
            product.kind == Product.KIND_FOOD -> State.NeedsPortion(product)
            product.mg != null -> {
                logDrink(product)
                State.Logged(product, fromCache = true)
            }
            else -> State.NeedsCaffeine(product, guessFor(product))
        }
    }

    /** The user has said which kind a new barcode is (spec 11.1). */
    fun chooseKind(product: Product, kind: String) {
        viewModelScope.launch {
            val typed = product.copy(kind = kind)
            products.upsert(typed)
            route(typed)
        }
    }

    /**
     * Stores what the user read off the can, so the app never asks again for
     * that barcode (spec 11.4) — including in flight mode.
     */
    /**
     * [mgPerReference] and [referenceMl] are what the label says — "32 mg per
     * 100 ml" — and [containerMl] is how much the can holds. The product table
     * stores the total for the container, because that is what a tap logs.
     */
    fun saveCaffeine(
        product: Product,
        mgPerReference: Int,
        referenceMl: Int,
        containerMl: Int,
        name: String,
    ) {
        viewModelScope.launch {
            val completed = product.copy(
                name = name.ifBlank { product.name },
                mg = OpenFoodFacts.totalMg(mgPerReference, referenceMl, containerMl),
                volumeMl = containerMl.takeIf { it > 0 } ?: product.volumeMl,
                source = Product.USER,
            )
            products.upsert(completed)
            logDrink(completed)
            _state.value = State.Logged(completed, fromCache = false)
        }
    }

    fun saveNewProduct(
        barcode: String,
        name: String,
        mgPerReference: Int,
        referenceMl: Int,
        containerMl: Int,
    ) {
        saveCaffeine(
            Product(barcode = barcode, kind = Product.KIND_DRINK, name = name, source = Product.USER),
            mgPerReference = mgPerReference,
            referenceMl = referenceMl,
            containerMl = containerMl,
            name = name,
        )
    }

    /**
     * A starting figure when the database has none.
     *
     * Open Food Facts rarely carries caffeine for colas, and three empty boxes
     * are little help when the tin is already in the bin. The guess is shown
     * as a guess, with what it was based on, and the user overwrites it with
     * whatever the label says.
     */
    private fun guessFor(product: Product): CaffeineReference.Guess? =
        CaffeineReference.forName(
            listOfNotNull(product.brand, product.name).joinToString(" ")
        )

    fun dismiss() {
        _state.value = State.Idle
    }

    private suspend fun logDrink(product: Product) {
        drinks.insert(
            Drink(
                name = listOfNotNull(product.brand, product.name).joinToString(" ").trim()
                    .ifBlank { product.name },
                mg = product.mg ?: 0,
                timestamp = System.currentTimeMillis(),
                volumeMl = product.volumeMl ?: 0,
            )
        )
    }
}
