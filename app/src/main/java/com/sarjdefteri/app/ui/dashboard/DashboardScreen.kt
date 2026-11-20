package com.sarjdefteri.app.ui.dashboard

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.sarjdefteri.app.ui.DashboardViewModel
import com.sarjdefteri.app.ui.Fmt
import com.sarjdefteri.app.ui.components.BarChart
import com.sarjdefteri.app.ui.components.ChartPoint
import com.sarjdefteri.app.ui.components.EmptyState
import com.sarjdefteri.app.ui.components.HorizontalBars
import com.sarjdefteri.app.ui.components.LineChart
import com.sarjdefteri.app.ui.components.SectionCard
import com.sarjdefteri.app.ui.components.StatTile
import com.sarjdefteri.app.ui.pro.LockedCard
import com.sarjdefteri.app.ui.theme.ChartAmber
import com.sarjdefteri.app.ui.theme.ChartBlue
import com.sarjdefteri.app.ui.theme.ChartTeal
import com.sarjdefteri.app.ui.theme.TeslaRed

@Composable
fun DashboardScreen(vm: DashboardViewModel, contentPadding: PaddingValues, onAdd: () -> Unit, onOdometer: () -> Unit, onPro: () -> Unit) {
    val a by vm.analytics.collectAsState()
    val settings by vm.settings.collectAsState()
    val t = a.totals
    val s = a.summary

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = contentPadding.calculateTopPadding() + 8.dp,
            bottom = contentPadding.calculateBottomPadding() + 88.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Column(Modifier.padding(vertical = 8.dp)) {
                Text(settings.vehicleName, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(Fmt.tl(t?.totalCost ?: 0.0), style = MaterialTheme.typography.displaySmall)
                Text("Toplam şarj harcaması", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        if (a.loaded && a.sessions.isEmpty()) {
            item {
                SectionCard {
                    EmptyState("Henüz şarj kaydı yok", "İlk şarjını ekle; tutar ve kWh girmen yeterli. Kilometre ve batarya bilgisi tüketim hesaplarını açar.")
                    Button(onClick = onAdd, modifier = Modifier.fillMaxWidth()) { Text("İlk şarjı ekle") }
                }
            }
            return@LazyColumn
        }

        item {
            SectionCard(modifier = Modifier.clickable(onClick = onOdometer)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Speed, null, tint = TeslaRed)
                    Column(Modifier.weight(1f).padding(start = 12.dp)) {
                        Text("Son şarjdan bu yana", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(Fmt.km(a.sinceLastChargeKm), style = MaterialTheme.typography.headlineSmall)
                        Text("Son şarj: ${Fmt.relativeDays(t?.lastChargeAt)} · ${Fmt.date(t?.lastChargeAt)}",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    OutlinedButton(onClick = onOdometer) { Text("Km gir") }
                }
            }
        }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StatTile("Toplam enerji", Fmt.kwh(t?.totalEnergyKwh), Modifier.weight(1f))
                    StatTile("Şarj sayısı", (t?.chargeCount ?: 0).toString(), Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StatTile("Ort. kWh maliyeti", Fmt.unit(t?.avgUnitCost), Modifier.weight(1f))
                    StatTile("Km başına", Fmt.perKm(s?.costPerKm), Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StatTile("Tüketim /100 km", s?.avgConsumptionPer100?.let { Fmt.num(it, 1) + " kWh" } ?: "—", Modifier.weight(1f),
                        hint = s?.avgBatteryConsumptionPer100?.let { "Batarya bazlı: ${Fmt.num(it, 1)}" } ?: "Şebekeden ölçülen")
                    StatTile("Toplam mesafe", Fmt.km(s?.totalDistanceKm), Modifier.weight(1f),
                        hint = t?.totalDiscount?.takeIf { it > 0 }?.let { "İndirim: ${Fmt.tlShort(it)}" })
                }
            }
        }

        val months = a.monthly
        if (months.isNotEmpty()) {
            item {
                SectionCard(title = "Aylık şarj harcaması") {
                    BarChart(months.map { ChartPoint(Fmt.month(it.month), it.cost) }, TeslaRed, { Fmt.tlShort(it) })
                }
            }
            item {
                SectionCard(title = "Aylık enerji", subtitle = "Alınan kWh") {
                    BarChart(months.map { ChartPoint(Fmt.month(it.month), it.energyKwh) }, ChartBlue, { Fmt.num(it, 0) + " kWh" })
                }
            }
            if (!settings.isPro) {
                item {
                    LockedCard("Gelişmiş analizler",
                        "Aylık kilometre, ortalama birim fiyat trendi ve istasyonlara göre maliyet Pro'da.", onPro)
                }
            }
            if (settings.isPro) item {
                SectionCard(title = "Aylık kilometre", subtitle = "Şarjlar arası mesafeden") {
                    BarChart(months.map { ChartPoint(Fmt.month(it.month), it.distanceKm) }, ChartTeal, { Fmt.num(it, 0) + " km" })
                }
            }
            if (settings.isPro && months.count { it.avgUnitCost != null } >= 1) {
                item {
                    SectionCard(title = "Ortalama birim fiyat", subtitle = "Ödenen ₺/kWh, aylık") {
                        LineChart(months.map { ChartPoint(Fmt.month(it.month), it.avgUnitCost) }, ChartAmber, { Fmt.num(it, 2) + " ₺" })
                    }
                }
            }
        }

        if (settings.isPro && a.stations.isNotEmpty()) {
            item {
                SectionCard(title = "İstasyonlara göre maliyet") {
                    val top = a.stations.take(8)
                    HorizontalBars(top.map { it.station to it.cost }, TeslaRed, { Fmt.tlShort(it) }) { i ->
                        val st = top[i]
                        "${st.count} şarj · ${Fmt.kwh(st.energyKwh)} · ${Fmt.unit(st.avgUnitCost)}"
                    }
                }
            }
        }
        item { Spacer(Modifier.height(4.dp)) }
    }
}
