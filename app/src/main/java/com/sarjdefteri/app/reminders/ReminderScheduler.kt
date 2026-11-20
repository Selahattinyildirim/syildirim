package com.sarjdefteri.app.reminders

import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.sarjdefteri.app.tesla.VehicleSnapshot
import java.util.concurrent.TimeUnit

object ReminderScheduler {
    private const val DISCONNECT = "disconnect_followup"
    private const val MONITOR = "charge_monitor"
    private const val NAV = "navigation_watch"
    private const val REMIND_LATER = "remind_later"
    private const val INCOMPLETE = "incomplete_records"

    private val network = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

    fun ensurePeriodic(context: Context) {
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            INCOMPLETE, ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<IncompleteRecordsWorker>(1, TimeUnit.DAYS)
                .setInitialDelay(6, TimeUnit.HOURS).build(),
        )
    }

    fun scheduleDisconnectFollowUp(context: Context, delayMinutes: Long = 3) {
        WorkManager.getInstance(context).enqueueUniqueWork(
            DISCONNECT, ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<DisconnectFollowUpWorker>().setInitialDelay(delayMinutes, TimeUnit.MINUTES).build(),
        )
    }

    fun cancelDisconnectFollowUp(context: Context) = WorkManager.getInstance(context).cancelUniqueWork(DISCONNECT)

    fun scheduleChargeMonitor(context: Context, delayMinutes: Long) {
        WorkManager.getInstance(context).enqueueUniqueWork(
            MONITOR, ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<ChargeMonitorWorker>().setConstraints(network)
                .setInitialDelay(delayMinutes, TimeUnit.MINUTES).build(),
        )
    }

    fun cancelChargeMonitor(context: Context) = WorkManager.getInstance(context).cancelUniqueWork(MONITOR)

    /** Şarjın başladığı tespit edildi: durumu kaydet, bildir ve takibe başla. */
    fun startChargeMonitor(context: Context, state: ReminderState, snap: VehicleSnapshot, notifyStart: Boolean) {
        val now = System.currentTimeMillis()
        if (state.monitorStartedAt == 0L || now - state.monitorStartedAt > 24 * 3600_000L) {
            state.clearMonitor()
            state.monitorStartedAt = now
            // charge_energy_added şarj başından beri biriktiği için başlangıç yüzdesi tahmini:
            state.monitorStartBattery = snap.batteryLevel
            state.monitorOdometer = snap.odometerKm
        }
        state.monitorSawCharging = true
        state.monitorLastEnergy = snap.chargeEnergyAddedKwh
        state.monitorLastBattery = snap.batteryLevel
        if (notifyStart && state.tryAcquire("charging_started_${state.monitorStartedAt}", 24 * 3600_000L)) {
            val power = snap.chargerPowerKw?.let { " · $it kW" } ?: ""
            val eta = snap.minutesToFullCharge?.takeIf { it > 0 }?.let { " · ~${it} dk kaldı" } ?: ""
            Notifications.post(
                context, Notifications.ID_CHARGING_STARTED,
                Notifications.builder(context, Notifications.CH_CHARGING, "Şarj başladı",
                    "Batarya %${snap.batteryLevel?.toInt() ?: "?"}$power$eta. Bitince kaydı hazırlayacağım.")
                    .addAction(0, "Kaydı şimdi başlat", Notifications.entryIntent(context, 31,
                        EntryPrefill(startedAt = state.monitorStartedAt, batteryBefore = state.monitorStartBattery,
                            odometer = snap.odometerKm, station = snap.fastChargerBrand, fromApi = true)))
                    .setContentIntent(Notifications.homeIntent(context, 32))
                    .setPriority(NotificationCompat.PRIORITY_DEFAULT),
            )
        }
        val next = (snap.minutesToFullCharge ?: 20).coerceIn(10, 30).toLong()
        scheduleChargeMonitor(context, next)
    }

    fun scheduleNavigationWatch(context: Context, delayMinutes: Long = 10) {
        WorkManager.getInstance(context).enqueueUniqueWork(
            NAV, ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<NavigationWatchWorker>().setConstraints(network)
                .setInitialDelay(delayMinutes, TimeUnit.MINUTES).build(),
        )
    }

    fun cancelNavigationWatch(context: Context) = WorkManager.getInstance(context).cancelUniqueWork(NAV)

    fun scheduleRemindLater(context: Context, minutes: Long) {
        WorkManager.getInstance(context).enqueueUniqueWork(
            REMIND_LATER, ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<RemindLaterWorker>().setInitialDelay(minutes, TimeUnit.MINUTES).build(),
        )
    }

    fun cancelAll(context: Context) {
        val wm = WorkManager.getInstance(context)
        listOf(DISCONNECT, MONITOR, NAV, REMIND_LATER).forEach { wm.cancelUniqueWork(it) }
    }
}
