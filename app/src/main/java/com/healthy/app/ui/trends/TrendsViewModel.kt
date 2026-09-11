package com.healthy.app.ui.trends

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.healthy.app.analysis.Trends
import com.healthy.app.core.Caffeine
import com.healthy.app.core.HealthyDay
import com.healthy.app.data.HealthyDatabase
import com.healthy.app.data.HealthySettings
import com.healthy.app.data.SettingsStore
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

data class DayPoint(
    val date: String,
    val sleepHours: Double?,
    val alertness: Int?,
    val caffeineMg: Int,
    val bedtimeMg: Int,
)

data class TrendsState(
    val means: Trends.Means = Trends.Means(null, null, null, null, null, null),
    val comparison: List<Trends.Comparison> = emptyList(),
    val ratedCount: Int = 0,
    val nightCount: Int = 0,
    val points: List<DayPoint> = emptyList(),
    val limitMg: Int = Caffeine.DEFAULT_BEDTIME_LIMIT_MG,
)

@OptIn(ExperimentalCoroutinesApi::class)
class TrendsViewModel(app: Application) : AndroidViewModel(app) {

    private val db = HealthyDatabase.get(app)
    private val settingsStore = SettingsStore(app)

    val state: StateFlow<TrendsState> = combine(
        db.nightDao().observeAll(),
        db.drinkDao().observeDecayWindow(0, Long.MAX_VALUE),
        settingsStore.settings,
    ) { nights, drinks, settings ->
        // The charts cover the last 30 days whether or not a night was logged,
        // because a day with caffeine and no night is still a data point.
        val today = HealthyDay.today()
        val days = (0 until WINDOW_DAYS).map { HealthyDay.plusDays(today, -(WINDOW_DAYS - 1L - it)) }
        // Keyed by the intake day each night followed, not by its own name:
        // with sleep numbered from midnight and intake from 04:00, a night and
        // the day whose coffee preceded it no longer share a date.
        val nightsByIntakeDay = nights.associateBy {
            HealthyDay.intakeDayForNight(it.date, it.sleepStart)
        }

        val points = days.map { date ->
            val from = HealthyDay.startOf(date)
            val to = HealthyDay.endOf(date)
            val dayDrinks = drinks.filter { it.timestamp in from until to }
            val night = nightsByIntakeDay[date]
            DayPoint(
                date = date,
                sleepHours = night?.minutes?.takeIf { it > 0 }?.let { it / 60.0 },
                alertness = night?.alertness,
                caffeineMg = dayDrinks.sumOf { it.mg },
                bedtimeMg = Math.round(
                    Caffeine.levelAt(drinks, bedtimeOn(date, settings), settings.halfLifeHours)
                ).toInt(),
            )
        }

        val samples = nights.map { night ->
            val intakeDay = HealthyDay.intakeDayForNight(night.date, night.sleepStart)
            val from = HealthyDay.startOf(intakeDay)
            val to = HealthyDay.endOf(intakeDay)
            val dayDrinks = drinks.filter { it.timestamp in from until to }
            Trends.Sample(
                night = night,
                caffeineMg = dayDrinks.sumOf { it.mg },
                bedtimeMg = Math.round(
                    Caffeine.levelAt(drinks, bedtimeOn(night.date, settings), settings.halfLifeHours)
                ).toInt(),
            )
        }

        TrendsState(
            means = Trends.means(samples),
            comparison = Trends.comparison(samples),
            ratedCount = Trends.ratedCount(samples),
            nightCount = nights.size,
            points = points,
            limitMg = settings.bedtimeLimitMg,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TrendsState())

    /** The target bedtime instant on a given logical day. */
    private fun bedtimeOn(date: String, settings: HealthySettings): Long {
        val time = runCatching {
            LocalTime.parse(settings.targetBedtime, DateTimeFormatter.ofPattern("HH:mm"))
        }.getOrElse { LocalTime.of(23, 30) }
        val zone = ZoneId.systemDefault()
        val base = LocalDate.parse(date)
        // A bedtime before the boundary belongs to the next calendar day,
        // since the logical day runs boundary to boundary.
        val day = if (time.hour < HealthyDay.BOUNDARY_HOUR) base.plusDays(1) else base
        return ZonedDateTime.of(day, time, zone).toInstant().toEpochMilli()
    }

    private companion object {
        const val WINDOW_DAYS = 30
    }
}
