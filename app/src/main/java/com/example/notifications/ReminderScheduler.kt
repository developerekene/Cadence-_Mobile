package com.example.notifications

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.AlarmManagerCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R
import com.example.domain.model.CalendarEvent
import com.example.domain.model.ReminderPresets
import com.example.domain.util.DateTimeUtils

class ReminderScheduler(private val context: Context) {

    private val alarmManager: AlarmManager?
        get() = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager

    fun ensureNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = context.getString(R.string.notification_channel_desc)
                enableVibration(true)
            }
            val manager = context.getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    fun hasNotificationPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    fun canScheduleExactAlarms(): Boolean {
        val am = alarmManager ?: return false
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            am.canScheduleExactAlarms()
        } else {
            true
        }
    }

    fun scheduleReminder(event: CalendarEvent) {
        cancelReminder(event.id)
        if (event.reminderMinutesBefore < 0) return

        val triggerAtMillis = event.startTimeMillis - event.reminderMinutesBefore * 60_000L
        if (triggerAtMillis <= System.currentTimeMillis()) return

        val am = alarmManager ?: return
        val pendingIntent = buildAlarmPendingIntent(event.id)

        if (canScheduleExactAlarms()) {
            AlarmManagerCompat.setExactAndAllowWhileIdle(
                am,
                AlarmManager.RTC_WAKEUP,
                triggerAtMillis,
                pendingIntent
            )
        } else {
            AlarmManagerCompat.setAndAllowWhileIdle(
                am,
                AlarmManager.RTC_WAKEUP,
                triggerAtMillis,
                pendingIntent
            )
        }
    }

    fun cancelReminder(eventId: String) {
        val am = alarmManager ?: return
        am.cancel(buildAlarmPendingIntent(eventId))
    }

    fun showEventNotification(event: CalendarEvent) {
        ensureNotificationChannel()
        if (!hasNotificationPermission()) return

        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_EVENT_ID, event.id)
        }
        val contentPendingIntent = PendingIntent.getActivity(
            context,
            event.id.hashCode(),
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val timeText = DateTimeUtils.formatTimeRange(
            event.startTimeMillis,
            event.endTimeMillis,
            event.isAllDay
        )
        val leadLabel = ReminderPresets.formatLabel(event.reminderMinutesBefore)
        val body = buildString {
            append(timeText)
            if (event.location.isNotBlank()) {
                append(" • ")
                append(event.location)
            }
            if (event.reminderMinutesBefore >= 0) {
                append(" (")
                append(leadLabel)
                append(")")
            }
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("${event.category.displayName}: ${event.title}")
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(
                if (event.description.isNotBlank()) "$body\n${event.description}" else body
            ))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_EVENT)
            .setAutoCancel(true)
            .setContentIntent(contentPendingIntent)
            .build()

        runCatching {
            NotificationManagerCompat.from(context).notify(event.id.hashCode(), notification)
        }
    }

    fun sendTestNotification(): Boolean {
        ensureNotificationChannel()
        if (!hasNotificationPermission()) return false

        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            TEST_NOTIFICATION_ID,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("Cadence Reminder Active")
            .setContentText("Push notifications are configured and ready for upcoming events.")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        return runCatching {
            NotificationManagerCompat.from(context).notify(TEST_NOTIFICATION_ID, notification)
            true
        }.getOrDefault(false)
    }

    private fun buildAlarmPendingIntent(eventId: String): PendingIntent {
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            action = ACTION_EVENT_REMINDER
            putExtra(EXTRA_EVENT_ID, eventId)
        }
        return PendingIntent.getBroadcast(
            context,
            eventId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    companion object {
        const val CHANNEL_ID = "cadence_reminders"
        const val ACTION_EVENT_REMINDER = "com.example.ACTION_EVENT_REMINDER"
        const val EXTRA_EVENT_ID = "extra_event_id"
        private const val TEST_NOTIFICATION_ID = 9001
    }
}
