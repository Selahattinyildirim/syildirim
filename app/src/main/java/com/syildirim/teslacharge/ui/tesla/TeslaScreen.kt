package com.syildirim.teslacharge.ui.tesla

import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.syildirim.teslacharge.data.settings.TeslaRegion
import com.syildirim.teslacharge.ui.Fmt
import com.syildirim.teslacharge.ui.components.InfoRow
import com.syildirim.teslacharge.ui.components.SectionCard
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TeslaScreen(vm: TeslaViewModel, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val s by vm.settings.collectAsState()
    val connected by vm.connected.collectAsState()
    val hasSecret by vm.hasSecret.collectAsState()
    val busy by vm.busy.collectAsState()
    val message by vm.message.collectAsState()
    val vehicles by vm.vehicles.collectAsState()
    val snap by vm.snapshot.collectAsState()

    var region by remember(s.teslaRegion) { mutableStateOf(s.teslaRegion) }
    var clientId by remember(s.teslaClientId) { mutableStateOf(s.teslaClientId) }
    var secret by remember { mutableStateOf("") }
    var redirect by remember(s.teslaRedirectUri) { mutableStateOf(s.teslaRedirectUri) }
    var pasted by remember { mutableStateOf("") }
    var domain by remember(s.teslaPartnerDomain) { mutableStateOf(s.teslaPartnerDomain) }
    var manualAccess by remember { mutableStateOf("") }
    var manualRefresh by remember { mutableStateOf("") }
    var showManual by remember { mutableStateOf(false) }

    Scaffold(topBar = {
        TopAppBar(title = { Text("Tesla Fleet API") },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Geri") } })
    }) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            message?.let { Text(it, color = MaterialTheme.colorScheme.secondary, style = MaterialTheme.typography.bodyMedium) }

            SectionCard(title = "Nasıl çalışır?") {
                Text(
                    "Tesla, üçüncü taraf uygulamalara yalnızca resmi Fleet API ile izin verir. Bunun için:\n" +
                        "1) developer.tesla.com'da ücretsiz geliştirici hesabı ve uygulama oluştur (Client ID / Secret).\n" +
                        "2) 'Allowed Redirect URI' olarak aşağıdaki adresi ekle.\n" +
                        "3) Kendi alan adında (ör. GitHub Pages) /.well-known/appspecific/com.tesla.3p.public-key.pem yayınla ve 'Bölge kaydı' yap.\n" +
                        "4) Tesla ile giriş yapıp aracını seç.\n\n" +
                        "Uygulama aracı asla uyandırmaz: yalnızca araç zaten çevrimiçiyken (sürüşte/şarjda) veri okur. " +
                        "Fleet API kullanımı Tesla tarafından ücretlendirilir; kişisel kullanımda aylık ücretsiz kredi genellikle yeterlidir.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            SectionCard(title = "1 · Uygulama bilgileri") {
                Text("Bölge", style = MaterialTheme.typography.labelSmall)
                TeslaRegion.entries.forEach { r ->
                    Row(Modifier.fillMaxWidth().clickable { region = r }, verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = region == r, onClick = { region = r })
                        Text(r.label)
                    }
                }
                Field(clientId, { clientId = it }, "Client ID")
                OutlinedTextField(secret, { secret = it }, label = { Text(if (hasSecret) "Client Secret (kayıtlı — değiştirmek için gir)" else "Client Secret") },
                    singleLine = true, visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp))
                Field(redirect, { redirect = it }, "Redirect URI")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { vm.saveConfig(region, clientId, secret, redirect); secret = "" }, modifier = Modifier.weight(1f)) { Text("Kaydet") }
                    if (hasSecret) OutlinedButton(onClick = vm::forgetSecret, modifier = Modifier.weight(1f)) { Text("Secret'ı sil") }
                }
                Text("Secret, Android Keystore ile şifrelenmiş olarak yalnızca bu cihazda saklanır.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            SectionCard(title = "2 · Bölge kaydı (tek seferlik)") {
                Field(domain, { domain = it }, "Uygulama alan adı (ör. kullanici.github.io)")
                OutlinedButton(onClick = { vm.registerPartner(domain) }, enabled = !busy && hasSecret, modifier = Modifier.fillMaxWidth()) {
                    Text("Bölgeye kaydet")
                }
            }

            SectionCard(title = "3 · Giriş", subtitle = if (connected) "Token kayıtlı" else "Bağlı değil") {
                Button(onClick = {
                    scope.launch {
                        vm.authorizeUrl()?.let { CustomTabsIntent.Builder().build().launchUrl(ctx, Uri.parse(it)) }
                    }
                }, enabled = !busy && clientId.isNotBlank(), modifier = Modifier.fillMaxWidth()) { Text("Tesla ile giriş yap") }
                Text("Tarayıcı uygulamaya dönmezse, açılan son sayfanın adresini kopyalayıp buraya yapıştır:",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Field(pasted, { pasted = it }, "Yönlendirme adresi veya kod")
                OutlinedButton(onClick = { vm.handleRedirect(pasted); pasted = "" }, enabled = pasted.isNotBlank() && !busy,
                    modifier = Modifier.fillMaxWidth()) { Text("Kodu işle") }
                TextButton(onClick = { showManual = !showManual }) { Text(if (showManual) "Elle token girişini gizle" else "Elle token gir (ileri düzey)") }
                if (showManual) {
                    Field(manualAccess, { manualAccess = it }, "Access token")
                    Field(manualRefresh, { manualRefresh = it }, "Refresh token (isteğe bağlı)")
                    OutlinedButton(onClick = { vm.saveManualTokens(manualAccess, manualRefresh); manualAccess = ""; manualRefresh = "" },
                        modifier = Modifier.fillMaxWidth()) { Text("Tokenları kaydet") }
                }
            }

            if (connected) {
                SectionCard(title = "4 · Araç", subtitle = s.teslaVehicleName.ifEmpty { "Araç seçilmedi" }) {
                    OutlinedButton(onClick = vm::loadVehicles, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("Araçları listele") }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        vehicles.forEach { v ->
                            FilterChip(selected = v.id == s.teslaVehicleId, onClick = { vm.selectVehicle(v) },
                                label = { Text("${v.displayName} · ${v.state}") })
                        }
                    }
                    Button(onClick = vm::readNow, enabled = !busy && s.teslaVehicleId.isNotEmpty(), modifier = Modifier.fillMaxWidth()) {
                        Text("Anlık veriyi oku (uyandırmaz)")
                    }
                    snap?.let { sn ->
                        InfoRow("Kilometre", Fmt.km(sn.odometerKm))
                        InfoRow("Batarya", Fmt.pct(sn.batteryLevel))
                        InfoRow("Şarj durumu", sn.chargingState ?: "—")
                        InfoRow("Eklenen enerji", Fmt.kwh(sn.chargeEnergyAddedKwh))
                        InfoRow("Şarj gücü", sn.chargerPowerKw?.let { "$it kW" } ?: "—")
                        InfoRow("Navigasyon hedefi", sn.activeRouteDestination ?: "—")
                    }
                    OutlinedButton(onClick = vm::disconnect, modifier = Modifier.fillMaxWidth()) { Text("Bağlantıyı kaldır ve tokenları sil") }
                }
            }
        }
    }
}

@Composable
private fun Field(value: String, onChange: (String) -> Unit, label: String) {
    OutlinedTextField(value, onChange, label = { Text(label) }, singleLine = true, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp))
}
