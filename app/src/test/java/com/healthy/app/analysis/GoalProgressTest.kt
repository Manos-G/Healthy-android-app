package com.healthy.app.analysis

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** How far along a weight goal is, and when that is worth saying. */
class GoalProgressTest {

    @Test
    fun `halfway from 80 to 70 is fifty percent`() {
        assertEquals(50, WeightGoal.percentOfGoal(startKg = 80.0, currentKg = 75.0, targetKg = 70.0))
    }

    /** Gaining towards a heavier goal counts the same way. */
    @Test
    fun `progress works in both directions`() {
        assertEquals(25, WeightGoal.percentOfGoal(startKg = 60.0, currentKg = 62.0, targetKg = 68.0))
    }

    /** Going the wrong way is no progress, never negative progress. */
    @Test
    fun `moving away from the goal reads as zero`() {
        assertEquals(0, WeightGoal.percentOfGoal(startKg = 80.0, currentKg = 82.0, targetKg = 70.0))
    }

    @Test
    fun `overshooting the goal stays finished`() {
        assertEquals(100, WeightGoal.percentOfGoal(startKg = 80.0, currentKg = 68.0, targetKg = 70.0))
    }

    @Test
    fun `nothing to measure gives no number`() {
        assertNull(WeightGoal.percentOfGoal(null, 75.0, 70.0))
        assertNull(WeightGoal.percentOfGoal(80.0, null, 70.0))
        assertNull(WeightGoal.percentOfGoal(80.0, 75.0, null))
        // A goal set at the weight already held has no distance to cover.
        assertNull(WeightGoal.percentOfGoal(80.0, 80.0, 80.0))
    }

    @Test
    fun `a milestone is every ten percent`() {
        assertEquals(10, WeightGoal.milestoneReached(percent = 12, alreadyCelebrated = 0))
        assertEquals(30, WeightGoal.milestoneReached(percent = 34, alreadyCelebrated = 20))
        assertEquals(100, WeightGoal.milestoneReached(percent = 100, alreadyCelebrated = 90))
    }

    @Test
    fun `below the first milestone there is nothing to celebrate`() {
        assertNull(WeightGoal.milestoneReached(percent = 9, alreadyCelebrated = 0))
    }

    /** Crossing the same milestone twice must not congratulate twice. */
    @Test
    fun `a milestone already acknowledged stays quiet`() {
        assertNull(WeightGoal.milestoneReached(percent = 31, alreadyCelebrated = 30))
        // Slipped back to 25 and climbed again: still nothing new to say.
        assertNull(WeightGoal.milestoneReached(percent = 25, alreadyCelebrated = 30))
    }
}
