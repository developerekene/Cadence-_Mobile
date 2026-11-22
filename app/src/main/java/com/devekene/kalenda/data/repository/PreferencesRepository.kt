package com.devekene.kalenda.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.devekene.kalenda.domain.model.CalendarViewMode
import com.devekene.kalenda.domain.model.ReminderPresets
import com.devekene.kalenda.domain.model.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.kalendaDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "kalenda_preferences"
)

data class UserPreferences(
    val themeMode: ThemeMode = ThemeMode.DARK,
    val defaultReminderMinutes: Int = ReminderPresets.FIFTEEN_MIN,
    val viewMode: CalendarViewMode = CalendarViewMode.MONTH_AND_AGENDA,
    val googleSyncEmail: String = "",
    val lastGoogleSyncMillis: Long = 0L,
    val biometricAppLockEnabled: Boolean = false,
    val pinHash: String = "",
    val pinSalt: String = "",
    val notifyUpcoming: Boolean = true,
    val notifyCurrent: Boolean = true,
    val notifyPast: Boolean = true
)

class PreferencesRepository(private val context: Context) {

    private object Keys {
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val DEFAULT_REMINDER = intPreferencesKey("default_reminder_minutes")
        val VIEW_MODE = stringPreferencesKey("calendar_view_mode")
        val GOOGLE_SYNC_EMAIL = stringPreferencesKey("google_sync_email")
        val LAST_GOOGLE_SYNC = longPreferencesKey("last_google_sync_millis")
        val BIOMETRIC_APP_LOCK = booleanPreferencesKey("biometric_app_lock_enabled")
        val PIN_HASH = stringPreferencesKey("pin_hash")
        val PIN_SALT = stringPreferencesKey("pin_salt")
        val NOTIFY_UPCOMING = booleanPreferencesKey("notify_upcoming")
        val NOTIFY_CURRENT = booleanPreferencesKey("notify_current")
        val NOTIFY_PAST = booleanPreferencesKey("notify_past")
    }

    val preferencesFlow: Flow<UserPreferences> = context.kalendaDataStore.data.map { prefs ->
        UserPreferences(
            themeMode = ThemeMode.fromName(prefs[Keys.THEME_MODE] ?: ThemeMode.DARK.name),
            defaultReminderMinutes = prefs[Keys.DEFAULT_REMINDER] ?: ReminderPresets.FIFTEEN_MIN,
            viewMode = CalendarViewMode.entries.firstOrNull {
                it.name == prefs[Keys.VIEW_MODE]
            } ?: CalendarViewMode.MONTH_AND_AGENDA,
            googleSyncEmail = prefs[Keys.GOOGLE_SYNC_EMAIL] ?: "",
            lastGoogleSyncMillis = prefs[Keys.LAST_GOOGLE_SYNC] ?: 0L,
            biometricAppLockEnabled = prefs[Keys.BIOMETRIC_APP_LOCK] ?: false,
            pinHash = prefs[Keys.PIN_HASH] ?: "",
            pinSalt = prefs[Keys.PIN_SALT] ?: "",
            notifyUpcoming = prefs[Keys.NOTIFY_UPCOMING] ?: true,
            notifyCurrent = prefs[Keys.NOTIFY_CURRENT] ?: true,
            notifyPast = prefs[Keys.NOTIFY_PAST] ?: true
        )
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.kalendaDataStore.edit { it[Keys.THEME_MODE] = mode.name }
    }

    suspend fun setDefaultReminderMinutes(minutes: Int) {
        context.kalendaDataStore.edit { it[Keys.DEFAULT_REMINDER] = minutes }
    }

    suspend fun setViewMode(mode: CalendarViewMode) {
        context.kalendaDataStore.edit { it[Keys.VIEW_MODE] = mode.name }
    }

    suspend fun setGoogleSyncEmail(email: String) {
        context.kalendaDataStore.edit { it[Keys.GOOGLE_SYNC_EMAIL] = email.trim() }
    }

    suspend fun setLastGoogleSyncMillis(timestamp: Long) {
        context.kalendaDataStore.edit { it[Keys.LAST_GOOGLE_SYNC] = timestamp }
    }

    suspend fun setBiometricPinSecurity(enabled: Boolean, pinSalt: String? = null, pinHash: String? = null) {
        context.kalendaDataStore.edit { prefs ->
            prefs[Keys.BIOMETRIC_APP_LOCK] = enabled
            if (pinSalt != null && pinHash != null) {
                prefs[Keys.PIN_SALT] = pinSalt
                prefs[Keys.PIN_HASH] = pinHash
            }
        }
    }

    suspend fun setNotificationPhases(upcoming: Boolean, current: Boolean, past: Boolean) {
        context.kalendaDataStore.edit { prefs ->
            prefs[Keys.NOTIFY_UPCOMING] = upcoming
            prefs[Keys.NOTIFY_CURRENT] = current
            prefs[Keys.NOTIFY_PAST] = past
        }
    }
}
