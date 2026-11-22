package com.devekene.kalenda.data.sync

import android.Manifest
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.CalendarContract
import androidx.core.content.ContextCompat
import com.devekene.kalenda.domain.model.CalendarEvent
import com.devekene.kalenda.domain.model.EventCategory
import com.devekene.kalenda.domain.model.SyncStatus
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID

data class GoogleSyncReport(
    val calendarIdFound: Long?,
    val pushedCount: Int,
    val pulledEvents: List<CalendarEvent>,
    val updatedMappings: Map<String, Long>,
    val statusNote: String
)

/**
 * Syncs Kalenda events with the Google Calendar account registered on the device via Android's
 * CalendarContract Provider matched by the user's added Google email address, and generates RFC 5545
 * .ics exports / Google Calendar insertion intents as cross-device fallbacks.
 */
class GoogleCalendarSyncManager(private val context: Context) {

    fun hasCalendarPermissions(): Boolean {
        val read = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_CALENDAR
        ) == PackageManager.PERMISSION_GRANTED
        val write = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.WRITE_CALENDAR
        ) == PackageManager.PERMISSION_GRANTED
        return read && write
    }

    fun findCalendarIdForGoogleEmail(email: String): Long? {
        if (!hasCalendarPermissions() || email.isBlank()) return null
        val target = email.trim().lowercase(Locale.US)
        val projection = arrayOf(
            CalendarContract.Calendars._ID,
            CalendarContract.Calendars.ACCOUNT_NAME,
            CalendarContract.Calendars.ACCOUNT_TYPE,
            CalendarContract.Calendars.OWNER_ACCOUNT,
            CalendarContract.Calendars.IS_PRIMARY
        )
        var bestId: Long? = null
        var bestScore = -1

        runCatching {
            context.contentResolver.query(
                CalendarContract.Calendars.CONTENT_URI,
                projection,
                null,
                null,
                null
            )?.use { cursor ->
                val idIdx = cursor.getColumnIndex(CalendarContract.Calendars._ID)
                val accNameIdx = cursor.getColumnIndex(CalendarContract.Calendars.ACCOUNT_NAME)
                val accTypeIdx = cursor.getColumnIndex(CalendarContract.Calendars.ACCOUNT_TYPE)
                val ownerIdx = cursor.getColumnIndex(CalendarContract.Calendars.OWNER_ACCOUNT)
                val primaryIdx = cursor.getColumnIndex(CalendarContract.Calendars.IS_PRIMARY)

                while (cursor.moveToNext()) {
                    val id = if (idIdx >= 0) cursor.getLong(idIdx) else continue
                    val accName = if (accNameIdx >= 0) cursor.getString(accNameIdx).orEmpty().lowercase(Locale.US) else ""
                    val accType = if (accTypeIdx >= 0) cursor.getString(accTypeIdx).orEmpty() else ""
                    val owner = if (ownerIdx >= 0) cursor.getString(ownerIdx).orEmpty().lowercase(Locale.US) else ""
                    val isPrimary = if (primaryIdx >= 0) cursor.getInt(primaryIdx) == 1 else false

                    if (accName == target || owner == target) {
                        val score = (if (accType == "com.google") 10 else 2) + (if (isPrimary) 5 else 0)
                        if (score > bestScore) {
                            bestScore = score
                            bestId = id
                        }
                    }
                }
            }
        }
        return bestId
    }

    fun syncWithDeviceGoogleCalendar(
        googleEmail: String,
        localEvents: List<CalendarEvent>
    ): GoogleSyncReport {
        val cleanEmail = googleEmail.trim()
        if (cleanEmail.isBlank()) {
            return GoogleSyncReport(
                calendarIdFound = null,
                pushedCount = 0,
                pulledEvents = emptyList(),
                updatedMappings = emptyMap(),
                statusNote = "Add your Google email address first."
            )
        }
        if (!hasCalendarPermissions()) {
            return GoogleSyncReport(
                calendarIdFound = null,
                pushedCount = 0,
                pulledEvents = emptyList(),
                updatedMappings = emptyMap(),
                statusNote = "Grant Calendar permissions to sync with $cleanEmail."
            )
        }

        val calendarId = findCalendarIdForGoogleEmail(cleanEmail)
        if (calendarId == null) {
            return GoogleSyncReport(
                calendarIdFound = null,
                pushedCount = 0,
                pulledEvents = emptyList(),
                updatedMappings = emptyMap(),
                statusNote = "Saved $cleanEmail. Sign in with this Google account in Android Settings > Accounts for automatic device sync, or export .ics below."
            )
        }

        val tzId = TimeZone.getDefault().id
        val newMappings = mutableMapOf<String, Long>()
        var pushed = 0

        localEvents.forEach { ev ->
            val values = ContentValues().apply {
                put(CalendarContract.Events.CALENDAR_ID, calendarId)
                put(CalendarContract.Events.TITLE, if (ev.isBiometricLocked) "Kalenda Protected Event" else ev.title)
                put(CalendarContract.Events.DESCRIPTION, if (ev.isBiometricLocked) "" else ev.description)
                put(CalendarContract.Events.EVENT_LOCATION, if (ev.isBiometricLocked) "" else ev.location)
                put(CalendarContract.Events.DTSTART, ev.startTimeMillis)
                put(CalendarContract.Events.DTEND, ev.endTimeMillis)
                put(CalendarContract.Events.ALL_DAY, if (ev.isAllDay) 1 else 0)
                put(CalendarContract.Events.EVENT_TIMEZONE, tzId)
            }

            if (ev.googleCalendarEventId != null) {
                val updateUri = ContentUris.withAppendedId(
                    CalendarContract.Events.CONTENT_URI,
                    ev.googleCalendarEventId
                )
                val rows = runCatching {
                    context.contentResolver.update(updateUri, values, null, null)
                }.getOrDefault(0)
                if (rows > 0) {
                    newMappings[ev.id] = ev.googleCalendarEventId
                    pushed++
                }
            } else {
                val insertedUri = runCatching {
                    context.contentResolver.insert(CalendarContract.Events.CONTENT_URI, values)
                }.getOrNull()
                val remoteId = insertedUri?.lastPathSegment?.toLongOrNull()
                if (remoteId != null) {
                    newMappings[ev.id] = remoteId
                    pushed++
                    if (ev.reminderMinutesBefore >= 0) {
                        val remValues = ContentValues().apply {
                            put(CalendarContract.Reminders.EVENT_ID, remoteId)
                            put(CalendarContract.Reminders.MINUTES, ev.reminderMinutesBefore)
                            put(CalendarContract.Reminders.METHOD, CalendarContract.Reminders.METHOD_ALERT)
                        }
                        runCatching {
                            context.contentResolver.insert(CalendarContract.Reminders.CONTENT_URI, remValues)
                        }
                    }
                }
            }
        }

        // Pull events from Google Calendar that aren't already tracked in Kalenda
        val knownRemoteIds = (localEvents.mapNotNull { it.googleCalendarEventId } + newMappings.values).toSet()
        val pulled = mutableListOf<CalendarEvent>()
        val windowStart = System.currentTimeMillis() - 30L * 24 * 60 * 60_000L
        val windowEnd = System.currentTimeMillis() + 180L * 24 * 60 * 60_000L

        val eventProjection = arrayOf(
            CalendarContract.Events._ID,
            CalendarContract.Events.TITLE,
            CalendarContract.Events.DESCRIPTION,
            CalendarContract.Events.EVENT_LOCATION,
            CalendarContract.Events.DTSTART,
            CalendarContract.Events.DTEND,
            CalendarContract.Events.ALL_DAY
        )
        runCatching {
            context.contentResolver.query(
                CalendarContract.Events.CONTENT_URI,
                eventProjection,
                "${CalendarContract.Events.CALENDAR_ID} = ? AND ${CalendarContract.Events.DTSTART} >= ? AND ${CalendarContract.Events.DTSTART} <= ?",
                arrayOf(calendarId.toString(), windowStart.toString(), windowEnd.toString()),
                "${CalendarContract.Events.DTSTART} ASC"
            )?.use { c ->
                val idI = c.getColumnIndex(CalendarContract.Events._ID)
                val titleI = c.getColumnIndex(CalendarContract.Events.TITLE)
                val descI = c.getColumnIndex(CalendarContract.Events.DESCRIPTION)
                val locI = c.getColumnIndex(CalendarContract.Events.EVENT_LOCATION)
                val startI = c.getColumnIndex(CalendarContract.Events.DTSTART)
                val endI = c.getColumnIndex(CalendarContract.Events.DTEND)
                val allDayI = c.getColumnIndex(CalendarContract.Events.ALL_DAY)

                while (c.moveToNext()) {
                    val remoteId = if (idI >= 0) c.getLong(idI) else continue
                    if (remoteId in knownRemoteIds) continue
                    val title = if (titleI >= 0) c.getString(titleI).orEmpty() else ""
                    if (title.isBlank()) continue
                    val startMs = if (startI >= 0) c.getLong(startI) else continue
                    val endMs = if (endI >= 0) c.getLong(endI).coerceAtLeast(startMs + 1800_000L) else startMs + 3600_000L
                    pulled += CalendarEvent(
                        id = UUID.randomUUID().toString(),
                        title = title,
                        description = if (descI >= 0) c.getString(descI).orEmpty() else "",
                        location = if (locI >= 0) c.getString(locI).orEmpty() else "",
                        category = EventCategory.PERSONAL,
                        startTimeMillis = startMs,
                        endTimeMillis = endMs,
                        isAllDay = allDayI >= 0 && c.getInt(allDayI) == 1,
                        reminderMinutesBefore = 15,
                        syncStatus = SyncStatus.SYNCED,
                        googleCalendarEventId = remoteId
                    )
                }
            }
        }

        return GoogleSyncReport(
            calendarIdFound = calendarId,
            pushedCount = pushed,
            pulledEvents = pulled,
            updatedMappings = newMappings,
            statusNote = "Synced $pushed pushed, ${pulled.size} imported with $cleanEmail"
        )
    }

    fun buildIcsExport(events: List<CalendarEvent>, googleEmail: String): String {
        val utcFmt = SimpleDateFormat("yyyyMMdd'T'HHmmss'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        return buildString {
            appendLine("BEGIN:VCALENDAR")
            appendLine("VERSION:2.0")
            appendLine("PRODID:-//Devekene//Kalenda//EN")
            if (googleEmail.isNotBlank()) {
                appendLine("X-WR-CALNAME:Kalenda ($googleEmail)")
            }
            events.forEach { ev ->
                appendLine("BEGIN:VEVENT")
                appendLine("UID:${ev.id}@kalenda.devekene.com")
                appendLine("DTSTAMP:${utcFmt.format(Date(ev.updatedAtMillis))}")
                appendLine("DTSTART:${utcFmt.format(Date(ev.startTimeMillis))}")
                appendLine("DTEND:${utcFmt.format(Date(ev.endTimeMillis))}")
                appendLine("SUMMARY:${escapeIcs(if (ev.isBiometricLocked) "Locked Event" else ev.title)}")
                if (!ev.isBiometricLocked && ev.description.isNotBlank()) {
                    appendLine("DESCRIPTION:${escapeIcs(ev.description)}")
                }
                if (!ev.isBiometricLocked && ev.location.isNotBlank()) {
                    appendLine("LOCATION:${escapeIcs(ev.location)}")
                }
                appendLine("END:VEVENT")
            }
            appendLine("END:VCALENDAR")
        }
    }

    fun buildInsertInGoogleCalendarIntent(event: CalendarEvent, googleEmail: String): Intent {
        return Intent(Intent.ACTION_INSERT).apply {
            data = CalendarContract.Events.CONTENT_URI
            putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, event.startTimeMillis)
            putExtra(CalendarContract.EXTRA_EVENT_END_TIME, event.endTimeMillis)
            putExtra(CalendarContract.EXTRA_EVENT_ALL_DAY, event.isAllDay)
            putExtra(CalendarContract.Events.TITLE, if (event.isBiometricLocked) "Kalenda Event" else event.title)
            if (!event.isBiometricLocked) {
                putExtra(CalendarContract.Events.DESCRIPTION, event.description)
                putExtra(CalendarContract.Events.EVENT_LOCATION, event.location)
            }
            if (googleEmail.isNotBlank()) {
                putExtra(Intent.EXTRA_EMAIL, googleEmail)
            }
        }
    }

    private fun escapeIcs(raw: String): String = raw
        .replace("\\", "\\\\")
        .replace(",", "\\,")
        .replace(";", "\\;")
        .replace("\n", "\\n")
}
