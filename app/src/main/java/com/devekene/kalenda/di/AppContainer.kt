package com.devekene.kalenda.di

import android.content.Context
import com.devekene.kalenda.data.local.AppDatabase
import com.devekene.kalenda.data.repository.EventRepository
import com.devekene.kalenda.data.repository.PreferencesRepository
import com.devekene.kalenda.data.sync.GoogleCalendarSyncManager
import com.devekene.kalenda.domain.search.NaturalLanguageSearchEngine
import com.devekene.kalenda.notifications.ReminderScheduler
import com.devekene.kalenda.security.CryptoManager

class AppContainer(context: Context) {
    val cryptoManager = CryptoManager()
    val reminderScheduler = ReminderScheduler(context.applicationContext)
    val googleCalendarSyncManager = GoogleCalendarSyncManager(context.applicationContext)
    private val database = AppDatabase.getInstance(context.applicationContext)
    val eventRepository = EventRepository(
        eventDao = database.eventDao(),
        cryptoManager = cryptoManager,
        reminderScheduler = reminderScheduler,
        googleSyncManager = googleCalendarSyncManager
    )
    val preferencesRepository = PreferencesRepository(context.applicationContext)
    val searchEngine = NaturalLanguageSearchEngine()
}
