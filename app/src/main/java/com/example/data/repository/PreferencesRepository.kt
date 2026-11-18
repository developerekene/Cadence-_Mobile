package com.example.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.domain.model.CalendarViewMode
import com.example.domain.model.ReminderPresets
import com.example.domain.model.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.cadenceDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "cadence_preferences"
)

data class UserPreferences(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val defaultReminderMinutes: Int = ReminderPresets.FIFTEEN_MIN,
    val viewMode: CalendarViewMode = CalendarViewMode.MONTH_AND_AGENDA,
    val offlineModeBannerDismissed: Boolean = false
)

class PreferencesRepository(private val context: Context) {

    private object Keys {
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val DEFAULT_REMINDER = intPreferencesKey("default_reminder_minutes")
        val VIEW_MODE = stringPreferencesKey("calendar_view_mode")
        val OFFLINE_BANNER_DISMISSED = booleanPreferencesKey("offline_banner_dismissed")
    }

    val preferencesFlow: Flow<UserPreferences> = context.cadenceDataStore.data.map { prefs ->
        UserPreferences(
            themeMode = ThemeMode.fromName(prefs[Keys.THEME_MODE] ?: ThemeMode.SYSTEM.name),
            defaultReminderMinutes = prefs[Keys.DEFAULT_REMINDER] ?: ReminderPresets.FIFTEEN_MIN,
            viewMode = CalendarViewMode.entries.firstOrNull {
                it.name == prefs[Keys.VIEW_MODE]
            } ?: CalendarViewMode.MONTH_AND_AGENDA,
            offlineModeBannerDismissed = prefs[Keys.OFFLINE_BANNER_DISMISSED] ?: false
        )
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.cadenceDataStore.edit { it[Keys.THEME_MODE] = mode.name }
    }

    suspend fun setDefaultReminderMinutes(minutes: Int) {
        context.cadenceDataStore.edit { it[Keys.DEFAULT_REMINDER] = minutes }
    }

    suspend fun setViewMode(mode: CalendarViewMode) {
        context.cadenceDataStore.edit { it[Keys.VIEW_MODE] = mode.name }
    }
}
