package com.sarjdefteri.app.ui.campaigns

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sarjdefteri.app.AppContainer
import com.sarjdefteri.app.data.db.CampaignEntity
import com.sarjdefteri.app.data.settings.AppSettings
import com.sarjdefteri.app.ui.Fmt
import java.time.LocalDate
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class CampaignsViewModel(private val c: AppContainer) : ViewModel() {
    val campaigns = c.charges.campaigns.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val settings = c.settings.settings.stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())
    fun setSourceUrl(v: String) = viewModelScope.launch { c.settings.setCampaignSourceUrl(v) }
    fun toggleActive(e: CampaignEntity) = viewModelScope.launch { c.charges.saveCampaign(e.copy(active = !e.active)) }
    fun delete(e: CampaignEntity) = viewModelScope.launch { c.charges.deleteCampaign(e) }
    /** Süresi dolmuş bir kampanyayı bu ay için kopyalar (aylık yenilenen kampanyalar için). */
    fun renewForThisMonth(e: CampaignEntity) = viewModelScope.launch {
        val today = LocalDate.now()
        c.charges.saveCampaign(e.copy(id = 0, active = true, validFrom = today.withDayOfMonth(1).toEpochDay(),
            validTo = today.withDayOfMonth(today.lengthOfMonth()).toEpochDay(), createdAt = System.currentTimeMillis()))
    }
}

data class CampaignForm(
    val title: String = "",
    val bank: String = "",
    val operator: String = "",
    val rate: String = "",
    val maxDiscount: String = "",
    val minSpend: String = "",
    val from: LocalDate? = LocalDate.now(),
    val to: LocalDate? = LocalDate.now().let { it.withDayOfMonth(it.lengthOfMonth()) },
    val notes: String = "",
    val sourceUrl: String = "",
)

class CampaignEditViewModel(private val c: AppContainer, private val id: Long?) : ViewModel() {
    var form by mutableStateOf(CampaignForm())
        private set
    private var existing: CampaignEntity? = null
    val stations = c.charges.stations.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val banks = c.charges.banks.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val isEdit get() = id != null

    init {
        if (id != null) viewModelScope.launch {
            c.charges.getCampaign(id)?.let { e ->
                existing = e
                form = CampaignForm(e.title, e.bank.orEmpty(), e.operator.orEmpty(), Fmt.input(e.discountRate), Fmt.input(e.maxDiscount),
                    Fmt.input(e.minSpend), e.validFrom?.let(LocalDate::ofEpochDay), e.validTo?.let(LocalDate::ofEpochDay),
                    e.notes.orEmpty(), e.sourceUrl.orEmpty())
            }
        }
    }

    fun update(t: CampaignForm.() -> CampaignForm) { form = form.t() }

    fun error(): String? {
        val r = Fmt.parse(form.rate) ?: return "İndirim oranı gerekli"
        if (r <= 0 || r > 100) return "İndirim oranı 0–100 arasında olmalı"
        if (form.maxDiscount.isNotBlank() && Fmt.parse(form.maxDiscount) == null) return "Tavan tutar geçersiz"
        if (form.minSpend.isNotBlank() && Fmt.parse(form.minSpend) == null) return "En az harcama geçersiz"
        val f = form.from; val t = form.to
        if (f != null && t != null && t.isBefore(f)) return "Bitiş tarihi başlangıçtan önce olamaz"
        return null
    }

    fun autoTitle(): String = listOfNotNull(
        form.bank.trim().ifEmpty { null }, form.operator.trim().ifEmpty { null },
        Fmt.parse(form.rate)?.let { "%" + Fmt.num(it, 1) },
    ).joinToString(" · ").ifEmpty { "Kampanya" }

    fun save(onDone: () -> Unit) {
        if (error() != null) return
        val f = form
        val e = CampaignEntity(
            id = existing?.id ?: 0, title = f.title.trim().ifEmpty { autoTitle() },
            bank = f.bank.trim().ifEmpty { null }, operator = f.operator.trim().ifEmpty { null },
            discountRate = Fmt.parse(f.rate)!!, maxDiscount = Fmt.parse(f.maxDiscount), minSpend = Fmt.parse(f.minSpend),
            validFrom = f.from?.toEpochDay(), validTo = f.to?.toEpochDay(), notes = f.notes.trim().ifEmpty { null },
            sourceUrl = f.sourceUrl.trim().ifEmpty { null }, active = existing?.active ?: true,
            createdAt = existing?.createdAt ?: System.currentTimeMillis(),
        )
        viewModelScope.launch { c.charges.saveCampaign(e); onDone() }
    }
}
