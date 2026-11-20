package com.sarjdefteri.app.reminders

import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.sarjdefteri.app.SarjDefteriApp
import com.sarjdefteri.app.tesla.TeslaResult
import java.util.Locale

private val Context.container get() = (applicationContext as SarjDefteriApp).container

/** Araçtan ayrıldıktan ~3 dk sonra: hâlâ ayrıysa şarj kontrolü / hatırlatma. */
class DisconnectFollowUpWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        applicationContext.container.reminderEngine.onDisconnectSettled()
        return Result.success()
    }
}

/**
 * Fleet API ile şarj takibi. Şarj sırasında araç zaten uyanık olduğundan sorgu aracı uyandırmaz;
 * araç uykuya geçtiyse uyandırılmaz, takip sonlandırılır. En fazla [MAX_POLLS] sorgu yapılır.
 */
class ChargeMonitorWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        val c = applicationContext.container
        val state = c.reminderState
        if (!c.settings.current().remindersEnabled || state.monitorStartedAt == 0L) { state.clearMonitor(); return Result.success() }
        state.monitorPolls = state.monitorPolls + 1
        if (state.monitorPolls > MAX_POLLS) { finish(); return Result.success() }

        when (val r = c.tesla.snapshotIfOnline()) {
            is TeslaResult.Ok -> {
                val s = r.value
                when {
                    s.isCharging -> ReminderScheduler.startChargeMonitor(applicationContext, state, s, notifyStart = false)
                    state.monitorSawCharging && (s.isChargeFinished || !s.isPluggedIn) -> {
                        state.monitorLastEnergy = s.chargeEnergyAddedKwh ?: state.monitorLastEnergy
                        state.monitorLastBattery = s.batteryLevel ?: state.monitorLastBattery
                        state.monitorOdometer = s.odometerKm ?: state.monitorOdometer
                        finish()
                    }
                    else -> { state.clearMonitor() }
                }
            }
            TeslaResult.VehicleAsleep -> finish() // şarj bitti, araç uyudu
            is TeslaResult.Error -> if (state.monitorPolls < MAX_POLLS) ReminderScheduler.scheduleChargeMonitor(applicationContext, 20) else finish()
            TeslaResult.NotConfigured -> state.clearMonitor()
        }
        return Result.success()
    }

    private fun finish() {
        val c = applicationContext.container
        val state = c.reminderState
        if (state.monitorSawCharging && state.tryAcquire("charge_complete_${state.monitorStartedAt}", 24 * 3600_000L)) {
            val energy = state.monitorLastEnergy
            val text = buildString {
                append("Şarj tamamlandı")
                energy?.let { append(": +${String.format(Locale("tr", "TR"), "%.1f", it)} kWh") }
                state.monitorLastBattery?.let { append(", batarya %${it.toInt()}") }
                append(". Tutarı girip kaydı 20 saniyede tamamla.")
            }
            Notifications.cancel(applicationContext, Notifications.ID_CHARGING_STARTED)
            val prefill = EntryPrefill(
                startedAt = state.monitorStartedAt, endedAt = System.currentTimeMillis(), energyKwh = energy,
                batteryBefore = state.monitorStartBattery, batteryAfter = state.monitorLastBattery,
                odometer = state.monitorOdometer, fromApi = true,
            )
            Notifications.post(
                applicationContext, Notifications.ID_CHARGE_COMPLETE,
                Notifications.builder(applicationContext, Notifications.CH_CHARGING, "Şarj kaydını oluştur", text)
                    .setContentIntent(Notifications.entryIntent(applicationContext, 41, prefill))
                    .addAction(0, "Hızlı kayıt", Notifications.entryIntent(applicationContext, 42, prefill))
                    .setPriority(NotificationCompat.PRIORITY_HIGH),
            )
        }
        state.clearMonitor()
    }

    companion object { const val MAX_POLLS = 60 }
}

