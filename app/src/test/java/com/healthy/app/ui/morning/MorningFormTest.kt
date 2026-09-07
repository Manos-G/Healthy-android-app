package com.healthy.app.ui.morning

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Duration arithmetic for the morning form (spec 5.2).
 *
 * The user sleeps at unusual hours, so none of these are edge cases here: a
 * night that starts after midnight and ends in the afternoon is the normal
 * case this app was written for.
 */
class MorningFormTest {

    @Test
    fun `the 02_38 to 13_49 night is eleven hours eleven minutes`() {
        val form = MorningForm(sleepStart = "02:38", sleepEnd = "13:49")
        assertEquals(671, form.minutes)
        assertEquals("11 h 11 m", form.durationLabel)
    }

    @Test
    fun `a night crossing midnight adds a day rather than going negative`() {
        val form = MorningForm(sleepStart = "23:30", sleepEnd = "07:15")
        assertEquals(465, form.minutes)
        assertEquals("7 h 45 m", form.durationLabel)
    }

    @Test
    fun `identical times mean a full day, not zero`() {
        assertEquals(24 * 60, MorningForm(sleepStart = "04:00", sleepEnd = "04:00").minutes)
    }

    @Test
    fun `duration is unknown until both times exist`() {
        assertNull(MorningForm(sleepStart = "23:30").minutes)
        assertNull(MorningForm(sleepEnd = "07:15").minutes)
        assertEquals("—", MorningForm().durationLabel)
    }

    @Test
    fun `a malformed time does not crash the form`() {
        assertNull(MorningForm(sleepStart = "not a time", sleepEnd = "07:15").minutes)
    }

    /** Spec 5.2: a night with only a rating still counts. */
    @Test
    fun `saving needs either times or a rating`() {
        assertFalse(MorningForm().canSave)
        assertTrue(MorningForm(alertness = 4).canSave)
        assertTrue(MorningForm(sleepStart = "23:30", sleepEnd = "07:15").canSave)
    }
}
