package com.healthy.app.health

import android.content.Context
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.HydrationRecord
import androidx.health.connect.client.records.OxygenSaturationRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.WeightRecord
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import com.healthy.app.core.HealthyDay
import com.healthy.app.analysis.Hypnogram
import com.healthy.app.data.entity.StageBlock
import java.time.Instant
import java.time.ZoneId

/**
 * Reads one night out of Health Connect (spec 3.3).
 *
 * Everything here is a query plus a translation into the app's own types. The
 * arithmetic lives in [SleepAnalysis] so it can be tested without a device.
 */
class HealthReader(private val context: Context) {

    data class NightData(
        val sleepStart: Long,
        val sleepEnd: Long,
        val minutes: Int,
        val stageBlocks: List<StageBlock>,
        val totals: SleepAnalysis.StageTotals,
        val restingHr: Int?,
        val spo2: Double?,
        /** Individual bpm samples, kept for the hypnogram in step 15. */
        val heartRateSampleCount: Int,
        /**
         * The app that wrote the session, and how many other apps wrote one
         * for the same night.
         *
         * With more than one writer installed (Mi Fitness and Gadgetbridge,
         * say) two sessions can describe the same night with different stages.
         * The reader keeps the longest, but the user should be able to see
         * which source that was rather than guess.
         */
        val source: String,
        val competingSessions: Int,
        /**
         * How many separate sleeps this day held. More than one means a nap
         * as well as a night, and [minutes] is their sum rather than the span
         * between the first and the last.
         */
        val sleepCount: Int = 1,
        /**
         * The longest sleep, which [sleepStart] and [sleepEnd] describe.
         *
         * Separate from [minutes], which is every sleep of the day added up.
         * When they differ the day held a nap as well as a night.
         */
        val mainSleepMinutes: Int = 0,
        /** Which app supplied the heart rate, which may differ from the sleep. */
        val heartRateSource: String? = null,
        /** Individual samples, which spec 18.3 requires over the 30-minute groups. */
        val heartRateSamples: List<Hypnogram.Sample> = emptyList(),
    )

    sealed interface Result {
        data class Found(val data: NightData) : Result
        data object NoSession : Result
        data class Failed(val reason: String) : Result
    }

