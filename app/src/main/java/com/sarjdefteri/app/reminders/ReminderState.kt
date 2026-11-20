package com.sarjdefteri.app.reminders

import android.content.Context

/**
 * Hatırlatma durumunu ve tekrar engelleme (cooldown) zaman damgalarını tutar.
 * Aynı olay için tekrar tekrar bildirim gönderilmesini engeller.
 */
class ReminderState(context: Context) {
    private val p = context.getSharedPreferences("reminder_state", Context.MODE_PRIVATE)

    /** [key] olayı için son bildirimden bu yana [cooldownMs] geçtiyse true döner ve zamanı işaretler. */
    @Synchronized
    fun tryAcquire(key: String, cooldownMs: Long, now: Long = System.currentTimeMillis()): Boolean {
        val last = p.getLong("cd_$key", 0L)
        if (now - last < cooldownMs) return false
        p.edit().putLong("cd_$key", now).apply()
        return true
    }

    fun markNotified(key: String, now: Long = System.currentTimeMillis()) = p.edit().putLong("cd_$key", now).apply()

    // --- araç bağlantı durumu ---
    val connectedDevices: Set<String> get() = p.getStringSet("connected", emptySet()) ?: emptySet()
    val isConnected: Boolean get() = connectedDevices.isNotEmpty()
    val lastConnectAt: Long get() = p.getLong("last_connect", 0L)
    val lastDisconnectAt: Long get() = p.getLong("last_disconnect", 0L)

    @Synchronized
    fun onConnected(address: String, now: Long = System.currentTimeMillis()): Boolean {
        val wasConnected = isConnected
        p.edit().putStringSet("connected", connectedDevices + address).putLong("last_connect", now).apply()
        return !wasConnected
    }

    /** @return true ise artık araca hiçbir bağlantı kalmadı. */
    @Synchronized
    fun onDisconnected(address: String, now: Long = System.currentTimeMillis()): Boolean {
        val remaining = connectedDevices - address
        p.edit().putStringSet("connected", remaining).putLong("last_disconnect", now).apply()
        return remaining.isEmpty()
    }

    // --- şarj takibi (Fleet API) ---
    var monitorStartedAt: Long
        get() = p.getLong("mon_start", 0L)
        set(v) = p.edit().putLong("mon_start", v).apply()
    var monitorStartBattery: Double?
        get() = p.getString("mon_bb", null)?.toDoubleOrNull()
        set(v) = p.edit().putString("mon_bb", v?.toString()).apply()
    var monitorOdometer: Double?
        get() = p.getString("mon_odo", null)?.toDoubleOrNull()
        set(v) = p.edit().putString("mon_odo", v?.toString()).apply()
    var monitorLastEnergy: Double?
        get() = p.getString("mon_energy", null)?.toDoubleOrNull()
        set(v) = p.edit().putString("mon_energy", v?.toString()).apply()
    var monitorLastBattery: Double?
        get() = p.getString("mon_ba", null)?.toDoubleOrNull()
        set(v) = p.edit().putString("mon_ba", v?.toString()).apply()
    var monitorSawCharging: Boolean
        get() = p.getBoolean("mon_saw", false)
        set(v) = p.edit().putBoolean("mon_saw", v).apply()
    var monitorPolls: Int
        get() = p.getInt("mon_polls", 0)
        set(v) = p.edit().putInt("mon_polls", v).apply()

    fun clearMonitor() = p.edit().remove("mon_start").remove("mon_bb").remove("mon_odo").remove("mon_energy")
        .remove("mon_ba").remove("mon_saw").remove("mon_polls").apply()

    var lastNavDestination: String?
        get() = p.getString("nav_dest", null)
        set(v) = p.edit().putString("nav_dest", v).apply()
    var navPolls: Int
        get() = p.getInt("nav_polls", 0)
        set(v) = p.edit().putInt("nav_polls", v).apply()
}
