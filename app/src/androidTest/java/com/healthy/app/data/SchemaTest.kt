package com.healthy.app.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.healthy.app.core.HealthyDay
import com.healthy.app.data.entity.Drink
import com.healthy.app.data.entity.Night
import com.healthy.app.data.entity.Note
import com.healthy.app.ui.notes.NoteSearch
import com.healthy.app.data.entity.Recipe
import com.healthy.app.data.entity.RecipeItem
import com.healthy.app.data.entity.StageBlock
import com.healthy.app.data.entity.Weight
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.ZoneId

/**
 * Exercises the parts of the data model that carry a rule from the spec.
 * These run on the phone, against real SQLite, not on the JVM.
 */
@RunWith(AndroidJUnit4::class)
class SchemaTest {

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

    /**
     * Acceptance test 5. A session from 02:38 to 13:49 must record against the
     * day the sleep started, which under a 04:00 boundary is the previous
     * calendar date.
     */
    @Test
    fun lateNightRecordsAgainstTheDayTheSleepStarted() {
        val athens = ZoneId.of("Europe/Athens")
        // 2026-03-05 02:38 local
        val start = java.time.ZonedDateTime.of(2026, 3, 5, 2, 38, 0, 0, athens)
            .toInstant().toEpochMilli()
        val end = java.time.ZonedDateTime.of(2026, 3, 5, 13, 49, 0, 0, athens)
            .toInstant().toEpochMilli()

        assertEquals("2026-03-04", HealthyDay.dayOf(start, athens))
        // The night ends inside the next logical day; the key stays the start day.
        assertEquals("2026-03-05", HealthyDay.dayOf(end, athens))
    }

    @Test
    fun nightRoundTripsWithNullSensorFields() = runBlocking {
        val night = Night(
            date = "2026-03-04",
            sleepStart = 1_772_000_000_000,
            sleepEnd = 1_772_040_000_000,
            minutes = 671,
            alertness = 4,
            energy3pm = 3,
            notes = "woke once",
        )
        db.nightDao().upsert(night)

        val read = db.nightDao().byDate("2026-03-04")
        assertNotNull(read)
        assertEquals(671, read!!.minutes)
        // "not reported" is a null, never a zero (spec 3.3).
        assertNull(read.wakeups)
        assertNull(read.deepMin)
        assertNull(read.spo2)
    }

    @Test
    fun editedFieldsSurviveTheRoundTrip() = runBlocking {
        db.nightDao().upsert(
            Night(
                date = "2026-03-06",
                sleepStart = 1L,
                sleepEnd = 2L,
                minutes = 1,
                editedFields = setOf("restingHr", "wakeups"),
            )
        )
        val read = db.nightDao().byDate("2026-03-06")!!
        assertEquals(setOf("restingHr", "wakeups"), read.editedFields)
    }

    /** Spec 4.2: a re-sync replaces the blocks and never duplicates them. */
    @Test
    fun resyncReplacesStageBlocksRatherThanDuplicating() = runBlocking {
        val date = "2026-03-07"
        db.nightDao().upsert(Night(date = date, sleepStart = 1L, sleepEnd = 2L, minutes = 1))

        val blocks = listOf(
            StageBlock(nightDate = date, type = StageBlock.DEEP, startTime = 10, endTime = 20),
            StageBlock(nightDate = date, type = StageBlock.LIGHT, startTime = 20, endTime = 30),
        )
        db.nightDao().replaceStageBlocks(date, blocks)
        db.nightDao().replaceStageBlocks(date, blocks)

        assertEquals(2, db.nightDao().stageBlocks(date).size)
    }

    /** Deleting a night takes its stage blocks with it. */
    @Test
    fun deletingANightCascadesToStageBlocks() = runBlocking {
        val date = "2026-03-08"
        db.nightDao().upsert(Night(date = date, sleepStart = 1L, sleepEnd = 2L, minutes = 1))
        db.nightDao().insertStageBlocks(
            listOf(StageBlock(nightDate = date, type = StageBlock.REM, startTime = 1, endTime = 2))
        )
        db.nightDao().delete(date)
        assertTrue(db.nightDao().stageBlocks(date).isEmpty())
    }

    /**
     * Spec 9.1: one tap logs the caffeine and the fluid together, so a day's
     * drinks answer both totals from one table.
     */
    @Test
    fun drinksSumBothCaffeineAndFluidForTheLogicalDay() = runBlocking {
        val day = "2026-03-09"
        val from = HealthyDay.startOf(day)
        val to = HealthyDay.endOf(day)

        db.drinkDao().insert(Drink(name = "Freddo espresso", mg = 125, timestamp = from + 3_600_000, volumeMl = 200))
        db.drinkDao().insert(Drink(name = "Hell 250 ml", mg = 80, timestamp = from + 7_200_000, volumeMl = 250))
        // Outside the window: 05:00 the following logical day.
        db.drinkDao().insert(Drink(name = "Espresso", mg = 63, timestamp = to + 3_600_000, volumeMl = 30))

        assertEquals(205, db.drinkDao().totalMg(from, to))
        assertEquals(450, db.drinkDao().observeVolumeMl(from, to).first())
    }