    /**
     * Finds the session belonging to the logical day [date] and reads it.
     *
     * The search window is deliberately wide: a night filed under a date can
     * start at 02:38 the following calendar morning and end at 13:49, so
     * querying the calendar day would miss it entirely. The window runs from
     * the logical day's 04:00 start to 04:00 two days later, and the session
     * kept is the one whose *start* falls in the logical day (spec 4.4).
     */
    suspend fun readNight(date: String, zone: ZoneId = ZoneId.systemDefault()): Result {
        val client = HealthConnect.client(context) ?: return Result.Failed("Health Connect is unavailable")

        return runCatching {
            val dayStart = HealthyDay.startOf(date, zone)
            val dayEnd = HealthyDay.endOf(date, zone)

            val sessions = client.readRecords(
                ReadRecordsRequest(
                    recordType = SleepSessionRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(
                        Instant.ofEpochMilli(dayStart),
                        Instant.ofEpochMilli(dayEnd + DAY_MILLIS),
                    ),
                )
            ).records

            val forThisNight = sessions
                .filter { HealthyDay.dayOf(it.startTime.toEpochMilli(), zone) == date }

            // Two apps writing the same night produce overlapping records of
            // one sleep, and only the denser one should count. A night and a
            // nap produce records that do not overlap, and both are real —
            // keeping only the longest recorded one sleep on a day with two.
            val chosen = SleepAnalysis.distinctSleeps(
                forThisNight.mapIndexed { index, record ->
                    SleepAnalysis.Session(
                        start = record.startTime.toEpochMilli(),
                        end = record.endTime.toEpochMilli(),
                        stageCount = record.stages.size,
                        index = index,
                    )
                }
            )
            if (chosen.isEmpty()) return@runCatching Result.NoSession

            /*
             * One of these sleeps is the night and the rest are naps.
             *
             * Taking the earliest start and the latest end described a stretch
             * that was never one sleep: an afternoon at 13:22 and an early
             * morning ending at 06:00 read as "asleep 13:22 to 06:00", which
             * is seventeen hours of which eight were spent awake. The times
             * now describe the longest sleep, which is a real one, and the
             * others are counted separately.
             */
            val main = chosen.maxByOrNull { it.millis } ?: chosen.first()
            val session = forThisNight[main.index]

            val start = main.start
            val end = main.end
            val mainMinutes = (main.millis / 60_000L).toInt()
            // Time asleep across the day is the sum, never the span.
            val minutes = SleepAnalysis.totalMinutes(chosen)

            // The stages, the hypnogram and the resting heart rate all
            // describe the main sleep, because they are drawn against its
            // start and end and a nap's blocks inside that window would be
            // hours of empty chart.
            val blocks = session.stages.map { stage ->
                StageBlock(
                    nightDate = date,
                    type = stage.stage.toStageName(),
                    startTime = stage.startTime.toEpochMilli(),
                    endTime = stage.endTime.toEpochMilli(),
                )
            }.sortedBy { it.startTime }

            // A record has to be read by a window wide enough to CONTAIN it,
            // not merely to overlap it: Health Connect's `between` matches
            // interval records that fall inside the range, so a writer that
            // packs a whole day into one HeartRateRecord — Gadgetbridge does —
            // is invisible to a query bounded by the sleep window. Read wide,
            // then filter the individual samples to the night.
            val heartRecords = client.readRecords(
                ReadRecordsRequest(
                    HeartRateRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(
                        Instant.ofEpochMilli(start - RECORD_MARGIN_MILLIS),
                        Instant.ofEpochMilli(end + RECORD_MARGIN_MILLIS),
                    ),
                )
            ).records

            // Never merge two writers' heart rate: the same beat recorded by
            // both would appear twice and drag the percentile. Take whichever
            // source has the most samples inside this night and use only that.
            val nightSamples = heartRecords
                .groupBy { it.metadata.dataOrigin.packageName }
                .mapValues { (_, records) ->
                    records.flatMap { it.samples }
                        .filter { sample -> sample.time.toEpochMilli() in start..end }
                        .map { Hypnogram.Sample(it.time.toEpochMilli(), it.beatsPerMinute.toInt()) }
                }
                .maxByOrNull { it.value.size }
                ?.value
                .orEmpty()
                .sortedBy { it.timeMillis }

            // Spec 3.3 asks for the 5th percentile of the beats-per-minute
            // values, and adds that the device writes 30-minute groups whose
            // minima should be used. That second instruction exists because a
            // writer that only exposes group min/max hides the samples.
            //
            // Gadgetbridge exposes every sample, and bucketing them defeats the
            // purpose: a 4-hour night is only 9 buckets, and the 5th percentile
            // of 9 numbers is just the lowest — precisely the single bad reading
            // the percentile was chosen to reject. So take the percentile over
            // the samples when they are dense enough to have one, and fall back
            // to bucket minima when they are not.
            val bpmForResting = if (nightSamples.size >= MIN_SAMPLES_FOR_PERCENTILE) {
                nightSamples.map { it.bpm }
            } else {
                nightSamples
                    .groupBy { (it.timeMillis - start) / THIRTY_MINUTES_MILLIS }
                    .mapNotNull { (_, group) -> group.minOfOrNull { it.bpm } }
            }

            // Same containment rule applies here, so read wide and filter.
            val oxygen = client.readRecords(
                ReadRecordsRequest(
                    OxygenSaturationRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(
                        Instant.ofEpochMilli(start - RECORD_MARGIN_MILLIS),
                        Instant.ofEpochMilli(end + RECORD_MARGIN_MILLIS),
                    ),
                )
            ).records
                .filter { it.time.toEpochMilli() in start..end }
                .groupBy { it.metadata.dataOrigin.packageName }
                .maxByOrNull { it.value.size }
                ?.value
                .orEmpty()
                .map { it.percentage.value }

            Result.Found(
                NightData(
                    sleepStart = start,
                    sleepEnd = end,
                    minutes = minutes,
                    mainSleepMinutes = mainMinutes,
                    stageBlocks = blocks,
                    totals = SleepAnalysis.stageTotals(blocks),
                    restingHr = SleepAnalysis.restingHeartRate(bpmForResting),
                    spo2 = SleepAnalysis.meanSpo2(oxygen),
                    heartRateSampleCount = nightSamples.size,
                    source = session.metadata.dataOrigin.packageName,
                    competingSessions = forThisNight.size - chosen.size,
                    sleepCount = chosen.size,
                    heartRateSamples = nightSamples,
                    heartRateSource = heartRecords
                        .groupBy { it.metadata.dataOrigin.packageName }
                        .mapValues { (_, r) ->
                            r.sumOf { rec ->
                                rec.samples.count { it.time.toEpochMilli() in start..end }
                            }
                        }
                        .filterValues { it > 0 }
                        .maxByOrNull { it.value }
                        ?.key,
                )
            )
        }.getOrElse { Result.Failed(it.message ?: it::class.simpleName ?: "unknown error") }
    }

