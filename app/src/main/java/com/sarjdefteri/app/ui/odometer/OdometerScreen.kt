package com.sarjdefteri.app.ui.odometer

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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.sarjdefteri.app.data.db.OdometerSource
import com.sarjdefteri.app.ui.Fmt
import com.sarjdefteri.app.ui.OdometerViewModel
import com.sarjdefteri.app.ui.components.NumberField
import com.sarjdefteri.app.ui.components.SectionCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OdometerScreen(vm: OdometerViewModel, onBack: () -> Unit) {
    val a by vm.analytics.collectAsState()
    val readings by vm.readings.collectAsState()
    var km by remember { mutableStateOf("") }
    var batt by remember { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }

    val lastCharge = a.sessions.maxByOrNull { it.startedAt }
    val ref = lastCharge?.let { it.odometerAfter ?: it.odometerBefore }
    val entered = Fmt.parse(km)
    val preview = if (entered != null && ref != null) entered - ref else null
    val error = when {
        entered == null -> null
        ref != null && entered < ref -> "Son şarjdaki km'den (${Fmt.km(ref)}) küçük olamaz"
        else -> null
    }

    Scaffold(topBar = {
        TopAppBar(title = { Text("Kilometre kaydı") },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Geri") } })
    }) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SectionCard {
                Text("Araç ekranındaki güncel kilometreyi gir.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                NumberField(km, { km = it }, "Güncel km", Modifier.fillMaxWidth().focusRequester(focus), suffix = "km", decimal = false,
                    supporting = error ?: ref?.let { "Son şarj: ${Fmt.km(it)} · ${Fmt.date(lastCharge.startedAt)}" })
                NumberField(batt, { batt = it }, "Batarya (isteğe bağlı)", Modifier.fillMaxWidth(), suffix = "%", decimal = false, imeAction = ImeAction.Done)
                if (preview != null && error == null) {
                    Text("Son şarjdan bu yana: ${Fmt.km(preview)}", style = MaterialTheme.typography.titleMedium)
                    a.summary?.avgConsumptionPer100?.let { c ->
                        Text("Tahmini harcanan enerji: ${Fmt.kwh(preview * c / 100)}", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Button(
                    onClick = { entered?.let { vm.save(it, Fmt.parse(batt), onBack) } },
                    enabled = entered != null && error == null,
                    modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(12.dp),
                ) { Text("Kaydet") }
            }
            if (readings.isNotEmpty()) {
                SectionCard(title = "Son okumalar") {
                    readings.take(15).forEach { r ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(Fmt.km(r.odometerKm) + (r.batteryPct?.let { " · %${Fmt.num(it, 0)}" } ?: ""))
                                Text(Fmt.dateTime(r.recordedAt) + " · " + when (r.source) {
                                    OdometerSource.MANUAL -> "Manuel"; OdometerSource.BLUETOOTH_PROMPT -> "Bildirim"; OdometerSource.TESLA_API -> "Tesla API"
                                }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            IconButton(onClick = { vm.delete(r) }) { Icon(Icons.Default.Close, "Sil") }
                        }
                    }
                }
            }
        }
    }
}
