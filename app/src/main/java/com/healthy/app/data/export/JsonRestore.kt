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
import org.json.JSONArray
import org.json.JSONObject

/**
 * Reads back what [JsonBackup] wrote (spec 5.4).
 *
 * The contract that matters is acceptance test 9: an export followed by an
 * import returns the same data. That is only true if absence survives the
 * round trip, so a JSON null becomes a Kotlin null here and never a zero — a
 * night with no recorded wake-ups must not come back reporting none.
 */
object JsonRestore {

    sealed interface Result {
        data class Ok(val data: JsonBackup.Everything) : Result
        data class Failed(val reason: String) : Result
    }

    fun parse(json: String): Result = runCatching {
        val root = JSONObject(json)

        val format = root.optInt("format", -1)
        if (format != JsonBackup.FORMAT_VERSION) {
            return Result.Failed(
                "This file is format $format and this app reads format " +
                    "${JsonBackup.FORMAT_VERSION}."
            )
        }

        val settingsJson = root.optJSONObject("settings")
        val settings = HealthySettings(
            targetBedtime = settingsJson?.optString("targetBedtime")
                ?.takeIf { it.isNotBlank() } ?: HealthySettings.DEFAULT_BEDTIME,
            halfLifeHours = settingsJson?.optDouble("halfLifeHours")
                ?.takeIf { !it.isNaN() } ?: com.healthy.app.core.Caffeine.DEFAULT_HALF_LIFE_HOURS,
            bedtimeLimitMg = settingsJson?.optInt(
                "bedtimeLimitMg",
                com.healthy.app.core.Caffeine.DEFAULT_BEDTIME_LIMIT_MG,
            ) ?: com.healthy.app.core.Caffeine.DEFAULT_BEDTIME_LIMIT_MG,
            fluidTargetMl = settingsJson?.optInt(
                "fluidTargetMl",
                HealthySettings.DEFAULT_FLUID_TARGET_ML,
            ) ?: HealthySettings.DEFAULT_FLUID_TARGET_ML,
            unitsPerBeer = settingsJson?.optDouble("unitsPerBeer")
                ?.takeIf { !it.isNaN() } ?: HealthySettings.DEFAULT_UNITS_PER_BEER,
            unitsPerWine = settingsJson?.optDouble("unitsPerWine")
                ?.takeIf { !it.isNaN() } ?: HealthySettings.DEFAULT_UNITS_PER_WINE,
        )

        Result.Ok(
            JsonBackup.Everything(
                nights = root.array("nights").map { o ->
                    Night(
                        date = o.getString("date"),
                        sleepStart = o.optLong("sleepStart"),
                        sleepEnd = o.optLong("sleepEnd"),
                        minutes = o.optInt("minutes"),
                        deepMin = o.intOrNull("deepMin"),
                        lightMin = o.intOrNull("lightMin"),
                        remMin = o.intOrNull("remMin"),
                        awakeMin = o.intOrNull("awakeMin"),
                        wakeups = o.intOrNull("wakeups"),
                        restingHr = o.intOrNull("restingHr"),
                        spo2 = o.doubleOrNull("spo2"),
                        alertness = o.intOrNull("alertness"),
                        energy3pm = o.intOrNull("energy3pm"),
                        alcoholUnits = o.doubleOrNull("alcoholUnits"),
                        lastMeal = o.stringOrNull("lastMeal"),
                        exercise = o.stringOrNull("exercise"),
                        roomTempC = o.doubleOrNull("roomTempC"),
                        notes = o.optString("notes"),
                        editedFields = o.optJSONArray("editedFields")
                            ?.let { arr -> (0 until arr.length()).map { arr.getString(it) }.toSet() }
                            .orEmpty(),
                    )
                },
                stageBlocks = root.array("stageBlocks").map { o ->
                    StageBlock(
                        id = o.optLong("id"),
                        nightDate = o.getString("nightDate"),
                        type = o.getString("type"),
                        startTime = o.optLong("startTime"),
                        endTime = o.optLong("endTime"),
                    )
                },
                drinks = root.array("drinks").map { o ->
                    Drink(
                        id = o.optLong("id"),
                        name = o.getString("name"),
                        mg = o.optInt("mg"),
                        timestamp = o.optLong("timestamp"),
                        volumeMl = o.optInt("volumeMl"),
                        alcoholUnits = o.optDouble("alcoholUnits", 0.0),
                    )
                },
                customDrinks = root.array("customDrinks").map { o ->
                    CustomDrink(
                        id = o.optLong("id"),
                        name = o.getString("name"),
                        mg = o.optInt("mg"),
                        volumeMl = o.optInt("volumeMl"),
                    )
                },
                weights = root.array("weights").map { o ->
                    Weight(
                        date = o.getString("date"),
                        weightKg = o.optDouble("weightKg"),
                        timestamp = o.optLong("timestamp"),
                        bodyFatPct = o.doubleOrNull("bodyFatPct"),
                        waterPct = o.doubleOrNull("waterPct"),
                        musclePct = o.doubleOrNull("musclePct"),
                        boneKg = o.doubleOrNull("boneKg"),
                        visceralFat = o.doubleOrNull("visceralFat"),
                        source = o.optString("source", Weight.MANUAL),
                    )
                },
                products = root.array("products").map { o ->
                    Product(
                        barcode = o.getString("barcode"),
                        kind = o.getString("kind"),
                        name = o.optString("name"),
                        brand = o.stringOrNull("brand"),
                        mg = o.intOrNull("mg"),
                        volumeMl = o.intOrNull("volumeMl"),
                        packGrams = o.intOrNull("packGrams"),
                        servingGrams = o.intOrNull("servingGrams"),
                        kcal100 = o.doubleOrNull("kcal100"),
                        protein100 = o.doubleOrNull("protein100"),
                        carbs100 = o.doubleOrNull("carbs100"),
                        sugar100 = o.doubleOrNull("sugar100"),
                        fat100 = o.doubleOrNull("fat100"),
                        saturatedFat100 = o.doubleOrNull("saturatedFat100"),
                        fibre100 = o.doubleOrNull("fibre100"),
                        salt100 = o.doubleOrNull("salt100"),
                        calcium100 = o.doubleOrNull("calcium100"),
                        iron100 = o.doubleOrNull("iron100"),
                        potassium100 = o.doubleOrNull("potassium100"),
                        magnesium100 = o.doubleOrNull("magnesium100"),
                        vitaminD100 = o.doubleOrNull("vitaminD100"),
                        vitaminB12100 = o.doubleOrNull("vitaminB12100"),
                        source = o.optString("source", Product.USER),
                    )
                },
                meals = root.array("meals").map { o ->
                    MealEntry(
                        id = o.optLong("id"),
                        timestamp = o.optLong("timestamp"),
                        mealType = o.getString("mealType"),
                        barcode = o.stringOrNull("barcode"),
                        recipeId = o.longOrNull("recipeId"),
                        grams = o.optDouble("grams"),
                    )
                },
                recipes = root.array("recipes").map { o ->
                    Recipe(
                        id = o.optLong("id"),
                        name = o.optString("name"),
                        cookedGrams = o.optDouble("cookedGrams"),
                        portions = o.optInt("portions"),
                    )
                },
                recipeItems = root.array("recipeItems").map { o ->
                    RecipeItem(
                        id = o.optLong("id"),
                        recipeId = o.optLong("recipeId"),
                        barcode = o.stringOrNull("barcode"),
                        childRecipeId = o.longOrNull("childRecipeId"),
                        name = o.optString("name"),
                        grams = o.optDouble("grams"),
                    )
                },
                notes = root.array("notes").map { o ->
                    Note(
                        id = o.optLong("id"),
                        date = o.getString("date"),
                        text = o.optString("text"),
                        createdAt = o.optLong("createdAt"),
                    )
                },
                settings = settings,
            )
        )
    }.getOrElse { Result.Failed(it.message ?: "the file could not be read as a Healthy backup") }

    private fun JSONObject.array(key: String): List<JSONObject> {
        val arr: JSONArray = optJSONArray(key) ?: return emptyList()
        return (0 until arr.length()).map { arr.getJSONObject(it) }
    }

    // JSONObject.isNull is the only way to tell an absent value from a zero,
    // which is the whole point of the round trip.
    private fun JSONObject.intOrNull(key: String): Int? = if (isNull(key)) null else optInt(key)
    private fun JSONObject.longOrNull(key: String): Long? = if (isNull(key)) null else optLong(key)
    private fun JSONObject.doubleOrNull(key: String): Double? =
        if (isNull(key)) null else optDouble(key).takeIf { !it.isNaN() }

    private fun JSONObject.stringOrNull(key: String): String? =
        if (isNull(key)) null else optString(key).takeIf { it.isNotEmpty() }
}
