package com.healthy.app.core

import com.healthy.app.data.entity.Drink
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The decay maths from spec 6, checked against the worked example in
 * acceptance test 6: a 125 mg dose reads 63 mg after 5 hours and 31 mg after
 * 10 hours, at the default 5 hour half-life.
 */
class CaffeineTest {

    private val t0 = 1_772_000_000_000L
    private fun hours(n: Double) = (n * 3_600_000).toLong()
    private fun dose(mg: Int, at: Long) = Drink(name = "test", mg = mg, timestamp = at)

    @Test
    fun `125 mg halves every five hours`() {
        val doses = listOf(dose(125, t0))
        val hl = Caffeine.DEFAULT_HALF_LIFE_HOURS

        assertEquals(125.0, Caffeine.levelAt(doses, t0, hl), 0.001)
        assertEquals(63.0, Math.round(Caffeine.levelAt(doses, t0 + hours(5.0), hl)).toDouble(), 0.001)
        assertEquals(31.0, Math.round(Caffeine.levelAt(doses, t0 + hours(10.0), hl)).toDouble(), 0.001)
    }

    @Test
    fun `a dose in the future does not count yet`() {
        val doses = listOf(dose(125, t0 + hours(2.0)))
        assertEquals(0.0, Caffeine.levelAt(doses, t0, Caffeine.DEFAULT_HALF_LIFE_HOURS), 0.001)
    }

    @Test
    fun `doses add together`() {
        val doses = listOf(dose(100, t0), dose(50, t0))
        assertEquals(150.0, Caffeine.levelAt(doses, t0, 5.0), 0.001)
    }

    @Test
    fun `a shorter half-life clears faster`() {
        val doses = listOf(dose(100, t0))
        val fast = Caffeine.levelAt(doses, t0 + hours(5.0), 4.0)
        val slow = Caffeine.levelAt(doses, t0 + hours(5.0), 6.0)
        assertTrue("a 4 h half-life must leave less than a 6 h one", fast < slow)
    }

    @Test
    fun `the curve returns one more point than the sample count`() {
        val curve = Caffeine.curve(listOf(dose(125, t0)), t0, t0 + hours(24.0), 5.0, samples = 120)
        assertEquals(121, curve.size)
        // Monotonically decreasing after a single dose at the window start.
        assertTrue(curve.zipWithNext().all { (a, b) -> b <= a + 1e-9 })
    }

    @Test
    fun `catalog matches the twenty drinks in the spec`() {
        assertEquals(20, DrinkCatalog.BUILT_IN.size)
        val freddo = DrinkCatalog.BUILT_IN.first { it.name == "Freddo espresso" }
        assertEquals(125, freddo.mg)
        assertEquals(200, freddo.volumeMl)
        // Dark chocolate carries caffeine but is not a fluid (spec 9.1).
        assertEquals(0, DrinkCatalog.BUILT_IN.first { it.name.startsWith("Dark chocolate") }.volumeMl)
    }
}
