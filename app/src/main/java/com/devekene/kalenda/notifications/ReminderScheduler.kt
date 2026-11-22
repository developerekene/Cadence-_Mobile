package com.devekene.kalenda.notifications

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
import com.devekene.kalenda.MainActivity
import com.devekene.kalenda.R
import com.devekene.kalenda.domain.model.CalendarEvent
import com.devekene.kalenda.domain.model.EventLifecyclePhase
import com.devekene.kalenda.domain.model.ReminderPresets
import com.devekene.kalenda.domain.util.DateTimeUtils

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

    fun scheduleEventLifecycleAlarms(
        event: CalendarEvent,
        notifyUpcoming: Boolean = true,
        notifyCurrent: Boolean = true,
        notifyPast: Boolean = true
    ) {
        cancelReminder(event.id)
        val now = System.currentTimeMillis()
        val am = alarmManager ?: return

        // 1. Upcoming alert (before event starts)
        if (notifyUpcoming && event.reminderMinutesBefore >= 0) {
            val triggerUpcoming = event.startTimeMillis - event.reminderMinutesBefore * 60_000L
            if (triggerUpcoming > now) {
                scheduleSingleAlarm(am, event.id, EventLifecyclePhase.UPCOMING, triggerUpcoming)
            }
        }

        // 2. Current alert (when event begins / becomes current)
        if (notifyCurrent && event.startTimeMillis > now) {
            scheduleSingleAlarm(am, event.id, EventLifecyclePhase.CURRENT, event.startTimeMillis)
        }

        // 3. Past alert (when event finishes and becomes past)
        if (notifyPast && event.endTimeMillis > now) {
            scheduleSingleAlarm(am, event.id, EventLifecyclePhase.PAST, event.endTimeMillis)
        }
    }

    private fun scheduleSingleAlarm(
        am: AlarmManager,
        eventId: String,
        phase: EventLifecyclePhase,
        triggerAtMillis: Long
    ) {
        val pendingIntent = buildAlarmPendingIntent(eventId, phase)
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
        EventLifecyclePhase.entries.forEach { phase ->
            am.cancel(buildAlarmPendingIntent(eventId, phase))
        }
    }

    fun showEventLifecycleNotification(
        event: CalendarEvent,
        phase: EventLifecyclePhase
    ) {
        ensureNotificationChannel()
        if (!hasNotificationPermission()) return

        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_EVENT_ID, event.id)
        }
        val requestCode = event.id.hashCode() * 31 + phase.ordinal
        val contentPendingIntent = PendingIntent.getActivity(
            context,
            requestCode,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val safeTitle = if (event.isBiometricLocked) "Locked ${event.category.displayName} Event" else event.title
        val headline = when (phase) {
            EventLifecyclePhase.UPCOMING -> "Upcoming • $safeTitle"
            EventLifecyclePhase.CURRENT -> "Happening Now • $safeTitle"
            EventLifecyclePhase.PAST -> "Completed (Past) • $safeTitle"
        }

        val timeText = DateTimeUtils.formatTimeRange(
            event.startTimeMillis,
            event.endTimeMillis,
            event.isAllDay
        )
        val body = if (event.isBiometricLocked) {
            "$timeText • Unlock with biometrics or PIN to view details"
        } else {
            buildString {
                append(timeText)
                if (event.location.isNotBlank()) {
                    append(" • ")
                    append(event.location)
                }
                if (phase == EventLifecyclePhase.UPCOMING && event.reminderMinutesBefore >= 0) {
                    append(" (")
                    append(ReminderPresets.formatLabel(event.reminderMinutesBefore))
                    append(")")
                } else if (phase == EventLifecyclePhase.PAST) {
                    append(" • Moved to past history")
                }
            }
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(headline)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_EVENT)
            .setAutoCancel(true)
            .setContentIntent(contentPendingIntent)
            .build()

        runCatching {
            NotificationManagerCompat.from(context).notify(requestCode, notification)
        }
    }

    /**
     * Evaluates all stored events right now and pushes summary alerts for Upcoming, Current, and Past events.
     */
    fun notifyLifecycleSummary(
        events: List<CalendarEvent>,
        notifyUpcoming: Boolean,
        notifyCurrent: Boolean,
        notifyPast: Boolean
    ): Boolean {
        ensureNotificationChannel()
        if (!hasNotificationPermission()) return false

        val now = System.currentTimeMillis()
        val upcoming = events.filter { it.startTimeMillis > now && it.startTimeMillis - now <= 24L * 3600_000L }
        val current = events.filter { now in it.startTimeMillis..it.endTimeMillis }
        val past = events.filter { it.endTimeMillis < now && now - it.endTimeMillis <= 7L * 24L * 3600_000L }

        var posted = false
        if (notifyCurrent && current.isNotEmpty()) {
            postDigest(
                id = DIGEST_CURRENT_ID,
                title = "Current Events (${current.size} live)",
                lines = current.take(3).map { formatDigestLine(it) }
            )
            posted = true
        }
        if (notifyUpcoming && upcoming.isNotEmpty()) {
            postDigest(
                id = DIGEST_UPCOMING_ID,
                title = "Upcoming in 24h (${upcoming.size})",
                lines = upcoming.take(3).map { formatDigestLine(it) }
            )
            posted = true
        }
        if (notifyPast && past.isNotEmpty()) {
            postDigest(
                id = DIGEST_PAST_ID,
                title = "Recent Past Events (${past.size})",
                lines = past.take(3).map { formatDigestLine(it) }
            )
            posted = true
        }

        if (!posted) {
            postDigest(
                id = DIGEST_STATUS_ID,
                title = "Kalenda Notifications Active",
                lines = listOf("You will be alerted when events are upcoming, current, and past.")
            )
        }
        return true
    }

    private fun formatDigestLine(event: CalendarEvent): String {
        val label = if (event.isBiometricLocked) "Locked ${event.category.displayName} event" else event.title
        val time = DateTimeUtils.formatTimeRange(event.startTimeMillis, event.endTimeMillis, event.isAllDay)
        return "$label ($time)"
    }

    private fun postDigest(id: Int, title: String, lines: List<String>) {
        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            id,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val body = lines.joinToString("\n")
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(title)
            .setContentText(lines.firstOrNull().orEmpty())
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()
        runCatching {
            NotificationManagerCompat.from(context).notify(id, notification)
        }
    }

    private fun buildAlarmPendingIntent(eventId: String, phase: EventLifecyclePhase): PendingIntent {
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            action = ACTION_EVENT_REMINDER
            putExtra(EXTRA_EVENT_ID, eventId)
            putExtra(EXTRA_PHASE, phase.name)
        }
        val requestCode = eventId.hashCode() * 31 + phase.ordinal
        return PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    companion object {
        const val CHANNEL_ID = "kalenda_reminders"
        const val ACTION_EVENT_REMINDER = "com.devekene.kalenda.ACTION_EVENT_REMINDER"
        const val EXTRA_EVENT_ID = "extra_event_id"
        const val EXTRA_PHASE = "extra_event_phase"
        private const val DIGEST_CURRENT_ID = 9001
        private const val DIGEST_UPCOMING_ID = 9002
        private const val DIGEST_PAST_ID = 9003
        private const val DIGEST_STATUS_ID = 9004
    }
}
