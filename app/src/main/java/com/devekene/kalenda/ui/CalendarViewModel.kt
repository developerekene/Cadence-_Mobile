package com.devekene.kalenda.ui

import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.devekene.kalenda.data.repository.EventRepository
import com.devekene.kalenda.data.repository.PreferencesRepository
import com.devekene.kalenda.data.repository.UserPreferences
import com.devekene.kalenda.data.sync.GoogleCalendarSyncManager
import com.devekene.kalenda.domain.model.CalendarEvent
import com.devekene.kalenda.domain.model.CalendarViewMode
import com.devekene.kalenda.domain.model.EventCategory
import com.devekene.kalenda.domain.model.RecurrenceRule
import com.devekene.kalenda.domain.model.SyncStatus
import com.devekene.kalenda.domain.model.ThemeMode
import com.devekene.kalenda.domain.search.NaturalLanguageSearchEngine
import com.devekene.kalenda.domain.search.ParsedSearchQuery
import com.devekene.kalenda.domain.search.SearchResultItem
import com.devekene.kalenda.domain.util.DateTimeUtils
import com.devekene.kalenda.domain.util.LocalDay
import com.devekene.kalenda.domain.util.YearMonthKey
import com.devekene.kalenda.notifications.ReminderScheduler
import com.devekene.kalenda.security.CryptoManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class EncryptionStats(
    val totalEncryptedEvents: Int = 0,
    val isHardwareBacked: Boolean = false,
    val keyFingerprint: String = "",
    val syncProviderLabel: String = ""
)

data class CalendarUiState(
    val isPreferencesLoaded: Boolean = false,
    val isAppUnlocked: Boolean = false,
    val unlockedEventIds: Set<String> = emptySet(),
    val selectedDay: LocalDay = LocalDay.today(),
    val visibleMonth: YearMonthKey = YearMonthKey.current(),
    val selectedCategoryFilter: EventCategory? = null,
    val preferences: UserPreferences = UserPreferences(),
    val allEvents: List<CalendarEvent> = emptyList(),
    val monthEventsByDay: Map<LocalDay, List<CalendarEvent>> = emptyMap(),
    val selectedDayEvents: List<CalendarEvent> = emptyList(),
    val searchQuery: String = "",
    val searchPastOnly: Boolean = false,
    val parsedSearch: ParsedSearchQuery = ParsedSearchQuery(""),
    val searchResults: List<SearchResultItem> = emptyList(),
    val editingDraft: CalendarEvent? = null,
    val viewingEvent: CalendarEvent? = null,
    val encryptionStats: EncryptionStats = EncryptionStats(),
    val hasNotificationPermission: Boolean = true,
    val canScheduleExactAlarms: Boolean = true,
    val hasCalendarPermissions: Boolean = false,
    val isSyncingGoogle: Boolean = false,
    val googleSyncStatusMessage: String? = null,
    val exportedBackupPayload: String? = null,
    val snackbarMessage: String? = null
) {
    val requiresLaunchUnlock: Boolean
        get() = isPreferencesLoaded && preferences.biometricAppLockEnabled && !isAppUnlocked
}

