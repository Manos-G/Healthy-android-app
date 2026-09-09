package com.healthy.app.analysis

import com.healthy.app.core.HealthyDay
import com.healthy.app.data.entity.MealEntry
import com.healthy.app.data.entity.Product
import java.time.ZoneId

/**
 * Daily energy intake, which is one half of the measured maintenance figure
 * (spec 16.1). The other half is what the weight trend did about it.
 */
object IntakeHistory {

    data class Day(val date: String, val kcal: Int, val itemCount: Int)

    /**
     * Energy per logical day, oldest first.
     *
     * Only days that actually have entries are returned. A day with no food
     * logged is not a day of eating nothing — it is a day the user did not
     * log, and averaging a zero into the intake would understate maintenance
     * and hand back a target that is too low.
     */
    fun byDay(
        meals: List<MealEntry>,
        products: Map<String, Product>,
        dishesPer100g: Map<Long, Nutrition.Totals> = emptyMap(),
        zone: ZoneId = ZoneId.systemDefault(),
    ): List<Day> =
        meals.groupBy { HealthyDay.dayOf(it.timestamp, zone) }
            .map { (date, entries) ->
                Day(
                    date = date,
                    kcal = Nutrition.totalFor(entries, products, dishesPer100g).kcal.toInt(),
                    itemCount = entries.size,
                )
            }
            .sortedBy { it.date }

    /**
     * Whether there is enough to measure with (spec 16.2): the window needs
     * complete food logs, and a day with a single item logged is not one.
     */
    fun completeDays(days: List<Day>, minimumItems: Int = 2): List<Day> =
        days.filter { it.itemCount >= minimumItems && it.kcal > 0 }
}
