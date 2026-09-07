package com.healthy.app.analysis

import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * Menstrual cycle arithmetic (spec 10).
 *
 * Off by default and hidden entirely until the user turns it on. The app never
 * asks for a gender and never shows this at first start: the toggle is the
 * only thing that brings any of it into existence.
 */
object Cycle {

    /** Health Connect's flow values, which the morning field mirrors (spec 10.3). */
    enum class Flow { None, Light, Medium, Heavy }

    /**
     * Cycle day counted from the most recent period start, where day 1 is the
     * first day of the period (spec 10.3).
     *
     * Null when no period has been recorded, or when the date being asked
     * about is before the most recent start — the app does not guess backwards
     * into a cycle it has no record of.
     */
    fun dayOfCycle(periodStarts: List<String>, on: String): Int? {
        val target = runCatching { LocalDate.parse(on) }.getOrNull() ?: return null
        val mostRecent = periodStarts
            .mapNotNull { runCatching { LocalDate.parse(it) }.getOrNull() }
            .filter { !it.isAfter(target) }
            .maxOrNull()
            ?: return null
        return ChronoUnit.DAYS.between(mostRecent, target).toInt() + 1
    }

    /**
     * The length of each completed cycle, in days: the gap between one period
     * start and the next.
     */
    fun cycleLengths(periodStarts: List<String>): List<Int> {
        val dates = periodStarts
            .mapNotNull { runCatching { LocalDate.parse(it) }.getOrNull() }
            .sorted()
        return dates.zipWithNext { a, b -> ChronoUnit.DAYS.between(a, b).toInt() }
    }

    /** The median completed cycle length. Null until two periods are recorded. */
    fun medianCycleLength(periodStarts: List<String>): Int? {
        val lengths = cycleLengths(periodStarts).sorted()
        if (lengths.isEmpty()) return null
        val mid = lengths.size / 2
        return if (lengths.size % 2 == 1) lengths[mid] else (lengths[mid - 1] + lengths[mid]) / 2
    }

    /**
     * Groups nights by cycle day for the chart in spec 10.4.
     *
     * Body temperature rises in the second half of the cycle and sleep quality
     * often falls before a period. The chart shows whether that is true for
     * this user rather than asserting that it is.
     */
    fun <T> byCycleDay(
        items: List<T>,
        periodStarts: List<String>,
        dateOf: (T) -> String,
    ): Map<Int, List<T>> =
        items.mapNotNull { item ->
            dayOfCycle(periodStarts, dateOf(item))?.let { day -> day to item }
        }.groupBy({ it.first }, { it.second })
}
