package com.healthy.app.core

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

/**
 * Two day boundaries, because two questions need different answers.
 *
 * Eating and drinking roll over at [BOUNDARY_HOUR], 04:00 (spec 4.4). A coffee
 * at 02:00 belongs to the day still being lived, not to the one about to
 * start, and a midnight boundary would file it under a day the user has not
 * reached yet.
 *
 * Sleep rolls over at [SLEEP_BOUNDARY_HOUR], midnight, so a night is filed
 * under the calendar date its sleep began. This user's sleep starts after
 * midnight — one recorded night ran 02:38 to 13:49 — so under a 04:00 rule it
 * was filed under the day before it started, which reads as the wrong night.
 *
 * Every conversion between an instant and a day goes through here. Nothing
 * else in the app is permitted to call [LocalDate.now] directly, and nothing
 * is permitted to pick a boundary of its own.
 */
object HealthyDay {

    /** Where a day of eating and drinking ends. */
    const val BOUNDARY_HOUR: Int = 4

    /** Where a night ends, for the purpose of naming it. */
    const val SLEEP_BOUNDARY_HOUR: Int = 0

    /**
     * The boundaries as they are written on screen.
     *
     * Derived, so moving an hour moves every sentence that mentions it. They
     * were typed out, and a boundary that says one thing in the code and
     * another in the text is worse than either.
     */
    val BOUNDARY_LABEL: String = "%02d:00".format(BOUNDARY_HOUR)
    val SLEEP_BOUNDARY_LABEL: String = "%02d:00".format(SLEEP_BOUNDARY_HOUR)

    private val ISO: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE

    private fun dayOf(epochMillis: Long, boundary: Int, zone: ZoneId): String {
        val local = Instant.ofEpochMilli(epochMillis).atZone(zone)
        val shifted = if (local.hour < boundary) local.minusDays(1) else local
        return shifted.toLocalDate().format(ISO)
    }

    private fun startOf(day: String, boundary: Int, zone: ZoneId): Long =
        ZonedDateTime.of(LocalDate.parse(day, ISO), LocalTime.of(boundary, 0), zone)
            .toInstant()
            .toEpochMilli()

    private fun endOf(day: String, boundary: Int, zone: ZoneId): Long =
        ZonedDateTime.of(LocalDate.parse(day, ISO).plusDays(1), LocalTime.of(boundary, 0), zone)
            .toInstant()
            .toEpochMilli()

    /**
     * The day of eating and drinking an instant belongs to, as `YYYY-MM-DD`.
     *
     * An instant at 02:38 on the 5th belongs to the 4th, because the day that
     * started at 04:00 on the 4th has not ended yet.
     */
    fun dayOf(epochMillis: Long, zone: ZoneId = ZoneId.systemDefault()): String =
        dayOf(epochMillis, BOUNDARY_HOUR, zone)

    /** The instant at which the day `YYYY-MM-DD` begins, local time. */
    fun startOf(day: String, zone: ZoneId = ZoneId.systemDefault()): Long =
        startOf(day, BOUNDARY_HOUR, zone)

    /** The instant at which the day `YYYY-MM-DD` ends, the next day. */
    fun endOf(day: String, zone: ZoneId = ZoneId.systemDefault()): Long =
        endOf(day, BOUNDARY_HOUR, zone)

    /**
     * The night an instant belongs to, on the midnight boundary.
     *
     * Sleep starting at 02:38 on the 5th is the night of the 5th: the date a
     * night carries is the calendar date its sleep began.
     */
    fun sleepDayOf(epochMillis: Long, zone: ZoneId = ZoneId.systemDefault()): String =
        dayOf(epochMillis, SLEEP_BOUNDARY_HOUR, zone)

    fun sleepStartOf(day: String, zone: ZoneId = ZoneId.systemDefault()): Long =
        startOf(day, SLEEP_BOUNDARY_HOUR, zone)

    fun sleepEndOf(day: String, zone: ZoneId = ZoneId.systemDefault()): Long =
        endOf(day, SLEEP_BOUNDARY_HOUR, zone)

    /** The day of eating and drinking that contains now. */
    fun today(zone: ZoneId = ZoneId.systemDefault()): String =
        dayOf(System.currentTimeMillis(), zone)

    /** The calendar date, which is also the night now in progress. */
    fun sleepToday(zone: ZoneId = ZoneId.systemDefault()): String =
        sleepDayOf(System.currentTimeMillis(), zone)

    /**
     * The night the morning screen opens on.
     *
     * A night carries the calendar date its sleep began. This user's sleep
     * starts after midnight, so the night just finished began today and today
     * is the right default — under the old 04:00 rule it was filed a day
     * earlier and the screen opened on the wrong night.
     *
     * Someone who falls asleep before midnight wants yesterday instead, and
     * the date arrows cover that in one tap.
     */
    fun lastNight(zone: ZoneId = ZoneId.systemDefault()): String = sleepToday(zone)

    /**
     * The eating-and-drinking day that was being lived when a night began.
     *
     * The two boundaries pull the names apart. A night that starts at 02:38 on
     * the 5th is the night of the 5th, while the coffee drunk before it counts
     * against the day that began at 04:00 on the 4th. Joining a night to the
     * intake that preceded it — which is the whole point of the comparison
     * table — has to go through the moment sleep actually started.
     *
     * [sleepStartMillis] is that moment when it is known. Without it the night
     * is assumed to have begun at its own midnight, which names the day the
     * user was still living.
     */
    fun intakeDayForNight(
        nightDate: String,
        sleepStartMillis: Long = 0,
        zone: ZoneId = ZoneId.systemDefault(),
    ): String =
        dayOf(sleepStartMillis.takeIf { it > 0 } ?: sleepStartOf(nightDate, zone), zone)

    /** Shift a logical day by a number of days. */
    fun plusDays(day: String, days: Long): String =
        LocalDate.parse(day, ISO).plusDays(days).format(ISO)
}
