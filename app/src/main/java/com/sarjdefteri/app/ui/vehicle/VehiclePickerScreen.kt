package com.sarjdefteri.app.ui.vehicle

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ElectricCar
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.sarjdefteri.app.SarjDefteriApp
import com.sarjdefteri.app.ui.Fmt
import com.sarjdefteri.app.ui.components.NumberField
import com.sarjdefteri.app.ui.components.SectionCard
import com.sarjdefteri.app.ui.components.TextInput
import com.sarjdefteri.app.ui.theme.BrandRed
import com.sarjdefteri.core.VehicleCatalog
import kotlinx.coroutines.launch

/** İlk açılışta ve Ayarlar'dan araç seçimi. Kapasite katalogdan gelir; listede yoksa elle girilir. */
@Composable
fun VehiclePickerScreen(firstRun: Boolean, onDone: () -> Unit) {
    val container = (LocalContext.current.applicationContext as SarjDefteriApp).container
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    var customName by remember { mutableStateOf("") }
    var customKwh by remember { mutableStateOf("") }
    val results = VehicleCatalog.search(query)

    fun pick(id: String, brand: String, name: String, kwh: Double) = scope.launch {
        container.settings.setVehicleProfile(id, brand, name, kwh); onDone()
    }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
    Column(Modifier.fillMaxSize().statusBarsPadding().imePadding()) {
        Row(Modifier.padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            if (!firstRun) IconButton(onClick = onDone) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Geri") }
            Column(Modifier.padding(start = 8.dp)) {
                Text("Aracını seç", style = MaterialTheme.typography.headlineSmall)
                if (firstRun) Text("Batarya kapasitesi tüketim hesabı için otomatik ayarlanır.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        OutlinedTextField(
            value = query, onValueChange = { query = it }, singleLine = true,
            label = { Text("Marka veya model ara") }, leadingIcon = { Icon(Icons.Default.Search, null) },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp), shape = RoundedCornerShape(12.dp),
        )
        LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            items(results, key = { it.id }) { p ->
                Row(
                    Modifier.fillMaxWidth().clickable { pick(p.id, p.brand, p.displayName, p.usableKwh) }.padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Default.ElectricCar, null, tint = BrandRed)
                    Text(p.displayName, Modifier.weight(1f).padding(start = 14.dp), style = MaterialTheme.typography.bodyLarge)
                    Text("≈ ${Fmt.num(p.usableKwh, 1)} kWh", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
            item {
                SectionCard(title = "Listede yok mu?", modifier = Modifier.padding(top = 16.dp)) {
                    TextInput(customName, { customName = it }, "Araç adı (ör. Skoda Enyaq)")
                    NumberField(customKwh, { customKwh = it }, "Kullanılabilir batarya", Modifier.fillMaxWidth(), suffix = "kWh",
                        supporting = "Bilmiyorsan aracın kataloğundaki net kapasiteyi yaz; sonra Ayarlar'dan değiştirebilirsin")
                    Button(
                        onClick = { pick("custom", customName.trim().substringBefore(' '), customName, Fmt.parse(customKwh) ?: 60.0) },
                        enabled = customName.isNotBlank(), modifier = Modifier.fillMaxWidth(),
                    ) { Text("Bu aracı kullan") }
                }
            }
        }
    }
    }
}
