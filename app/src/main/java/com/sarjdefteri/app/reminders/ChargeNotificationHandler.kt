package com.sarjdefteri.app.reminders

import android.content.Context
import androidx.core.app.NotificationCompat
import com.sarjdefteri.app.data.settings.SettingsRepository
import com.sarjdefteri.app.ui.Fmt
import com.sarjdefteri.core.ChargeNotificationParser
import com.sarjdefteri.core.ChargeNotificationParser.EventType
import com.sarjdefteri.core.TextNorm

/**
 * Şarj operatörü uygulamalarının bildirimlerini değerlendirir. Yalnızca kullanıcının seçtiği uygulamalar işlenir;
 * diğer bildirimlerin içeriğine bakılmaz. Aynı şarj oturumu için tek "kaydet" bildirimi gösterilir (sonraki ödeme
 * bildirimi aynı öneriyi günceller).
 */
class ChargeNotificationHandler(
    private val context: Context,
    private val settings: SettingsRepository,
) {
    sealed interface Outcome {
        data object IgnoredApp : Outcome
        data object Disabled : Outcome
        data object NotChargeEvent : Outcome
        data object Started : Outcome
        data object Duplicate : Outcome
        data class Completed(val prefill: EntryPrefill) : Outcome
    }

    private val prefs = context.getSharedPreferences("operator_sessions", Context.MODE_PRIVATE)

    suspend fun handle(pkg: String, appLabel: String, title: String?, text: String?, postedAt: Long): Outcome {
        val s = settings.current()
        if (pkg !in s.watchedApps) return Outcome.IgnoredApp
        if (!s.isPro || !s.remindersEnabled || !s.operatorNotifications) return Outcome.Disabled
        val ev = ChargeNotificationParser.parse(title, text)
        return when (ev.type) {
            EventType.UNKNOWN -> Outcome.NotChargeEvent
            EventType.STARTED -> onStarted(pkg, appLabel, postedAt)
            EventType.COMPLETED, EventType.PAYMENT -> onCompleted(pkg, appLabel, ev, postedAt)
        }
    }

    private fun onStarted(pkg: String, label: String, at: Long): Outcome {
        val last = prefs.getLong("start_$pkg", 0L)
        if (at - last in 0 until SESSION_MS) return Outcome.Duplicate // canlı güncellenen "şarj ediliyor" bildirimleri
        prefs.edit().putLong("start_$pkg", at).remove("done_$pkg").apply()
        Notifications.post(
            context, idFor(pkg, started = true),
            Notifications.builder(context, Notifications.CH_REMINDERS, "$label: şarj başladı",
                "Bitince operatörün bildirimini yakalayıp kaydı hazırlayacağım.")
                .setContentIntent(Notifications.entryIntent(context, idFor(pkg, true), EntryPrefill(startedAt = at, station = label)))
                .setTimeoutAfter(8 * 3600_000L)
                .setPriority(NotificationCompat.PRIORITY_LOW),
        )
        return Outcome.Started
    }

    private fun onCompleted(pkg: String, label: String, ev: ChargeNotificationParser.Event, at: Long): Outcome {
        val doneAt = prefs.getLong("done_$pkg", 0L)
        val sameSession = at - doneAt in 0 until SESSION_MS
        val prevEnergy = if (sameSession) prefs.getString("done_kwh_$pkg", null)?.toDoubleOrNull() else null
        val prevAmount = if (sameSession) prefs.getString("done_amt_$pkg", null)?.toDoubleOrNull() else null
        if (sameSession && (ev.energyKwh == null || ev.energyKwh == prevEnergy) && (ev.amount == null || ev.amount == prevAmount)) {
            return Outcome.Duplicate
        }
        val startAt = prefs.getLong("start_$pkg", 0L).takeIf { it > 0 && at - it in 0..MAX_CHARGE_MS }
        val prefill = EntryPrefill(
            startedAt = startAt ?: at, endedAt = at,
            energyKwh = ev.energyKwh ?: prevEnergy, totalPaid = ev.amount ?: prevAmount,
            batteryAfter = ev.batteryPercent, station = label,
        )
        prefs.edit().putLong("done_$pkg", if (sameSession) doneAt else at)
            .putString("done_kwh_$pkg", prefill.energyKwh?.toString()).putString("done_amt_$pkg", prefill.totalPaid?.toString()).apply()

        val details = listOfNotNull(prefill.energyKwh?.let { Fmt.kwh(it) }, prefill.totalPaid?.let { Fmt.tl(it) })
        val text = if (details.isEmpty()) "$label · tutarı ve kWh'i girip kaydı tamamla." else "$label · " + details.joinToString(" · ")
        Notifications.cancel(context, idFor(pkg, started = true))
        Notifications.post(
            context, idFor(pkg, started = false),
            Notifications.builder(context, Notifications.CH_CHARGING, "Şarj Defteri'ne kaydet", text)
                .setContentIntent(Notifications.entryIntent(context, idFor(pkg, false), prefill))
                .addAction(0, "Kaydet", Notifications.entryIntent(context, idFor(pkg, false) + 1, prefill))
                .setPriority(NotificationCompat.PRIORITY_HIGH),
        )
        return Outcome.Completed(prefill)
    }

    private fun idFor(pkg: String, started: Boolean) = 20_000 + (pkg.hashCode() and 0xFFF) * 2 + if (started) 0 else 1

    companion object {
        const val SESSION_MS = 30 * 60_000L
        const val MAX_CHARGE_MS = 12 * 3600_000L

        private val OPERATOR_HINTS = listOf("zes", "trugo", "esarj", "e-sarj", "voltrun", "astor", "sharz", "tesla", "beefull",
            "wat", "otowatt", "powersarj", "aksa", "enyakit", "ionity", "togg", "trumore", "sarj", "charge", "charging")

        /** Uygulama adı bir şarj operatörüne benziyor mu (seçim listesinde öne çıkarmak için). */
        fun looksLikeOperator(label: String): Boolean {
            val n = TextNorm.normalize(label)
            return OPERATOR_HINTS.any { n.contains(it) }
        }
    }
}
