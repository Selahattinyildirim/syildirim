package com.sarjdefteri.app.ui.entry

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sarjdefteri.app.AppContainer
import com.sarjdefteri.core.PriceCalculator
import com.sarjdefteri.app.data.db.ChargeSessionEntity
import com.sarjdefteri.app.data.db.RecordSource
import com.sarjdefteri.app.reminders.Notifications
import com.sarjdefteri.app.ui.Fmt
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class EntryForm(
    val startedAt: Long = System.currentTimeMillis(),
    val endedAt: Long? = null,
    val station: String = "",
    val location: String = "",
    val total: String = "",
    val energy: String = "",
    val unit: String = "",
    val discountRate: String = "",
    val discountAmount: String = "",
    val campaign: String = "",
    val bank: String = "",
    val payment: String = "",
    val odoBefore: String = "",
    val odoAfter: String = "",
    val battBefore: String = "",
    val battAfter: String = "",
    val notes: String = "",
    val exclude: Boolean = false,
    val distanceOverride: String = "",
)

class EntryViewModel(private val c: AppContainer, private val editId: Long?) : ViewModel() {
    private val initialForm = EntryForm()
    var form by mutableStateOf(initialForm)
        private set
    var loaded by mutableStateOf(false)
        private set
    var showAll by mutableStateOf(false)
    var autoFilledFromApi by mutableStateOf(false)
        private set
    var lastKnownOdometer by mutableStateOf<Double?>(null)
        private set
    private var existing: ChargeSessionEntity? = null
    private var fromNotification = false
    private var lastUnitByStation: Map<String, Double> = emptyMap()

    val stations = c.charges.stations.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val banks = c.charges.banks.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val payments = c.charges.paymentMethods.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val isEdit get() = editId != null
    val campaigns = c.charges.campaigns.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Tarih/istasyon/bankaya uyan en iyi kampanyalar (banka seçilmemişse karta göre öneri). */
    fun campaignSuggestions(): List<com.sarjdefteri.core.CampaignMatcher.Suggestion> {
        val date = java.time.Instant.ofEpochMilli(form.startedAt).atZone(java.time.ZoneId.systemDefault()).toLocalDate()
        return com.sarjdefteri.core.CampaignMatcher.suggestions(
            campaigns.value.map { it.toCampaign() }, date, form.station.ifBlank { null }, form.bank.ifBlank { null }, grossAmount(),
        ).take(2)
    }

    /** İndirim öncesi tutar: enerji × birim, yoksa girilen tutar (kampanya öncesi kabul edilir). */
    private fun grossAmount(): Double? {
        val e = Fmt.parse(form.energy); val u = Fmt.parse(form.unit)
        return if (e != null && u != null) e * u else Fmt.parse(form.total)
    }

    fun applyCampaign(s: com.sarjdefteri.core.CampaignMatcher.Suggestion) {
        val c = s.campaign
        var f = form.copy(campaign = c.title, bank = form.bank.ifBlank { c.bank.orEmpty() })
        val e = Fmt.parse(f.energy); val u = Fmt.parse(f.unit); val t = Fmt.parse(f.total)
        val gross = if (e != null && u != null) e * u else t
        if (gross != null && gross > 0) {
            val d = c.discountFor(gross)
            f = f.copy(discountAmount = Fmt.input(Math.round(d * 100) / 100.0), discountRate = Fmt.input(Math.round(d / gross * 10000) / 100.0))
            f = if (e != null && u != null) f.copy(total = "")            // toplam yeniden hesaplanır
            else f.copy(total = Fmt.input(Math.round((gross - d) * 100) / 100.0), unit = e?.let { Fmt.input(Math.round(gross / it * 10000) / 10000.0) } ?: f.unit)
        } else {
            f = f.copy(discountRate = Fmt.input(c.discountRate))
        }
        form = f
    }

    init {
        viewModelScope.launch {
            val all = c.charges.allSessionsOnce()
            lastUnitByStation = all.sortedBy { it.startedAt }
                .filter { it.station != null && it.unitPrice != null }
                .associate { it.station!!.trim() to it.unitPrice!! }
            lastKnownOdometer = c.charges.latestKnownOdometer()

            if (editId != null) {
                c.charges.getSession(editId)?.let { e ->
                    existing = e
                    form = e.toForm()
                    showAll = true
                }
            } else {
                val last = all.maxByOrNull { it.startedAt }
                var f = EntryForm(bank = last?.bank.orEmpty(), payment = last?.paymentMethod.orEmpty())
                c.pendingPrefill.value?.let { p ->
                    c.pendingPrefill.value = null
                    autoFilledFromApi = p.fromApi || p.energyKwh != null || p.totalPaid != null
                    fromNotification = !p.fromApi && autoFilledFromApi
                    f = f.copy(
                        startedAt = p.startedAt ?: f.startedAt,
                        endedAt = p.endedAt,
                        energy = Fmt.input(p.energyKwh),
                        battBefore = Fmt.input(p.batteryBefore),
                        battAfter = Fmt.input(p.batteryAfter),
                        odoBefore = Fmt.input(p.odometer),
                        station = p.station.orEmpty(),
                        total = Fmt.input(p.totalPaid),
                    )
                    Notifications.cancel(c.appContext, Notifications.ID_CHARGE_COMPLETE)
                }
                // Kullanıcı yükleme bitmeden yazmaya başladıysa girdilerini koru, yalnızca boş alanları doldur.
                val cur = form
                if (cur != initialForm) {
                    f = cur.copy(
                        startedAt = if (cur.startedAt == initialForm.startedAt) f.startedAt else cur.startedAt,
                        endedAt = cur.endedAt ?: f.endedAt,
                        station = cur.station.ifBlank { f.station },
                        bank = cur.bank.ifBlank { f.bank },
                        payment = cur.payment.ifBlank { f.payment },
                        energy = cur.energy.ifBlank { f.energy },
                        odoBefore = cur.odoBefore.ifBlank { f.odoBefore },
                        battBefore = cur.battBefore.ifBlank { f.battBefore },
                        battAfter = cur.battAfter.ifBlank { f.battAfter },
                        total = cur.total.ifBlank { f.total },
                    )
                }
                if (f.unit.isEmpty() && f.station.isNotEmpty() && f.total.isEmpty()) {
                    lastUnitByStation[f.station.trim()]?.let { f = f.copy(unit = Fmt.input(it)) }
                }
                form = f
            }
            loaded = true
        }
    }

