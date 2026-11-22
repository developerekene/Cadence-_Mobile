package com.devekene.kalenda.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.devekene.kalenda.KalendaApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val app = context.applicationContext as? KalendaApplication ?: return
        val pendingResult = goAsync()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val prefs = app.container.preferencesRepository.preferencesFlow.first()
                app.container.eventRepository.rescheduleAllLifecycleAlarms(
                    notifyUpcoming = prefs.notifyUpcoming,
                    notifyCurrent = prefs.notifyCurrent,
                    notifyPast = prefs.notifyPast
                )
            } finally {
                pendingResult.finish()
            }
        }
    }
}