/** Sürüş sırasında navigasyon hedefi şarj istasyonu mu? (Fleet API, isteğe bağlı) */
class NavigationWatchWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        val c = applicationContext.container
        val state = c.reminderState
        val s = c.settings.current()
        if (!s.remindersEnabled || !s.navigationWatch || !state.isConnected) return Result.success()
        state.navPolls = state.navPolls + 1
        if (state.navPolls > MAX_POLLS) return Result.success()

        var nextDelay = 10L
        val r = c.tesla.snapshotIfOnline(includeLocation = true)
        if (r is TeslaResult.Ok) {
            val dest = r.value.activeRouteDestination
            val minutes = r.value.activeRouteMinutesToArrival
            if (dest != null && ReminderEngine.looksLikeCharger(dest) && minutes != null) {
                if (minutes <= 15 && state.lastNavDestination != dest &&
                    state.tryAcquire("nav_${dest.hashCode()}", 6 * 3600_000L)
                ) {
                    state.lastNavDestination = dest
                    Notifications.post(
                        applicationContext, Notifications.ID_NAVIGATION,
                        Notifications.builder(applicationContext, Notifications.CH_REMINDERS, "Varış noktasında şarj edecek misin?",
                            "$dest · ~${minutes.toInt()} dk. Kaydı şimdiden hazırlayabilirim.")
                            .addAction(0, "Evet, hazırla", Notifications.entryIntent(applicationContext, 51,
                                EntryPrefill(station = dest, odometer = r.value.odometerKm, batteryBefore = r.value.batteryLevel, fromApi = true)))
                            .addAction(0, "Hayır", Notifications.actionIntent(applicationContext, NotificationActionReceiver.ACTION_DISMISS, Notifications.ID_NAVIGATION))
                            .setTimeoutAfter(60 * 60_000L),
                    )
                } else if (minutes > 15) {
                    nextDelay = (minutes - 12).toLong().coerceIn(3, 15)
                }
            }
        }
        ReminderScheduler.scheduleNavigationWatch(applicationContext, nextDelay)
        return Result.success()
    }

    companion object { const val MAX_POLLS = 18 }
}

/** Kullanıcının "sonra hatırlat" dediği şarj bitiş hatırlatması. */
class RemindLaterWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        if (!applicationContext.container.settings.current().remindersEnabled) return Result.success()
        val prefill = EntryPrefill(startedAt = System.currentTimeMillis() - 60 * 60_000L)
        Notifications.post(
            applicationContext, Notifications.ID_REMIND_LATER,
            Notifications.builder(applicationContext, Notifications.CH_REMINDERS, "Şarj bitti mi?",
                "Şarj kaydını oluşturmayı unutma: kWh ve tutarı girmen yeterli.")
                .setContentIntent(Notifications.entryIntent(applicationContext, 61, prefill))
                .addAction(0, "Kaydet", Notifications.entryIntent(applicationContext, 62, prefill))
                .addAction(0, "30 dk sonra", Notifications.actionIntent(applicationContext, NotificationActionReceiver.ACTION_REMIND_30M, Notifications.ID_REMIND_LATER)),
        )
        return Result.success()
    }
}

/** Günlük: eksik bilgili kayıtlar varsa en fazla 3 günde bir hatırlat. */
class IncompleteRecordsWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        val c = applicationContext.container
        val s = c.settings.current()
        if (!s.remindersEnabled || !s.incompleteRecordReminder) return Result.success()
        val count = c.charges.countIncompleteBefore(System.currentTimeMillis() - 24 * 3600_000L)
        if (count > 0 && c.reminderState.tryAcquire("incomplete", 3 * 24 * 3600_000L)) {
            Notifications.post(
                applicationContext, Notifications.ID_INCOMPLETE,
                Notifications.builder(applicationContext, Notifications.CH_REMINDERS, "Eksik şarj kayıtları",
                    "$count kayıtta kWh, tutar veya kilometre eksik. Tamamlarsan tüketim hesapları doğru olur.")
                    .setContentIntent(Notifications.homeIntent(applicationContext, 71))
                    .setPriority(NotificationCompat.PRIORITY_LOW),
            )
        }
        return Result.success()
    }
}
