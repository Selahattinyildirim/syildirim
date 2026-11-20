package com.syildirim.teslacharge.reminders

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** WorkManager işleri yeniden başlatmada kendini korur; burada yalnızca bağlantı durumunu sıfırlarız. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            context.getSharedPreferences("reminder_state", Context.MODE_PRIVATE).edit().remove("connected").apply()
            ReminderScheduler.ensurePeriodic(context)
        }
    }
}
