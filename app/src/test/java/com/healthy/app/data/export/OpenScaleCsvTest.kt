package com.healthy.app.data.export

import com.healthy.app.data.entity.Weight
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId

/** Reading an OpenScale export (spec 8.4, acceptance test 32). */
class OpenScaleCsvTest {

    private val athens: ZoneId = ZoneId.of("Europe/Athens")

    @Test
    fun `a typical export is read`() {
        val csv = """
            dateTime,weight,fat,water,muscle,bone,visceralFat,comment
            2026-03-04 08:15:00,81.4,18.2,55.1,42.0,3.1,7,morning
            2026-03-05 08:20:00,81.1,18.0,55.4,42.1,3.1,7,
        """.trimIndent()

        val outcome = OpenScaleCsv.parse(csv, athens)
        assertEquals(2, outcome.rows.size)
        assertEquals(0, outcome.skipped)

        val first = outcome.rows.first()
        assertEquals("2026-03-04", first.date)
        assertEquals(81.4, first.weightKg, 0.0001)
        assertEquals(18.2, first.bodyFatPct!!, 0.0001)
        assertEquals(3.1, first.boneKg!!, 0.0001)
        assertEquals(7.0, first.visceralFat!!, 0.0001)
        assertEquals(Weight.OPENSCALE, first.source)
    }

    /** A dumb scale writes only weight; the rest must stay absent, not zero. */
    @Test
    fun `unmeasured composition stays absent rather than becoming zero`() {
        val csv = """
            dateTime,weight,fat,water
            2026-03-04 08:15:00,81.4,0,0
        """.trimIndent()
        val row = OpenScaleCsv.parse(csv, athens).rows.single()
        assertEquals(81.4, row.weightKg, 0.0001)
        assertNull("a zero from the scale means not measured", row.bodyFatPct)
        assertNull(row.waterPct)
    }

    @Test
    fun `a file with only weight columns still imports`() {
        val csv = "date,weight\n2026-03-04,80.0"
        val outcome = OpenScaleCsv.parse(csv, athens)
        assertEquals(1, outcome.rows.size)
        assertNull(outcome.rows.single().bodyFatPct)
    }

    @Test
    fun `columns are found by name, not by position`() {
        val csv = """
            comment,weight,dateTime,fat
            hello,81.4,2026-03-04 08:15:00,18.2
        """.trimIndent()
        val row = OpenScaleCsv.parse(csv, athens).rows.single()
        assertEquals(81.4, row.weightKg, 0.0001)
        assertEquals(18.2, row.bodyFatPct!!, 0.0001)
    }

    @Test
    fun `a renamed weight column with a unit suffix still resolves`() {
        val csv = "dateTime,weight(kg)\n2026-03-04 08:15:00,79.5"
        assertEquals(79.5, OpenScaleCsv.parse(csv, athens).rows.single().weightKg, 0.0001)
    }

    @Test
    fun `a quoted comment containing a comma does not shift the columns`() {
        val csv = """
            dateTime,comment,weight
            2026-03-04 08:15:00,"after a run, before food",81.4
        """.trimIndent()
        assertEquals(81.4, OpenScaleCsv.parse(csv, athens).rows.single().weightKg, 0.0001)
    }

    @Test
    fun `a decimal comma is read as a decimal point`() {
        val csv = "dateTime,weight\n2026-03-04 08:15:00,\"81,4\""
        assertEquals(81.4, OpenScaleCsv.parse(csv, athens).rows.single().weightKg, 0.0001)
    }

    @Test
    fun `a broken row is skipped and counted rather than dropped silently`() {
        val csv = """
            dateTime,weight
            2026-03-04 08:15:00,81.4
            not a date,also not a weight
            2026-03-06 08:15:00,
        """.trimIndent()
        val outcome = OpenScaleCsv.parse(csv, athens)
        assertEquals(1, outcome.rows.size)
        assertEquals(2, outcome.skipped)
    }

    @Test
    fun `several readings on one day collapse to the last`() {
        val csv = """
            dateTime,weight
            2026-03-04 08:15:00,81.4
            2026-03-04 20:30:00,82.1
        """.trimIndent()
        val row = OpenScaleCsv.parse(csv, athens).rows.single()
        assertEquals(82.1, row.weightKg, 0.0001)
    }

    /** The 04:00 boundary applies here as everywhere else. */
    @Test
    fun `a reading before four in the morning files with the previous day`() {
        val csv = "dateTime,weight\n2026-03-05 02:30:00,81.4"
        assertEquals("2026-03-04", OpenScaleCsv.parse(csv, athens).rows.single().date)
    }

    @Test
    fun `a file that is not an OpenScale export is refused with a reason`() {
        val outcome = OpenScaleCsv.parse("name,age\nManos,30", athens)
        assertTrue(outcome.rows.isEmpty())
        assertTrue(outcome.problem!!.contains("OpenScale"))
    }

    @Test
    fun `an empty file is refused`() {
        assertTrue(OpenScaleCsv.parse("", athens).problem != null)
    }
}
