package com.syildirim.teslacharge.ui.history

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
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
import com.syildirim.teslacharge.core.RecordFilter
import com.syildirim.teslacharge.core.UsageCalculator
import com.syildirim.teslacharge.data.db.ChargeSessionEntity
import com.syildirim.teslacharge.ui.Fmt
import com.syildirim.teslacharge.ui.HistoryViewModel
import com.syildirim.teslacharge.ui.components.DateTimePickerDialog
import com.syildirim.teslacharge.ui.components.EmptyState
import com.syildirim.teslacharge.ui.components.NumberField
import com.syildirim.teslacharge.ui.theme.TeslaRed
import java.time.LocalDate
import java.time.ZoneId

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(vm: HistoryViewModel, contentPadding: PaddingValues, onOpen: (Long) -> Unit) {
    val context = LocalContext.current
    val list by vm.filtered.collectAsState()
    val analytics by vm.analytics.collectAsState()
    val filter by vm.filter.collectAsState()
    var showFilters by remember { mutableStateOf(false) }
    var exportMenu by remember { mutableStateOf(false) }

    val done: (Boolean) -> Unit = { ok -> Toast.makeText(context, if (ok) "Dışa aktarıldı" else "Dışa aktarma başarısız", Toast.LENGTH_SHORT).show() }
    val csvLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        uri?.let { vm.export(context.contentResolver, it, xlsx = false, onDone = done) }
    }
    val xlsxLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
    ) { uri -> uri?.let { vm.export(context.contentResolver, it, xlsx = true, onDone = done) } }
    val stamp = LocalDate.now().toString()

    Column(Modifier.fillMaxSize().padding(top = contentPadding.calculateTopPadding())) {
        TopAppBar(
            title = { Text("Şarj geçmişi") },
            actions = {
                IconButton(onClick = { showFilters = true }) {
                    BadgedBox(badge = { if (filter.isActive) Badge(containerColor = TeslaRed) }) { Icon(Icons.Default.FilterList, "Filtrele") }
                }
                Column {
                    IconButton(onClick = { exportMenu = true }) { Icon(Icons.Default.FileDownload, "Dışa aktar") }
                    DropdownMenu(expanded = exportMenu, onDismissRequest = { exportMenu = false }) {
                        DropdownMenuItem(text = { Text("Excel (.xlsx)") }, onClick = { exportMenu = false; xlsxLauncher.launch("sarj-gecmisi-$stamp.xlsx") })
                        DropdownMenuItem(text = { Text("CSV") }, onClick = { exportMenu = false; csvLauncher.launch("sarj-gecmisi-$stamp.csv") })
                    }
                }
            },
        )
        if (filter.isActive) {
            Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                val total = list.sumOf { it.toRecord().effectiveCost ?: 0.0 }
                Text("${list.size} kayıt · ${Fmt.tl(total)} · ${Fmt.kwh(list.sumOf { it.energyKwh ?: 0.0 })}",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                TextButton(onClick = { vm.filter.value = RecordFilter() }) { Text("Temizle") }
            }
        }
        if (analytics.loaded && list.isEmpty()) {
            EmptyState(if (filter.isActive) "Eşleşen kayıt yok" else "Kayıt yok",
                if (filter.isActive) "Filtreleri değiştirmeyi dene." else "Sağ alttaki + ile ilk şarjını ekle.")
        }
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = contentPadding.calculateBottomPadding() + 88.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(list, key = { it.id }) { s -> SessionRow(s, analytics.segments[s.id]) { onOpen(s.id) } }
        }
    }

    if (showFilters) {
        val stations by vm.stations.collectAsState()
        val banks by vm.banks.collectAsState()
        ModalBottomSheet(onDismissRequest = { showFilters = false }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
            FilterSheet(filter, stations, banks, onApply = { vm.filter.value = it; showFilters = false })
        }
    }
}

