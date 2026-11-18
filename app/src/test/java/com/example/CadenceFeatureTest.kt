package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.AppDatabase
import com.example.data.repository.EventRepository
import com.example.domain.model.CalendarEvent
import com.example.domain.model.EventCategory
import com.example.domain.model.RecurrenceRule
import com.example.domain.search.NaturalLanguageSearchEngine
import com.example.domain.search.TimeOfDayFilter
import com.example.domain.util.DateTimeUtils
import com.example.domain.util.LocalDay
import com.example.notifications.ReminderScheduler
import com.example.security.CryptoManager
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class CadenceFeatureTest {

    private lateinit var context: Context
    private lateinit var database: AppDatabase
    private lateinit var cryptoManager: CryptoManager
    private lateinit var repository: EventRepository

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        cryptoManager = CryptoManager("test_cadence_alias")
        repository = EventRepository(
            eventDao = database.eventDao(),
            cryptoManager = cryptoManager,
            reminderScheduler = ReminderScheduler(context)
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `crypto manager encrypts with unique IVs and decrypts cleanly`() {
        val secretTitle = "Board Strategy Sync"
        val cipherA = cryptoManager.encrypt(secretTitle)
        val cipherB = cryptoManager.encrypt(secretTitle)

        assertTrue(cipherA.startsWith("v1:"))
        assertNotEquals(cipherA, cipherB)
        assertNotEquals(secretTitle, cipherA)
        assertEquals(secretTitle, cryptoManager.decrypt(cipherA))
        assertEquals(secretTitle, cryptoManager.decrypt(cipherB))
    }

    @Test
    fun `passphrase encrypted backup export and import roundtrips cleanly`() = runTest {
        val today = LocalDay.today()
        val event = CalendarEvent(
            id = "",
            title = "Annual Physical",
            description = "Bring lab paperwork",
            location = "Clinic 4B",
            category = EventCategory.HEALTH,
            startTimeMillis = today.atTimeMillis(10, 0),
            endTimeMillis = today.atTimeMillis(11, 0),
            isAllDay = false,
            reminderMinutesBefore = 30
        )
        repository.saveEvent(event)

        val rawEntities = database.eventDao().getAllActiveEventsSync()
        assertEquals(1, rawEntities.size)
        assertNotEquals("Annual Physical", rawEntities.first().encryptedTitle)

        val backup = repository.exportEncryptedBackup("strong-pass-123")
        assertTrue(backup.startsWith("CADENCE_E2EE_V1:"))

        database.eventDao().hardDeleteEvent(rawEntities.first().id)
        assertTrue(repository.allEvents.first().isEmpty())

        val restoredCount = repository.importEncryptedBackup(backup, "strong-pass-123")
        assertEquals(1, restoredCount)
        val restored = repository.allEvents.first()
        assertEquals("Annual Physical", restored.first().title)
        assertEquals("Bring lab paperwork", restored.first().description)
        assertEquals("Clinic 4B", restored.first().location)
    }

    @Test
    fun `natural language search filters past entries by date phrase, category, and keywords`() {
        val engine = NaturalLanguageSearchEngine()
        val today = LocalDay(2026, 11, 18)
        val nowMillis = today.atTimeMillis(12, 0)

        val events = listOf(
            CalendarEvent(
                id = "1",
                title = "Sprint Architecture Review",
                description = "Reviewed offline Room schema",
                location = "Room 302",
                category = EventCategory.WORK,
                startTimeMillis = today.plusDays(-7).atTimeMillis(9, 30),
                endTimeMillis = today.plusDays(-7).atTimeMillis(10, 30),
                isAllDay = false,
                reminderMinutesBefore = 15
            ),
            CalendarEvent(
                id = "2",
                title = "Morning Run",
                description = "5k waterfront",
                location = "Park",
                category = EventCategory.HEALTH,
                startTimeMillis = today.plusDays(-1).atTimeMillis(7, 0),
                endTimeMillis = today.plusDays(-1).atTimeMillis(8, 0),
                isAllDay = false,
                reminderMinutesBefore = -1
            ),
            CalendarEvent(
                id = "3",
                title = "Budget Planning",
                description = "Q4 taxes",
                location = "Home",
                category = EventCategory.FINANCE,
                startTimeMillis = today.plusDays(3).atTimeMillis(18, 0),
                endTimeMillis = today.plusDays(3).atTimeMillis(19, 0),
                isAllDay = false,
                reminderMinutesBefore = 60,
                recurrenceRule = RecurrenceRule.MONTHLY
            )
        )

        val (parsed1, results1) = engine.search(events, "work last week architecture", nowMillis)
        assertTrue(EventCategory.WORK in parsed1.categories)
        assertEquals(1, results1.size)
        assertEquals("1", results1.first().event.id)
        assertTrue(results1.first().isPast)

        val (parsed2, results2) = engine.search(events, "health yesterday morning", nowMillis)
        assertEquals(TimeOfDayFilter.MORNING, parsed2.timeOfDay)
        assertEquals(1, results2.size)
        assertEquals("2", results2.first().event.id)

        val (_, results3) = engine.search(events, "past with reminders", nowMillis)
        assertEquals(1, results3.size)
        assertEquals("1", results3.first().event.id)
    }

    @Test
    fun `drag and drop reschedule preserves duration and moves to target day and time`() {
        val day = LocalDay(2026, 11, 18)
        val origStart = day.atTimeMillis(9, 0)
        val origEnd = day.atTimeMillis(10, 30)
        val targetDay = day.plusDays(1)

        val (newStart, newEnd) = DateTimeUtils.moveEventToDayAndMinutes(
            originalStart = origStart,
            originalEnd = origEnd,
            targetDay = targetDay,
            newStartMinutesFromMidnight = 14 * 60 + 15
        )

        assertEquals(targetDay, LocalDay.fromEpochMillis(newStart))
        assertEquals(14, DateTimeUtils.hourOf(newStart))
        assertEquals(15, DateTimeUtils.minuteOf(newStart))
        assertEquals(90, DateTimeUtils.durationMinutes(newStart, newEnd))
    }
}