    fun update(transform: EntryForm.() -> EntryForm) { form = form.transform() }

    fun selectStation(name: String) {
        val f = form.copy(station = name)
        form = if (f.unit.isBlank()) lastUnitByStation[name.trim()]?.let { f.copy(unit = Fmt.input(it)) } ?: f else f
    }

    fun useLastOdometer() { lastKnownOdometer?.let { form = form.copy(odoBefore = Fmt.input(it)) } }

    /** Canlı hesaplama önizlemesi. */
    fun computed(): PriceCalculator.PriceFields = PriceCalculator.complete(
        PriceCalculator.PriceFields(Fmt.parse(form.total), Fmt.parse(form.energy), Fmt.parse(form.unit),
            Fmt.parse(form.discountRate), Fmt.parse(form.discountAmount))
    )

    fun validationError(): String? {
        val f = form
        fun bad(s: String) = s.isNotBlank() && Fmt.parse(s) == null
        if (listOf(f.total, f.energy, f.unit, f.discountRate, f.discountAmount, f.odoBefore, f.odoAfter, f.battBefore, f.battAfter, f.distanceOverride).any(::bad))
            return "Sayısal alanlardan biri geçersiz"
        listOf(f.battBefore, f.battAfter).mapNotNull { Fmt.parse(it) }.forEach { if (it !in 0.0..100.0) return "Batarya yüzdesi 0–100 arasında olmalı" }
        Fmt.parse(f.discountRate)?.let { if (it !in 0.0..100.0) return "İndirim oranı 0–100 arasında olmalı" }
        val ob = Fmt.parse(f.odoBefore); val oa = Fmt.parse(f.odoAfter)
        if (ob != null && oa != null && oa < ob) return "Şarj sonrası km, öncesinden küçük olamaz"
        if (f.endedAt != null && f.endedAt < f.startedAt) return "Bitiş zamanı başlangıçtan önce olamaz"
        return null
    }

    fun save(onSaved: (Long) -> Unit) {
        if (validationError() != null) return
        val f = form
        val base = existing
        val entity = ChargeSessionEntity(
            id = base?.id ?: 0,
            startedAt = f.startedAt,
            endedAt = f.endedAt,
            station = f.station, location = f.location,
            totalPaid = Fmt.parse(f.total), energyKwh = Fmt.parse(f.energy), unitPrice = Fmt.parse(f.unit),
            discountRate = Fmt.parse(f.discountRate), discountAmount = Fmt.parse(f.discountAmount),
            campaign = f.campaign, bank = f.bank, paymentMethod = f.payment,
            odometerBefore = Fmt.parse(f.odoBefore), odometerAfter = Fmt.parse(f.odoAfter),
            batteryBefore = Fmt.parse(f.battBefore), batteryAfter = Fmt.parse(f.battAfter),
            notes = f.notes, excludeFromConsumption = f.exclude, distanceOverrideKm = Fmt.parse(f.distanceOverride),
            source = base?.source ?: when {
                fromNotification -> RecordSource.NOTIFICATION
                autoFilledFromApi -> RecordSource.TESLA_API
                else -> RecordSource.MANUAL
            },
            createdAt = base?.createdAt ?: System.currentTimeMillis(),
        )
        viewModelScope.launch { onSaved(c.charges.save(entity)) }
    }
}

private fun ChargeSessionEntity.toForm() = EntryForm(
    startedAt = startedAt, endedAt = endedAt, station = station.orEmpty(), location = location.orEmpty(),
    total = Fmt.input(totalPaid), energy = Fmt.input(energyKwh), unit = Fmt.input(unitPrice),
    discountRate = Fmt.input(discountRate), discountAmount = Fmt.input(discountAmount), campaign = campaign.orEmpty(),
    bank = bank.orEmpty(), payment = paymentMethod.orEmpty(), odoBefore = Fmt.input(odometerBefore),
    odoAfter = Fmt.input(odometerAfter), battBefore = Fmt.input(batteryBefore), battAfter = Fmt.input(batteryAfter),
    notes = notes.orEmpty(), exclude = excludeFromConsumption, distanceOverride = Fmt.input(distanceOverrideKm),
)
