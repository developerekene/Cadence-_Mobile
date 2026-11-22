package com.devekene.kalenda.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.devekene.kalenda.KalendaApplication
import com.devekene.kalenda.domain.model.EventLifecyclePhase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val eventId = intent.getStringExtra(ReminderScheduler.EXTRA_EVENT_ID) ?: return
        val phaseName = intent.getStringExtra(ReminderScheduler.EXTRA_PHASE) ?: EventLifecyclePhase.UPCOMING.name
        val phase = EventLifecyclePhase.entries.firstOrNull { it.name == phaseName } ?: EventLifecyclePhase.UPCOMING
        val app = context.applicationContext as? KalendaApplication ?: return
        val pendingResult = goAsync()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val prefs = app.container.preferencesRepository.preferencesFlow.first()
                val enabled = when (phase) {
                    EventLifecyclePhase.UPCOMING -> prefs.notifyUpcoming
                    EventLifecyclePhase.CURRENT -> prefs.notifyCurrent
                    EventLifecyclePhase.PAST -> prefs.notifyPast
                }
                if (enabled) {
                    val event = app.container.eventRepository.getEventById(eventId)
                    if (event != null) {
                        app.container.reminderScheduler.showEventLifecycleNotification(event, phase)
                    }
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
