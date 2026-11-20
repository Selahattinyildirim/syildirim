package com.sarjdefteri.app.reminders

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.sarjdefteri.app.MainActivity
import com.sarjdefteri.app.R

object Notifications {
    const val CH_REMINDERS = "reminders"
    const val CH_CHARGING = "charging"

    const val ID_CONNECT = 1001
    const val ID_DISCONNECT = 1002
    const val ID_CHARGING_STARTED = 1003
    const val ID_CHARGE_COMPLETE = 1004
    const val ID_NAVIGATION = 1005
    const val ID_INCOMPLETE = 1006
    const val ID_REMIND_LATER = 1007
    const val ID_CAMPAIGNS = 1008

    fun createChannels(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CH_REMINDERS, "Hatırlatmalar", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Kilometre ve şarj kaydı hatırlatmaları"
            }
        )
        nm.createNotificationChannel(
            NotificationChannel(CH_CHARGING, "Şarj durumu", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Şarj başladı / tamamlandı bildirimleri (Tesla Fleet API)"
            }
        )
    }

    fun canPost(context: Context): Boolean =
        (Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) &&
            NotificationManagerCompat.from(context).areNotificationsEnabled()

    private fun openAppIntent(context: Context, requestCode: Int, configure: Intent.() -> Unit): PendingIntent {
        val i = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            configure()
        }
        return PendingIntent.getActivity(context, requestCode, i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    fun entryIntent(context: Context, requestCode: Int, prefill: EntryPrefill) =
        openAppIntent(context, requestCode) { prefill.writeTo(this) }

    fun odometerIntent(context: Context, requestCode: Int) =
        openAppIntent(context, requestCode) { putExtra(EntryPrefill.EXTRA_ROUTE, EntryPrefill.ROUTE_ODOMETER) }

    fun campaignsIntent(context: Context, requestCode: Int) =
        openAppIntent(context, requestCode) { putExtra(EntryPrefill.EXTRA_ROUTE, EntryPrefill.ROUTE_CAMPAIGNS) }

    fun homeIntent(context: Context, requestCode: Int) = openAppIntent(context, requestCode) {}

    fun actionIntent(context: Context, action: String, notificationId: Int): PendingIntent {
        val i = Intent(context, NotificationActionReceiver::class.java).apply {
            this.action = action
            putExtra(NotificationActionReceiver.EXTRA_NOTIFICATION_ID, notificationId)
        }
        return PendingIntent.getBroadcast(context, action.hashCode() + notificationId, i,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    fun builder(context: Context, channel: String, title: String, text: String): NotificationCompat.Builder =
        NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_stat_bolt)
            .setColor(0xFFE31937.toInt())
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)

    /** Aynı id ile gönderilen bildirim öncekini günceller (yığılma olmaz). */
    fun post(context: Context, id: Int, builder: NotificationCompat.Builder) {
        if (!canPost(context)) return
        try {
            NotificationManagerCompat.from(context).notify(id, builder.build())
        } catch (_: SecurityException) { }
    }

    fun cancel(context: Context, id: Int) = NotificationManagerCompat.from(context).cancel(id)
}
