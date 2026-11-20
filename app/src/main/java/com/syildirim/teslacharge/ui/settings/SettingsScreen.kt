package com.syildirim.teslacharge.ui.settings

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.syildirim.teslacharge.reminders.Notifications
import com.syildirim.teslacharge.ui.Fmt
import com.syildirim.teslacharge.ui.components.NumberField
import com.syildirim.teslacharge.ui.components.SectionCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(vm: SettingsViewModel, contentPadding: PaddingValues, onTesla: () -> Unit) {
    val ctx = LocalContext.current
    val s by vm.settings.collectAsState()
    val devices by vm.bondedDevices.collectAsState()
    val teslaConnected by vm.teslaConnected.collectAsState()
    var notifAllowed by remember { mutableStateOf(Notifications.canPost(ctx)) }
    var btAllowed by remember { mutableStateOf(vm.hasBtPermission(ctx)) }
    var capacityText by remember(s.batteryCapacityKwh) { mutableStateOf(Fmt.input(s.batteryCapacityKwh)) }

    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { notifAllowed = Notifications.canPost(ctx) }
    val btLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        btAllowed = vm.hasBtPermission(ctx); vm.refreshDevices(ctx)
    }
    LaunchedEffect(Unit) { vm.refreshDevices(ctx) }

    Column(Modifier.fillMaxSize().padding(top = contentPadding.calculateTopPadding())) {
        TopAppBar(title = { Text("Ayarlar") })
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)
                .padding(bottom = contentPadding.calculateBottomPadding() + 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SectionCard(title = "Hatırlatmalar", subtitle = "Aynı olay için tekrar bildirim gönderilmez") {
                ToggleRow("Tüm hatırlatmalar", null, s.remindersEnabled, vm::setReminders)
                if (s.remindersEnabled) {
                    HorizontalDivider()
                    ToggleRow("Araca binince km sor", "Bluetooth bağlantısında, en fazla 3 saatte bir", s.remindOnConnect, vm::setConnect)
                    ToggleRow("Araçtan ayrılınca hatırlat", "Bağlantı kesildikten 3 dk sonra, 4 saatte bir", s.remindOnDisconnect, vm::setDisconnect)
                    ToggleRow("Eksik kayıt hatırlatması", "Günlük kontrol, 3 günde bir bildirim", s.incompleteRecordReminder, vm::setIncomplete)
                    ToggleRow("Şarj algılama (Tesla API)", "Araçtan ayrılınca şarjda mı diye kontrol eder; aracı uyandırmaz",
                        s.chargeDetectionViaApi, vm::setChargeDetection, enabled = teslaConnected)
                    ToggleRow("Navigasyon hedefi kontrolü (Tesla API)", "Sürüşte 10 dk'da bir; hedef şarj istasyonuysa sorar",
                        s.navigationWatch, vm::setNavWatch, enabled = teslaConnected)
                }
                if (!notifAllowed) {
                    Button(onClick = {
                        if (Build.VERSION.SDK_INT >= 33) notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        else ctx.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, ctx.packageName))
                    }, modifier = Modifier.fillMaxWidth()) { Text("Bildirim iznini ver") }
                } else {
                    OutlinedButton(onClick = { vm.sendTestNotification(ctx) }, modifier = Modifier.fillMaxWidth()) { Text("Test bildirimi gönder") }
                }
            }

            SectionCard(title = "Araç Bluetooth'u", subtitle = "Telefonunun araçla eşleşmiş multimedya bağlantısını seç") {
                if (!btAllowed) {
                    Text("Eşleşmiş cihazları görmek ve bağlantı olaylarını almak için 'Yakındaki cihazlar' izni gerekir.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Button(onClick = { if (Build.VERSION.SDK_INT >= 31) btLauncher.launch(Manifest.permission.BLUETOOTH_CONNECT) },
                        modifier = Modifier.fillMaxWidth()) { Text("Bluetooth iznini ver") }
                } else if (devices.isEmpty()) {
                    Text("Eşleşmiş cihaz bulunamadı. Telefonu araca Bluetooth (telefon/medya) ile eşleştir.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    devices.forEach { d ->
                        val selected = d.address in s.bluetoothDeviceAddresses
                        Row(Modifier.fillMaxWidth().clickable { vm.toggleDevice(d, !selected) }, verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = selected, onCheckedChange = { vm.toggleDevice(d, it) })
                            Column(Modifier.weight(1f)) {
                                Text(d.name)
                                Text(d.address + if (d.looksLikeTesla) " · Tesla olabilir" else "", style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                    if (s.bluetoothDeviceAddresses.isEmpty()) Text("Seçim yapılmazsa adı 'Tesla' veya 'Model Y' içeren cihazlar kullanılır.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                HorizontalDivider()
                ToggleRow("Telefon anahtarı BLE adını da algıla (deneysel)",
                    "Tesla uygulamasının BLE oturumuna erişilemez; sistem olayı üretilirse ad deseniyle eşleştirilir", s.detectTeslaBleNames, vm::setDetectBle)
            }

            SectionCard(title = "Tesla Fleet API", subtitle = if (teslaConnected) "Bağlı: ${s.teslaVehicleName.ifEmpty { "araç seçilmedi" }}" else "Bağlı değil — manuel giriş tam çalışır") {
                Row(Modifier.fillMaxWidth().clickable(onClick = onTesla), verticalAlignment = Alignment.CenterVertically) {
                    Text("Otomatik km, batarya ve şarj verisi", modifier = Modifier.weight(1f))
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null)
                }
            }

            SectionCard(title = "Araç", subtitle = "Batarya bazlı tüketim hesabı için") {
                NumberField(capacityText, { capacityText = it; Fmt.parse(it)?.let(vm::setCapacity) }, "Kullanılabilir batarya kapasitesi",
                    Modifier.fillMaxWidth(), suffix = "kWh", supporting = "Model Y Juniper Long Range ≈ 75 kWh, Standart (RWD) ≈ 60 kWh")
            }

            SectionCard(title = "Gizlilik") {
                Text("Tüm şarj verileri yalnızca bu telefondaki yerel veritabanında tutulur; hesap, sunucu veya analitik yoktur. " +
                    "Tesla tokenları Android Keystore anahtarıyla şifrelenir ve yedeklere dahil edilmez. " +
                    "Ağ erişimi yalnızca Tesla Fleet API bağlandığında, Tesla sunucularına yapılır.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedButton(onClick = {
                    ctx.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", ctx.packageName, null)))
                }, modifier = Modifier.fillMaxWidth()) { Text("Uygulama izinleri") }
            }
        }
    }
}

@Composable
private fun ToggleRow(title: String, subtitle: String?, checked: Boolean, onChange: (Boolean) -> Unit, enabled: Boolean = true) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end = 8.dp)) {
            Text(title, color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
            subtitle?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        Switch(checked = checked && enabled, onCheckedChange = onChange, enabled = enabled)
    }
}
