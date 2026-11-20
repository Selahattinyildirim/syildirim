package com.syildirim.teslacharge

import android.app.Application
import androidx.work.Configuration
import com.syildirim.teslacharge.reminders.Notifications
import com.syildirim.teslacharge.reminders.ReminderScheduler

class TeslaChargeApp : Application(), Configuration.Provider {
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
