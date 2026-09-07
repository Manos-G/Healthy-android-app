package com.healthy.app.analysis

import com.healthy.app.data.entity.Night
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The comparison engine (spec 5.3). */
class TrendsTest {

    private fun sample(
        date: String,
        alertness: Int? = null,
        energy: Int? = null,
        minutes: Int = 420,
        caffeine: Int = 0,
        bedtime: Int = 0,
        restingHr: Int? = null,
    ) = Trends.Sample(
        night = Night(
            date = date,
            sleepStart = 1L,
            sleepEnd = 2L,
            minutes = minutes,
            alertness = alertness,
            energy3pm = energy,
            restingHr = restingHr,
        ),
        caffeineMg = caffeine,
        bedtimeMg = bedtime,
    )

    @Test
    fun `a night with one rating still scores`() {
        assertEquals(4.0, sample("2026-03-01", alertness = 4).score!!, 0.001)
        assertEquals(2.0, sample("2026-03-02", energy = 2).score!!, 0.001)
        assertEquals(3.0, sample("2026-03-03", alertness = 4, energy = 2).score!!, 0.001)
    }

    @Test
    fun `an unrated night has no score and is excluded`() {
        assertNull(sample("2026-03-04").score)
        assertEquals(0, Trends.ratedCount(listOf(sample("2026-03-04"))))
    }

    /** Spec 5.3: the table needs six rated nights before it says anything. */
    @Test
    fun `the table stays shut below six rated nights`() {
        val five = (1..5).map { sample("2026-03-0$it", alertness = it) }
        assertTrue(Trends.comparison(five).isEmpty())
        assertEquals(5, Trends.ratedCount(five))
    }

    @Test
    fun `unrated nights do not count towards the six`() {
        val samples = (1..5).map { sample("2026-03-0$it", alertness = 3) } +
            (6..9).map { sample("2026-03-0$it") }
        assertTrue("four unrated nights must not open the table", Trends.comparison(samples).isEmpty())
    }

    @Test
    fun `the best third is compared against the worst third`() {
        // Six nights: scores 5,5 best; 1,1 worst; 3,3 in the middle and ignored.
        val samples = listOf(
            sample("2026-03-01", alertness = 5, caffeine = 100),
            sample("2026-03-02", alertness = 5, caffeine = 120),
            sample("2026-03-03", alertness = 3, caffeine = 300),
            sample("2026-03-04", alertness = 3, caffeine = 300),
            sample("2026-03-05", alertness = 1, caffeine = 500),
            sample("2026-03-06", alertness = 1, caffeine = 520),
        )
        val caffeine = Trends.comparison(samples).first { it.label == "Caffeine, day total" }
        assertEquals(110.0, caffeine.best!!, 0.001)
        assertEquals(510.0, caffeine.worst!!, 0.001)
        assertEquals(-400.0, caffeine.difference!!, 0.001)
        assertEquals(2, caffeine.bestCount)
        assertEquals(2, caffeine.worstCount)
    }

    @Test
    fun `an input nobody recorded is left out of the table`() {
        val samples = (1..6).map { sample("2026-03-0$it", alertness = it) }
        val labels = Trends.comparison(samples).map { it.label }
        assertTrue("resting heart rate was never recorded", "Resting heart rate" !in labels)
        assertTrue("sleep is always present", "Sleep" in labels)
    }

    @Test
    fun `an input recorded on only some nights still reports its counts`() {
        val samples = listOf(
            sample("2026-03-01", alertness = 5, restingHr = 50),
            sample("2026-03-02", alertness = 5),
            sample("2026-03-03", alertness = 3),
            sample("2026-03-04", alertness = 3),
            sample("2026-03-05", alertness = 1, restingHr = 60),
            sample("2026-03-06", alertness = 1, restingHr = 62),
        )
        val hr = Trends.comparison(samples).first { it.label == "Resting heart rate" }
        assertEquals(50.0, hr.best!!, 0.001)
        assertEquals(61.0, hr.worst!!, 0.001)
        assertEquals(1, hr.bestCount)
        assertEquals(2, hr.worstCount)
    }

    @Test
    fun `means skip absent values rather than counting them as zero`() {
        val samples = listOf(
            sample("2026-03-01", alertness = 4, restingHr = 50),
            sample("2026-03-02", alertness = 2),
        )
        val means = Trends.means(samples)
        assertEquals(3.0, means.alertness!!, 0.001)
        assertEquals("one reading, not an average with a zero", 50.0, means.restingHr!!, 0.001)
        assertNull(means.wakeups)
    }

    @Test
    fun `nine nights split into thirds of three`() {
        val samples = (1..9).map { sample("2026-03-0$it", alertness = (it % 5) + 1, caffeine = it * 10) }
        val row = Trends.comparison(samples).first { it.label == "Caffeine, day total" }
        assertEquals(3, row.bestCount)
        assertEquals(3, row.worstCount)
    }

    @Test
    fun `the meal gap handles a meal before midnight and sleep after it`() {
        val night = Night(
            date = "2026-03-04",
            // Sleep starts 02:38 local.
            sleepStart = java.time.ZonedDateTime.of(2026, 3, 5, 2, 38, 0, 0, java.time.ZoneId.systemDefault())
                .toInstant().toEpochMilli(),
            sleepEnd = 3L,
            minutes = 671,
            lastMeal = "21:00",
        )
        val gap = Trends.Sample(night, caffeineMg = 0, bedtimeMg = 0).mealGapHours!!
        assertEquals(5.63, gap, 0.02)
    }
}
