package com.healthy.app.data.export

import com.healthy.app.core.HealthyDay
import com.healthy.app.data.entity.Weight
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Reads an OpenScale CSV export (spec 8.4).
 *
 * OpenScale already solves Bluetooth scales across many models, so this app
 * does not write scale code — it reads what OpenScale exports.
 *
 * The parser is driven by the header rather than by column position, and
 * matches names loosely, because OpenScale's export has changed shape across
 * versions and a file the user actually has must not be rejected over a
 * renamed column. A row whose weight cannot be read is skipped and counted
 * rather than silently dropped.
 */
object OpenScaleCsv {

    data class Outcome(
        val rows: List<Weight>,
        val skipped: Int,
        val problem: String? = null,
    )

    /** Header names accepted for each field, lowercased and stripped. */
    private val DATE_KEYS = listOf("datetime", "date", "time", "timestamp")
    private val WEIGHT_KEYS = listOf("weight", "weightkg", "kg", "mass")
    private val FAT_KEYS = listOf("fat", "bodyfat", "fatpercent", "bodyfatpct")
    private val WATER_KEYS = listOf("water", "waterpercent", "hydration")
    private val MUSCLE_KEYS = listOf("muscle", "musclepercent")
    private val BONE_KEYS = listOf("bone", "bonemass", "bonekg")
    private val VISCERAL_KEYS = listOf("visceralfat", "visceral")

    private val DATE_FORMATS = listOf(
        "yyyy-MM-dd HH:mm:ss",
        "yyyy-MM-dd'T'HH:mm:ss",
        "yyyy-MM-dd HH:mm",
        "dd.MM.yyyy HH:mm",
        "dd/MM/yyyy HH:mm",
        "yyyy/MM/dd HH:mm",
    )

    fun parse(text: String, zone: ZoneId = ZoneId.systemDefault()): Outcome {
        val lines = text.lineSequence().filter { it.isNotBlank() }.toList()
        if (lines.isEmpty()) return Outcome(emptyList(), 0, "the file is empty")

        val header = splitRow(lines.first()).map { normalise(it) }
        val dateAt = header.indexOfFirstIn(DATE_KEYS)
        val weightAt = header.indexOfFirstIn(WEIGHT_KEYS)
        if (dateAt < 0 || weightAt < 0) {
            return Outcome(
                emptyList(), 0,
                "this does not look like an OpenScale export: no date and weight columns found",
            )
        }

        val fatAt = header.indexOfFirstIn(FAT_KEYS)
        val waterAt = header.indexOfFirstIn(WATER_KEYS)
        val muscleAt = header.indexOfFirstIn(MUSCLE_KEYS)
        val boneAt = header.indexOfFirstIn(BONE_KEYS)
        val visceralAt = header.indexOfFirstIn(VISCERAL_KEYS)

        var skipped = 0
        val rows = lines.drop(1).mapNotNull { line ->
            val cells = splitRow(line)
            val instant = cells.getOrNull(dateAt)?.let(::parseInstant)
            val kg = cells.getOrNull(weightAt)?.let(::parseNumber)
            if (instant == null || kg == null || kg <= 0) {
                skipped++
                return@mapNotNull null
            }
            val millis = instant.atZone(zone).toInstant().toEpochMilli()
            Weight(
                // The logical day, so an evening weigh-in after midnight files
                // with the day it belongs to like everything else.
                date = HealthyDay.dayOf(millis, zone),
                weightKg = kg,
                timestamp = millis,
                // A scale that did not measure a field writes nothing or a
                // zero; both mean "not measured" and must not become 0.0.
                bodyFatPct = cells.getOrNull(fatAt)?.let(::parseNumber)?.takeIf { it > 0 },
                waterPct = cells.getOrNull(waterAt)?.let(::parseNumber)?.takeIf { it > 0 },
                musclePct = cells.getOrNull(muscleAt)?.let(::parseNumber)?.takeIf { it > 0 },
                boneKg = cells.getOrNull(boneAt)?.let(::parseNumber)?.takeIf { it > 0 },
                visceralFat = cells.getOrNull(visceralAt)?.let(::parseNumber)?.takeIf { it > 0 },
                source = Weight.OPENSCALE,
            )
        }
            // One reading per day, keeping the last: OpenScale can hold several
            // and the weight table is keyed by day.
            .groupBy { it.date }
            .map { (_, sameDay) -> sameDay.maxBy { it.timestamp } }
            .sortedBy { it.date }

        return Outcome(rows, skipped)
    }

    private fun List<String>.indexOfFirstIn(keys: List<String>): Int {
        forEachIndexed { index, name -> if (name in keys) return index }
        // Fall back to a prefix match, so "weight(kg)" still resolves.
        forEachIndexed { index, name ->
            if (keys.any { name.startsWith(it) }) return index
        }
        return -1
    }

    private fun normalise(cell: String): String =
        cell.trim().trim('"').lowercase().filter { it.isLetterOrDigit() }

    /** Minimal RFC 4180 split: honours quotes so a quoted comma stays put. */
    private fun splitRow(line: String): List<String> {
        val cells = mutableListOf<String>()
        val current = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < line.length) {
            val c = line[i]
            when {
                c == '"' && inQuotes && i + 1 < line.length && line[i + 1] == '"' -> {
                    current.append('"'); i++
                }
                c == '"' -> inQuotes = !inQuotes
                c == ',' && !inQuotes -> {
                    cells += current.toString(); current.clear()
                }
                else -> current.append(c)
            }
            i++
        }
        cells += current.toString()
        return cells.map { it.trim() }
    }

    private fun parseNumber(cell: String): Double? =
        cell.trim().trim('"').replace(',', '.').toDoubleOrNull()

    private fun parseInstant(cell: String): LocalDateTime? {
        val text = cell.trim().trim('"')
        DATE_FORMATS.forEach { pattern ->
            runCatching {
                return LocalDateTime.parse(text, DateTimeFormatter.ofPattern(pattern))
            }
        }
        // A date with no time is midnight, which the 04:00 boundary then files
        // with the previous day — correct, since a weight recorded at midnight
        // belongs to the day that was still running.
        runCatching { return LocalDate.parse(text).atStartOfDay() }
        // Epoch millis, which some exports use.
        text.toLongOrNull()?.let { millis ->
            return LocalDateTime.ofInstant(
                java.time.Instant.ofEpochMilli(millis),
                ZoneId.systemDefault(),
            )
        }
        return null
    }
}