    /** Spec 8.4: an import skips a date already stored. */
    @Test
    fun weightImportIgnoresADateAlreadyStored() = runBlocking {
        db.weightDao().upsert(
            Weight(date = "2026-03-10", weightKg = 81.4, timestamp = 1L, source = Weight.MANUAL)
        )
        db.weightDao().insertIgnoringExisting(
            listOf(
                Weight(date = "2026-03-10", weightKg = 99.9, timestamp = 2L, source = Weight.OPENSCALE),
                Weight(date = "2026-03-11", weightKg = 81.1, timestamp = 3L, source = Weight.OPENSCALE),
            )
        )
        assertEquals(81.4, db.weightDao().byDate("2026-03-10")!!.weightKg, 0.001)
        assertEquals(81.1, db.weightDao().byDate("2026-03-11")!!.weightKg, 0.001)
        assertEquals(2, db.weightDao().count())
    }

    @Test
    fun weightKeepsNullBodyCompositionSeparateFromZero() = runBlocking {
        db.weightDao().upsert(Weight(date = "2026-03-12", weightKg = 80.0, timestamp = 1L))
        val read = db.weightDao().byDate("2026-03-12")!!
        assertNull(read.bodyFatPct)
        assertNull(read.visceralFat)
    }

    /** Spec 15.2: the FTS index finds a note by a word in its text. */
    @Test
    fun notesAreFoundByFullTextSearch() = runBlocking {
        db.noteDao().insert(Note(date = "2026-03-13", text = "sore throat, slept badly", createdAt = 1L))
        db.noteDao().insert(Note(date = "2026-03-14", text = "teeth pain returned", createdAt = 2L))

        val hits = db.noteDao().search("throat").first()
        assertEquals(1, hits.size)
        assertEquals("2026-03-13", hits.first().date)

        val dates = db.noteDao().observeDatesWithNotes("2026-03-01", "2026-03-31").first()
        assertEquals(2, dates.size)
    }

    /** Editing a note keeps the FTS index in step with the row. */
    @Test
    fun editingANoteUpdatesTheSearchIndex() = runBlocking {
        val id = db.noteDao().insert(Note(date = "2026-03-15", text = "headache", createdAt = 1L))
        db.noteDao().update(Note(id = id, date = "2026-03-15", text = "migraine", createdAt = 1L))

        assertTrue(db.noteDao().search("headache").first().isEmpty())
        assertEquals(1, db.noteDao().search("migraine").first().size)
    }

    /**
     * The notes screen searches as the user types, so a prefix has to match.
     * NoteSearchTest checks the query is built correctly; this checks SQLite
     * agrees.
     */
    @Test
    fun aPrefixMatchesBeforeTheWordIsFinished() = runBlocking {
        db.noteDao().insert(Note(date = "2026-03-16", text = "sore throat again", createdAt = 1L))

        // Built the way the notes screen builds it, so this test fails if the
        // query syntax ever stops being valid FTS4.
        assertEquals(1, db.noteDao().search(NoteSearch.toMatchQuery("thro")).first().size)
        assertEquals(1, db.noteDao().search(NoteSearch.toMatchQuery("sore thro")).first().size)
        assertTrue(db.noteDao().search(NoteSearch.toMatchQuery("zzz")).first().isEmpty())
        // A term that is only punctuation must not throw, and must find nothing.
        assertTrue(db.noteDao().search(NoteSearch.toMatchQuery("!!!")).first().isEmpty())
        // A typed operator is searched for as a word rather than parsed.
        assertTrue(db.noteDao().search(NoteSearch.toMatchQuery("sore OR zzz")).first().isEmpty())
    }

    @Test
    fun recipeSavesWithItemsAndCascadesOnDelete() = runBlocking {
        val id = db.recipeDao().saveRecipe(
            Recipe(name = "Lentil soup", cookedGrams = 700.0, portions = 3),
            listOf(
                RecipeItem(recipeId = 0, name = "lentils", grams = 300.0),
                RecipeItem(recipeId = 0, name = "onion", grams = 150.0),
            ),
        )
        assertEquals(2, db.recipeDao().items(id).size)

        db.recipeDao().deleteRecipe(db.recipeDao().recipe(id)!!)
        assertTrue(db.recipeDao().items(id).isEmpty())
    }
}
