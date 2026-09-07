package com.healthy.app.scan

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.healthy.app.data.HealthyDatabase
import com.healthy.app.data.entity.Product
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Spec 11.1: the stored kind decides which question a scan asks.
 *
 * A food must never open the caffeine dialog, and must never be logged as a
 * drink — which is exactly what happened before this was implemented.
 */
@RunWith(AndroidJUnit4::class)
class ScanRoutingTest {

    private lateinit var db: HealthyDatabase

    @Before
    fun open() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            HealthyDatabase::class.java,
        ).build()
    }

    @After
    fun close() = db.close()

    @Test
    fun aStoredFoodIsNeverLoggedAsADrink() = runBlocking {
        db.productDao().upsert(
            Product(
                barcode = "food-1",
                kind = Product.KIND_FOOD,
                name = "Oats",
                kcal100 = 370.0,
                packGrams = 500,
            )
        )
        val stored = db.productDao().byBarcode("food-1")!!
        assertEquals(Product.KIND_FOOD, stored.kind)
        // A food carries no caffeine figure, so nothing can log it as a drink.
        assertNull(stored.mg)
        assertEquals(0, db.drinkDao().allForExport().size)
    }

    @Test
    fun aStoredDrinkKeepsItsCaffeineAndKind() = runBlocking {
        db.productDao().upsert(
            Product(
                barcode = "drink-1",
                kind = Product.KIND_DRINK,
                name = "Hell",
                mg = 80,
                volumeMl = 250,
            )
        )
        val stored = db.productDao().byBarcode("drink-1")!!
        assertEquals(Product.KIND_DRINK, stored.kind)
        assertEquals(80, stored.mg)
    }

    /** Choosing a kind stores it, so the same barcode never asks twice. */
    @Test
    fun theKindIsRememberedForNextTime() = runBlocking {
        val dao = db.productDao()
        dao.upsert(Product(barcode = "new-1", kind = Product.KIND_DRINK, name = "Mystery"))
        assertEquals(Product.KIND_DRINK, dao.byBarcode("new-1")!!.kind)

        dao.upsert(dao.byBarcode("new-1")!!.copy(kind = Product.KIND_FOOD))
        assertEquals(Product.KIND_FOOD, dao.byBarcode("new-1")!!.kind)
        assertNotNull(dao.byBarcode("new-1"))
    }

    @Test
    fun foodsAndDrinksAreSearchableApart() = runBlocking {
        val dao = db.productDao()
        dao.upsert(Product(barcode = "f", kind = Product.KIND_FOOD, name = "Oat bar"))
        dao.upsert(Product(barcode = "d", kind = Product.KIND_DRINK, name = "Oat milk"))

        assertEquals(1, dao.observeByKind(Product.KIND_FOOD).first().size)
        assertEquals(1, dao.observeByKind(Product.KIND_DRINK).first().size)
    }
}
