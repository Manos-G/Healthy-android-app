package com.healthy.app.core

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

/**
 * The day boundary for this app is 04:00, not midnight (spec 4.4).
 *
 * This user goes to sleep after midnight. One recorded night ran 02:38 to
 * 13:49. A midnight boundary splits that single night across two calendar
 * days, which corrupts every per-day total and every night-to-day join.
 *
 * Every conversion between an instant and a logical day goes through here.
 * Nothing else in the app is permitted to call [LocalDate.now] directly.
 */
object HealthyDay {

    /** The hour at which one logical day ends and the next begins. */
    const val BOUNDARY_HOUR: Int = 4

    private val ISO: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE

    /**
     * The logical day an instant belongs to, as `YYYY-MM-DD`.
     *
     * An instant at 02:38 on the 5th belongs to the 4th, because the day that
     * started at 04:00 on the 4th has not ended yet.
     */
    fun dayOf(epochMillis: Long, zone: ZoneId = ZoneId.systemDefault()): String {
        val local = Instant.ofEpochMilli(epochMillis).atZone(zone)
        val shifted = if (local.hour < BOUNDARY_HOUR) local.minusDays(1) else local
        return shifted.toLocalDate().format(ISO)
    }

    /** The instant at which the logical day `YYYY-MM-DD` begins (04:00 local). */
    fun startOf(day: String, zone: ZoneId = ZoneId.systemDefault()): Long =
        ZonedDateTime.of(LocalDate.parse(day, ISO), LocalTime.of(BOUNDARY_HOUR, 0), zone)
            .toInstant()
            .toEpochMilli()

    /** The instant at which the logical day `YYYY-MM-DD` ends (04:00 the next day). */
    fun endOf(day: String, zone: ZoneId = ZoneId.systemDefault()): Long =
        ZonedDateTime.of(LocalDate.parse(day, ISO).plusDays(1), LocalTime.of(BOUNDARY_HOUR, 0), zone)
            .toInstant()
            .toEpochMilli()

    /** The logical day that contains [epochMillis], defaulting to now. */
    fun today(zone: ZoneId = ZoneId.systemDefault()): String =
        dayOf(System.currentTimeMillis(), zone)

    /** Shift a logical day by a number of days. */
    fun plusDays(day: String, days: Long): String =
        LocalDate.parse(day, ISO).plusDays(days).format(ISO)
}
