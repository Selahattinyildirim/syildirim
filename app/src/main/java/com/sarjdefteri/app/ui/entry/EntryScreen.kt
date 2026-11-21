package com.sarjdefteri.app.ui.entry

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.LocalOffer
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.sarjdefteri.app.ui.Fmt
import com.sarjdefteri.app.ui.components.DateTimePickerDialog
import com.sarjdefteri.app.ui.components.NumberField
import com.sarjdefteri.app.ui.components.SectionCard
import com.sarjdefteri.app.ui.components.TextInput
import com.sarjdefteri.app.ui.theme.BrandRed

private val DEFAULT_BANKS = listOf("Akbank", "Garanti BBVA", "İş Bankası", "Yapı Kredi", "QNB", "Ziraat", "Enpara", "VakıfBank", "Halkbank", "DenizBank")
private val DEFAULT_PAYMENTS = listOf("Kredi kartı", "Banka kartı", "Uygulama cüzdanı", "Tesla hesabı", "Nakit")

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun EntryScreen(vm: EntryViewModel, onBack: () -> Unit, onSaved: (Long) -> Unit) {
    val f = vm.form
    val stations by vm.stations.collectAsState()
    val banks by vm.banks.collectAsState()
    val payments by vm.payments.collectAsState()
    var pickStart by remember { mutableStateOf(false) }
    var pickEnd by remember { mutableStateOf(false) }
    val computed = vm.computed()
    @Suppress("UNUSED_VARIABLE") val campaignList by vm.campaigns.collectAsState()
    val suggestions = vm.campaignSuggestions()
    val error = vm.validationError()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (vm.isEdit) "Kaydı düzenle" else "Yeni şarj") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Geri") } },
                actions = { if (!vm.isEdit) TextButton(onClick = { vm.showAll = !vm.showAll }) { Text(if (vm.showAll) "Hızlı mod" else "Tüm alanlar") } },
            )
        },
        bottomBar = {
            Surface(tonalElevation = 3.dp) {
                Column(Modifier.navigationBarsPadding().imePadding().padding(horizontal = 16.dp, vertical = 10.dp)) {
                    val summary = buildString {
                        computed.totalPaid?.let { append(Fmt.tl(it)) }
                        computed.energyKwh?.let { if (isNotEmpty()) append(" · "); append(Fmt.kwh(it)) }
                        computed.unitPrice?.let { if (isNotEmpty()) append(" · "); append(Fmt.unit(it)) }
                    }
                    if (error != null) Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    else if (summary.isNotEmpty()) Text(summary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Button(
                        onClick = { vm.save(onSaved) }, enabled = vm.loaded && error == null,
                        modifier = Modifier.fillMaxWidth().padding(top = 6.dp).height(52.dp), shape = RoundedCornerShape(12.dp),
                    ) { Text("Kaydet") }
                }
            }
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (vm.autoFilledFromApi) {
                AssistChip(onClick = {}, label = { Text("Otomatik dolduruldu — kontrol edip kaydet") },
                    leadingIcon = { Icon(Icons.Default.AutoAwesome, null) })
            }

            SectionCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Schedule, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Column(Modifier.weight(1f).padding(start = 12.dp)) {
                        Text("Şarj zamanı", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(Fmt.dateTime(f.startedAt), style = MaterialTheme.typography.bodyLarge)
                    }
                    TextButton(onClick = { pickStart = true }) { Text("Değiştir") }
                }
                TextInput(f.station, { v -> vm.update { copy(station = v) } }, "Şarj istasyonu")
                if (stations.isNotEmpty()) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        stations.take(6).forEach { s -> FilterChip(selected = f.station.trim() == s, onClick = { vm.selectStation(s) }, label = { Text(s) }) }
                    }
                }
            }

            SectionCard(title = "Ödeme ve enerji", subtitle = "İkisini girmen yeterli, üçüncüsü hesaplanır") {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    NumberField(f.energy, { v -> vm.update { copy(energy = v) } }, "Enerji", Modifier.weight(1f), suffix = "kWh",
                        supporting = if (f.energy.isBlank()) computed.energyKwh?.let { "≈ ${Fmt.num(it)}" } else null)
                    NumberField(f.total, { v -> vm.update { copy(total = v) } }, "Ödenen", Modifier.weight(1f), suffix = "₺",
                        supporting = if (f.total.isBlank()) computed.totalPaid?.let { "≈ ${Fmt.num(it)}" } else null)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    NumberField(f.unit, { v -> vm.update { copy(unit = v) } }, "Birim fiyat", Modifier.weight(1f), suffix = "₺/kWh",
                        supporting = if (f.unit.isBlank()) computed.unitPrice?.let { "≈ ${Fmt.num(it, 4)}" } else "İndirim öncesi")
                    NumberField(f.discountRate, { v -> vm.update { copy(discountRate = v) } }, "İndirim", Modifier.weight(1f), suffix = "%",
                        supporting = if (f.discountRate.isBlank()) computed.discountRate?.let { "≈ %${Fmt.num(it)}" } else null)
                }
            }

            if (suggestions.isNotEmpty() && f.campaign.isBlank()) {
                SectionCard(title = if (f.bank.isBlank()) "En avantajlı kart" else "Uygun kampanya") {
                    suggestions.forEach { sg ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.LocalOffer, null, tint = BrandRed)
                            Column(Modifier.weight(1f).padding(start = 10.dp)) {
                                Text(sg.campaign.title, style = MaterialTheme.typography.bodyLarge)
                                Text("%" + Fmt.num(sg.campaign.discountRate, 1) + (sg.discount?.let { " · −" + Fmt.tl(it) } ?: ""),
                                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            TextButton(onClick = { vm.applyCampaign(sg) }) { Text("Uygula") }
                        }
                    }
                }
            } else if (f.campaign.isNotBlank()) {
                AssistChip(onClick = { vm.update { copy(campaign = "", discountRate = "", discountAmount = "") } },
                    label = { Text("Kampanya: ${f.campaign}  ✕") }, leadingIcon = { Icon(Icons.Default.LocalOffer, null) })
            }

            SectionCard(title = "Banka") {
                TextInput(f.bank, { v -> vm.update { copy(bank = v) } }, "Kredi kartı bankası")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    (banks + DEFAULT_BANKS).distinct().take(8).forEach { b ->
                        FilterChip(selected = f.bank.trim() == b, onClick = { vm.update { copy(bank = if (bank.trim() == b) "" else b) } }, label = { Text(b) })
                    }
                }
            }

            SectionCard(title = "Araç") {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    NumberField(f.odoBefore, { v -> vm.update { copy(odoBefore = v) } }, "Kilometre", Modifier.weight(1f), suffix = "km", decimal = false,
                        supporting = vm.lastKnownOdometer?.let { "Son: ${Fmt.num(it, 0)}" })
                    NumberField(f.battBefore, { v -> vm.update { copy(battBefore = v) } }, "Önce", Modifier.weight(0.7f), suffix = "%", decimal = false)
                    NumberField(f.battAfter, { v -> vm.update { copy(battAfter = v) } }, "Sonra", Modifier.weight(0.7f), suffix = "%", decimal = false,
                        imeAction = ImeAction.Done)
                }
                if (f.odoBefore.isBlank() && vm.lastKnownOdometer != null) {
                    TextButton(onClick = vm::useLastOdometer) { Text("Son bilinen km'yi kullan (${Fmt.km(vm.lastKnownOdometer)})") }
                }
            }

            if (vm.showAll) {
                SectionCard(title = "Ayrıntılar") {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Bitiş zamanı", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(f.endedAt?.let { Fmt.dateTime(it) } ?: "Belirtilmedi")
                        }
                        if (f.endedAt != null) TextButton(onClick = { vm.update { copy(endedAt = null) } }) { Text("Temizle") }
                        TextButton(onClick = { pickEnd = true }) { Text("Seç") }
                    }
                    TextInput(f.location, { v -> vm.update { copy(location = v) } }, "Konum / adres")
                    NumberField(f.discountAmount, { v -> vm.update { copy(discountAmount = v) } }, "İndirim tutarı", Modifier.fillMaxWidth(), suffix = "₺",
                        supporting = if (f.discountAmount.isBlank()) computed.discountAmount?.let { "≈ ${Fmt.tl(it)}" } else null)
                    TextInput(f.campaign, { v -> vm.update { copy(campaign = v) } }, "Kampanya")
                    TextInput(f.payment, { v -> vm.update { copy(payment = v) } }, "Ödeme yöntemi")
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        (payments + DEFAULT_PAYMENTS).distinct().take(6).forEach { p ->
                            FilterChip(selected = f.payment.trim() == p, onClick = { vm.update { copy(payment = p) } }, label = { Text(p) })
                        }
                    }
                    NumberField(f.odoAfter, { v -> vm.update { copy(odoAfter = v) } }, "Şarj sonrası km", Modifier.fillMaxWidth(), suffix = "km",
                        decimal = false, supporting = "Boşsa şarj öncesi km kullanılır")
                    TextInput(f.notes, { v -> vm.update { copy(notes = v) } }, "Notlar", singleLine = false)
                }

                SectionCard(title = "Tüketim düzeltmesi", subtitle = "Araç kullanılmadan yapılan kısa şarjlar veya eksik kayıtlar için") {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Tüketim hesabından hariç tut")
                            Text("Bu şarjın enerjisi ve önceki km aralığı ortalamaya katılmaz", style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(checked = f.exclude, onCheckedChange = { v -> vm.update { copy(exclude = v) } })
                    }
                    HorizontalDivider()
                    NumberField(f.distanceOverride, { v -> vm.update { copy(distanceOverride = v) } }, "Önceki şarjdan bu yana km (elle)",
                        Modifier.fillMaxWidth(), suffix = "km", supporting = "Km bilgisi eksikse mesafeyi doğrudan gir")
                }
            } else {
                OutlinedButton(onClick = { vm.showAll = true }, modifier = Modifier.fillMaxWidth()) {
                    Text("Kampanya, ödeme yöntemi, notlar ve düzeltmeler")
                }
            }
            Spacer(Modifier.height(12.dp))
        }
    }

    if (pickStart) DateTimePickerDialog(f.startedAt, { pickStart = false }) { vm.update { copy(startedAt = it) }; pickStart = false }
    if (pickEnd) DateTimePickerDialog(f.endedAt ?: f.startedAt, { pickEnd = false }) { vm.update { copy(endedAt = it) }; pickEnd = false }
}
