package com.healthy.app.analysis

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Energy need and the daily target (spec 16). */
class EnergyTest {

    /**
     * The worked case: eating 2000 kcal while the trend falls 1 kg over 14
     * days means the body spent 7700 kcal more than it ate, which is 550 a
     * day, so maintenance was 2550.
     */
    @Test
    fun `maintenance is intake plus the energy the lost tissue supplied`() {
        val result = Energy.measuredMaintenance(List(14) { 2000 }, trendChangeKg = -1.0)!!
        assertEquals(2550, result.kcal)
        assertEquals(Energy.Source.Measured, result.source)
        assertEquals(14, result.days)
    }

    @Test
    fun `a rising trend means maintenance was below intake`() {
        val result = Energy.measuredMaintenance(List(14) { 3000 }, trendChangeKg = 1.0)!!
        assertEquals(2450, result.kcal)
    }

    @Test
    fun `a flat trend means maintenance equals intake`() {
        assertEquals(2200, Energy.measuredMaintenance(List(14) { 2200 }, 0.0)!!.kcal)
    }

    /** Spec 16.2: below 14 days there is no measured number to give. */
    @Test
    fun `the measured window needs fourteen days`() {
        assertNull(Energy.measuredMaintenance(List(13) { 2000 }, -1.0))
        assertTrue(Energy.measuredMaintenance(List(14) { 2000 }, -1.0) != null)
    }

    @Test
    fun `the formula is marked as an estimate with no days behind it`() {
        val estimate = Energy.mifflinStJeor(80.0, 178.0, 30, Energy.Sex.Male)
        assertEquals(Energy.Source.Estimated, estimate.source)
        assertEquals(0, estimate.days)
        // 10*80 + 6.25*178 - 5*30 + 5 = 1767.5 BMR, x1.4 = 2474.5
        assertEquals(2475, estimate.kcal)
    }

    @Test
    fun `the basal rate carries no activity multiplier`() {
        assertEquals(1768, Energy.basalRate(80.0, 178.0, 30, Energy.Sex.Male))
    }

    /** A negative rate is losing weight, the convention the whole app uses. */
    @Test
    fun `losing half a kilo a week comes off the maintenance number`() {
        // -0.5 kg/week = -3850 kcal/week = 550 a day less.
        val target = Energy.dailyTarget(maintenanceKcal = 2550, rateKgPerWeek = -0.5, basalKcal = 1600)
        assertEquals(2000, target.kcal)
        assertFalse(target.clampedToBasal)
    }

    /** Spec 16.3: the target must never fall below the basal rate. */
    @Test
    fun `an impossible rate is clamped to the basal rate`() {
        val target = Energy.dailyTarget(maintenanceKcal = 2000, rateKgPerWeek = -1.5, basalKcal = 1600)
        assertEquals(1600, target.kcal)
        assertTrue("the user must be told the rate is too fast", target.clampedToBasal)
    }

    @Test
    fun `gaining weight raises the target above maintenance`() {
        val target = Energy.dailyTarget(maintenanceKcal = 2500, rateKgPerWeek = 0.25, basalKcal = 1600)
        assertEquals(2775, target.kcal)
        assertFalse(target.clampedToBasal)
    }
}
