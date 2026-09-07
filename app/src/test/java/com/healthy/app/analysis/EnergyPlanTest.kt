package com.healthy.app.analysis

import com.healthy.app.data.entity.MealEntry
import com.healthy.app.data.entity.Product
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The energy plan and the percentages shown beside every figure (spec 16). */
class EnergyPlanTest {

    private val measured = Energy.Maintenance(2550, Energy.Source.Measured, 14)

    @Test
    fun `a half kilo a week comes off maintenance and reaches the goal in time`() {
        val plan = Energy.plan(
            maintenance = measured,
            rateKgPerWeek = -0.5,
            basalKcal = 1700,
            currentKg = 82.0,
            targetKg = 78.0,
        )
        assertEquals(2000, plan.targetKcal)
        assertFalse(plan.clampedToBasal)
        // 4 kg at half a kilo a week is 8 weeks.
        assertEquals(8, plan.weeksToTarget)
    }

    @Test
    fun `a rate pointing away from the goal never arrives`() {
        val plan = Energy.plan(measured, rateKgPerWeek = 0.5, basalKcal = 1700, currentKg = 82.0, targetKg = 78.0)
        assertNull("gaining will not reach a lower goal", plan.weeksToTarget)
    }

    @Test
    fun `already at the goal is zero weeks away`() {
        val plan = Energy.plan(measured, -0.5, 1700, currentKg = 78.0, targetKg = 78.0)
        assertEquals(0, plan.weeksToTarget)
    }

    /** Spec 16.3: the target never falls below the basal rate. */
    @Test
    fun `an impossible rate is held at the basal rate and says so`() {
        val plan = Energy.plan(measured, rateKgPerWeek = -1.5, basalKcal = 1700, currentKg = 82.0, targetKg = 70.0)
        assertEquals(1700, plan.targetKcal)
        assertTrue(plan.clampedToBasal)
    }

    @Test
    fun `no goal means the target is simply maintenance`() {
        val plan = Energy.plan(measured, rateKgPerWeek = 0.0, basalKcal = 1700, currentKg = 82.0, targetKg = null)
        assertEquals(2550, plan.targetKcal)
        assertNull(plan.weeksToTarget)
    }

    @Test
    fun `a percentage is a share of the day's target`() {
        assertEquals(10, Energy.percentOfTarget(200.0, 2000))
        assertEquals(100, Energy.percentOfTarget(2000.0, 2000))
        assertEquals(125, Energy.percentOfTarget(2500.0, 2000))
        assertEquals(0, Energy.percentOfTarget(0.0, 2000))
    }

    /** No target means no percentage, rather than a division by nothing. */
    @Test
    fun `without a target there is no percentage`() {
        assertNull(Energy.percentOfTarget(200.0, null))
        assertNull(Energy.percentOfTarget(200.0, 0))
    }

    private val oats = Product(
        barcode = "1", kind = Product.KIND_FOOD, name = "Oats", kcal100 = 370.0,
    )

    private fun meal(day: Int, hour: Int, grams: Double, id: Long) = MealEntry(
        id = id,
        timestamp = java.time.ZonedDateTime
            .of(2026, 3, day, hour, 0, 0, 0, java.time.ZoneId.systemDefault())
            .toInstant().toEpochMilli(),
        mealType = MealEntry.LUNCH,
        barcode = "1",
        grams = grams,
    )

    @Test
    fun `intake is grouped by the logical day`() {
        val meals = listOf(
            meal(day = 4, hour = 12, grams = 100.0, id = 1),
            meal(day = 4, hour = 20, grams = 100.0, id = 2),
            meal(day = 5, hour = 12, grams = 100.0, id = 3),
        )
        val days = IntakeHistory.byDay(meals, mapOf("1" to oats))
        assertEquals(2, days.size)
        assertEquals(740, days.first().kcal)
        assertEquals(370, days.last().kcal)
    }

    /**
     * A day with nothing logged is not a day of eating nothing. Averaging a
     * zero into the intake would understate maintenance and hand back a target
     * that is too low.
     */
    @Test
    fun `a barely logged day is not counted as a complete one`() {
        val days = listOf(
            IntakeHistory.Day("2026-03-04", 2100, itemCount = 5),
            IntakeHistory.Day("2026-03-05", 180, itemCount = 1),
            IntakeHistory.Day("2026-03-06", 0, itemCount = 0),
        )
        val complete = IntakeHistory.completeDays(days)
        assertEquals(1, complete.size)
        assertEquals("2026-03-04", complete.single().date)
    }
}
