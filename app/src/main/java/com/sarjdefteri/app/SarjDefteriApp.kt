package com.sarjdefteri.app

import android.app.Application
import androidx.work.Configuration
import com.sarjdefteri.app.reminders.Notifications
import com.sarjdefteri.app.reminders.ReminderScheduler

class SarjDefteriApp : Application(), Configuration.Provider {
    lateinit var container: AppContainer
        private set

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setMinimumLoggingLevel(android.util.Log.WARN).build()

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        Notifications.createChannels(this)
        ReminderScheduler.ensurePeriodic(this)
    }
}
