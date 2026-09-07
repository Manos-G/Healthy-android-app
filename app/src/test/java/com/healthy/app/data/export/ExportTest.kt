package com.healthy.app.data.export

import com.healthy.app.data.entity.Drink
import com.healthy.app.data.entity.Night
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId

/** Export format (spec 5.4, acceptance test 8). */
class ExportTest {

    private val athens: ZoneId = ZoneId.of("Europe/Athens")

    @Test
    fun `a field holding a comma is quoted`() {
        assertEquals("\"sore throat, slept badly\"", Csv.escape("sore throat, slept badly"))
    }

    @Test
    fun `an embedded quote is doubled`() {
        assertEquals("\"he said \"\"fine\"\"\"", Csv.escape("he said \"fine\""))
    }

    @Test
    fun `a plain field is left alone and null becomes empty`() {
        assertEquals("Freddo espresso", Csv.escape("Freddo espresso"))
        assertEquals("", Csv.escape(null))
    }

    @Test
    fun `the nights header names every column in the spec`() {
        val header = Csv.nights(emptyList(), athens).lineSequence().first().split(",")
        listOf(
            "date", "sleepStart", "sleepEnd", "minutes", "deepMin", "lightMin", "remMin",
            "awakeMin", "wakeups", "restingHr", "spo2", "alertness", "energy3pm",
            "alcoholUnits", "lastMeal", "exercise", "roomTempC", "notes", "editedFields",
        ).forEach { column ->
            assertTrue("nights CSV is missing $column", column in header)
        }
    }

    @Test
    fun `a null sensor value exports as empty, never as zero`() {
        val csv = Csv.nights(
            listOf(Night(date = "2026-03-05", sleepStart = 0, sleepEnd = 0, minutes = 671)),
            athens,
        )
        val header = csv.lineSequence().first().split(",")
        val row = csv.lineSequence().drop(1).first().split(",")
        assertEquals("", row[header.indexOf("wakeups")])
        assertEquals("", row[header.indexOf("restingHr")])
        assertEquals("", row[header.indexOf("spo2")])
    }

    @Test
    fun `a drink row carries the logical day it belongs to`() {
        // 02:38 on 5 March is still the logical day that began at 04:00 on the 4th.
        val at0238 = java.time.ZonedDateTime.of(2026, 3, 5, 2, 38, 0, 0, athens)
            .toInstant().toEpochMilli()
        val csv = Csv.drinks(
            listOf(Drink(id = 1, name = "Espresso", mg = 63, timestamp = at0238, volumeMl = 30)),
            athens,
        )
        val header = csv.lineSequence().first().split(",")
        val row = csv.lineSequence().drop(1).first().split(",")
        assertEquals("2026-03-04", row[header.indexOf("loggedOnDay")])
        assertEquals("63", row[header.indexOf("mg")])
        assertEquals("30", row[header.indexOf("volumeMl")])
    }

    @Test
    fun `a note containing a newline stays one CSV field`() {
        val escaped = Csv.escape("line one\nline two")
        assertTrue(escaped.startsWith("\"") && escaped.endsWith("\""))
    }
}
