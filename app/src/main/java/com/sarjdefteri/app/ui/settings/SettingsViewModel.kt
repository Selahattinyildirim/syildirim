package com.sarjdefteri.app.ui.settings

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sarjdefteri.app.AppContainer
import com.sarjdefteri.core.TeslaBleName
import com.sarjdefteri.app.data.settings.AppSettings
import com.sarjdefteri.app.reminders.Notifications
import com.sarjdefteri.app.reminders.ReminderScheduler
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class BtDevice(val address: String, val name: String, val looksLikeTesla: Boolean)

class SettingsViewModel(private val c: AppContainer) : ViewModel() {
    val settings = c.settings.settings.stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())
    val bondedDevices = MutableStateFlow<List<BtDevice>>(emptyList())
    val teslaConnected = MutableStateFlow(c.tesla.hasTokens())

    fun hasBtPermission(ctx: Context) = Build.VERSION.SDK_INT < 31 ||
        ContextCompat.checkSelfPermission(ctx, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission")
    fun refreshDevices(ctx: Context) {
        teslaConnected.value = c.tesla.hasTokens()
        if (!hasBtPermission(ctx)) { bondedDevices.value = emptyList(); return }
        val adapter = ctx.getSystemService(BluetoothManager::class.java)?.adapter ?: return
        bondedDevices.value = runCatching {
            adapter.bondedDevices.map { d ->
                val n = d.name ?: d.address
                BtDevice(d.address, n, n.contains("tesla", true) || n.contains("model y", true) || TeslaBleName.looksLikeTesla(n))
            }.sortedByDescending { it.looksLikeTesla }
        }.getOrDefault(emptyList())
    }

    fun toggleDevice(d: BtDevice, selected: Boolean) = viewModelScope.launch { c.settings.toggleBluetoothDevice(d.address, d.name, selected) }
    fun setReminders(v: Boolean) = viewModelScope.launch {
        c.settings.setRemindersEnabled(v); if (!v) ReminderScheduler.cancelAll(c.appContext)
    }
    fun setConnect(v: Boolean) = viewModelScope.launch { c.settings.setRemindOnConnect(v) }
    fun setDisconnect(v: Boolean) = viewModelScope.launch { c.settings.setRemindOnDisconnect(v) }
    fun setChargeDetection(v: Boolean) = viewModelScope.launch { c.settings.setChargeDetection(v) }
    fun setNavWatch(v: Boolean) = viewModelScope.launch { c.settings.setNavigationWatch(v) }
    fun setIncomplete(v: Boolean) = viewModelScope.launch { c.settings.setIncompleteReminder(v) }
    fun setDetectBle(v: Boolean) = viewModelScope.launch { c.settings.setDetectTeslaBle(v) }
    fun setVehicleName(v: String) = viewModelScope.launch { c.settings.setVehicleName(v) }
    fun setProDebug(v: Boolean) = viewModelScope.launch { c.settings.setProDebugOverride(v) }
    fun setCapacity(v: Double) = viewModelScope.launch { c.settings.setBatteryCapacity(v.coerceIn(30.0, 120.0)) }

    fun sendTestNotification(ctx: Context) {
        Notifications.post(ctx, 9999, Notifications.builder(ctx, Notifications.CH_REMINDERS, "Tesla'ya mı bindin?",
            "Son şarjından bu yana yaptığın kilometreyi kaydedelim. (Test bildirimi)")
            .setContentIntent(Notifications.odometerIntent(ctx, 9999)))
    }
}
