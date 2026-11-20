package com.sarjdefteri.app.reminders

import android.content.Intent

/** Bildirimden hızlı kayıt ekranına taşınan, otomatik doldurulabilen alanlar. */
data class EntryPrefill(
    val startedAt: Long? = null,
    val endedAt: Long? = null,
    val energyKwh: Double? = null,
    val batteryBefore: Double? = null,
    val batteryAfter: Double? = null,
    val odometer: Double? = null,
    val station: String? = null,
    val fromApi: Boolean = false,
) {
    fun writeTo(i: Intent): Intent = i.apply {
        putExtra(EXTRA_ROUTE, ROUTE_ENTRY)
        startedAt?.let { putExtra("p_start", it) }
        endedAt?.let { putExtra("p_end", it) }
        energyKwh?.let { putExtra("p_energy", it) }
        batteryBefore?.let { putExtra("p_bb", it) }
        batteryAfter?.let { putExtra("p_ba", it) }
        odometer?.let { putExtra("p_odo", it) }
        station?.let { putExtra("p_station", it) }
        putExtra("p_api", fromApi)
    }

    companion object {
        const val EXTRA_ROUTE = "route"
        const val ROUTE_ENTRY = "entry"
        const val ROUTE_ODOMETER = "odometer"

        fun from(i: Intent): EntryPrefill? {
            if (i.getStringExtra(EXTRA_ROUTE) != ROUTE_ENTRY) return null
            fun d(k: String) = if (i.hasExtra(k)) i.getDoubleExtra(k, 0.0) else null
            fun l(k: String) = if (i.hasExtra(k)) i.getLongExtra(k, 0L) else null
            return EntryPrefill(l("p_start"), l("p_end"), d("p_energy"), d("p_bb"), d("p_ba"), d("p_odo"),
                i.getStringExtra("p_station"), i.getBooleanExtra("p_api", false))
        }
    }
}
