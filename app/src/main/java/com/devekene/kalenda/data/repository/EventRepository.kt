package com.devekene.kalenda.data.repository

import com.devekene.kalenda.data.local.EventDao
import com.devekene.kalenda.data.local.EventEntity
import com.devekene.kalenda.data.sync.GoogleCalendarSyncManager
import com.devekene.kalenda.data.sync.GoogleSyncReport
import com.devekene.kalenda.domain.model.CalendarEvent
import com.devekene.kalenda.domain.model.EventCategory
import com.devekene.kalenda.domain.model.RecurrenceRule
import com.devekene.kalenda.domain.model.SyncStatus
import com.devekene.kalenda.notifications.ReminderScheduler
import com.devekene.kalenda.security.CryptoManager
import java.util.Calendar
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

class EventRepository(
    private val eventDao: EventDao,
    private val cryptoManager: CryptoManager,
    private val reminderScheduler: ReminderScheduler,
    private val googleSyncManager: GoogleCalendarSyncManager? = null
) {
    val allEvents: Flow<List<CalendarEvent>> = eventDao.observeAllActiveEvents().map { list ->
        list.mapNotNull { entity -> runCatching { entity.toDomain() }.getOrNull() }
    }

    suspend fun getEventById(id: String): CalendarEvent? = withContext(Dispatchers.IO) {
        eventDao.getEventById(id)?.let { runCatching { it.toDomain() }.getOrNull() }
    }

    suspend fun saveEvent(
        event: CalendarEvent,
        notifyUpcoming: Boolean = true,
        notifyCurrent: Boolean = true,
        notifyPast: Boolean = true
    ): CalendarEvent = withContext(Dispatchers.IO) {
        val finalId = event.id.ifBlank { UUID.randomUUID().toString() }
        val normalized = event.copy(
            id = finalId,
            syncStatus = if (event.googleCalendarEventId != null) SyncStatus.SYNCED else SyncStatus.LOCAL_ONLY,
            updatedAtMillis = System.currentTimeMillis()
        )
        eventDao.upsertEvent(normalized.toEntity())
        reminderScheduler.scheduleEventLifecycleAlarms(
            normalized,
            notifyUpcoming = notifyUpcoming,
            notifyCurrent = notifyCurrent,
            notifyPast = notifyPast
        )
        normalized
    }

    suspend fun setBiometricLocked(eventId: String, locked: Boolean): CalendarEvent? =
        withContext(Dispatchers.IO) {
            val existing = getEventById(eventId) ?: return@withContext null
            val updated = existing.copy(
                isBiometricLocked = locked,
                updatedAtMillis = System.currentTimeMillis()
            )
            eventDao.upsertEvent(updated.toEntity())
            reminderScheduler.scheduleEventLifecycleAlarms(updated)
            updated
        }

    suspend fun rescheduleEvent(
        eventId: String,
        newStartMillis: Long,
        newEndMillis: Long
    ): CalendarEvent? = withContext(Dispatchers.IO) {
        val existing = getEventById(eventId) ?: return@withContext null
        val updated = existing.copy(
            startTimeMillis = newStartMillis,
            endTimeMillis = newEndMillis,
            updatedAtMillis = System.currentTimeMillis()
        )
        eventDao.upsertEvent(updated.toEntity())
        reminderScheduler.scheduleEventLifecycleAlarms(updated)
        updated
    }

    suspend fun deleteEvent(eventId: String) = withContext(Dispatchers.IO) {
        reminderScheduler.cancelReminder(eventId)
        eventDao.softDeleteEvent(
            id = eventId,
            syncStatus = SyncStatus.LOCAL_ONLY.name,
            updatedAt = System.currentTimeMillis()
        )
    }

    suspend fun rescheduleAllUpcomingReminders(
        notifyUpcoming: Boolean = true,
        notifyCurrent: Boolean = true,
        notifyPast: Boolean = true
    ) = rescheduleAllLifecycleAlarms(notifyUpcoming, notifyCurrent, notifyPast)

    suspend fun rescheduleAllLifecycleAlarms(
        notifyUpcoming: Boolean = true,
        notifyCurrent: Boolean = true,
        notifyPast: Boolean = true
    ) = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val entities = eventDao.getAllActiveEventsSync()
        entities.forEach { entity ->
            runCatching { entity.toDomain() }.getOrNull()?.let { event ->
                if (event.endTimeMillis > now) {
                    reminderScheduler.scheduleEventLifecycleAlarms(
                        event,
                        notifyUpcoming = notifyUpcoming,
                        notifyCurrent = notifyCurrent,
                        notifyPast = notifyPast
                    )
                }
            }
        }
    }

    suspend fun syncWithGoogleCalendar(googleEmail: String): GoogleSyncReport =
        withContext(Dispatchers.IO) {
            val manager = googleSyncManager ?: return@withContext GoogleSyncReport(
                calendarIdFound = null,
                pushedCount = 0,
                pulledEvents = emptyList(),
                updatedMappings = emptyMap(),
                statusNote = "Google sync manager unavailable"
            )
            val currentEvents = eventDao.getAllActiveEventsSync().mapNotNull {
                runCatching { it.toDomain() }.getOrNull()
            }
            val report = manager.syncWithDeviceGoogleCalendar(googleEmail, currentEvents)

            if (report.updatedMappings.isNotEmpty()) {
                val updatedList = currentEvents.mapNotNull { ev ->
                    val remoteId = report.updatedMappings[ev.id] ?: return@mapNotNull null
                    ev.copy(
                        googleCalendarEventId = remoteId,
                        syncStatus = SyncStatus.SYNCED,
                        updatedAtMillis = System.currentTimeMillis()
                    ).toEntity()
                }
                if (updatedList.isNotEmpty()) {
                    eventDao.upsertAll(updatedList)
                }
            }

            if (report.pulledEvents.isNotEmpty()) {
                val toInsert = report.pulledEvents.filter { pulled ->
                    val rid = pulled.googleCalendarEventId
                    rid == null || eventDao.getEventByGoogleId(rid) == null
                }
                if (toInsert.isNotEmpty()) {
                    eventDao.upsertAll(toInsert.map { it.toEntity() })
                    toInsert.forEach { reminderScheduler.scheduleEventLifecycleAlarms(it) }
                }
            }

            report
        }

    suspend fun buildIcsExportForGoogle(googleEmail: String): String = withContext(Dispatchers.IO) {
        val currentEvents = eventDao.getAllActiveEventsSync().mapNotNull {
            runCatching { it.toDomain() }.getOrNull()
        }
        googleSyncManager?.buildIcsExport(currentEvents, googleEmail) ?: ""
    }

    suspend fun exportEncryptedBackup(passphrase: String): String = withContext(Dispatchers.IO) {
        val events = eventDao.getAllActiveEventsSync().mapNotNull {
            runCatching { it.toDomain() }.getOrNull()
        }
        val array = JSONArray()
        events.forEach { e ->
            array.put(
                JSONObject().apply {
                    put("id", e.id)
                    put("title", e.title)
                    put("description", e.description)
                    put("location", e.location)
                    put("category", e.category.name)
                    put("startTimeMillis", e.startTimeMillis)
                    put("endTimeMillis", e.endTimeMillis)
                    put("isAllDay", e.isAllDay)
                    put("reminderMinutesBefore", e.reminderMinutesBefore)
                    put("recurrenceRule", e.recurrenceRule.name)
                    put("updatedAtMillis", e.updatedAtMillis)
                    put("isBiometricLocked", e.isBiometricLocked)
                }
            )
        }
        val root = JSONObject().apply {
            put("schema", 2)
            put("exportedAt", System.currentTimeMillis())
            put("events", array)
        }
        cryptoManager.encryptWithPassphrase(root.toString(), passphrase)
    }

    suspend fun importEncryptedBackup(payload: String, passphrase: String): Int =
        withContext(Dispatchers.IO) {
            val jsonText = cryptoManager.decryptWithPassphrase(payload, passphrase)
            val root = JSONObject(jsonText)
            val array = root.getJSONArray("events")
            val imported = mutableListOf<CalendarEvent>()
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                imported += CalendarEvent(
                    id = obj.optString("id").ifBlank { UUID.randomUUID().toString() },
                    title = obj.getString("title"),
                    description = obj.optString("description", ""),
                    location = obj.optString("location", ""),
                    category = EventCategory.fromName(obj.optString("category")),
                    startTimeMillis = obj.getLong("startTimeMillis"),
                    endTimeMillis = obj.getLong("endTimeMillis"),
                    isAllDay = obj.optBoolean("isAllDay", false),
                    reminderMinutesBefore = obj.optInt("reminderMinutesBefore", 15),
                    recurrenceRule = RecurrenceRule.fromName(obj.optString("recurrenceRule")),
                    syncStatus = SyncStatus.LOCAL_ONLY,
                    updatedAtMillis = obj.optLong("updatedAtMillis", System.currentTimeMillis()),
                    isBiometricLocked = obj.optBoolean("isBiometricLocked", false)
                )
            }
            eventDao.upsertAll(imported.map { it.toEntity() })
            imported.forEach { reminderScheduler.scheduleEventLifecycleAlarms(it) }
            imported.size
        }

    fun expandRecurringInWindow(
        events: List<CalendarEvent>,
        windowStartMillis: Long,
        windowEndMillis: Long
    ): List<CalendarEvent> {
        val result = mutableListOf<CalendarEvent>()
        events.forEach { event ->
            if (event.recurrenceRule == RecurrenceRule.NONE) {
                if (event.endTimeMillis >= windowStartMillis && event.startTimeMillis <= windowEndMillis) {
                    result += event
                }
            } else {
                val duration = (event.endTimeMillis - event.startTimeMillis).coerceAtLeast(15 * 60_000L)
                val cal = Calendar.getInstance().apply { timeInMillis = event.startTimeMillis }
                var safety = 0
                while (cal.timeInMillis <= windowEndMillis && safety < 180) {
                    val occStart = cal.timeInMillis
                    val occEnd = occStart + duration
                    if (occEnd >= windowStartMillis) {
                        result += event.copy(startTimeMillis = occStart, endTimeMillis = occEnd)
                    }
                    when (event.recurrenceRule) {
                        RecurrenceRule.DAILY -> cal.add(Calendar.DAY_OF_YEAR, 1)
                        RecurrenceRule.WEEKLY -> cal.add(Calendar.WEEK_OF_YEAR, 1)
                        RecurrenceRule.MONTHLY -> cal.add(Calendar.MONTH, 1)
                        RecurrenceRule.NONE -> break
                    }
                    safety++
                }
            }
        }
        return result.sortedBy { it.startTimeMillis }
    }

    private fun CalendarEvent.toEntity(): EventEntity = EventEntity(
        id = id,
        encryptedTitle = cryptoManager.encrypt(title),
        encryptedDescription = cryptoManager.encrypt(description),
        encryptedLocation = cryptoManager.encrypt(location),
        category = category.name,
        startTimeMillis = startTimeMillis,
        endTimeMillis = endTimeMillis,
        isAllDay = isAllDay,
        reminderMinutesBefore = reminderMinutesBefore,
        recurrenceRule = recurrenceRule.name,
        syncStatus = syncStatus.name,
        updatedAtMillis = updatedAtMillis,
        isDeleted = false,
        isBiometricLocked = isBiometricLocked,
        googleCalendarEventId = googleCalendarEventId
    )

    private fun EventEntity.toDomain(): CalendarEvent = CalendarEvent(
        id = id,
        title = cryptoManager.decrypt(encryptedTitle),
        description = cryptoManager.decrypt(encryptedDescription),
        location = cryptoManager.decrypt(encryptedLocation),
        category = EventCategory.fromName(category),
        startTimeMillis = startTimeMillis,
        endTimeMillis = endTimeMillis,
        isAllDay = isAllDay,
        reminderMinutesBefore = reminderMinutesBefore,
        recurrenceRule = RecurrenceRule.fromName(recurrenceRule),
        syncStatus = SyncStatus.fromName(syncStatus),
        updatedAtMillis = updatedAtMillis,
        isBiometricLocked = isBiometricLocked,
        googleCalendarEventId = googleCalendarEventId
    )
}
