package com.devekene.kalenda

import android.app.Application
import com.devekene.kalenda.di.AppContainer

class KalendaApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        container.reminderScheduler.ensureNotificationChannel()
    }
}
