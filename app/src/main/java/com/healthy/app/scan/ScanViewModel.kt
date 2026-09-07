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

        /** Found online but with no caffeine value; the user reads the can. */
        data class NeedsCaffeine(val product: Product) : State

        /** Not in the local table and not in Open Food Facts either. */
        data class Unknown(val barcode: String, val reason: String?) : State

        data class Working(val barcode: String) : State
    }

    private val _state = MutableStateFlow<State>(State.Idle)
    val state: StateFlow<State> = _state.asStateFlow()

    fun onBarcode(barcode: String) {
        viewModelScope.launch {
            _state.value = State.Working(barcode)

            val cached = products.byBarcode(barcode)
            if (cached != null) {
                if (cached.mg != null) {
                    logDrink(cached)
                    _state.value = State.Logged(cached, fromCache = true)
                } else {
                    _state.value = State.NeedsCaffeine(cached)
                }
                return@launch
            }

            // Only now, and only for this one host (spec 11.5).
            when (val result = withContext(Dispatchers.IO) { OpenFoodFacts.lookup(barcode) }) {
                is OpenFoodFacts.Result.Found -> {
                    products.upsert(result.product)
                    if (result.product.mg != null) {
                        logDrink(result.product)
                        _state.value = State.Logged(result.product, fromCache = false)
                    } else {
                        _state.value = State.NeedsCaffeine(result.product)
                    }
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
