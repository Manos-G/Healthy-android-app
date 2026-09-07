package com.healthy.app.scan

import com.healthy.app.data.entity.Product
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Reading an Open Food Facts response (spec 11.3). */
class OpenFoodFactsTest {

    private fun found(json: String): Product {
        val result = OpenFoodFacts.parse("5201234567890", Product.KIND_DRINK, json)
        assertTrue("expected a product, got $result", result is OpenFoodFacts.Result.Found)
        return (result as OpenFoodFacts.Result.Found).product
    }

    @Test
    fun `name brand and volume are read`() {
        val p = found(
            """{"product":{"product_name":"Hell Classic","brands":"Hell","quantity":"250 ml",
               "nutriments":{}}}"""
        )
        assertEquals("Hell Classic", p.name)
        assertEquals("Hell", p.brand)
        assertEquals(250, p.volumeMl)
        assertEquals(Product.OFF, p.source)
    }

    /**
     * caffeine_100g is grams per 100 ml, so 0.0032 is 32 mg per 100 ml, and a
     * 250 ml can holds 80 mg (spec 11.3).
     */
    @Test
    fun `caffeine converts from grams per 100ml to mg for the can`() {
        val p = found(
            """{"product":{"product_name":"Hell","quantity":"250 ml",
               "nutriments":{"caffeine_100g":0.0032}}}"""
        )
        assertEquals(80, p.mg)
    }

    /** The field is absent far more often than not; the user is asked then. */
    @Test
    fun `a missing caffeine value stays absent rather than becoming zero`() {
        val p = found("""{"product":{"product_name":"Water","quantity":"500 ml","nutriments":{}}}""")
        assertNull(p.mg)
    }

    @Test
    fun `nutrients for food are read, including the two that matter for sleep`() {
        val p = found(
            """{"product":{"product_name":"Oats","nutriments":{
               "energy-kcal_100g":370,"proteins_100g":13.5,"carbohydrates_100g":58.7,
               "sugars_100g":1.1,"fat_100g":6.5,"fiber_100g":10.1,"salt_100g":0.02,
               "magnesium_100g":0.177,"vitamin-d_100g":0.0}}}"""
        )
        assertEquals(370.0, p.kcal100!!, 0.001)
        assertEquals(13.5, p.protein100!!, 0.001)
        assertEquals(10.1, p.fibre100!!, 0.001)
        assertEquals(0.177, p.magnesium100!!, 0.0001)
    }

    @Test
    fun `an unknown barcode is reported as unknown`() {
        val result = OpenFoodFacts.parse("000", Product.KIND_DRINK, """{"status":0}""")
        assertTrue(result is OpenFoodFacts.Result.Unknown)
    }

    @Test
    fun `quantity parsing handles the units the field actually uses`() {
        assertEquals(250, OpenFoodFacts.parseQuantityMl("250 ml"))
        assertEquals(330, OpenFoodFacts.parseQuantityMl("330ml"))
        assertEquals(1500, OpenFoodFacts.parseQuantityMl("1.5 L"))
        assertEquals(1500, OpenFoodFacts.parseQuantityMl("1,5 l"))
        assertEquals(330, OpenFoodFacts.parseQuantityMl("33 cl"))
        assertEquals(30, OpenFoodFacts.parseQuantityMl("30 g"))
    }

    @Test
    fun `free text that is not a quantity gives nothing rather than a guess`() {
        assertNull(OpenFoodFacts.parseQuantityMl(""))
        assertNull(OpenFoodFacts.parseQuantityMl(null))
        assertNull(OpenFoodFacts.parseQuantityMl("family pack"))
    }

    /** Spec 11.5: the app reaches exactly one host. */
    @Test
    fun `the only host is world openfoodfacts org`() {
        assertEquals("world.openfoodfacts.org", OpenFoodFacts.HOST)
    }
}
