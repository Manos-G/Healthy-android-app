package com.healthy.app.core

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

/**
 * The day boundary for this app is [BOUNDARY_HOUR], not midnight (spec 4.4).
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
    const val BOUNDARY_HOUR: Int = 12

    /**
     * The boundary as it is written on screen.
     *
     * Derived, so moving [BOUNDARY_HOUR] moves every sentence that mentions
     * it. They were typed out, and a boundary that says one thing in the code
     * and another in the text is worse than either.
     */
    val BOUNDARY_LABEL: String = "%02d:00".format(BOUNDARY_HOUR)

    private val ISO: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE

    /**
     * The logical day an instant belongs to, as `YYYY-MM-DD`.
     *
     * An instant at 02:38 on the 5th belongs to the 4th, because the day that
     * started at the boundary on the 4th has not ended yet.
     */
    fun dayOf(epochMillis: Long, zone: ZoneId = ZoneId.systemDefault()): String {
        val local = Instant.ofEpochMilli(epochMillis).atZone(zone)
        val shifted = if (local.hour < BOUNDARY_HOUR) local.minusDays(1) else local
        return shifted.toLocalDate().format(ISO)
    }

    /** The instant at which the logical day `YYYY-MM-DD` begins, local time. */
    fun startOf(day: String, zone: ZoneId = ZoneId.systemDefault()): Long =
        ZonedDateTime.of(LocalDate.parse(day, ISO), LocalTime.of(BOUNDARY_HOUR, 0), zone)
            .toInstant()
            .toEpochMilli()

    /** The instant at which the logical day `YYYY-MM-DD` ends, the next day. */
    fun endOf(day: String, zone: ZoneId = ZoneId.systemDefault()): Long =
        ZonedDateTime.of(LocalDate.parse(day, ISO).plusDays(1), LocalTime.of(BOUNDARY_HOUR, 0), zone)
            .toInstant()
            .toEpochMilli()

    /** The logical day that contains [epochMillis], defaulting to now. */
    fun today(zone: ZoneId = ZoneId.systemDefault()): String =
        dayOf(System.currentTimeMillis(), zone)

    /**
     * The logical day the night just finished is filed under.
     *
     * A night is filed under the day its sleep started, and sleep that starts
     * before the boundary belongs to the previous logical day. So when the user opens
     * the morning screen after waking, the night they mean is yesterday's
     * logical day, not today's — today's night has not happened yet.
     *
     * Sleep that starts after the boundary and ends the same logical day (a long
     * daytime sleep) is the exception; the date arrows cover it.
     */
    fun lastNight(zone: ZoneId = ZoneId.systemDefault()): String =
        plusDays(today(zone), -1)

    /** Shift a logical day by a number of days. */
    fun plusDays(day: String, days: Long): String =
        LocalDate.parse(day, ISO).plusDays(days).format(ISO)
}
