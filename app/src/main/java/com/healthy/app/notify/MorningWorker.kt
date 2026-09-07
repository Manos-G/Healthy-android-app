package com.healthy.app.notify

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.healthy.app.core.HealthyDay
import com.healthy.app.data.HealthyDatabase
import com.healthy.app.data.SettingsStore
import com.healthy.app.health.HealthConnect
import com.healthy.app.health.HealthReader
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

/**
 * Watches for the end of a sleep session (spec 14.2).
 *
 * Runs every 30 minutes and asks a simple question: has the watch recorded a
 * night that this app has not, and has it not already said so? A fixed daily
 * alarm would be wrong for a user who wakes at 07:00 one day and 13:49 the
 * next.
 */
class MorningWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val context = applicationContext
        val settings = SettingsStore(context)

        // Record every run, successful or not: a status line many hours old is
        // how the user finds out the system stopped the job (spec 14.4).
        settings.setLastJobRun(System.currentTimeMillis())

        if (!HealthConnect.hasAllReadPermissions(context)) {
            android.util.Log.i(TAG, "no read permissions; nothing to do")
            return Result.success()
        }

        val night = HealthReader(context).mostRecentSession()
        if (night == null) {
            android.util.Log.i(TAG, "no sleep session in the last 3 days")
            return Result.success()
        }
        val stored = settings.settings.first()
        android.util.Log.i(
            TAG,
            "session ${night.date} ends ${night.endMillis}, " +
                "lastNotified ${stored.lastNotifiedSleepEnd}",
        )

        // Already told them about this one.
        if (stored.lastNotifiedSleepEnd == night.endMillis) {
            android.util.Log.i(TAG, "already notified for this session")
            return Result.success()
        }

        // Already logged, so there is nothing to ask for.
        if (HealthyDatabase.get(context).nightDao().exists(night.date)) {
            android.util.Log.i(TAG, "night ${night.date} already saved")
            settings.setLastNotifiedSleepEnd(night.endMillis)
            return Result.success()
        }

        android.util.Log.i(TAG, "notifying for ${night.date}")
        MorningNotifier.notifyNight(context, night.date, night.minutes)
        settings.setLastNotifiedSleepEnd(night.endMillis)
        return Result.success()
    }

    companion object {
        private const val TAG = "HealthyWorker"
        private const val NAME = "morning-notification"

        fun enable(context: Context) {
            val request = PeriodicWorkRequestBuilder<MorningWorker>(30, TimeUnit.MINUTES).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                NAME,
                // KEEP, so toggling the setting does not reset the interval and
                // push the next run half an hour away each time.
                ExistingPeriodicWorkPolicy.KEEP,
                request,
            )
        }

        /**
         * Runs the same check immediately, through the same worker.
         *
         * The periodic job cannot be triggered on demand, so without this
         * there is no way to find out whether the reminder works except to
         * sleep and see. It also gives the user a way to prove the thing is
         * alive rather than trusting a timestamp.
         */
        fun runOnce(context: Context) {
            WorkManager.getInstance(context).enqueueUniqueWork(
                "$NAME-once",
                androidx.work.ExistingWorkPolicy.REPLACE,
                androidx.work.OneTimeWorkRequestBuilder<MorningWorker>().build(),
            )
        }

        fun disable(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(NAME)
        }
    }
}

/** The date the notification should open, if the app was launched by one. */
fun nightDateFromIntent(intent: android.content.Intent?): String? =
    intent?.getStringExtra(MorningNotifier.EXTRA_DATE)
        ?.takeIf { runCatching { java.time.LocalDate.parse(it) }.isSuccess }
        ?: run { HealthyDay.today(); null }
