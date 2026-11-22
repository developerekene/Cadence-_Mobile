package com.devekene.kalenda

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.fragment.app.FragmentActivity
import com.devekene.kalenda.notifications.ReminderScheduler
import com.devekene.kalenda.ui.CalendarViewModel
import com.devekene.kalenda.ui.KalendaApp

class MainActivity : FragmentActivity() {

    private val viewModel: CalendarViewModel by viewModels {
        val container = (application as KalendaApplication).container
        CalendarViewModel.Factory(
            eventRepository = container.eventRepository,
            preferencesRepository = container.preferencesRepository,
            searchEngine = container.searchEngine,
            cryptoManager = container.cryptoManager,
            reminderScheduler = container.reminderScheduler,
            googleSyncManager = container.googleCalendarSyncManager
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val initialEventId = intent?.getStringExtra(ReminderScheduler.EXTRA_EVENT_ID)
        setContent {
            KalendaApp(
                viewModel = viewModel,
                initialEventId = initialEventId
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        val eventId = intent.getStringExtra(ReminderScheduler.EXTRA_EVENT_ID)
        if (!eventId.isNullOrBlank()) {
            viewModel.openEventById(eventId)
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.refreshPermissionState()
    }
}
