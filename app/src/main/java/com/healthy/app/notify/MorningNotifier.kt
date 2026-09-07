package com.healthy.app.notify

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.healthy.app.MainActivity
import com.healthy.app.R

/**
 * The morning notification (spec 14.3).
 *
 * The trigger is the end of sleep, not the clock: this user wakes at a
 * different time every day, and one night ended at 13:49. A notification at
 * 08:00 arrives during sleep, gets dismissed, and is never seen again.
 */
object MorningNotifier {

    const val CHANNEL_ID = "morning"
    const val NOTIFICATION_ID = 1
    const val EXTRA_DATE = "com.healthy.app.NIGHT_DATE"

    fun ensureChannel(context: Context) {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Log last night",
            // IMPORTANCE_LOW puts it in the shade with no sound and no
            // vibration (spec 14.3). This is a reminder, not an alarm.
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "A reminder to rate the night, sent when your watch reports you woke."
            setShowBadge(false)
            enableVibration(false)
        }
        context.getSystemService(NotificationManager::class.java)
            ?.createNotificationChannel(channel)
    }

    fun notifyNight(context: Context, date: String, minutes: Int) {
        ensureChannel(context)

        // One tap opens the morning screen at the right date, never the Today
        // screen (spec 14.3).
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_DATE, date)
        }
        val pending = PendingIntent.getActivity(
            context,
            date.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Log last night")
            .setContentText("${minutes / 60} h ${minutes % 60} m recorded")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(pending)
            .setAutoCancel(true)
            // Dismissible: an ongoing notification the user cannot clear is a
            // nuisance, and the job will not send this one twice anyway.
            .setOngoing(false)
            .build()

        runCatching {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
        }
    }

    fun clear(context: Context) {
        NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
    }
}
