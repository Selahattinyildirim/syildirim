package com.sarjdefteri.app

import android.content.Context
import com.sarjdefteri.app.billing.BillingManager
import com.sarjdefteri.app.data.ChargeRepository
import com.sarjdefteri.app.data.db.AppDatabase
import com.sarjdefteri.app.data.settings.SettingsRepository
import com.sarjdefteri.app.export.Exporter
import com.sarjdefteri.app.reminders.EntryPrefill
import com.sarjdefteri.app.reminders.ReminderEngine
import com.sarjdefteri.app.reminders.ReminderState
import com.sarjdefteri.app.tesla.SecureStore
import com.sarjdefteri.app.tesla.TeslaRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow

/** Basit elle bağımlılık enjeksiyonu (Hilt gerektirmeden modüler yapı). */
class AppContainer(context: Context) {
    val appContext: Context = context.applicationContext
    val database: AppDatabase by lazy { AppDatabase.build(appContext) }
    val charges: ChargeRepository by lazy { ChargeRepository(database) }
    val settings: SettingsRepository by lazy { SettingsRepository(appContext) }
    val secureStore: SecureStore by lazy { SecureStore(appContext) }
    val tesla: TeslaRepository by lazy { TeslaRepository(settings, secureStore) }
    val reminderState: ReminderState by lazy { ReminderState(appContext) }
    val reminderEngine: ReminderEngine by lazy { ReminderEngine(appContext, settings, charges, tesla, reminderState) }
    val exporter: Exporter by lazy { Exporter() }
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    val billing: BillingManager by lazy { BillingManager(appContext, appScope, settings) }

    /** Bildirimden gelen hızlı kayıt ön-doldurma verisi (tek seferlik tüketilir). */
    val pendingPrefill = MutableStateFlow<EntryPrefill?>(null)
    /** OAuth yönlendirme adresi (deep link). */
    val pendingOAuthRedirect = MutableStateFlow<String?>(null)
    /** Dış tetikleyiciden (bildirim, deep link) açılacak ekran. */
    val pendingRoute = MutableStateFlow<String?>(null)
}
