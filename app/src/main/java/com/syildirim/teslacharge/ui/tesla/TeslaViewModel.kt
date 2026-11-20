package com.syildirim.teslacharge.ui.tesla

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.syildirim.teslacharge.AppContainer
import com.syildirim.teslacharge.data.db.OdometerSource
import com.syildirim.teslacharge.data.settings.AppSettings
import com.syildirim.teslacharge.data.settings.TeslaRegion
import com.syildirim.teslacharge.tesla.TeslaResult
import com.syildirim.teslacharge.tesla.TeslaVehicle
import com.syildirim.teslacharge.tesla.VehicleSnapshot
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class TeslaViewModel(private val c: AppContainer) : ViewModel() {
    val settings = c.settings.settings.stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())
    val connected = MutableStateFlow(c.tesla.hasTokens())
    val hasSecret = MutableStateFlow(c.tesla.hasClientSecret())
    val busy = MutableStateFlow(false)
    val message = MutableStateFlow<String?>(null)
    val vehicles = MutableStateFlow<List<TeslaVehicle>>(emptyList())
    val snapshot = MutableStateFlow<VehicleSnapshot?>(null)

    init {
        viewModelScope.launch {
            c.pendingOAuthRedirect.collect { uri -> if (uri != null) { c.pendingOAuthRedirect.value = null; handleRedirect(uri) } }
        }
    }

    fun saveConfig(region: TeslaRegion, clientId: String, secret: String, redirect: String) = viewModelScope.launch {
        c.settings.setTeslaConfig(region, clientId, redirect.ifBlank { AppSettings.DEFAULT_REDIRECT_URI })
        if (secret.isNotBlank()) c.tesla.saveClientSecret(secret)
        hasSecret.value = c.tesla.hasClientSecret()
        message.value = "Ayarlar kaydedildi"
    }

    suspend fun authorizeUrl(): String? = c.tesla.buildAuthorizeUrl().also { if (it == null) message.value = "Önce Client ID girin" }

    fun handleRedirect(uri: String) = run(onOk = { "Giriş başarılı" }) {
        when (val r = c.tesla.handleRedirect(uri)) {
            is TeslaResult.Ok -> { connected.value = true; loadVehiclesInternal(); r }
            else -> r
        }
    }

    fun saveManualTokens(access: String, refresh: String) {
        if (access.isBlank()) return
        c.tesla.saveManualTokens(access, refresh)
        connected.value = true
        loadVehicles()
    }

    fun loadVehicles() = run(onOk = { "${vehicles.value.size} araç bulundu" }) { loadVehiclesInternal() }

    private suspend fun loadVehiclesInternal(): TeslaResult<Unit> = when (val r = c.tesla.listVehicles()) {
        is TeslaResult.Ok -> {
            vehicles.value = r.value
            if (r.value.size == 1 && settings.value.teslaVehicleId.isEmpty()) selectVehicleInternal(r.value.first())
            TeslaResult.Ok(Unit)
        }
        is TeslaResult.Error -> r
        TeslaResult.NotConfigured -> TeslaResult.NotConfigured
        TeslaResult.VehicleAsleep -> TeslaResult.VehicleAsleep
    }

    private suspend fun selectVehicleInternal(v: TeslaVehicle) = c.settings.setVehicle(v.id, v.displayName, v.vin)
    fun selectVehicle(v: TeslaVehicle) = viewModelScope.launch { selectVehicleInternal(v); message.value = "${v.displayName} seçildi" }

    fun readNow() = run(onOk = { "Veri okundu (araç uyandırılmadı)" }) {
        when (val r = c.tesla.snapshotIfOnline(includeLocation = true)) {
            is TeslaResult.Ok -> {
                snapshot.value = r.value
                r.value.odometerKm?.let { c.charges.addOdometerReading(it, r.value.batteryLevel, OdometerSource.TESLA_API) }
                TeslaResult.Ok(Unit)
            }
            is TeslaResult.Error -> r
            TeslaResult.VehicleAsleep -> TeslaResult.VehicleAsleep
            TeslaResult.NotConfigured -> TeslaResult.NotConfigured
        }
    }

    fun registerPartner(domain: String) = run(onOk = { "Bölge kaydı tamam" }) {
        if (domain.isBlank()) TeslaResult.Error("Alan adı girin") else c.tesla.registerPartnerAccount(domain)
    }

    fun disconnect() = viewModelScope.launch {
        c.tesla.disconnect(); connected.value = false; vehicles.value = emptyList(); snapshot.value = null
        message.value = "Tesla bağlantısı kaldırıldı, tokenlar silindi"
    }

    fun forgetSecret() { c.tesla.forgetClientSecret(); hasSecret.value = false }

    private fun run(onOk: () -> String, block: suspend () -> TeslaResult<*>) = viewModelScope.launch {
        busy.value = true
        message.value = when (val r = runCatching { block() }.getOrElse { TeslaResult.Error(it.message ?: "Hata") }) {
            is TeslaResult.Ok<*> -> onOk()
            TeslaResult.VehicleAsleep -> "Araç uykuda. Pil tüketmemek için uyandırılmadı; araç uyanıkken tekrar dene."
            TeslaResult.NotConfigured -> "Önce Tesla ile giriş yapıp araç seçin"
            is TeslaResult.Error -> r.message
        }
        busy.value = false
    }
}
