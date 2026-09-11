package com.healthy.app.core


import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Which stretch of time a log list is showing.
 *
 * Two different questions need two different answers, and the app had only
 * one. "What have I drunk lately" is a rolling question: at 04:20 a coffee
 * from 03:00 is still very much part of the answer, but the logical day rolled
 * over at the boundary and took it off the screen. "How much did I have on
 * Tuesday" is a whole-day question, and it has to use that boundary or a late
 * night lands on the wrong date.
 *
 * So [Rolling] answers the first and [Day] answers the second, and the list
 * says which one it is showing rather than leaving the user to work out why
 * their drink vanished.
 *
 * Totals measured against a daily guideline — energy, the fluid target, the
 * caffeine limit — always use a [Day]. A percentage of a rolling window would
 * be a percentage of nothing in particular.
 */
sealed interface LogWindow {

    /** The last 24 hours from now, ignoring the boundary entirely. */
    data object Rolling : LogWindow

    /** One logical day, boundary to boundary, named by the date it started. */
    data class Day(val date: String) : LogWindow

    fun startMillis(now: Long, zone: ZoneId = ZoneId.systemDefault()): Long = when (this) {
        Rolling -> now - DAY_MILLIS
        is Day -> HealthyDay.startOf(date, zone)
    }

    fun endMillis(now: Long, zone: ZoneId = ZoneId.systemDefault()): Long = when (this) {
        Rolling -> now
        is Day -> HealthyDay.endOf(date, zone)
    }

    /**
     * The logical day this window's totals belong to.
     *
     * A rolling window has no day of its own, so it borrows the current one:
     * the fluid target and the energy target are daily figures and have to be
     * measured against a day even while the list beside them is not.
     */
    fun dayForTotals(now: Long, zone: ZoneId = ZoneId.systemDefault()): String = when (this) {
        Rolling -> HealthyDay.dayOf(now, zone)
        is Day -> date
    }

    companion object {
        const val DAY_MILLIS = 24 * 60 * 60 * 1000L

        private val LABEL: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE d MMM")

        /**
         * One step back in time. From the rolling view that means today as a
         * whole day, then each earlier day in turn.
         */
        fun earlier(window: LogWindow, now: Long, zone: ZoneId = ZoneId.systemDefault()): LogWindow =
            when (window) {
                Rolling -> Day(HealthyDay.dayOf(now, zone))
                is Day -> Day(HealthyDay.plusDays(window.date, -1))
            }

        /** One step towards now, stopping at the rolling view. */
        fun later(window: LogWindow, now: Long, zone: ZoneId = ZoneId.systemDefault()): LogWindow? =
            when (window) {
                Rolling -> null
                is Day -> {
                    val today = HealthyDay.dayOf(now, zone)
                    if (window.date >= today) Rolling else Day(HealthyDay.plusDays(window.date, 1))
                }
            }

        fun label(window: LogWindow, now: Long, zone: ZoneId = ZoneId.systemDefault()): String =
            when (window) {
                Rolling -> "Last 24 hours"
                is Day -> {
                    val today = HealthyDay.dayOf(now, zone)
                    when (window.date) {
                        today -> "Today, since ${HealthyDay.BOUNDARY_LABEL}"
                        HealthyDay.plusDays(today, -1) -> "Yesterday"
                        // A screen can compose before its first emission, so
                        // this is reachable with a date that was never set.
                        // Falling back beats taking the screen down.
                        else -> runCatching { LocalDate.parse(window.date).format(LABEL) }
                            .getOrDefault(window.date)
                    }
                }
            }

        /** True while the window has nothing newer to step to. */
        fun isNewest(window: LogWindow): Boolean = window is Rolling

        /** Where the day boundary falls inside a rolling window, if it does. */
        fun boundaryWithin(window: LogWindow, now: Long, zone: ZoneId = ZoneId.systemDefault()): Long? {
            if (window !is Rolling) return null
            val start = HealthyDay.startOf(HealthyDay.dayOf(now, zone), zone)
            return start.takeIf { it in (now - DAY_MILLIS)..now }
        }

        /** Only used by the tests, to keep [Instant] out of the callers. */
        internal fun at(text: String, zone: ZoneId = ZoneId.systemDefault()): Long =
            java.time.LocalDateTime.parse(text).atZone(zone).toInstant().toEpochMilli()
    }
}
