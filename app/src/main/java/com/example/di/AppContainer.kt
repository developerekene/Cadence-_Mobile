package com.example.di

import android.content.Context
import com.example.data.local.AppDatabase
import com.example.data.repository.EventRepository
import com.example.data.repository.PreferencesRepository
import com.example.domain.search.NaturalLanguageSearchEngine
import com.example.notifications.ReminderScheduler
import com.example.security.CryptoManager

class AppContainer(context: Context) {
    val cryptoManager = CryptoManager()
    val reminderScheduler = ReminderScheduler(context.applicationContext)
    private val database = AppDatabase.getInstance(context.applicationContext)
    val eventRepository = EventRepository(
        eventDao = database.eventDao(),
        cryptoManager = cryptoManager,
        reminderScheduler = reminderScheduler
    )
    val preferencesRepository = PreferencesRepository(context.applicationContext)
    val searchEngine = NaturalLanguageSearchEngine()
}