@Composable
private fun SessionRow(s: ChargeSessionEntity, seg: UsageCalculator.Segment?, onClick: () -> Unit) {
    val rec = s.toRecord()
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(s.station ?: "İstasyon belirtilmedi", style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(Fmt.dateTime(s.startedAt), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(Fmt.tl(rec.effectiveCost), style = MaterialTheme.typography.titleMedium)
            }
            Spacer(Modifier.height(6.dp))
            val parts = listOfNotNull(
                s.energyKwh?.let { Fmt.kwh(it) },
                rec.effectiveCost?.let { c -> s.energyKwh?.takeIf { it > 0 }?.let { Fmt.unit(c / it) } },
                seg?.distanceKm?.let { "+" + Fmt.km(it) },
                seg?.consumptionPer100?.let { Fmt.consumption(it) },
                s.bank,
            )
            Text(parts.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            val tags = listOfNotNull(
                if (rec.hasDiscount) "İndirimli" + (s.discountRate?.let { " %${Fmt.num(it, 0)}" } ?: "") else null,
                s.campaign,
                if (rec.isIncomplete) "Eksik bilgi" else null,
                if (s.excludeFromConsumption) "Hesaptan hariç" else null,
                if (seg?.odometerError == true) "Km tutarsız" else null,
            )
            if (tags.isNotEmpty()) {
                Text(tags.joinToString("  •  "), style = MaterialTheme.typography.labelSmall, color = TeslaRed, modifier = Modifier.padding(top = 4.dp))
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FilterSheet(initial: RecordFilter, stations: List<String>, banks: List<String>, onApply: (RecordFilter) -> Unit) {
    var f by remember { mutableStateOf(initial) }
    var minText by remember { mutableStateOf(Fmt.input(initial.minAmount)) }
    var maxText by remember { mutableStateOf(Fmt.input(initial.maxAmount)) }
    var pickFrom by remember { mutableStateOf(false) }
    var pickTo by remember { mutableStateOf(false) }
    val zone = ZoneId.systemDefault()

    Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 24.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Filtreler", style = MaterialTheme.typography.titleLarge)

        Text("Tarih", style = MaterialTheme.typography.labelSmall)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val today = LocalDate.now(zone)
            fun start(d: LocalDate) = d.atStartOfDay(zone).toInstant().toEpochMilli()
            listOf(
                "Bu ay" to start(today.withDayOfMonth(1)),
                "Son 3 ay" to start(today.minusMonths(3)),
                "Bu yıl" to start(today.withDayOfYear(1)),
            ).forEach { (label, from) ->
                FilterChip(selected = f.fromMillis == from && f.toMillis == null, onClick = { f = f.copy(fromMillis = from, toMillis = null) }, label = { Text(label) })
            }
            FilterChip(selected = f.fromMillis == null && f.toMillis == null, onClick = { f = f.copy(fromMillis = null, toMillis = null) }, label = { Text("Tümü") })
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { pickFrom = true }, modifier = Modifier.weight(1f)) { Text("Başlangıç: " + (f.fromMillis?.let { Fmt.date(it) } ?: "—")) }
            OutlinedButton(onClick = { pickTo = true }, modifier = Modifier.weight(1f)) { Text("Bitiş: " + (f.toMillis?.let { Fmt.date(it) } ?: "—")) }
        }

        if (stations.isNotEmpty()) {
            Text("İstasyon", style = MaterialTheme.typography.labelSmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                stations.forEach { s ->
                    FilterChip(selected = s in f.stations, onClick = {
                        f = f.copy(stations = if (s in f.stations) f.stations - s else f.stations + s)
                    }, label = { Text(s, maxLines = 1, overflow = TextOverflow.Ellipsis) })
                }
            }
        }
        if (banks.isNotEmpty()) {
            Text("Banka", style = MaterialTheme.typography.labelSmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                banks.forEach { b ->
                    FilterChip(selected = b in f.banks, onClick = { f = f.copy(banks = if (b in f.banks) f.banks - b else f.banks + b) }, label = { Text(b) })
                }
            }
        }
        Text("Şarj tutarı", style = MaterialTheme.typography.labelSmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NumberField(minText, { minText = it }, "En az", Modifier.weight(1f), suffix = "₺")
            NumberField(maxText, { maxText = it }, "En çok", Modifier.weight(1f), suffix = "₺")
        }
        Text("İndirim", style = MaterialTheme.typography.labelSmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(RecordFilter.DiscountFilter.ALL to "Tümü", RecordFilter.DiscountFilter.DISCOUNTED to "İndirimli",
                RecordFilter.DiscountFilter.NOT_DISCOUNTED to "İndirimsiz").forEach { (v, label) ->
                FilterChip(selected = f.discount == v, onClick = { f = f.copy(discount = v) }, label = { Text(label) })
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { onApply(RecordFilter()) }, modifier = Modifier.weight(1f)) { Text("Sıfırla") }
            Button(onClick = { onApply(f.copy(minAmount = Fmt.parse(minText), maxAmount = Fmt.parse(maxText))) }, modifier = Modifier.weight(1f)) { Text("Uygula") }
        }
    }

    if (pickFrom) DateTimePickerDialog(f.fromMillis ?: System.currentTimeMillis(), { pickFrom = false }) { f = f.copy(fromMillis = it); pickFrom = false }
    if (pickTo) DateTimePickerDialog(f.toMillis ?: System.currentTimeMillis(), { pickTo = false }) { f = f.copy(toMillis = it); pickTo = false }
}