    data class RecentSession(val date: String, val endMillis: Long, val minutes: Int)

    /**
     * The most recently finished sleep session, which is what the notification
     * job compares against (spec 14.2).
     */
    suspend fun mostRecentSession(zone: ZoneId = ZoneId.systemDefault()): RecentSession? {
        val client = HealthConnect.client(context) ?: return null
        return runCatching {
            client.readRecords(
                ReadRecordsRequest(
                    SleepSessionRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(
                        Instant.now().minusSeconds(3 * 24 * 60 * 60),
                        Instant.now(),
                    ),
                )
            ).records
                .maxByOrNull { it.endTime.toEpochMilli() }
                ?.let {
                    RecentSession(
                        date = HealthyDay.dayOf(it.startTime.toEpochMilli(), zone),
                        endMillis = it.endTime.toEpochMilli(),
                        minutes = ((it.endTime.toEpochMilli() - it.startTime.toEpochMilli()) / 60_000L).toInt(),
                    )
                }
        }.getOrNull()
    }

    data class ExternalWeight(
        val kilograms: Double,
        val atMillis: Long,
        /** Which app wrote it, so the user can see where a weight came from. */
        val source: String,
    )

    /**
     * Weight records written by anything (spec 3.3).
     *
     * The spec says read as well as write, so a scale that publishes to Health
     * Connect is picked up rather than ignored. Records this app wrote itself
     * are excluded: re-importing our own writes would double-count them.
     */
    suspend fun readWeights(sinceDays: Long = 365): List<ExternalWeight> {
        val client = HealthConnect.client(context) ?: return emptyList()
        val ourPackage = context.packageName
        val window = TimeRangeFilter.between(
            Instant.now().minusSeconds(sinceDays * 24 * 60 * 60),
            Instant.now(),
        )
        return runCatching {
            client.readRecords(
                ReadRecordsRequest(WeightRecord::class, timeRangeFilter = window)
            ).records
                .filter { it.metadata.dataOrigin.packageName != ourPackage }
                .map {
                    ExternalWeight(
                        kilograms = it.weight.inKilograms,
                        atMillis = it.time.toEpochMilli(),
                        source = it.metadata.dataOrigin.packageName,
                    )
                }
        }.getOrDefault(emptyList())
    }

    /** Hydration written by other apps for a logical day, in millilitres. */
    suspend fun readHydrationMl(from: Long, to: Long): Int {
        val client = HealthConnect.client(context) ?: return 0
        val ourPackage = context.packageName
        return runCatching {
            client.readRecords(
                ReadRecordsRequest(
                    HydrationRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(
                        Instant.ofEpochMilli(from),
                        Instant.ofEpochMilli(to),
                    ),
                )
            ).records
                .filter { it.metadata.dataOrigin.packageName != ourPackage }
                .sumOf { it.volume.inMilliliters }
                .toInt()
        }.getOrDefault(0)
    }

