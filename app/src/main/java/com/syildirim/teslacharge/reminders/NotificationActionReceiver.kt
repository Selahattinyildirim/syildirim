package com.syildirim.teslacharge.reminders

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class NotificationActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getIntExtra(EXTRA_NOTIFICATION_ID, 0)
        when (intent.action) {
            ACTION_REMIND_1H -> ReminderScheduler.scheduleRemindLater(context, 60)
            ACTION_REMIND_30M -> ReminderScheduler.scheduleRemindLater(context, 30)
            ACTION_DISMISS -> Unit
        }
        if (id != 0) Notifications.cancel(context, id)
    }

    companion object {
        const val EXTRA_NOTIFICATION_ID = "notification_id"
        const val ACTION_REMIND_1H = "com.syildirim.teslacharge.REMIND_1H"
        const val ACTION_REMIND_30M = "com.syildirim.teslacharge.REMIND_30M"
        const val ACTION_DISMISS = "com.syildirim.teslacharge.DISMISS"
    }
}
