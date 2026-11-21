package com.sarjdefteri.app.ui.campaigns

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.sarjdefteri.app.data.db.CampaignEntity
import com.sarjdefteri.app.ui.Fmt
import com.sarjdefteri.app.ui.components.EmptyState
import com.sarjdefteri.app.ui.components.NumberField
import com.sarjdefteri.app.ui.components.SectionCard
import com.sarjdefteri.app.ui.components.TextInput
import com.sarjdefteri.app.ui.theme.BrandRed
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

private val dateFmt = DateTimeFormatter.ofPattern("d MMM", Fmt.tr)

fun campaignSummary(e: CampaignEntity): String = buildString {
    append("%" + Fmt.num(e.discountRate, 1))
    e.maxDiscount?.let { append(" · en fazla " + Fmt.tlShort(it)) }
    e.minSpend?.let { append(" · min. " + Fmt.tlShort(it)) }
}

fun campaignValidity(e: CampaignEntity, today: LocalDate = LocalDate.now()): String {
    val to = e.validTo?.let(LocalDate::ofEpochDay)
    val from = e.validFrom?.let(LocalDate::ofEpochDay)
    return when {
        to != null && to.isBefore(today) -> "Süresi doldu (${to.format(dateFmt)})"
        from != null && from.isAfter(today) -> "${from.format(dateFmt)} tarihinde başlar"
        to != null -> {
            val left = java.time.temporal.ChronoUnit.DAYS.between(today, to)
            if (left == 0L) "Bugün son gün" else "${to.format(dateFmt)} tarihine kadar · $left gün"
        }
        else -> "Süresiz"
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CampaignsScreen(vm: CampaignsViewModel, contentPadding: PaddingValues, onEdit: (Long?) -> Unit) {
    val ctx = LocalContext.current
    val list by vm.campaigns.collectAsState()
    val s by vm.settings.collectAsState()
    var editUrl by remember { mutableStateOf(false) }
    val today = LocalDate.now()
    val (current, past) = list.partition { it.toCampaign().isValidOn(today) || (it.validFrom?.let(LocalDate::ofEpochDay)?.isAfter(today) == true && it.active) }

    Column(Modifier.fillMaxSize().padding(top = contentPadding.calculateTopPadding())) {
        TopAppBar(title = { Text("Kampanyalar") })
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = contentPadding.calculateBottomPadding() + 88.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                SectionCard(title = "Kampanya kaynağın", subtitle = "Takip ettiğin kanal veya banka sayfası") {
                    if (s.campaignSourceUrl.isNotBlank()) {
                        Text(s.campaignSourceUrl, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = {
                                runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(s.campaignSourceUrl)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                            }, modifier = Modifier.weight(1f)) {
                                Icon(Icons.AutoMirrored.Filled.OpenInNew, null); Text("  Kaynağı aç")
                            }
                            OutlinedButton(onClick = { editUrl = true }) { Text("Değiştir") }
                        }
                    } else {
                        Text("Kampanyaları takip ettiğin bağlantıyı (WhatsApp kanalı, banka kampanya sayfası…) ekle; tek dokunuşla açılsın.",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        OutlinedButton(onClick = { editUrl = true }, modifier = Modifier.fillMaxWidth()) { Text("Bağlantı ekle") }
                    }
                    Text("Gördüğün kampanyayı + ile ekle; şarj kaydında hangi kartın daha avantajlı olduğu otomatik gösterilir.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (current.isEmpty()) item { EmptyState("Aktif kampanya yok", "Bu ay geçerli bir kart veya operatör kampanyası ekle.") }
            items(current, key = { it.id }) { e -> CampaignRow(e, onClick = { onEdit(e.id) }, vm = vm, expired = false) }
            if (past.isNotEmpty()) {
                item { Text("Geçmiş / pasif", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp)) }
                items(past, key = { it.id }) { e -> CampaignRow(e, onClick = { onEdit(e.id) }, vm = vm, expired = true) }
            }
            item {
                Text("Kampanya bilgileri kullanıcı tarafından girilir ve yalnızca bilgi amaçlıdır. Güncel koşullar için bankanın resmi duyurusunu kontrol edin.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
            }
        }
    }

    if (editUrl) {
        var url by remember { mutableStateOf(s.campaignSourceUrl) }
        AlertDialog(
            onDismissRequest = { editUrl = false },
            title = { Text("Kampanya kaynağı") },
            text = { OutlinedTextField(url, { url = it }, label = { Text("https://…") }, singleLine = true) },
            confirmButton = { TextButton(onClick = { vm.setSourceUrl(url); editUrl = false }) { Text("Kaydet") } },
            dismissButton = { TextButton(onClick = { editUrl = false }) { Text("İptal") } },
        )
    }
}

@Composable
private fun CampaignRow(e: CampaignEntity, onClick: () -> Unit, vm: CampaignsViewModel, expired: Boolean) {
    var menu by remember { mutableStateOf(false) }
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(Modifier.padding(start = 14.dp, top = 12.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(e.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    color = if (expired) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface)
                Text(listOfNotNull(e.bank, e.operator ?: "Tüm istasyonlar").joinToString(" · "), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(campaignSummary(e), style = MaterialTheme.typography.bodyMedium, color = if (expired) MaterialTheme.colorScheme.onSurfaceVariant else BrandRed)
                Text(if (!e.active) "Pasif" else campaignValidity(e), style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Column {
                IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, "Seçenekler") }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    if (expired) DropdownMenuItem(text = { Text("Bu ay için yenile") }, onClick = { menu = false; vm.renewForThisMonth(e) })
                    DropdownMenuItem(text = { Text(if (e.active) "Pasifleştir" else "Etkinleştir") }, onClick = { menu = false; vm.toggleActive(e) })
                    DropdownMenuItem(text = { Text("Sil") }, onClick = { menu = false; vm.delete(e) })
                }
            }
        }
    }
}

private val DEFAULT_BANKS = listOf("Akbank", "Garanti BBVA", "İş Bankası", "Yapı Kredi", "QNB", "Ziraat", "Enpara", "VakıfBank", "Halkbank", "DenizBank")
private val DEFAULT_OPERATORS = listOf("Tesla", "ZES", "Eşarj", "Trugo", "Voltrun", "Astor", "Sharz")

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun CampaignEditScreen(vm: CampaignEditViewModel, onBack: () -> Unit) {
    val f = vm.form
    val banks by vm.banks.collectAsState()
    val stations by vm.stations.collectAsState()
    var pickFrom by remember { mutableStateOf(false) }
    var pickTo by remember { mutableStateOf(false) }
    val err = vm.error()

    Scaffold(
        topBar = {
            TopAppBar(title = { Text(if (vm.isEdit) "Kampanyayı düzenle" else "Yeni kampanya") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Geri") } })
        },
        bottomBar = {
            Surface(tonalElevation = 3.dp) {
                Column(Modifier.navigationBarsPadding().imePadding().padding(horizontal = 16.dp, vertical = 10.dp)) {
                    Text(err ?: vm.autoTitle(), style = MaterialTheme.typography.bodySmall,
                        color = if (err != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
                    Button(onClick = { vm.save(onBack) }, enabled = err == null,
                        modifier = Modifier.fillMaxWidth().padding(top = 6.dp).height(52.dp), shape = RoundedCornerShape(12.dp)) { Text("Kaydet") }
                }
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SectionCard(title = "Kart / banka") {
                TextInput(f.bank, { v -> vm.update { copy(bank = v) } }, "Banka (boş: tüm kartlar)")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    (banks + DEFAULT_BANKS).distinct().take(10).forEach { b ->
                        FilterChip(selected = f.bank.trim() == b, onClick = { vm.update { copy(bank = if (bank.trim() == b) "" else b) } }, label = { Text(b) })
                    }
                }
            }
            SectionCard(title = "İstasyon / operatör") {
                TextInput(f.operator, { v -> vm.update { copy(operator = v) } }, "Operatör (boş: tüm istasyonlar)",
                    supporting = "İstasyon adında geçen ifade, ör. ZES")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    (DEFAULT_OPERATORS + stations).distinct().take(10).forEach { o ->
                        FilterChip(selected = f.operator.trim() == o, onClick = { vm.update { copy(operator = if (operator.trim() == o) "" else o) } }, label = { Text(o) })
                    }
                }
            }
            SectionCard(title = "İndirim") {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    NumberField(f.rate, { v -> vm.update { copy(rate = v) } }, "Oran", Modifier.weight(1f), suffix = "%")
                    NumberField(f.maxDiscount, { v -> vm.update { copy(maxDiscount = v) } }, "En fazla", Modifier.weight(1f), suffix = "₺")
                }
                NumberField(f.minSpend, { v -> vm.update { copy(minSpend = v) } }, "En az harcama (isteğe bağlı)", Modifier.fillMaxWidth(), suffix = "₺")
            }
            SectionCard(title = "Geçerlilik") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { pickFrom = true }, modifier = Modifier.weight(1f)) { Text("Başlangıç: " + (f.from?.format(dateFmt) ?: "—")) }
                    OutlinedButton(onClick = { pickTo = true }, modifier = Modifier.weight(1f)) { Text("Bitiş: " + (f.to?.format(dateFmt) ?: "—")) }
                }
                TextButton(onClick = {
                    val t = LocalDate.now(); vm.update { copy(from = t.withDayOfMonth(1), to = t.withDayOfMonth(t.lengthOfMonth())) }
                }) { Text("Bu ay boyunca") }
            }
            SectionCard(title = "Ayrıntılar") {
                TextInput(f.title, { v -> vm.update { copy(title = v) } }, "Başlık (boş: otomatik)")
                TextInput(f.sourceUrl, { v -> vm.update { copy(sourceUrl = v) } }, "Kaynak bağlantısı (isteğe bağlı)")
                TextInput(f.notes, { v -> vm.update { copy(notes = v) } }, "Notlar / katılım koşulu (ör. SMS ile katıl)", singleLine = false)
            }
        }
    }
    if (pickFrom) DateOnlyPicker(f.from, { pickFrom = false }) { vm.update { copy(from = it) }; pickFrom = false }
    if (pickTo) DateOnlyPicker(f.to, { pickTo = false }) { vm.update { copy(to = it) }; pickTo = false }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateOnlyPicker(initial: LocalDate?, onDismiss: () -> Unit, onPick: (LocalDate) -> Unit) {
    val state = rememberDatePickerState((initial ?: LocalDate.now()).atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli())
    DatePickerDialog(onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = {
            state.selectedDateMillis?.let { onPick(java.time.Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()) } ?: onDismiss()
        }) { Text("Tamam") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("İptal") } }) { DatePicker(state = state) }
}