    /**
     * A diagnostic sweep used to answer the open questions in START-HERE:
     * whether the watch writes awake blocks, and how recent the heart rate is.
     */
    data class Survey(
        val sessions: Int,
        val sessionsWithStages: Int,
        val stageTypesSeen: Set<String>,
        val awakeBlockCount: Int,
        val mostRecentHeartRate: Long?,
        val heartRateSamples: Int,
        val spo2Samples: Int,
        /** Which apps are writing, now that more than one can be. */
        val sleepSources: Set<String>,
        val heartRateSources: Set<String>,
        /** Which apps write weights here, so a missing reading can be traced. */
        val weightRecords: Int,
        val weightSources: Set<String>,
    )

    suspend fun survey(days: Long = 7): Survey? {
        val client = HealthConnect.client(context) ?: return null
        val now = Instant.now()
        val from = now.minusSeconds(days * 24 * 60 * 60)
        val window = TimeRangeFilter.between(from, now)

        return runCatching {
            val sessions = client.readRecords(
                ReadRecordsRequest(SleepSessionRecord::class, timeRangeFilter = window)
            ).records

            val allStages = sessions.flatMap { it.stages }
            val heart = client.readRecords(
                ReadRecordsRequest(
                    HeartRateRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(
                        from.minusMillis(RECORD_MARGIN_MILLIS),
                        now.plusMillis(RECORD_MARGIN_MILLIS),
                    ),
                )
            ).records
            val oxygen = client.readRecords(
                ReadRecordsRequest(OxygenSaturationRecord::class, timeRangeFilter = window)
            ).records
            // A year, because a weight history is sparse by nature.
            val weights = client.readRecords(
                ReadRecordsRequest(
                    WeightRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(now.minusSeconds(365 * 86400), now),
                )
            ).records

            Survey(
                sessions = sessions.size,
                sessionsWithStages = sessions.count { it.stages.isNotEmpty() },
                stageTypesSeen = allStages.map { it.stage.toStageName() }.toSet(),
                awakeBlockCount = allStages.count { it.stage.toStageName() == StageBlock.AWAKE },
                mostRecentHeartRate = heart.flatMap { it.samples }
                    .maxOfOrNull { it.time.toEpochMilli() },
                heartRateSamples = heart.sumOf { it.samples.size },
                spo2Samples = oxygen.size,
                sleepSources = sessions.map { it.metadata.dataOrigin.packageName }.toSet(),
                heartRateSources = heart.map { it.metadata.dataOrigin.packageName }.toSet(),
                weightRecords = weights.size,
                weightSources = weights.map { it.metadata.dataOrigin.packageName }.toSet(),
            )
        }.getOrNull()
    }

    private companion object {
        const val DAY_MILLIS = 24 * 60 * 60 * 1000L

        /**
         * How far either side of the night to look for records. A writer may
         * pack a whole day into one record, and it has to be fully inside the
         * query window to come back at all.
         */
        const val RECORD_MARGIN_MILLIS = 36 * 60 * 60 * 1000L
        const val THIRTY_MINUTES_MILLIS = 30 * 60 * 1000L

        /**
         * Below this many samples the 5th percentile has too little to work
         * with, so the 30-minute group minima are used instead.
         */
        const val MIN_SAMPLES_FOR_PERCENTILE = 40
    }
}

/** Health Connect's integer stage types, mapped to the names in `stage_block`. */
private fun Int.toStageName(): String = when (this) {
    SleepSessionRecord.STAGE_TYPE_DEEP -> StageBlock.DEEP
    SleepSessionRecord.STAGE_TYPE_REM -> StageBlock.REM
    SleepSessionRecord.STAGE_TYPE_AWAKE,
    SleepSessionRecord.STAGE_TYPE_AWAKE_IN_BED,
    SleepSessionRecord.STAGE_TYPE_OUT_OF_BED -> StageBlock.AWAKE
    // LIGHT, SLEEPING and UNKNOWN all end up here: the watch's "light" is the
    // catch-all it writes for anything it did not classify as deep or REM.
    else -> StageBlock.LIGHT
}
