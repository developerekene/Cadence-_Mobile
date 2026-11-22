package com.devekene.kalenda.domain.model

import androidx.compose.ui.graphics.Color
import com.devekene.kalenda.ui.theme.CategoryFinanceDark
import com.devekene.kalenda.ui.theme.CategoryFinanceLight
import com.devekene.kalenda.ui.theme.CategoryFocusDark
import com.devekene.kalenda.ui.theme.CategoryFocusLight
import com.devekene.kalenda.ui.theme.CategoryHealthDark
import com.devekene.kalenda.ui.theme.CategoryHealthLight
import com.devekene.kalenda.ui.theme.CategoryPersonalDark
import com.devekene.kalenda.ui.theme.CategoryPersonalLight
import com.devekene.kalenda.ui.theme.CategorySocialDark
import com.devekene.kalenda.ui.theme.CategorySocialLight
import com.devekene.kalenda.ui.theme.CategoryWorkDark
import com.devekene.kalenda.ui.theme.CategoryWorkLight

enum class EventCategory(
    val displayName: String,
    val lightColor: Color,
    val darkColor: Color,
    val keywords: List<String>
) {
    WORK(
        displayName = "Work",
        lightColor = CategoryWorkLight,
        darkColor = CategoryWorkDark,
        keywords = listOf("work", "office", "meeting", "standup", "sync", "review", "sprint", "project", "client")
    ),
    PERSONAL(
        displayName = "Personal",
        lightColor = CategoryPersonalLight,
        darkColor = CategoryPersonalDark,
        keywords = listOf("personal", "home", "errand", "family", "chore", "life")
    ),
    HEALTH(
        displayName = "Health",
        lightColor = CategoryHealthLight,
        darkColor = CategoryHealthDark,
        keywords = listOf("health", "gym", "workout", "run", "doctor", "dentist", "therapy", "yoga", "meditate")
    ),
    FOCUS(
        displayName = "Focus",
        lightColor = CategoryFocusLight,
        darkColor = CategoryFocusDark,
        keywords = listOf("focus", "deep", "study", "read", "write", "code", "design", "research")
    ),
    SOCIAL(
        displayName = "Social",
        lightColor = CategorySocialLight,
        darkColor = CategorySocialDark,
        keywords = listOf("social", "dinner", "lunch", "coffee", "party", "birthday", "hangout", "friends")
    ),
    FINANCE(
        displayName = "Finance",
        lightColor = CategoryFinanceLight,
        darkColor = CategoryFinanceDark,
        keywords = listOf("finance", "bill", "tax", "budget", "bank", "invoice", "pay", "rent")
    );

    fun accentColor(isDark: Boolean): Color = if (isDark) darkColor else lightColor

    companion object {
        fun fromName(raw: String): EventCategory =
            entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: PERSONAL
    }
}

enum class RecurrenceRule(val displayName: String) {
    NONE("One-time"),
    DAILY("Daily"),
    WEEKLY("Weekly"),
    MONTHLY("Monthly");

    companion object {
        fun fromName(raw: String): RecurrenceRule =
            entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: NONE
    }
}

enum class SyncStatus(val label: String) {
    LOCAL_ONLY("Stored locally (E2EE)"),
    PENDING_SYNC("Queued for sync"),
    SYNCED("Synced to Google Calendar");

    companion object {
        fun fromName(raw: String): SyncStatus =
            entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: LOCAL_ONLY
    }
}

enum class EventLifecyclePhase(val label: String) {
    UPCOMING("Upcoming"),
    CURRENT("Current"),
    PAST("Past")
}

enum class ThemeMode(val label: String) {
    SYSTEM("System"),
    LIGHT("Light"),
    DARK("Dark");

    companion object {
        fun fromName(raw: String): ThemeMode =
            entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: DARK
    }
}

enum class CalendarViewMode(val label: String) {
    MONTH_AND_AGENDA("Month + Day"),
    TIMELINE_DRAG("Drag Timeline")
}

data class CalendarEvent(
    val id: String,
    val title: String,
    val description: String,
    val location: String,
    val category: EventCategory,
    val startTimeMillis: Long,
    val endTimeMillis: Long,
    val isAllDay: Boolean,
    val reminderMinutesBefore: Int,
    val recurrenceRule: RecurrenceRule = RecurrenceRule.NONE,
    val syncStatus: SyncStatus = SyncStatus.LOCAL_ONLY,
    val updatedAtMillis: Long = System.currentTimeMillis(),
    val isBiometricLocked: Boolean = false,
    val googleCalendarEventId: Long? = null
) {
    fun lifecyclePhase(nowMillis: Long = System.currentTimeMillis()): EventLifecyclePhase = when {
        nowMillis < startTimeMillis -> EventLifecyclePhase.UPCOMING
        nowMillis <= endTimeMillis -> EventLifecyclePhase.CURRENT
        else -> EventLifecyclePhase.PAST
    }
}

object ReminderPresets {
    const val NONE = -1
    const val AT_START = 0
    const val FIVE_MIN = 5
    const val TEN_MIN = 10
    const val FIFTEEN_MIN = 15
    const val THIRTY_MIN = 30
    const val ONE_HOUR = 60
    const val ONE_DAY = 1440

    val options = listOf(NONE, AT_START, FIVE_MIN, TEN_MIN, FIFTEEN_MIN, THIRTY_MIN, ONE_HOUR, ONE_DAY)

    fun formatLabel(minutesBefore: Int): String = when {
        minutesBefore < 0 -> "No reminder"
        minutesBefore == 0 -> "At start time"
        minutesBefore < 60 -> "$minutesBefore min before"
        minutesBefore == 60 -> "1 hour before"
        minutesBefore < 1440 && minutesBefore % 60 == 0 -> "${minutesBefore / 60} hours before"
        minutesBefore == 1440 -> "1 day before"
        minutesBefore % 1440 == 0 -> "${minutesBefore / 1440} days before"
        else -> "$minutesBefore min before"
    }
}
