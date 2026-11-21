package com.sarjdefteri.app.reminders

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.sarjdefteri.app.SarjDefteriApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Kullanıcının izin verdiği "Bildirim erişimi" ile çalışır. Seçilmemiş uygulamaların bildirimleri
 * ChargeNotificationHandler içinde paket adına bakılarak hemen atlanır; içerik saklanmaz veya gönderilmez.
 */
class ChargeNotificationListener : NotificationListenerService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (sbn.packageName == packageName) return
        val extras = sbn.notification?.extras ?: return
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()
        val text = (extras.getCharSequence(Notification.EXTRA_BIG_TEXT) ?: extras.getCharSequence(Notification.EXTRA_TEXT))?.toString()
        val pkg = sbn.packageName
        val posted = sbn.postTime
        scope.launch {
            val app = applicationContext as SarjDefteriApp
            if (pkg !in app.container.settings.current().watchedApps) return@launch
            val label = runCatching { packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0)).toString() }
                .getOrDefault(pkg)
            app.container.chargeNotificationHandler.handle(pkg, label, title, text, posted)
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
