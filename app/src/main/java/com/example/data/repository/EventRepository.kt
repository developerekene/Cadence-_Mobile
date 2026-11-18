package com.example.data.repository

import com.example.data.local.EventDao
import com.example.data.local.EventEntity
import com.example.domain.model.CalendarEvent
import com.example.domain.model.EventCategory
import com.example.domain.model.RecurrenceRule
import com.example.domain.model.SyncStatus
import com.example.notifications.ReminderScheduler
import com.example.security.CryptoManager
import java.util.Calendar
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * Future-ready sync engine contract so Firebase Firestore credentials can be plugged in seamlessly
 * without altering the UI or encrypted Room persistence layer.
 */
interface CalendarSyncEngine {
    val providerName: String
    val isConfigured: Boolean
    suspend fun syncPendingEvents(events: List<CalendarEvent>): Result<Int>
}

class OfflineOnlySyncEngine : CalendarSyncEngine {
    override val providerName: String = "Local Encrypted Vault (Firebase Sync Ready)"
    override val isConfigured: Boolean = false
    override suspend fun syncPendingEvents(events: List<CalendarEvent>): Result<Int> =
        Result.success(0)
}

class EventRepository(
    private val eventDao: EventDao,
    private val cryptoManager: CryptoManager,
    private val reminderScheduler: ReminderScheduler,
    val syncEngine: CalendarSyncEngine = OfflineOnlySyncEngine()
) {
    val allEvents: Flow<List<CalendarEvent>> = eventDao.observeAllActiveEvents().map { list ->
        list.mapNotNull { entity -> runCatching { entity.toDomain() }.getOrNull() }
    }

    suspend fun getEventById(id: String): CalendarEvent? = withContext(Dispatchers.IO) {
        eventDao.getEventById(id)?.let { runCatching { it.toDomain() }.getOrNull() }
    }

    suspend fun saveEvent(event: CalendarEvent): CalendarEvent = withContext(Dispatchers.IO) {
        val finalId = event.id.ifBlank { UUID.randomUUID().toString() }
        val normalized = event.copy(
            id = finalId,
            syncStatus = if (syncEngine.isConfigured) SyncStatus.PENDING_SYNC else SyncStatus.LOCAL_ONLY,
            updatedAtMillis = System.currentTimeMillis()
        )
        eventDao.upsertEvent(normalized.toEntity())
        reminderScheduler.scheduleReminder(normalized)
        normalized
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
            syncStatus = if (syncEngine.isConfigured) SyncStatus.PENDING_SYNC else SyncStatus.LOCAL_ONLY,
            updatedAtMillis = System.currentTimeMillis()
        )
        eventDao.upsertEvent(updated.toEntity())
        reminderScheduler.scheduleReminder(updated)
        updated
    }

    suspend fun deleteEvent(eventId: String) = withContext(Dispatchers.IO) {
        reminderScheduler.cancelReminder(eventId)
        eventDao.softDeleteEvent(
            id = eventId,
            syncStatus = if (syncEngine.isConfigured) SyncStatus.PENDING_SYNC.name else SyncStatus.LOCAL_ONLY.name,
            updatedAt = System.currentTimeMillis()
        )
    }

    suspend fun rescheduleAllUpcomingReminders() = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val entities = eventDao.getAllActiveEventsSync()
        entities.forEach { entity ->
            runCatching { entity.toDomain() }.getOrNull()?.let { event ->
                if (event.startTimeMillis > now && event.reminderMinutesBefore >= 0) {
                    reminderScheduler.scheduleReminder(event)
                }
            }
        }
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
                }
            )
        }
        val root = JSONObject().apply {
            put("schema", 1)
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
                    updatedAtMillis = obj.optLong("updatedAtMillis", System.currentTimeMillis())
                )
            }
            eventDao.upsertAll(imported.map { it.toEntity() })
            imported.forEach { reminderScheduler.scheduleReminder(it) }
            imported.size
        }

    /**
     * Expands recurring events within a visible calendar window so Daily/Weekly/Monthly events
     * appear cleanly on the month grid and day timeline while editing/dragging modifies the master item.
     */
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
        isDeleted = false
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
        updatedAtMillis = updatedAtMillis
    )
}
