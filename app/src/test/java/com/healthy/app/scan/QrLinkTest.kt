package com.healthy.app.scan

import com.healthy.app.core.IngredientCatalog
import com.healthy.app.data.entity.Product
import com.healthy.app.data.entity.Recipe
import com.healthy.app.data.entity.RecipeItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Sharing a recipe with someone who is not in the room. */
class QrLinkTest {

    private val flour = IngredientCatalog.ALL.first { it.name == "Flour, plain" }.toProduct()
    private val ownProduct = Product(
        barcode = "5201234567890",
        kind = Product.KIND_FOOD,
        name = "Bakery bread",
        kcal100 = 260.0,
        protein100 = 9.0,
        source = Product.USER,
    )
    private val recipe = Recipe(id = 3, name = "Pancakes", cookedGrams = 500.0, portions = 4)
    private val items = listOf(
        RecipeItem(recipeId = 3, barcode = flour.barcode, name = flour.name, grams = 200.0),
        RecipeItem(recipeId = 3, barcode = ownProduct.barcode, name = ownProduct.name, grams = 100.0),
    )
    private val products = mapOf(flour.barcode to flour, ownProduct.barcode to ownProduct)

    @Test
    fun `a link round trips back to the same payload`() {
        val payload = QrPayload.encode(recipe, items, products)
        val link = QrPayload.toLink(payload)
        // Our own scheme. An https link was swallowed by Messenger's in-app
        // browser and landed on a domain nobody here owns.
        assertTrue(link, link.startsWith("healthy://i#"))
        assertEquals(payload, QrPayload.fromLink(link))
    }

    /**
     * The route that has to work when nothing else does: the receiver pastes
     * the whole chat message, code buried in the middle of it.
     */
    @Test
    fun `a pasted message with the code inside it is understood`() {
        val code = QrPayload.toCode(QrPayload.encode(recipe, items, products))
        val message = "Pancakes — a recipe from Healthy.\n\n$code\n\n" +
            "To add it: open Healthy, Food tab, \"Paste a shared item\"."
        val dish = QrPayload.decodeAny(message) as QrPayload.Decoded.Dish
        assertEquals("Pancakes", dish.recipe.name)
        assertEquals(2, dish.items.size)
    }

    @Test
    fun `a bare code pasted on its own works too`() {
        val code = QrPayload.toCode(QrPayload.encode(recipe, items, products))
        assertTrue(QrPayload.decodeAny(code) is QrPayload.Decoded.Dish)
        assertTrue(QrPayload.decodeAny("  $code  ") is QrPayload.Decoded.Dish)
    }

    /**
     * The reason the products travel at all: without them the other phone gets
     * barcodes it has never seen and the dish resolves to zero.
     */
    @Test
    fun `a shared recipe arrives with the values of ingredients the other phone lacks`() {
        val decoded = QrPayload.decodeAny(QrPayload.toLink(QrPayload.encode(recipe, items, products)))
        val dish = decoded as QrPayload.Decoded.Dish
        assertEquals("Pancakes", dish.recipe.name)
        assertEquals(2, dish.items.size)
        val bread = dish.products.first { it.barcode == ownProduct.barcode }
        assertEquals(260.0, bread.kcal100!!, 0.01)
    }

    /** A built-in ingredient is rebuilt locally, so it need not be sent. */
    @Test
    fun `built-in ingredients are not carried in the payload but arrive anyway`() {
        val payload = QrPayload.encode(recipe, items, products)
        assertTrue("the catalog item should not be embedded", !payload.contains("76.3"))
        val dish = QrPayload.decodeAny(payload) as QrPayload.Decoded.Dish
        val restored = dish.products.first { it.barcode == flour.barcode }
        assertEquals(364.0, restored.kcal100!!, 0.01)
    }

    @Test
    fun `compression keeps a real recipe short enough to paste`() {
        val code = QrPayload.toCode(QrPayload.encode(recipe, items, products))
        assertTrue("code was ${code.length} characters", code.length < 600)
    }

    @Test
    fun `a scanned square still decodes, link or not`() {
        val dish = QrPayload.decodeAny(QrPayload.encode(recipe, items, products))
        assertTrue(dish is QrPayload.Decoded.Dish)
    }

    @Test
    fun `someone else's link is refused rather than half read`() {
        assertNull(QrPayload.fromLink("https://example.com/i#abc"))
        assertNull(QrPayload.fromCode("not-a-real-code"))
        assertTrue(QrPayload.decodeAny("just some chat text") is QrPayload.Decoded.NotOurs)
    }
}
