package com.sarjdefteri.app.reminders

import android.content.Context
import androidx.core.app.NotificationCompat
import com.sarjdefteri.core.TeslaBleName
import com.sarjdefteri.app.data.ChargeRepository
import com.sarjdefteri.app.data.db.OdometerSource
import com.sarjdefteri.app.data.settings.AppSettings
import com.sarjdefteri.app.data.settings.SettingsRepository
import com.sarjdefteri.app.tesla.TeslaRepository
import com.sarjdefteri.app.tesla.TeslaResult

/** Bluetooth olaylarını değerlendirip gerektiğinde (ve en fazla bir kez) bildirim üretir. */
class ReminderEngine(
    private val context: Context,
    private val settings: SettingsRepository,
    private val charges: ChargeRepository,
    private val tesla: TeslaRepository,
    private val state: ReminderState,
) {
    fun matches(s: AppSettings, address: String, name: String?): Boolean {
        if (address in s.bluetoothDeviceAddresses) return true
        if (name != null && name in s.bluetoothDeviceNames) return true
        if (s.detectTeslaBleNames && TeslaBleName.looksLikeTesla(name)) return true
        // Hiç cihaz seçilmemişse varsayılan araç adlarını kabul et
        if (s.bluetoothDeviceAddresses.isEmpty() && name != null) {
            val n = name.lowercase()
            return n.contains("tesla") || n.contains("model y")
        }
        return false
    }

    suspend fun onBluetoothEvent(connected: Boolean, address: String, name: String?) {
        val s = settings.current()
        if (!s.remindersEnabled || !matches(s, address, name)) return
        if (connected) {
            if (state.onConnected(address)) onVehicleConnected(s)
        } else {
            if (state.onDisconnected(address)) {
                ReminderScheduler.cancelNavigationWatch(context)
                ReminderScheduler.scheduleDisconnectFollowUp(context)
            }
        }
    }

    private suspend fun onVehicleConnected(s: AppSettings) {
        Notifications.cancel(context, Notifications.ID_DISCONNECT)
        ReminderScheduler.cancelDisconnectFollowUp(context)
        val apiReady = tesla.isReady()
        if (apiReady && s.navigationWatch) {
            state.navPolls = 0
            ReminderScheduler.scheduleNavigationWatch(context, delayMinutes = 2)
        }
        if (!s.remindOnConnect) return

        if (apiReady) {
            // Araç sürüş sırasında zaten uyanık: uyandırmadan km'yi otomatik al, bildirim gerekmez.
            when (val r = tesla.snapshotIfOnline()) {
                is TeslaResult.Ok -> {
                    r.value.odometerKm?.let {
                        charges.addOdometerReading(it, r.value.batteryLevel, OdometerSource.TESLA_API)
                        return
                    }
                }
                else -> Unit
            }
        }
        // Yakın zamanda km girildiyse tekrar sorma.
        val lastReading = charges.latestOdometerReading()
        if (lastReading != null && System.currentTimeMillis() - lastReading.recordedAt < CONNECT_COOLDOWN_MS) return
        if (!state.tryAcquire("connect", CONNECT_COOLDOWN_MS)) return
        val since = sinceLastChargeText()
        Notifications.post(
            context, Notifications.ID_CONNECT,
            Notifications.builder(context, Notifications.CH_REMINDERS, "Tesla'ya mı bindin?",
                "Son şarjından bu yana yaptığın kilometreyi kaydedelim.$since")
                .setContentIntent(Notifications.odometerIntent(context, 11))
                .addAction(0, "Km gir", Notifications.odometerIntent(context, 12))
                .addAction(0, "Şimdi değil", Notifications.actionIntent(context, NotificationActionReceiver.ACTION_DISMISS, Notifications.ID_CONNECT))
                .setTimeoutAfter(30 * 60_000L)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT),
        )
    }

    private suspend fun sinceLastChargeText(): String {
        val last = charges.latestSession() ?: return ""
        val days = ((System.currentTimeMillis() - last.startedAt) / 86_400_000L).toInt()
        return if (days <= 0) " (Son şarj: bugün)" else " (Son şarj: $days gün önce)"
    }

    /** Araçtan ayrıldıktan birkaç dakika sonra çağrılır (bağlantı dalgalanmalarını elemek için). */
    suspend fun onDisconnectSettled() {
        if (state.isConnected) return
        val s = settings.current()
        if (!s.remindersEnabled) return
        if (tesla.isReady() && s.chargeDetectionViaApi) {
            when (val r = tesla.snapshotIfOnline()) {
                is TeslaResult.Ok -> {
                    val snap = r.value
                    snap.odometerKm?.let { charges.addOdometerReading(it, snap.batteryLevel, OdometerSource.TESLA_API) }
                    if (snap.isCharging) {
                        ReminderScheduler.startChargeMonitor(context, state, snap, notifyStart = true)
                    }
                    return // API bilgisi var: şarj yoksa gereksiz bildirim gönderme
                }
                TeslaResult.VehicleAsleep -> return
                else -> Unit // hata → manuel hatırlatmaya düş
            }
        }
        if (!s.remindOnDisconnect) return
        if (!state.tryAcquire("disconnect", DISCONNECT_COOLDOWN_MS)) return
        Notifications.post(
            context, Notifications.ID_DISCONNECT,
            Notifications.builder(context, Notifications.CH_REMINDERS, "Araçtan ayrıldın",
                "Şarja bağladıysan kaydı şimdi başlatabilir ya da bitince hatırlatma kurabilirsin.")
                .setContentIntent(Notifications.entryIntent(context, 21, EntryPrefill(startedAt = System.currentTimeMillis())))
                .addAction(0, "Şarj kaydı", Notifications.entryIntent(context, 22, EntryPrefill(startedAt = System.currentTimeMillis())))
                .addAction(0, "1 saat sonra", Notifications.actionIntent(context, NotificationActionReceiver.ACTION_REMIND_1H, Notifications.ID_DISCONNECT))
                .addAction(0, "Şarj etmiyorum", Notifications.actionIntent(context, NotificationActionReceiver.ACTION_DISMISS, Notifications.ID_DISCONNECT))
                .setTimeoutAfter(2 * 3600_000L)
                .setPriority(NotificationCompat.PRIORITY_LOW),
        )
    }

    companion object {
        const val CONNECT_COOLDOWN_MS = 3 * 3600_000L
        const val DISCONNECT_COOLDOWN_MS = 4 * 3600_000L

        val CHARGER_KEYWORDS = listOf(
            "supercharger", "süper şarj", "şarj", "sarj", "charging", "charger", "charge point",
            "zes", "eşarj", "esarj", "trugo", "voltrun", "astor", "sharz", "beefull", "otowatt", "wat mobilite",
            "enyakıt", "aksa şarj", "g-charge", "powersarj", "ionity", "fastned",
        )

        fun looksLikeCharger(destination: String?): Boolean {
            val d = destination?.lowercase() ?: return false
            return CHARGER_KEYWORDS.any { d.contains(it) }
        }
    }
}
