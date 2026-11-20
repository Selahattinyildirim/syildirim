package com.syildirim.teslacharge.reminders

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.syildirim.teslacharge.TeslaChargeApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * ACL_CONNECTED / ACL_DISCONNECTED: Android'in manifestte dinlenmesine izin verdiği istisna yayınlardır,
 * uygulama kapalıyken de çalışır. Araç multimedya (klasik Bluetooth) bağlantısı güvenilir sinyaldir.
 * Tesla uygulamasının telefon-anahtarı BLE oturumu başka bir uygulamaya ait olduğundan doğrudan
 * okunamaz; yalnızca sistem genelinde bir ACL olayı üretirse ve ad deseni eşleşirse (deneysel) yakalanır.
 */
class BluetoothEventReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action != BluetoothDevice.ACTION_ACL_CONNECTED && action != BluetoothDevice.ACTION_ACL_DISCONNECTED) return
        val device: BluetoothDevice = (if (Build.VERSION.SDK_INT >= 33)
            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
        else @Suppress("DEPRECATION") intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)) ?: return

        val address = runCatching { device.address }.getOrNull() ?: return
        val name = safeName(context, device)
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            try {
                val app = context.applicationContext as TeslaChargeApp
                app.container.reminderEngine.onBluetoothEvent(
                    connected = action == BluetoothDevice.ACTION_ACL_CONNECTED, address = address, name = name,
                )
            } finally {
                pending.finish()
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun safeName(context: Context, device: BluetoothDevice): String? {
        if (Build.VERSION.SDK_INT >= 31 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED
        ) return null
        return runCatching { device.name }.getOrNull()
    }
}
