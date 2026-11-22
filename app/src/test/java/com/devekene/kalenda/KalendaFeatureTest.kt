package com.devekene.kalenda

import android.Manifest
import android.content.Context
import androidx.core.app.ActivityCompat
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.devekene.kalenda.data.local.AppDatabase
import com.devekene.kalenda.data.repository.EventRepository
import com.devekene.kalenda.data.sync.GoogleCalendarSyncManager
import com.devekene.kalenda.domain.model.CalendarEvent
import com.devekene.kalenda.domain.model.EventCategory
import com.devekene.kalenda.domain.model.EventLifecyclePhase
import com.devekene.kalenda.domain.model.RecurrenceRule
import com.devekene.kalenda.domain.search.NaturalLanguageSearchEngine
import com.devekene.kalenda.domain.search.TimeOfDayFilter
import com.devekene.kalenda.domain.util.DateTimeUtils
import com.devekene.kalenda.domain.util.LocalDay
import com.devekene.kalenda.notifications.ReminderScheduler
import com.devekene.kalenda.security.CryptoManager
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class KalendaFeatureTest {

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
        cryptoManager = CryptoManager("test_kalenda_alias")
        repository = EventRepository(
            eventDao = database.eventDao(),
            cryptoManager = cryptoManager,
            reminderScheduler = ReminderScheduler(context),
            googleSyncManager = GoogleCalendarSyncManager(context)
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
    fun `six digit pin PBKDF2 hash and verification rejects wrong pin`() {
        val (salt, hash) = cryptoManager.hashPin("482910")
        assertTrue(cryptoManager.verifyPin("482910", salt, hash))
        assertFalse(cryptoManager.verifyPin("000000", salt, hash))
        assertFalse(cryptoManager.verifyPin("48291", salt, hash))
    }

    @Test
    fun `passphrase encrypted backup export and import roundtrips cleanly including biometric lock`() = runTest {
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
            reminderMinutesBefore = 30,
            isBiometricLocked = true
        )
        repository.saveEvent(event)

        val rawEntities = database.eventDao().getAllActiveEventsSync()
        assertEquals(1, rawEntities.size)
        assertNotEquals("Annual Physical", rawEntities.first().encryptedTitle)
        assertTrue(rawEntities.first().isBiometricLocked)

        val backup = repository.exportEncryptedBackup("strong-pass-123")
        assertTrue(backup.startsWith("KALENDA_E2EE_V1:"))

        database.eventDao().hardDeleteEvent(rawEntities.first().id)
        assertTrue(repository.allEvents.first().isEmpty())

        val restoredCount = repository.importEncryptedBackup(backup, "strong-pass-123")
        assertEquals(1, restoredCount)
        val restored = repository.allEvents.first().first()
        assertEquals("Annual Physical", restored.title)
        assertEquals("Bring lab paperwork", restored.description)
        assertEquals("Clinic 4B", restored.location)
        assertTrue(restored.isBiometricLocked)
    }

    @Test
    fun `natural language search filters past entries and masks locked event keywords`() {
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
                title = "Private Tax Audit",
                description = "Confidential filings",
                location = "Vault Office",
                category = EventCategory.FINANCE,
                startTimeMillis = today.plusDays(3).atTimeMillis(18, 0),
                endTimeMillis = today.plusDays(3).atTimeMillis(19, 0),
                isAllDay = false,
                reminderMinutesBefore = 60,
                recurrenceRule = RecurrenceRule.MONTHLY,
                isBiometricLocked = true
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

        // Locked event's private title shouldn't match when not unlocked
        val (_, hiddenResults) = engine.search(events, "audit", nowMillis, unlockedEventIds = emptySet())
        assertTrue(hiddenResults.isEmpty())

        // Once unlocked in session, its keywords match
        val (_, unlockedResults) = engine.search(events, "audit", nowMillis, unlockedEventIds = setOf("3"))
        assertEquals(1, unlockedResults.size)
        assertEquals("3", unlockedResults.first().event.id)
    }

    @Test
    fun `event lifecycle phase accurately classifies upcoming, current, and past`() {
        val now = 1_700_000_000_000L
        val upcoming = CalendarEvent(
            id = "u",
            title = "Future",
            description = "",
            location = "",
            category = EventCategory.PERSONAL,
            startTimeMillis = now + 60_000L,
            endTimeMillis = now + 120_000L,
            isAllDay = false,
            reminderMinutesBefore = 15
        )
        val current = upcoming.copy(
            id = "c",
            startTimeMillis = now - 60_000L,
            endTimeMillis = now + 60_000L
        )
        val past = upcoming.copy(
            id = "p",
            startTimeMillis = now - 120_000L,
            endTimeMillis = now - 60_000L
        )

        assertEquals(EventLifecyclePhase.UPCOMING, upcoming.lifecyclePhase(now))
        assertEquals(EventLifecyclePhase.CURRENT, current.lifecyclePhase(now))
        assertEquals(EventLifecyclePhase.PAST, past.lifecyclePhase(now))
    }

    @Test
    fun `google calendar ics export hides biometric locked event summary`() {
        val syncManager = GoogleCalendarSyncManager(context)
        val day = LocalDay(2026, 11, 20)
        val events = listOf(
            CalendarEvent(
                id = "ev-open",
                title = "Team Standup",
                description = "Daily sync",
                location = "HQ",
                category = EventCategory.WORK,
                startTimeMillis = day.atTimeMillis(9, 0),
                endTimeMillis = day.atTimeMillis(9, 30),
                isAllDay = false,
                reminderMinutesBefore = 10
            ),
            CalendarEvent(
                id = "ev-locked",
                title = "Secret Medical",
                description = "Private notes",
                location = "Secret Room",
                category = EventCategory.HEALTH,
                startTimeMillis = day.atTimeMillis(14, 0),
                endTimeMillis = day.atTimeMillis(15, 0),
                isAllDay = false,
                reminderMinutesBefore = 15,
                isBiometricLocked = true
            )
        )
        val ics = syncManager.buildIcsExport(events, "alex@gmail.com")
        assertTrue(ics.contains("X-WR-CALNAME:Kalenda (alex@gmail.com)"))
        assertTrue(ics.contains("SUMMARY:Team Standup"))
        assertTrue(ics.contains("SUMMARY:Locked Event"))
        assertFalse(ics.contains("Secret Medical"))
        assertFalse(ics.contains("Private notes"))
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

    @Test
    fun `fragment activity accepts 32-bit ActivityResultRegistry permission request codes`() {
        val activity = Robolectric.buildActivity(MainActivity::class.java).create().get()
        ActivityCompat.requestPermissions(
            activity,
            arrayOf(Manifest.permission.POST_NOTIFICATIONS),
            0x00010000
        )
    }
}
