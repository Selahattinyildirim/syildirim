package com.syildirim.teslacharge.ui

import android.content.ContentResolver
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.syildirim.teslacharge.AppContainer
import com.syildirim.teslacharge.TeslaChargeApp
import com.syildirim.teslacharge.core.ChargeRecord
import com.syildirim.teslacharge.core.RecordFilter
import com.syildirim.teslacharge.core.StatsCalculator
import com.syildirim.teslacharge.core.UsageCalculator
import com.syildirim.teslacharge.data.db.ChargeSessionEntity
import com.syildirim.teslacharge.data.db.OdometerReadingEntity
import com.syildirim.teslacharge.data.db.OdometerSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
inline fun <reified VM : ViewModel> appViewModel(key: String? = null, crossinline create: (AppContainer) -> VM): VM {
    val container = (LocalContext.current.applicationContext as TeslaChargeApp).container
    return viewModel(key = key, factory = viewModelFactory { initializer { create(container) } })
}

/** Tüm kayıtlardan türetilen hesaplanmış görünüm. */
data class Analytics(
    val sessions: List<ChargeSessionEntity> = emptyList(),
    val records: List<ChargeRecord> = emptyList(),
    val segments: Map<Long, UsageCalculator.Segment> = emptyMap(),
    val summary: UsageCalculator.Summary? = null,
    val totals: StatsCalculator.Totals? = null,
    val monthly: List<StatsCalculator.MonthPoint> = emptyList(),
    val stations: List<StatsCalculator.StationPoint> = emptyList(),
    val latestOdometer: Double? = null,
    val sinceLastChargeKm: Double? = null,
    val batteryCapacity: Double = 75.0,
    val loaded: Boolean = false,
)

fun buildAnalytics(sessions: List<ChargeSessionEntity>, readings: List<OdometerReadingEntity>, capacity: Double): Analytics {
    val records = sessions.map { it.toRecord() }
    val segs = UsageCalculator.segments(records)
    val last = sessions.maxByOrNull { it.startedAt }
    val lastOdo = last?.let { it.odometerAfter ?: it.odometerBefore }
    val newerReading = readings.filter { last == null || it.recordedAt >= last.startedAt }.maxByOrNull { it.recordedAt }?.odometerKm
    val latestOdo = listOfNotNull(lastOdo, newerReading).maxOrNull()
    return Analytics(
        sessions = sessions,
        records = records,
        segments = segs.associateBy { it.chargeId },
        summary = UsageCalculator.summarize(segs, capacity),
        totals = StatsCalculator.totals(records),
        monthly = StatsCalculator.monthly(records, segs),
        stations = StatsCalculator.byStation(records),
        latestOdometer = latestOdo,
        sinceLastChargeKm = UsageCalculator.distanceSinceLastCharge(records, latestOdo),
        batteryCapacity = capacity,
        loaded = true,
    )
}

class DashboardViewModel(c: AppContainer) : ViewModel() {
    val analytics: StateFlow<Analytics> = combine(c.charges.allSessions, c.charges.odometerReadings, c.settings.settings) { s, r, st ->
        withContext(Dispatchers.Default) { buildAnalytics(s, r, st.batteryCapacityKwh) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Analytics())
}

class HistoryViewModel(private val c: AppContainer) : ViewModel() {
    val filter = MutableStateFlow(RecordFilter())

    val analytics: StateFlow<Analytics> = combine(c.charges.allSessions, c.charges.odometerReadings, c.settings.settings) { s, r, st ->
        withContext(Dispatchers.Default) { buildAnalytics(s, r, st.batteryCapacityKwh) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Analytics())

    val filtered: StateFlow<List<ChargeSessionEntity>> = combine(analytics, filter) { a, f ->
        a.sessions.filter { f.matches(it.toRecord()) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val stations = c.charges.stations.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val banks = c.charges.banks.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun export(resolver: ContentResolver, uri: Uri, xlsx: Boolean, onDone: (Boolean) -> Unit) {
        val rows = filtered.value
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    resolver.openOutputStream(uri)?.use { out ->
                        if (xlsx) c.exporter.writeXlsx(rows, out) else c.exporter.writeCsv(rows, out)
                    } ?: error("Dosya açılamadı")
                }.isSuccess
            }
            onDone(ok)
        }
    }
}

class DetailViewModel(private val c: AppContainer, id: Long) : ViewModel() {
    val session = c.charges.observeSession(id).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val segment: StateFlow<UsageCalculator.Segment?> = c.charges.allSessions.map { all ->
        UsageCalculator.segments(all.map { it.toRecord() }).firstOrNull { it.chargeId == id }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun delete(onDone: () -> Unit) {
        val s = session.value ?: return
        viewModelScope.launch { c.charges.delete(s); onDone() }
    }
}

class OdometerViewModel(private val c: AppContainer) : ViewModel() {
    val analytics: StateFlow<Analytics> = combine(c.charges.allSessions, c.charges.odometerReadings, c.settings.settings) { s, r, st ->
        buildAnalytics(s, r, st.batteryCapacityKwh)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Analytics())
    val readings = c.charges.odometerReadings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun save(km: Double, battery: Double?, onDone: () -> Unit) = viewModelScope.launch {
        c.charges.addOdometerReading(km, battery, OdometerSource.MANUAL)
        onDone()
    }

    fun delete(r: OdometerReadingEntity) = viewModelScope.launch { c.charges.deleteOdometerReading(r) }
}
