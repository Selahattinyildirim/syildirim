package com.syildirim.teslacharge.ui.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.syildirim.teslacharge.data.db.RecordSource
import com.syildirim.teslacharge.ui.DetailViewModel
import com.syildirim.teslacharge.ui.Fmt
import com.syildirim.teslacharge.ui.components.InfoRow
import com.syildirim.teslacharge.ui.components.SectionCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScreen(vm: DetailViewModel, onBack: () -> Unit, onEdit: (Long) -> Unit) {
    val s by vm.session.collectAsState()
    val seg by vm.segment.collectAsState()
    var confirmDelete by remember { mutableStateOf(false) }

    Scaffold(topBar = {
        TopAppBar(
            title = { Text("Şarj detayı") },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Geri") } },
            actions = {
                s?.let { e ->
                    IconButton(onClick = { onEdit(e.id) }) { Icon(Icons.Default.Edit, "Düzenle") }
                    IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Default.Delete, "Sil") }
                }
            },
        )
    }) { padding ->
        val e = s ?: return@Scaffold
        val rec = e.toRecord()
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column {
                Text(e.station ?: "İstasyon belirtilmedi", style = MaterialTheme.typography.headlineSmall)
                Text(Fmt.dateTime(e.startedAt) + (e.endedAt?.let { " – " + Fmt.time(it) } ?: ""),
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            SectionCard(title = "Ödeme") {
                InfoRow("Ödenen toplam", Fmt.tl(rec.effectiveCost))
                InfoRow("Enerji", Fmt.kwh(e.energyKwh))
                InfoRow("Birim fiyat (liste)", Fmt.unit(e.unitPrice))
                InfoRow("Ödenen birim fiyat", Fmt.unit(rec.effectiveCost?.let { c -> e.energyKwh?.takeIf { it > 0 }?.let { c / it } }))
                InfoRow("İndirim", if (rec.hasDiscount) "${Fmt.tl(e.discountAmount)} (%${Fmt.num(e.discountRate, 1)})" else "Yok")
                InfoRow("Kampanya", e.campaign ?: "—")
                InfoRow("Banka", e.bank ?: "—")
                InfoRow("Ödeme yöntemi", e.paymentMethod ?: "—")
            }
            SectionCard(title = "Araç") {
                InfoRow("Km (öncesi → sonrası)", "${Fmt.km(e.odometerBefore)} → ${Fmt.km(e.odometerAfter)}")
                InfoRow("Batarya", "${Fmt.pct(e.batteryBefore)} → ${Fmt.pct(e.batteryAfter)}")
                InfoRow("Konum", e.location ?: "—")
            }
            SectionCard(title = "Önceki şarjdan bu yana") {
                val sg = seg
                when {
                    sg == null || sg.previousChargeId == null && sg.distanceKm == null ->
                        Text("İlk kayıt: karşılaştırılacak önceki şarj yok.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    sg.odometerError -> Text("Kilometre önceki şarjdan küçük görünüyor. Kaydı düzenleyip km'yi düzeltin veya mesafeyi elle girin.",
                        color = MaterialTheme.colorScheme.error)
                    else -> {
                        InfoRow("Yapılan yol", Fmt.km(sg.distanceKm) + if (sg.distanceIsManual) " (elle)" else "")
                        InfoRow("Tüketim (şebeke)", Fmt.consumption(sg.consumptionPer100))
                        InfoRow("Km başına maliyet", Fmt.perKm(sg.costPerKm))
                        InfoRow("Kullanılan batarya", sg.batteryUsedPct?.let { "%" + Fmt.num(it, 0) } ?: "—")
                        if (sg.excluded) Text("Bu kayıt tüketim ortalamalarına katılmıyor.", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            if (!e.notes.isNullOrBlank()) SectionCard(title = "Notlar") { Text(e.notes) }
            Text(
                "Kaynak: " + when (e.source) { RecordSource.MANUAL -> "Manuel"; RecordSource.TESLA_API -> "Tesla API"; RecordSource.NOTIFICATION -> "Bildirim" } +
                    " · Güncellendi: " + Fmt.dateTime(e.updatedAt),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Kayıt silinsin mi?") },
            text = { Text("Bu işlem geri alınamaz.") },
            confirmButton = { TextButton(onClick = { confirmDelete = false; vm.delete(onBack) }) { Text("Sil") } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Vazgeç") } },
        )
    }
}
