package com.healthy.app.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.healthy.app.data.entity.Night
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The split rating flow from spec 5.2: the morning saves alertness only, and
 * the 15:00 energy arrives later from the Today screen.
 */
@RunWith(AndroidJUnit4::class)
class MorningNightTest {

    private lateinit var db: HealthyDatabase

    @Before
    fun open() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            HealthyDatabase::class.java,
        ).build()
    }

    @After
    fun close() = db.close()

    private fun night(date: String, alertness: Int? = null, energy: Int? = null) = Night(
        date = date,
        sleepStart = 1L,
        sleepEnd = 2L,
        minutes = 671,
        alertness = alertness,
        energy3pm = energy,
    )

    @Test
    fun morningSaveLeavesEnergyEmpty() = runBlocking {
        db.nightDao().upsert(night("2026-03-20", alertness = 4))
        val saved = db.nightDao().byDate("2026-03-20")!!
        assertEquals(4, saved.alertness)
        assertNull("the morning cannot know the afternoon", saved.energy3pm)
    }

    @Test
    fun theAfternoonRatingFillsTheGap() = runBlocking {
        db.nightDao().upsert(night("2026-03-21", alertness = 3))
        db.nightDao().setEnergy3pm("2026-03-21", 2)

        val updated = db.nightDao().byDate("2026-03-21")!!
        assertEquals(3, updated.alertness)
        assertEquals(2, updated.energy3pm)
    }

    /** Only a night rated in the morning and missing its afternoon rating. */
    @Test
    fun theCardTargetsTheMostRecentUnratedNight() = runBlocking {
        db.nightDao().upsert(night("2026-03-18", alertness = 3, energy = 3)) // complete
        db.nightDao().upsert(night("2026-03-19", alertness = 4))             // waiting
        db.nightDao().upsert(night("2026-03-17"))                            // never rated

        val target = db.nightDao().observeAwaitingEnergyRating().first()
        assertEquals("2026-03-19", target?.date)
    }

    @Test
    fun noCardWhenEveryRatedNightIsComplete() = runBlocking {
        db.nightDao().upsert(night("2026-03-22", alertness = 5, energy = 4))
        assertNull(db.nightDao().observeAwaitingEnergyRating().first())
    }

    /**
     * Re-saving the morning form must not discard an energy rating already
     * given that afternoon. The form carries the stored value through.
     */
    @Test
    fun reSavingTheMorningKeepsAnEnergyRatingAlreadyGiven() = runBlocking {
        val date = "2026-03-23"
        db.nightDao().upsert(night(date, alertness = 4))
        db.nightDao().setEnergy3pm(date, 5)

        val existing = db.nightDao().byDate(date)!!
        db.nightDao().upsert(
            night(date, alertness = 2).copy(energy3pm = existing.energy3pm)
        )

        val after = db.nightDao().byDate(date)!!
        assertEquals(2, after.alertness)
        assertEquals("the afternoon rating must survive a morning re-save", 5, after.energy3pm)
    }
}
