package com.example

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.example.notifications.ReminderScheduler
import com.example.ui.CadenceApp
import com.example.ui.CalendarViewModel

class MainActivity : ComponentActivity() {

    private val viewModel: CalendarViewModel by viewModels {
        val container = (application as CadenceApplication).container
        CalendarViewModel.Factory(
            eventRepository = container.eventRepository,
            preferencesRepository = container.preferencesRepository,
            searchEngine = container.searchEngine,
            cryptoManager = container.cryptoManager,
            reminderScheduler = container.reminderScheduler
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val initialEventId = intent?.getStringExtra(ReminderScheduler.EXTRA_EVENT_ID)
        setContent {
            CadenceApp(
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
