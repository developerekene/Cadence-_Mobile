package com.example.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.repository.EventRepository
import com.example.data.repository.PreferencesRepository
import com.example.data.repository.UserPreferences
import com.example.domain.model.CalendarEvent
import com.example.domain.model.CalendarViewMode
import com.example.domain.model.EventCategory
import com.example.domain.model.RecurrenceRule
import com.example.domain.model.SyncStatus
import com.example.domain.model.ThemeMode
import com.example.domain.search.NaturalLanguageSearchEngine
import com.example.domain.search.ParsedSearchQuery
import com.example.domain.search.SearchResultItem
import com.example.domain.util.DateTimeUtils
import com.example.domain.util.LocalDay
import com.example.domain.util.YearMonthKey
import com.example.notifications.ReminderScheduler
import com.example.security.CryptoManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
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
    val exportedBackupPayload: String? = null,
    val snackbarMessage: String? = null
)

class CalendarViewModel(
    private val eventRepository: EventRepository,
    private val preferencesRepository: PreferencesRepository,
    private val searchEngine: NaturalLanguageSearchEngine,
    private val cryptoManager: CryptoManager,
    private val reminderScheduler: ReminderScheduler
) : ViewModel() {

    private data class NavFilterState(
        val selectedDay: LocalDay = LocalDay.today(),
        val visibleMonth: YearMonthKey = YearMonthKey.current(),
        val selectedCategoryFilter: EventCategory? = null,
        val searchQuery: String = "",
        val searchPastOnly: Boolean = false
    )

    private data class ModalState(
        val editingDraft: CalendarEvent? = null,
        val viewingEvent: CalendarEvent? = null,
        val hasNotificationPermission: Boolean = true,
        val canScheduleExactAlarms: Boolean = true,
        val exportedBackupPayload: String? = null,
        val snackbarMessage: String? = null
    )

    private val navFilterState = MutableStateFlow(NavFilterState())
    private val modalState = MutableStateFlow(
        ModalState(
            hasNotificationPermission = reminderScheduler.hasNotificationPermission(),
            canScheduleExactAlarms = reminderScheduler.canScheduleExactAlarms()
        )
    )
    private val keyFingerprint = runCatching { cryptoManager.getKeyFingerprint() }.getOrDefault("AES-256")
    private val isHardwareBacked = runCatching { cryptoManager.isHardwareKeystoreActive }.getOrDefault(false)

    val uiState: StateFlow<CalendarUiState> = combine(
        eventRepository.allEvents,
        preferencesRepository.preferencesFlow,
        navFilterState,
        modalState
    ) { events, prefs, nav, modal ->
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
        val (parsed, results) = searchEngine.search(searchInputEvents, nav.searchQuery, now)

        CalendarUiState(
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
                syncProviderLabel = eventRepository.syncEngine.providerName
            ),
            hasNotificationPermission = modal.hasNotificationPermission,
            canScheduleExactAlarms = modal.canScheduleExactAlarms,
            exportedBackupPayload = modal.exportedBackupPayload,
            snackbarMessage = modal.snackbarMessage
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = CalendarUiState()
    )

    fun selectDay(day: LocalDay) {
        navFilterState.update {
            it.copy(
                selectedDay = day,
                visibleMonth = day.toYearMonth()
            )
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
            ThemeMode.SYSTEM -> ThemeMode.DARK
            ThemeMode.DARK -> ThemeMode.LIGHT
            ThemeMode.LIGHT -> ThemeMode.SYSTEM
        }
        setThemeMode(next)
        postSnackbar("Theme: ${next.label}")
    }

    fun setDefaultReminderMinutes(minutes: Int) {
        viewModelScope.launch { preferencesRepository.setDefaultReminderMinutes(minutes) }
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
                modalState.update { it.copy(viewingEvent = event) }
            }
        }
    }

    fun closeEventDetail() {
        modalState.update { it.copy(viewingEvent = null) }
    }

    fun saveEvent(event: CalendarEvent) {
        viewModelScope.launch {
            val saved = eventRepository.saveEvent(event)
            val savedDay = LocalDay.fromEpochMillis(saved.startTimeMillis)
            navFilterState.update {
                it.copy(selectedDay = savedDay, visibleMonth = savedDay.toYearMonth())
            }
            modalState.update {
                it.copy(
                    editingDraft = null,
                    viewingEvent = null,
                    snackbarMessage = if (saved.reminderMinutesBefore >= 0) {
                        "Saved encrypted • Reminder set"
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
            postSnackbar("Moved \"${event.title}\" to ${targetDay.formatShortDate()} at ${DateTimeUtils.formatTime(newStart)}")
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
                canScheduleExactAlarms = reminderScheduler.canScheduleExactAlarms()
            )
        }
    }

    fun sendTestNotification() {
        val sent = reminderScheduler.sendTestNotification()
        postSnackbar(
            if (sent) "Test reminder notification sent"
            else "Enable notification permission to receive alerts"
        )
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

    private fun postSnackbar(message: String) {
        modalState.update { it.copy(snackbarMessage = message) }
    }

    class Factory(
        private val eventRepository: EventRepository,
        private val preferencesRepository: PreferencesRepository,
        private val searchEngine: NaturalLanguageSearchEngine,
        private val cryptoManager: CryptoManager,
        private val reminderScheduler: ReminderScheduler
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return CalendarViewModel(
                eventRepository = eventRepository,
                preferencesRepository = preferencesRepository,
                searchEngine = searchEngine,
                cryptoManager = cryptoManager,
                reminderScheduler = reminderScheduler
            ) as T
        }
    }
}
