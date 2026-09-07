package com.healthy.app.analysis

import com.healthy.app.core.Caffeine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Acceptance test 28: every number on the sources screen matches the value the
 * code uses.
 *
 * The entries are built from the constants themselves, so these tests check
 * that the constants are the ones the spec names, and that the wiring has not
 * been replaced by a hardcoded string during some later edit.
 */
class SourcesTest {

    private fun entry(item: String) = Sources.ENTRIES.first { it.item == item }

    @Test
    fun `the spec's nine items are all present`() {
        listOf(
            "Caffeine half-life",
            "Bedtime caffeine limit",
            "Weight smoothing factor",
            "Energy in 1 kg of tissue",
            "Resting heart rate",
            "Heart rate cycle detection",
            "Sleep stages",
            "Maximum weight change rate",
            "Reference intakes",
        ).forEach { item ->
            assertTrue("the sources screen is missing '$item'", Sources.ENTRIES.any { it.item == item })
        }
    }

    @Test
    fun `the quoted half-life is the one the caffeine model uses`() {
        assertEquals(5.0, Caffeine.DEFAULT_HALF_LIFE_HOURS, 0.0001)
        assertEquals("5 h", entry("Caffeine half-life").value)
    }

    @Test
    fun `the quoted bedtime limit is the one the verdict uses`() {
        assertEquals(50, Caffeine.DEFAULT_BEDTIME_LIMIT_MG)
        assertEquals("50 mg", entry("Bedtime caffeine limit").value)
    }

    @Test
    fun `the quoted smoothing factor is the one the trend uses`() {
        assertEquals(0.1, WeightTrend.SMOOTHING, 0.0001)
        assertEquals("0.1", entry("Weight smoothing factor").value)
    }

    @Test
    fun `the quoted tissue energy is the one the TDEE uses`() {
        assertEquals(7700.0, Energy.KCAL_PER_KG, 0.0001)
        assertEquals("7700 kcal", entry("Energy in 1 kg of tissue").value)
    }

    @Test
    fun `the quoted cycle window is the one the hypnogram uses`() {
        assertEquals(20, Hypnogram.MINIMUM_ISOLATION_MINUTES)
        assertEquals("Local minima, 20 min window", entry("Heart rate cycle detection").value)
    }

    @Test
    fun `the quoted rate limit is the one the goal refuses above`() {
        assertEquals(0.01, WeightGoal.MAX_RATE_FRACTION, 0.0001)
        assertEquals("1 percent each week", entry("Maximum weight change rate").value)
    }

    @Test
    fun `the quoted hold band and patience are the ones the goal uses`() {
        assertEquals(1.0, WeightGoal.HOLD_BAND_KG, 0.0001)
        assertEquals(7, WeightGoal.HOLD_PATIENCE_DAYS)
        assertTrue(entry("Hold-the-weight band").basis.contains("7 days"))
    }

    @Test
    fun `the quoted energy window is the one the measurement uses`() {
        assertEquals(14, Energy.WINDOW_DAYS)
        assertEquals("14-day window", entry("Measured energy need").value)
    }

    /** Spec 17: every entry must state what the app does not know. */
    @Test
    fun `every entry states a limitation`() {
        Sources.ENTRIES.forEach { e ->
            assertTrue("'${e.item}' states no limitation", e.limitation.length > 20)
            assertTrue("'${e.item}' has no basis", e.basis.length > 10)
        }
    }

    /** Spec 18.6: the hypnogram must disclaim naming stages. */
    @Test
    fun `the hypnogram disclaimer refuses to claim brain activity`() {
        val text = Sources.HYPNOGRAM_DISCLAIMER
        assertTrue(text.contains("not brain activity"))
        assertTrue(text.contains("EEG"))
        assertTrue(text.contains("does not name the stages"))
    }

    /** The stage entry must carry the agreement figure the spec quotes. */
    @Test
    fun `the sleep stage entry admits how often the watch is right`() {
        val e = entry("Sleep stages")
        assertTrue(e.limitation.contains("60 to 80 percent"))
    }

    @Test
    fun `no entry promises certainty`() {
        Sources.ENTRIES.forEach { e ->
            val text = (e.basis + " " + e.limitation).lowercase()
            listOf("guaranteed", "exactly measures", "precisely measures").forEach { claim ->
                assertFalse("'${e.item}' overclaims with '$claim'", text.contains(claim))
            }
        }
    }
}
