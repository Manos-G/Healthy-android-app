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
     * The real value from the Open Food Facts record for Red Bull, checked
     * against the tin: 0.032 grams per 100 ml is 32 mg per 100 ml, so a 250 ml
     * can holds 80 mg.
     *
     * Spec 11.3 says multiply by 10, which would give 0.8 mg. Grams to
     * milligrams is a factor of 1000. An earlier version here used 10000 and
     * would have logged 800 mg for one can — the kind of error that only shows
     * up against a real product, which is why this test uses the real number.
     */
    @Test
    fun `caffeine converts from grams per 100ml to mg for the whole can`() {
        val p = found(
            """{"product":{"product_name":"Red Bull","quantity":"250ml",
               "nutriments":{"caffeine_100g":0.032}}}"""
        )
        assertEquals(80, p.mg)
    }

    /** A serving figure is already the amount in the container. */
    @Test
    fun `caffeine_serving is preferred and needs no volume`() {
        val p = found(
            """{"product":{"product_name":"Red Bull","quantity":"250ml",
               "nutriments":{"caffeine_100g":0.032,"caffeine_serving":0.08}}}"""
        )
        assertEquals(80, p.mg)
    }

    @Test
    fun `a 330 ml cola with a caffeine value scales to the can`() {
        // 0.0097 g/100ml is 9.7 mg/100ml, so 330 ml holds about 32 mg.
        val p = found(
            """{"product":{"product_name":"Cola","quantity":"330 ml",
               "nutriments":{"caffeine_100g":0.0097}}}"""
        )
        assertEquals(32, p.mg)
    }

    /** The field is absent far more often than not; the user is asked then. */
    /**
     * The common case. Checked against the live database: Coca-Cola and
     * Coca-Cola Zero carry no caffeine figure at all, while Red Bull does. The
     * user is asked once and never again for that barcode.
     */
    @Test
    fun `a missing caffeine value stays absent rather than becoming zero`() {
        val p = found("""{"product":{"product_name":"Cola","quantity":"330 ml","nutriments":{}}}""")
        assertNull(p.mg)
    }

    /**
     * Reported from the phone: a Hell white peach scanned as 100 ml when the
     * can holds 250. The record carries a caffeine figure and no quantity, so
     * the only honest reading of "0.032 g per 100 g" is 32 mg per 100 ml with
     * the container size unknown.
     *
     * The figure must not be dressed up as a whole can, and the volume must
     * stay null rather than default to 100, because a null is a question the
     * review dialog can ask and a 100 is a wrong answer nobody is shown.
     */
    @Test
    fun `no quantity leaves the volume unknown rather than assuming 100 ml`() {
        val p = found(
            """{"product":{"product_name":"Hell White Peach","brands":"Hell",
               "nutriments":{"caffeine_100g":0.032}}}"""
        )
        assertNull(p.volumeMl)
        assertEquals(32, p.mg)
        // What the user corrects it to in the review dialog.
        assertEquals(80, OpenFoodFacts.totalMg(32, 100, 250))
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

    /**
     * What a label actually states: a figure against a reference volume, and
     * separately how much the container holds. Conflating the two turns a
     * 330 ml cola into 32 mg instead of 106.
     */
    @Test
    fun `a per-100ml label scales to the container`() {
        assertEquals(106, OpenFoodFacts.totalMg(mgPerReference = 32, referenceMl = 100, containerMl = 330))
        assertEquals(80, OpenFoodFacts.totalMg(32, 100, 250))
        assertEquals(160, OpenFoodFacts.totalMg(32, 100, 500))
    }

    @Test
    fun `a label giving the whole can is the reference equalling the container`() {
        assertEquals(80, OpenFoodFacts.totalMg(mgPerReference = 80, referenceMl = 250, containerMl = 250))
    }

    @Test
    fun `a nonsense reference volume does not divide by zero`() {
        assertEquals(80, OpenFoodFacts.totalMg(80, 0, 250))
        assertEquals(80, OpenFoodFacts.totalMg(80, 100, 0))
    }

    /** Spec 11.5: the app reaches exactly one host. */
    @Test
    fun `the only host is world openfoodfacts org`() {
        assertEquals("world.openfoodfacts.org", OpenFoodFacts.HOST)
    }
}
