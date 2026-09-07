package com.healthy.app.data.export

import com.healthy.app.data.entity.Drink
import com.healthy.app.data.entity.Night
import com.healthy.app.data.entity.Note
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * CSV export (spec 5.4).
 *
 * These are pure functions over lists so the format can be tested on the JVM
 * without a device or a file picker.
 *
 * Every row carries both the raw epoch millis and a readable local timestamp.
 * The millis are what re-import needs; the readable column is what makes the
 * file useful in a spreadsheet, which is what acceptance test 8 asks for.
 */
object Csv {

    private val ISO_SECONDS: DateTimeFormatter =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

    /** RFC 4180: wrap in quotes when the value holds a comma, quote or newline. */
    fun escape(value: String?): String {
        val text = value ?: return ""
        return if (text.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) {
            "\"" + text.replace("\"", "\"\"") + "\""
        } else {
            text
        }
    }

    private fun Long.readable(zone: ZoneId): String =
        if (this <= 0L) "" else Instant.ofEpochMilli(this).atZone(zone).format(ISO_SECONDS)

    private fun row(vararg cells: Any?): String =
        cells.joinToString(",") { escape(it?.toString()) }

    fun nights(nights: List<Night>, zone: ZoneId = ZoneId.systemDefault()): String = buildString {
        appendLine(
            "date,sleepStart,sleepEnd,sleepStartLocal,sleepEndLocal,minutes,hours," +
                "deepMin,lightMin,remMin,awakeMin,wakeups,restingHr,spo2," +
                "alertness,energy3pm,alcoholUnits,lastMeal,exercise,roomTempC,notes,editedFields"
        )
        nights.forEach { n ->
            appendLine(
                row(
                    n.date, n.sleepStart, n.sleepEnd,
                    n.sleepStart.readable(zone), n.sleepEnd.readable(zone),
                    n.minutes, "%.2f".format(n.minutes / 60.0),
                    n.deepMin, n.lightMin, n.remMin, n.awakeMin, n.wakeups,
                    n.restingHr, n.spo2,
                    n.alertness, n.energy3pm, n.alcoholUnits, n.lastMeal,
                    n.exercise, n.roomTempC, n.notes,
                    n.editedFields.sorted().joinToString(" "),
                )
            )
        }
    }

    fun drinks(drinks: List<Drink>, zone: ZoneId = ZoneId.systemDefault()): String = buildString {
        appendLine("id,name,mg,volumeMl,timestamp,localTime,loggedOnDay")
        drinks.forEach { d ->
            appendLine(
                row(
                    d.id, d.name, d.mg, d.volumeMl, d.timestamp,
                    d.timestamp.readable(zone),
                    // The logical day, so a spreadsheet groups the way the app does.
                    com.healthy.app.core.HealthyDay.dayOf(d.timestamp, zone),
                )
            )
        }
    }

    fun notes(notes: List<Note>, zone: ZoneId = ZoneId.systemDefault()): String = buildString {
        appendLine("id,date,text,createdAt,createdLocal")
        notes.forEach { n ->
            appendLine(row(n.id, n.date, n.text, n.createdAt, n.createdAt.readable(zone)))
        }
    }
}
