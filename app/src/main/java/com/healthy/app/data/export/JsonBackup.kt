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
 * The complete-backup JSON (spec 5.4).
 *
 * Written with `org.json`, which ships with Android, rather than with
 * kotlinx-serialization. That is deliberate: the build forces
 * kotlinx-serialization to 1.8.1 to keep Room's migration tests working, and
 * using it here would promote that force from a test-path workaround to
 * something governing the code that writes the user's only backup. See the
 * REVISIT AT STEP 17 note in app/build.gradle.kts.
 *
 * Every table is included, and every value round-trips: nulls stay null rather
 * than becoming zero, so "not reported" survives an export and an import.
 */
object JsonBackup {

    /** Bumped when the shape changes, so a future import can branch on it. */
    const val FORMAT_VERSION = 1

    data class Everything(
        val nights: List<Night>,
        val stageBlocks: List<StageBlock>,
        val drinks: List<Drink>,
        val customDrinks: List<CustomDrink>,
        val weights: List<Weight>,
        val products: List<Product>,
        val meals: List<MealEntry>,
        val recipes: List<Recipe>,
        val recipeItems: List<RecipeItem>,
        val notes: List<Note>,
        val settings: HealthySettings,
    )

    fun build(data: Everything, exportedAt: Long, dbVersion: Int): String {
        val root = JSONObject()
        root.put("format", FORMAT_VERSION)
        root.put("app", "Healthy")
        root.put("databaseVersion", dbVersion)
        root.put("exportedAt", exportedAt)

        root.put(
            "settings",
            JSONObject()
                .put("targetBedtime", data.settings.targetBedtime)
                .put("halfLifeHours", data.settings.halfLifeHours)
                .put("bedtimeLimitMg", data.settings.bedtimeLimitMg)
                .put("fluidTargetMl", data.settings.fluidTargetMl)
                .put("unitsPerBeer", data.settings.unitsPerBeer)
                .put("unitsPerWine", data.settings.unitsPerWine)
                .put("mlPerAlcoholUnit", data.settings.mlPerAlcoholUnit),
        )

        root.put("nights", data.nights.map { n ->
            obj {
                put("date", n.date)
                put("sleepStart", n.sleepStart)
                put("sleepEnd", n.sleepEnd)
                put("minutes", n.minutes)
                putOrNull("deepMin", n.deepMin)
                putOrNull("lightMin", n.lightMin)
                putOrNull("remMin", n.remMin)
                putOrNull("awakeMin", n.awakeMin)
                putOrNull("wakeups", n.wakeups)
                putOrNull("restingHr", n.restingHr)
                putOrNull("spo2", n.spo2)
                putOrNull("alertness", n.alertness)
                putOrNull("energy3pm", n.energy3pm)
                putOrNull("alcoholUnits", n.alcoholUnits)
                putOrNull("lastMeal", n.lastMeal)
                putOrNull("exercise", n.exercise)
                putOrNull("roomTempC", n.roomTempC)
                put("notes", n.notes)
                put("editedFields", JSONArray(n.editedFields.sorted()))
            }
        }.toJsonArray())

        root.put("stageBlocks", data.stageBlocks.map { b ->
            obj {
                put("id", b.id); put("nightDate", b.nightDate); put("type", b.type)
                put("startTime", b.startTime); put("endTime", b.endTime)
            }
        }.toJsonArray())

        root.put("drinks", data.drinks.map { d ->
            obj {
                put("id", d.id); put("name", d.name); put("mg", d.mg)
                put("volumeMl", d.volumeMl); put("timestamp", d.timestamp)
                put("alcoholUnits", d.alcoholUnits)
            }
        }.toJsonArray())

        root.put("customDrinks", data.customDrinks.map { d ->
            obj { put("id", d.id); put("name", d.name); put("mg", d.mg); put("volumeMl", d.volumeMl) }
        }.toJsonArray())

        root.put("weights", data.weights.map { w ->
            obj {
                put("date", w.date); put("weightKg", w.weightKg); put("timestamp", w.timestamp)
                putOrNull("bodyFatPct", w.bodyFatPct); putOrNull("waterPct", w.waterPct)
                putOrNull("musclePct", w.musclePct); putOrNull("boneKg", w.boneKg)
                putOrNull("visceralFat", w.visceralFat); put("source", w.source)
            }
        }.toJsonArray())

        root.put("products", data.products.map { p ->
            obj {
                put("barcode", p.barcode); put("kind", p.kind); put("name", p.name)
                putOrNull("brand", p.brand); putOrNull("mg", p.mg); putOrNull("volumeMl", p.volumeMl)
                putOrNull("packGrams", p.packGrams); putOrNull("servingGrams", p.servingGrams)
                putOrNull("kcal100", p.kcal100); putOrNull("protein100", p.protein100)
                putOrNull("carbs100", p.carbs100); putOrNull("sugar100", p.sugar100)
                putOrNull("fat100", p.fat100)
                putOrNull("saturatedFat100", p.saturatedFat100)
                putOrNull("fibre100", p.fibre100)
                putOrNull("salt100", p.salt100); putOrNull("calcium100", p.calcium100)
                putOrNull("iron100", p.iron100); putOrNull("potassium100", p.potassium100)
                putOrNull("magnesium100", p.magnesium100); putOrNull("vitaminD100", p.vitaminD100)
                putOrNull("vitaminB12100", p.vitaminB12100); put("source", p.source)
            }
        }.toJsonArray())

        root.put("meals", data.meals.map { m ->
            obj {
                put("id", m.id); put("timestamp", m.timestamp); put("mealType", m.mealType)
                putOrNull("barcode", m.barcode); putOrNull("recipeId", m.recipeId)
                put("grams", m.grams)
            }
        }.toJsonArray())

        root.put("recipes", data.recipes.map { r ->
            obj {
                put("id", r.id); put("name", r.name)
                put("cookedGrams", r.cookedGrams); put("portions", r.portions)
            }
        }.toJsonArray())

        root.put("recipeItems", data.recipeItems.map { i ->
            obj {
                put("id", i.id); put("recipeId", i.recipeId); putOrNull("barcode", i.barcode)
                putOrNull("childRecipeId", i.childRecipeId); put("name", i.name); put("grams", i.grams)
            }
        }.toJsonArray())

        root.put("notes", data.notes.map { n ->
            obj { put("id", n.id); put("date", n.date); put("text", n.text); put("createdAt", n.createdAt) }
        }.toJsonArray())

        return root.toString(2)
    }

    private fun obj(block: JSONObject.() -> Unit) = JSONObject().apply(block)

    private fun List<JSONObject>.toJsonArray() = JSONArray().also { arr -> forEach(arr::put) }

    /**
     * `JSONObject.put(key, null)` removes the key, which would make an absent
     * value indistinguishable from a value that was never exported. JSON null
     * is written explicitly so "not reported" survives the round trip.
     */
    private fun JSONObject.putOrNull(key: String, value: Any?) {
        if (value == null) put(key, JSONObject.NULL) else put(key, value)
    }
}
