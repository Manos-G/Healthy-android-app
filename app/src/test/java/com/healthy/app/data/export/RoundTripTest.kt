package com.healthy.app.data.export

import com.healthy.app.data.HealthySettings
import com.healthy.app.data.entity.CustomDrink
import com.healthy.app.data.entity.Drink
import com.healthy.app.data.entity.MealEntry
import com.healthy.app.data.entity.Night
import com.healthy.app.data.entity.Note
import com.healthy.app.data.entity.Product
import com.healthy.app.data.entity.Recipe
import com.healthy.app.data.entity.RecipeItem
import com.healthy.app.data.entity.StageBlock
import com.healthy.app.data.entity.Weight
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Acceptance test 9: a JSON export and a JSON import return the same data.
 *
 * The hard part is absence. A night with no recorded wake-ups must come back
 * with no recorded wake-ups, not with zero — the distinction the whole app is
 * built on has to survive a trip through a file.
 */
class RoundTripTest {

    private val everything = JsonBackup.Everything(
        nights = listOf(
            Night(
                date = "2026-03-04",
                sleepStart = 1_772_000_000_000,
                sleepEnd = 1_772_040_260_000,
                minutes = 671,
                deepMin = 174,
                lightMin = 329,
                remMin = 168,
                awakeMin = 0,
                wakeups = null,
                restingHr = null,
                spo2 = 96.3,
                alertness = 4,
                energy3pm = null,
                alcoholUnits = null,
                lastMeal = "21:00",
                exercise = "light",
                roomTempC = 19.5,
                notes = "sore throat, slept badly",
                editedFields = setOf("restingHr", "wakeups"),
            ),
            Night(date = "2026-03-05", sleepStart = 1L, sleepEnd = 2L, minutes = 400),
        ),
        stageBlocks = listOf(
            StageBlock(id = 1, nightDate = "2026-03-04", type = "deep", startTime = 10, endTime = 20),
        ),
        drinks = listOf(
            Drink(id = 1, name = "Freddo espresso", mg = 125, timestamp = 5L, volumeMl = 200),
            Drink(id = 2, name = "Beer, 330 ml", mg = 0, timestamp = 6L, volumeMl = 330, alcoholUnits = 1.7),
        ),
        customDrinks = listOf(CustomDrink(id = 1, name = "Office filter", mg = 90, volumeMl = 250)),
        weights = listOf(
            Weight(date = "2026-03-04", weightKg = 81.4, timestamp = 7L, bodyFatPct = null),
            Weight(date = "2026-03-05", weightKg = 81.2, timestamp = 8L, bodyFatPct = 18.2, source = Weight.OPENSCALE),
        ),
        products = listOf(
            Product(barcode = "5201234567890", kind = "drink", name = "Hell", mg = 80, volumeMl = 250, source = Product.OFF),
        ),
        meals = listOf(MealEntry(id = 1, timestamp = 9L, mealType = "dinner", barcode = "5201234567890", grams = 250.0)),
        recipes = listOf(Recipe(id = 1, name = "Lentil soup", cookedGrams = 700.0, portions = 3)),
        recipeItems = listOf(RecipeItem(id = 1, recipeId = 1, name = "lentils", grams = 300.0)),
        notes = listOf(Note(id = 1, date = "2026-03-04", text = "teeth pain", createdAt = 11L)),
        settings = HealthySettings(
            targetBedtime = "01:30",
            halfLifeHours = 4.5,
            bedtimeLimitMg = 40,
            fluidTargetMl = 2500,
            unitsPerBeer = 2.0,
            unitsPerWine = 1.5,
            mlPerAlcoholUnit = 17.7,
        ),
    )

    private fun roundTrip(): JsonBackup.Everything {
        val json = JsonBackup.build(everything, exportedAt = 123L, dbVersion = 3)
        val result = JsonRestore.parse(json)
        assertTrue("parse failed: $result", result is JsonRestore.Result.Ok)
        return (result as JsonRestore.Result.Ok).data
    }

    @Test
    fun `every table returns with the same number of rows`() {
        val back = roundTrip()
        assertEquals(everything.nights.size, back.nights.size)
        assertEquals(everything.stageBlocks.size, back.stageBlocks.size)
        assertEquals(everything.drinks.size, back.drinks.size)
        assertEquals(everything.customDrinks.size, back.customDrinks.size)
        assertEquals(everything.weights.size, back.weights.size)
        assertEquals(everything.products.size, back.products.size)
        assertEquals(everything.meals.size, back.meals.size)
        assertEquals(everything.recipes.size, back.recipes.size)
        assertEquals(everything.recipeItems.size, back.recipeItems.size)
        assertEquals(everything.notes.size, back.notes.size)
    }

    @Test
    fun `a night returns identical, absences included`() {
        val back = roundTrip().nights.first { it.date == "2026-03-04" }
        assertEquals(everything.nights.first(), back)
    }

    /** The distinction the app is built on must survive a file. */
    @Test
    fun `an unrecorded value comes back unrecorded, not as zero`() {
        val back = roundTrip().nights.first { it.date == "2026-03-04" }
        assertNull("wake-ups were not reported", back.wakeups)
        assertNull("resting heart rate was not reported", back.restingHr)
        assertNull("the afternoon rating was never given", back.energy3pm)
        assertNull(back.alcoholUnits)
        // But a real zero stays a zero.
        assertEquals(0, back.awakeMin)
    }

    @Test
    fun `edited fields survive so a later sync still respects them`() {
        val back = roundTrip().nights.first { it.date == "2026-03-04" }
        assertEquals(setOf("restingHr", "wakeups"), back.editedFields)
    }

    @Test
    fun `alcohol units on a drink survive`() {
        val beer = roundTrip().drinks.first { it.name == "Beer, 330 ml" }
        assertEquals(1.7, beer.alcoholUnits, 0.0001)
        assertEquals(330, beer.volumeMl)
    }

    @Test
    fun `body composition returns absent where it was absent`() {
        val back = roundTrip().weights
        assertNull(back.first { it.date == "2026-03-04" }.bodyFatPct)
        assertEquals(18.2, back.first { it.date == "2026-03-05" }.bodyFatPct!!, 0.0001)
        assertEquals(Weight.OPENSCALE, back.first { it.date == "2026-03-05" }.source)
    }

    @Test
    fun `settings return identical`() {
        assertEquals(everything.settings, roundTrip().settings)
    }

    @Test
    fun `a note containing punctuation survives`() {
        assertEquals("sore throat, slept badly", roundTrip().nights.first().notes)
    }

    @Test
    fun `a file from a future format is refused rather than half-read`() {
        val json = JsonBackup.build(everything, 1L, 3).replace("\"format\": 1", "\"format\": 99")
        val result = JsonRestore.parse(json)
        assertTrue(result is JsonRestore.Result.Failed)
        assertTrue((result as JsonRestore.Result.Failed).reason.contains("format"))
    }

    @Test
    fun `rubbish is refused with a reason`() {
        assertTrue(JsonRestore.parse("not json at all") is JsonRestore.Result.Failed)
    }
}
