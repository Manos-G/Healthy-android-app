package com.healthy.app.health

import android.content.Context
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.OxygenSaturationRecord
import androidx.health.connect.client.records.SleepSessionRecord
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

            val session = forThisNight
                // The longest, in case a nap and the main sleep share a day,
                // or two apps both wrote the night.
                .maxByOrNull { it.endTime.toEpochMilli() - it.startTime.toEpochMilli() }
                ?: return@runCatching Result.NoSession

            val start = session.startTime.toEpochMilli()
            val end = session.endTime.toEpochMilli()

            val blocks = session.stages.map { stage ->
                StageBlock(
                    nightDate = date,
                    type = stage.stage.toStageName(),
                    startTime = stage.startTime.toEpochMilli(),
                    endTime = stage.endTime.toEpochMilli(),
                )
            }

            val window = TimeRangeFilter.between(
                Instant.ofEpochMilli(start),
                Instant.ofEpochMilli(end),
            )

            val heartRecords = client.readRecords(
                ReadRecordsRequest(HeartRateRecord::class, timeRangeFilter = window)
            ).records

            // Each record is a 30-minute group; the spec says use the minimum
            // of each group, so a within-group spike cannot raise the result.
            val groupMinima = heartRecords.mapNotNull { record ->
                record.samples.minOfOrNull { it.beatsPerMinute.toInt() }
            }

            val oxygen = client.readRecords(
                ReadRecordsRequest(OxygenSaturationRecord::class, timeRangeFilter = window)
            ).records.map { it.percentage.value }

            Result.Found(
                NightData(
                    sleepStart = start,
                    sleepEnd = end,
                    minutes = ((end - start) / 60_000L).toInt(),
                    stageBlocks = blocks,
                    totals = SleepAnalysis.stageTotals(blocks),
                    restingHr = SleepAnalysis.restingHeartRate(groupMinima),
                    spo2 = SleepAnalysis.meanSpo2(oxygen),
                    heartRateSampleCount = heartRecords.sumOf { it.samples.size },
                    source = session.metadata.dataOrigin.packageName,
                    competingSessions = forThisNight.size - 1,
                    heartRateSamples = heartRecords.flatMap { record ->
                        record.samples.map {
                            Hypnogram.Sample(it.time.toEpochMilli(), it.beatsPerMinute.toInt())
                        }
                    }.sortedBy { it.timeMillis },
                )
            )
        }.getOrElse { Result.Failed(it.message ?: it::class.simpleName ?: "unknown error") }
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
                ReadRecordsRequest(HeartRateRecord::class, timeRangeFilter = window)
            ).records
            val oxygen = client.readRecords(
                ReadRecordsRequest(OxygenSaturationRecord::class, timeRangeFilter = window)
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
            )
        }.getOrNull()
    }

    private companion object {
        const val DAY_MILLIS = 24 * 60 * 60 * 1000L
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
