package com.sarjdefteri.app.ui.pro

import android.app.Activity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sarjdefteri.app.AppContainer
import com.sarjdefteri.app.data.settings.AppSettings
import com.sarjdefteri.app.ui.components.SectionCard
import com.sarjdefteri.app.ui.theme.TeslaRed
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn

class ProViewModel(private val c: AppContainer) : ViewModel() {
    val settings = c.settings.settings.stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())
    val price = c.billing.price
    val error = c.billing.lastError
    init { c.billing.start() }
    fun buy(activity: Activity): Boolean = c.billing.launchPurchase(activity)
    fun restore() = c.billing.restore()
}

val PRO_FEATURES = listOf(
    "Tesla Fleet API otomasyonu" to "Km, batarya ve şarj bitişi otomatik; bildirimden önceden doldurulmuş kayıt",
    "Gelişmiş analizler" to "İstasyonlara göre maliyet, aylık km ve birim fiyat trendi",
    "Excel (.xlsx) dışa aktarma" to "Muhasebe ve gider takibi için biçimli tablo",
    "Uygulamanın gelişimine destek" to "Tek seferlik ödeme, abonelik yok, reklam yok",
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProScreen(vm: ProViewModel, onBack: () -> Unit) {
    val activity = LocalContext.current as Activity
    val s by vm.settings.collectAsState()
    val price by vm.price.collectAsState()
    val error by vm.error.collectAsState()
    var info by remember { mutableStateOf<String?>(null) }

    Scaffold(topBar = {
        TopAppBar(title = { Text("Şarj Defteri Pro") },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Geri") } })
    }) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(if (s.isPro) "Pro etkin. Teşekkürler!" else "Temel takip her zaman ücretsiz.\nPro ile otomasyon ve derin analiz açılır.",
                style = MaterialTheme.typography.titleMedium)
            SectionCard {
                PRO_FEATURES.forEach { (title, desc) ->
                    Row(verticalAlignment = Alignment.Top, modifier = Modifier.padding(vertical = 4.dp)) {
                        Icon(Icons.Default.CheckCircle, null, tint = TeslaRed)
                        Column(Modifier.padding(start = 12.dp)) {
                            Text(title, style = MaterialTheme.typography.titleSmall)
                            Text(desc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            if (!s.isPro) {
                Button(
                    onClick = { if (!vm.buy(activity)) info = "Google Play'e bağlanılamadı veya ürün henüz yayında değil. Biraz sonra tekrar deneyin." },
                    modifier = Modifier.fillMaxWidth().height(54.dp), shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = TeslaRed, contentColor = MaterialTheme.colorScheme.onSecondary),
                ) { Text(price?.let { "Pro'ya geç · $it" } ?: "Pro'ya geç") }
                OutlinedButton(onClick = { vm.restore(); info = "Satın alımlar kontrol ediliyor…" }, modifier = Modifier.fillMaxWidth()) {
                    Text("Satın alımı geri yükle")
                }
            }
            (info ?: error)?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            Text("Ödeme Google Play üzerinden alınır; kart bilgileriniz uygulamaya iletilmez. Satın alım aynı Google hesabıyla diğer cihazlarınızda da geçerlidir.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Kilitli özellik yer tutucusu. */
@Composable
fun LockedCard(title: String, text: String, onUnlock: () -> Unit) {
    SectionCard(title = title) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Lock, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f).padding(start = 12.dp))
        }
        TextButton(onClick = onUnlock) { Text("Pro ile aç") }
    }
}