class CalendarViewModel(
    private val eventRepository: EventRepository,
    private val preferencesRepository: PreferencesRepository,
    private val searchEngine: NaturalLanguageSearchEngine,
    private val cryptoManager: CryptoManager,
    private val reminderScheduler: ReminderScheduler,
    private val googleSyncManager: GoogleCalendarSyncManager
) : ViewModel() {

    private data class NavFilterState(
        val selectedDay: LocalDay = LocalDay.today(),
        val visibleMonth: YearMonthKey = YearMonthKey.current(),
        val selectedCategoryFilter: EventCategory? = null,
        val searchQuery: String = "",
        val searchPastOnly: Boolean = false
    )

    private data class SecuritySessionState(
        val isPreferencesLoaded: Boolean = false,
        val isAppUnlocked: Boolean = false,
        val unlockedEventIds: Set<String> = emptySet()
    )

    private data class ModalState(
        val editingDraft: CalendarEvent? = null,
        val viewingEvent: CalendarEvent? = null,
        val hasNotificationPermission: Boolean = true,
        val canScheduleExactAlarms: Boolean = true,
        val hasCalendarPermissions: Boolean = false,
        val isSyncingGoogle: Boolean = false,
        val googleSyncStatusMessage: String? = null,
        val exportedBackupPayload: String? = null,
        val snackbarMessage: String? = null
    )

    private val navFilterState = MutableStateFlow(NavFilterState())
    private val securityState = MutableStateFlow(SecuritySessionState())
    private val modalState = MutableStateFlow(
        ModalState(
            hasNotificationPermission = reminderScheduler.hasNotificationPermission(),
            canScheduleExactAlarms = reminderScheduler.canScheduleExactAlarms(),
            hasCalendarPermissions = googleSyncManager.hasCalendarPermissions()
        )
    )
    private val keyFingerprint = runCatching { cryptoManager.getKeyFingerprint() }.getOrDefault("AES-256")
    private val isHardwareBacked = runCatching { cryptoManager.isHardwareKeystoreActive }.getOrDefault(false)

    private val trackedPreferencesFlow = preferencesRepository.preferencesFlow.onEach { prefs ->
        securityState.update { state ->
            if (!state.isPreferencesLoaded) {
                state.copy(
                    isPreferencesLoaded = true,
                    isAppUnlocked = !prefs.biometricAppLockEnabled
                )
            } else {
                state
            }
        }
    }

    val uiState: StateFlow<CalendarUiState> = combine(
        eventRepository.allEvents,
        trackedPreferencesFlow,
        navFilterState,
        modalState,
        securityState
    ) { events, prefs, nav, modal, sec ->
        val monthWindowStart = nav.visibleMonth.plusMonths(-1).startMillis()
        val monthWindowEnd = nav.visibleMonth.plusMonths(1).endMillis()
        val expanded = eventRepository.expandRecurringInWindow(events, monthWindowStart, monthWindowEnd)

        val filteredExpanded = if (nav.selectedCategoryFilter == null) {
            expanded
        } else {
            expanded.filter { it.category == nav.selectedCategoryFilter }
        }

        val byDay = mutableMapOf<LocalDay, MutableList<CalendarEvent>>()
        filteredExpanded.forEach { ev ->
            val startDay = LocalDay.fromEpochMillis(ev.startTimeMillis)
            val endDay = LocalDay.fromEpochMillis(ev.endTimeMillis)
            var cursor = startDay
            var guard = 0
            while (cursor <= endDay && guard < 31) {
                byDay.getOrPut(cursor) { mutableListOf() }.add(ev)
                cursor = cursor.plusDays(1)
                guard++
            }
        }

        val dayEvents = byDay[nav.selectedDay]?.sortedWith(
            compareByDescending<CalendarEvent> { it.isAllDay }.thenBy { it.startTimeMillis }
        ) ?: emptyList()

        val now = System.currentTimeMillis()
        val searchInputEvents = if (nav.searchPastOnly) {
            events.filter { it.endTimeMillis < now }
        } else {
            events
        }
        val (parsed, results) = searchEngine.search(
            events = searchInputEvents,
            query = nav.searchQuery,
            nowMillis = now,
            unlockedEventIds = sec.unlockedEventIds
        )

        val syncLabel = if (prefs.googleSyncEmail.isNotBlank()) {
            "Google Calendar (${prefs.googleSyncEmail})"
        } else {
            "Offline AES-256 Vault (Add Google email in Settings to sync)"
        }

        CalendarUiState(
            isPreferencesLoaded = sec.isPreferencesLoaded,
            isAppUnlocked = sec.isAppUnlocked,
            unlockedEventIds = sec.unlockedEventIds,
            selectedDay = nav.selectedDay,
            visibleMonth = nav.visibleMonth,
            selectedCategoryFilter = nav.selectedCategoryFilter,
            preferences = prefs,
            allEvents = events,
            monthEventsByDay = byDay,
            selectedDayEvents = dayEvents,
            searchQuery = nav.searchQuery,
            searchPastOnly = nav.searchPastOnly,
            parsedSearch = parsed,
            searchResults = results,
            editingDraft = modal.editingDraft,
            viewingEvent = modal.viewingEvent,
            encryptionStats = EncryptionStats(
                totalEncryptedEvents = events.size,
                isHardwareBacked = isHardwareBacked,
                keyFingerprint = keyFingerprint,
                syncProviderLabel = syncLabel
            ),
            hasNotificationPermission = modal.hasNotificationPermission,
            canScheduleExactAlarms = modal.canScheduleExactAlarms,
            hasCalendarPermissions = modal.hasCalendarPermissions,
            isSyncingGoogle = modal.isSyncingGoogle,
            googleSyncStatusMessage = modal.googleSyncStatusMessage,
            exportedBackupPayload = modal.exportedBackupPayload,
            snackbarMessage = modal.snackbarMessage
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = CalendarUiState()
    )

    fun unlockAppWithBiometrics() {
        securityState.update { it.copy(isAppUnlocked = true) }
    }

    fun verifyLaunchPin(pin: String): Boolean {
        val prefs = uiState.value.preferences
        val ok = cryptoManager.verifyPin(pin, prefs.pinSalt, prefs.pinHash)
        if (ok) {
            securityState.update { it.copy(isAppUnlocked = true) }
        }
        return ok
    }

    fun lockAppNow() {
        if (uiState.value.preferences.biometricAppLockEnabled) {
            securityState.update { it.copy(isAppUnlocked = false, unlockedEventIds = emptySet()) }
        }
    }

    fun activateBiometricPinSecurity(pin: String) {
        viewModelScope.launch {
            runCatching {
                val (salt, hash) = cryptoManager.hashPin(pin)
                preferencesRepository.setBiometricPinSecurity(
                    enabled = true,
                    pinSalt = salt,
                    pinHash = hash
                )
            }.onSuccess {
                securityState.update { it.copy(isAppUnlocked = true) }
                postSnackbar("Biometrics & 6-digit PIN lock activated for app launch")
            }.onFailure { err ->
                postSnackbar(err.message ?: "Failed to set PIN")
            }
        }
    }

    fun updateSecurityPin(newPin: String) {
        viewModelScope.launch {
            runCatching {
                val (salt, hash) = cryptoManager.hashPin(newPin)
                preferencesRepository.setBiometricPinSecurity(
                    enabled = true,
                    pinSalt = salt,
                    pinHash = hash
                )
            }.onSuccess {
                postSnackbar("6-digit PIN updated")
            }.onFailure { err ->
                postSnackbar(err.message ?: "Failed to update PIN")
            }
        }
    }

    fun disableBiometricPinSecurity() {
        viewModelScope.launch {
            preferencesRepository.setBiometricPinSecurity(enabled = false)
            securityState.update { it.copy(isAppUnlocked = true) }
            postSnackbar("Launch lock disabled")
        }
    }

    fun verifyConfiguredPin(pin: String): Boolean {
        val prefs = uiState.value.preferences
        return cryptoManager.verifyPin(pin, prefs.pinSalt, prefs.pinHash)
    }

    fun markEventUnlockedAndOpen(event: CalendarEvent) {
        securityState.update { it.copy(unlockedEventIds = it.unlockedEventIds + event.id) }
        modalState.update { it.copy(viewingEvent = event) }
    }

    fun setEventBiometricLocked(event: CalendarEvent, locked: Boolean) {
        viewModelScope.launch {
            val updated = eventRepository.setBiometricLocked(event.id, locked)
            if (updated != null) {
                securityState.update { state ->
                    val nextUnlocked = if (locked) {
                        state.unlockedEventIds - updated.id
                    } else {
                        state.unlockedEventIds + updated.id
                    }
                    state.copy(unlockedEventIds = nextUnlocked)
                }
                modalState.update {
                    it.copy(
                        viewingEvent = if (locked) null else updated,
                        snackbarMessage = if (locked) {
                            "Event locked with biometrics"
                        } else {
                            "Biometric lock removed from event"
                        }
                    )
                }
            }
        }
    }

    fun selectDay(day: LocalDay) {
        navFilterState.update {
            it.copy(selectedDay = day, visibleMonth = day.toYearMonth())
        }
    }

    fun jumpToToday() {
        val today = LocalDay.today()
        navFilterState.update {
            it.copy(selectedDay = today, visibleMonth = today.toYearMonth())
        }
    }

    fun shiftMonth(delta: Int) {
        navFilterState.update { it.copy(visibleMonth = it.visibleMonth.plusMonths(delta)) }
    }

    fun selectCategoryFilter(category: EventCategory?) {
        navFilterState.update {
            val next = if (it.selectedCategoryFilter == category) null else category
            it.copy(selectedCategoryFilter = next)
        }
    }

    fun setViewMode(mode: CalendarViewMode) {
        viewModelScope.launch { preferencesRepository.setViewMode(mode) }
    }

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { preferencesRepository.setThemeMode(mode) }
    }

    fun cycleThemeMode() {
        val current = uiState.value.preferences.themeMode
        val next = when (current) {
            ThemeMode.DARK -> ThemeMode.LIGHT
            ThemeMode.LIGHT -> ThemeMode.SYSTEM
            ThemeMode.SYSTEM -> ThemeMode.DARK
        }
        setThemeMode(next)
        postSnackbar("Theme: ${next.label}")
    }

    fun setDefaultReminderMinutes(minutes: Int) {
        viewModelScope.launch { preferencesRepository.setDefaultReminderMinutes(minutes) }
    }

    fun setNotificationPhases(upcoming: Boolean, current: Boolean, past: Boolean) {
        viewModelScope.launch {
            preferencesRepository.setNotificationPhases(upcoming, current, past)
            eventRepository.rescheduleAllLifecycleAlarms(upcoming, current, past)
            postSnackbar("Lifecycle notification preferences updated")
        }
    }

    fun triggerLifecycleStatusNotifications() {
        val prefs = uiState.value.preferences
        val sent = reminderScheduler.notifyLifecycleSummary(
            events = uiState.value.allEvents,
            notifyUpcoming = prefs.notifyUpcoming,
            notifyCurrent = prefs.notifyCurrent,
            notifyPast = prefs.notifyPast
        )
        postSnackbar(
            if (sent) "Pushed Upcoming, Current & Past schedule alerts"
            else "Enable notification permission to receive alerts"
        )
    }

    fun setGoogleSyncEmail(email: String) {
        viewModelScope.launch {
            preferencesRepository.setGoogleSyncEmail(email.trim())
            modalState.update {
                it.copy(
                    googleSyncStatusMessage = if (email.isBlank()) {
                        "Google email cleared"
                    } else {
                        "Saved Google email: ${email.trim()}"
                    }
                )
            }
        }
    }

    fun syncWithGoogleCalendar(emailOverride: String? = null) {
        viewModelScope.launch {
            val email = (emailOverride ?: uiState.value.preferences.googleSyncEmail).trim()
            if (email.isBlank() || !email.contains("@")) {
                postSnackbar("Enter a valid Google email address")
                return@launch
            }
            preferencesRepository.setGoogleSyncEmail(email)
            modalState.update { it.copy(isSyncingGoogle = true) }
            runCatching {
                eventRepository.syncWithGoogleCalendar(email)
            }.onSuccess { report ->
                if (report.calendarIdFound != null) {
                    preferencesRepository.setLastGoogleSyncMillis(System.currentTimeMillis())
                }
                modalState.update {
                    it.copy(
                        isSyncingGoogle = false,
                        googleSyncStatusMessage = report.statusNote,
                        snackbarMessage = report.statusNote
                    )
                }
            }.onFailure { err ->
                modalState.update {
                    it.copy(
                        isSyncingGoogle = false,
                        googleSyncStatusMessage = err.message ?: "Google Calendar sync failed",
                        snackbarMessage = err.message ?: "Google Calendar sync failed"
                    )
                }
            }
        }
    }

    suspend fun buildIcsExportPayload(): String {
        return eventRepository.buildIcsExportForGoogle(uiState.value.preferences.googleSyncEmail)
    }

    fun buildGoogleCalendarInsertIntent(event: CalendarEvent): Intent {
        return googleSyncManager.buildInsertInGoogleCalendarIntent(
            event = event,
            googleEmail = uiState.value.preferences.googleSyncEmail
        )
    }

    fun updateSearchQuery(query: String) {
        navFilterState.update { it.copy(searchQuery = query) }
    }

    fun toggleSearchPastOnly() {
        navFilterState.update { it.copy(searchPastOnly = !it.searchPastOnly) }
    }

    fun openNewEventEditor(
        day: LocalDay = navFilterState.value.selectedDay,
        defaultHour: Int = 9
    ) {
        val startMillis = day.atTimeMillis(defaultHour.coerceIn(0, 22), 0)
        val endMillis = startMillis + 60 * 60_000L
        val draft = CalendarEvent(
            id = "",
            title = "",
            description = "",
            location = "",
            category = navFilterState.value.selectedCategoryFilter ?: EventCategory.PERSONAL,
            startTimeMillis = startMillis,
            endTimeMillis = endMillis,
            isAllDay = false,
            reminderMinutesBefore = uiState.value.preferences.defaultReminderMinutes,
            recurrenceRule = RecurrenceRule.NONE,
            syncStatus = SyncStatus.LOCAL_ONLY
        )
        modalState.update { it.copy(editingDraft = draft, viewingEvent = null) }
    }

    fun openEditEventEditor(event: CalendarEvent) {
        modalState.update { it.copy(editingDraft = event, viewingEvent = null) }
    }

    fun closeEventEditor() {
        modalState.update { it.copy(editingDraft = null) }
    }

    fun openEventDetail(event: CalendarEvent) {
        modalState.update { it.copy(viewingEvent = event) }
    }

    fun openEventById(eventId: String) {
        viewModelScope.launch {
            eventRepository.getEventById(eventId)?.let { event ->
                navFilterState.update {
                    val day = LocalDay.fromEpochMillis(event.startTimeMillis)
                    it.copy(selectedDay = day, visibleMonth = day.toYearMonth())
                }
                if (!event.isBiometricLocked || event.id in securityState.value.unlockedEventIds) {
                    modalState.update { it.copy(viewingEvent = event) }
                } else {
                    postSnackbar("Select the locked event to unlock with biometrics")
                }
            }
        }
    }

    fun closeEventDetail() {
        modalState.update { it.copy(viewingEvent = null) }
    }

    fun saveEvent(event: CalendarEvent) {
        viewModelScope.launch {
            val prefs = uiState.value.preferences
            val saved = eventRepository.saveEvent(
                event = event,
                notifyUpcoming = prefs.notifyUpcoming,
                notifyCurrent = prefs.notifyCurrent,
                notifyPast = prefs.notifyPast
            )
            val savedDay = LocalDay.fromEpochMillis(saved.startTimeMillis)
            navFilterState.update {
                it.copy(selectedDay = savedDay, visibleMonth = savedDay.toYearMonth())
            }
            if (saved.isBiometricLocked) {
                securityState.update { it.copy(unlockedEventIds = it.unlockedEventIds + saved.id) }
            }
            modalState.update {
                it.copy(
                    editingDraft = null,
                    viewingEvent = null,
                    snackbarMessage = if (saved.reminderMinutesBefore >= 0) {
                        "Saved encrypted • Lifecycle alerts scheduled"
                    } else {
                        "Saved encrypted locally"
                    }
                )
            }
        }
    }

    fun deleteEvent(eventId: String) {
        viewModelScope.launch {
            eventRepository.deleteEvent(eventId)
            securityState.update { it.copy(unlockedEventIds = it.unlockedEventIds - eventId) }
            modalState.update {
                it.copy(
                    editingDraft = null,
                    viewingEvent = null,
                    snackbarMessage = "Event deleted"
                )
            }
        }
    }

    fun rescheduleEventByDrag(
        event: CalendarEvent,
        targetDay: LocalDay,
        newStartMinutesFromMidnight: Int
    ) {
        viewModelScope.launch {
            val (newStart, newEnd) = DateTimeUtils.moveEventToDayAndMinutes(
                originalStart = event.startTimeMillis,
                originalEnd = event.endTimeMillis,
                targetDay = targetDay,
                newStartMinutesFromMidnight = newStartMinutesFromMidnight
            )
            eventRepository.rescheduleEvent(event.id, newStart, newEnd)
            navFilterState.update {
                it.copy(selectedDay = targetDay, visibleMonth = targetDay.toYearMonth())
            }
            val displayTitle = if (event.isBiometricLocked && event.id !in uiState.value.unlockedEventIds) {
                "Locked event"
            } else {
                event.title
            }
            postSnackbar("Moved \"$displayTitle\" to ${targetDay.formatShortDate()} at ${DateTimeUtils.formatTime(newStart)}")
        }
    }

    fun shiftEventOneDay(event: CalendarEvent, dayDelta: Int) {
        viewModelScope.launch {
            val shiftMillis = dayDelta * 24L * 60L * 60_000L
            val newStart = event.startTimeMillis + shiftMillis
            val newEnd = event.endTimeMillis + shiftMillis
            eventRepository.rescheduleEvent(event.id, newStart, newEnd)
            val newDay = LocalDay.fromEpochMillis(newStart)
            navFilterState.update { it.copy(selectedDay = newDay, visibleMonth = newDay.toYearMonth()) }
            modalState.update { it.copy(viewingEvent = null) }
            postSnackbar("Rescheduled to ${newDay.formatFullDate()}")
        }
    }

    fun refreshPermissionState() {
        modalState.update {
            it.copy(
                hasNotificationPermission = reminderScheduler.hasNotificationPermission(),
                canScheduleExactAlarms = reminderScheduler.canScheduleExactAlarms(),
                hasCalendarPermissions = googleSyncManager.hasCalendarPermissions()
            )
        }
    }

    fun exportEncryptedVault(passphrase: String) {
        viewModelScope.launch {
            runCatching {
                eventRepository.exportEncryptedBackup(passphrase)
            }.onSuccess { payload ->
                modalState.update {
                    it.copy(
                        exportedBackupPayload = payload,
                        snackbarMessage = "E2EE vault payload generated"
                    )
                }
            }.onFailure { err ->
                postSnackbar(err.message ?: "Encryption failed")
            }
        }
    }

    fun clearExportedPayload() {
        modalState.update { it.copy(exportedBackupPayload = null) }
    }

    fun importEncryptedVault(payload: String, passphrase: String) {
        viewModelScope.launch {
            runCatching {
                eventRepository.importEncryptedBackup(payload, passphrase)
            }.onSuccess { count ->
                postSnackbar("Decrypted & restored $count events")
            }.onFailure {
                postSnackbar("Invalid passphrase or corrupted vault payload")
            }
        }
    }

    fun clearSnackbar() {
        modalState.update { it.copy(snackbarMessage = null) }
    }

    fun postSnackbar(message: String) {
        modalState.update { it.copy(snackbarMessage = message) }
    }

    class Factory(
        private val eventRepository: EventRepository,
        private val preferencesRepository: PreferencesRepository,
        private val searchEngine: NaturalLanguageSearchEngine,
        private val cryptoManager: CryptoManager,
        private val reminderScheduler: ReminderScheduler,
        private val googleSyncManager: GoogleCalendarSyncManager
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return CalendarViewModel(
                eventRepository = eventRepository,
                preferencesRepository = preferencesRepository,
                searchEngine = searchEngine,
                cryptoManager = cryptoManager,
                reminderScheduler = reminderScheduler,
                googleSyncManager = googleSyncManager
            ) as T
        }
    }
}
