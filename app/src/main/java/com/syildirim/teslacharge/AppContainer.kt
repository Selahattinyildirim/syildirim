package com.syildirim.teslacharge

import android.content.Context
import com.syildirim.teslacharge.data.ChargeRepository
import com.syildirim.teslacharge.data.db.AppDatabase
import com.syildirim.teslacharge.data.settings.SettingsRepository
import com.syildirim.teslacharge.export.Exporter
import com.syildirim.teslacharge.reminders.EntryPrefill
import com.syildirim.teslacharge.reminders.ReminderEngine
import com.syildirim.teslacharge.reminders.ReminderState
import com.syildirim.teslacharge.tesla.SecureStore
import com.syildirim.teslacharge.tesla.TeslaRepository
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

    /** Bildirimden gelen hızlı kayıt ön-doldurma verisi (tek seferlik tüketilir). */
    val pendingPrefill = MutableStateFlow<EntryPrefill?>(null)
    /** OAuth yönlendirme adresi (deep link). */
    val pendingOAuthRedirect = MutableStateFlow<String?>(null)
    /** Dış tetikleyiciden (bildirim, deep link) açılacak ekran. */
    val pendingRoute = MutableStateFlow<String?>(null)
}
