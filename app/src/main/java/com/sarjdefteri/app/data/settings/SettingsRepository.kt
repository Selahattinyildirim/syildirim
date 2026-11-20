package com.sarjdefteri.app.data.settings

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "settings")

enum class TeslaRegion(val label: String, val apiBase: String) {
    EU("Avrupa / Türkiye", "https://fleet-api.prd.eu.vn.cloud.tesla.com"),
    NA("Kuzey Amerika / Asya-Pasifik", "https://fleet-api.prd.na.vn.cloud.tesla.com"),
    CN("Çin", "https://fleet-api.prd.cn.vn.cloud.tesla.cn"),
}

data class AppSettings(
    val remindersEnabled: Boolean = true,
    val remindOnConnect: Boolean = true,
    val remindOnDisconnect: Boolean = true,
    val chargeDetectionViaApi: Boolean = true,
    val navigationWatch: Boolean = false,
    val incompleteRecordReminder: Boolean = true,
    val bluetoothDeviceAddresses: Set<String> = emptySet(),
    val bluetoothDeviceNames: Set<String> = emptySet(),
    val detectTeslaBleNames: Boolean = false,
    val batteryCapacityKwh: Double = 75.0,
    val teslaRegion: TeslaRegion = TeslaRegion.EU,
    val teslaClientId: String = "",
    val teslaRedirectUri: String = DEFAULT_REDIRECT_URI,
    val teslaVehicleId: String = "",
    val teslaVehicleName: String = "",
    val teslaVin: String = "",
    val teslaPartnerDomain: String = "",
    val vehicleName: String = "Aracım",
    /** Google Play'den doğrulanan Pro durumu (önbellek). */
    val proPurchased: Boolean = false,
    /** Yalnızca debug derlemelerinde: Pro özelliklerini test etmek için. */
    val proDebugOverride: Boolean = false,
    /** Kullanıcının takip ettiği kampanya kaynağı (ör. bir WhatsApp kanalı veya banka sayfası). */
    val campaignSourceUrl: String = "",
    val campaignMonthlyReminder: Boolean = true,
) {
    val isPro: Boolean get() = proPurchased || proDebugOverride

    companion object {
        const val DEFAULT_REDIRECT_URI = "sarjdefteri://oauth/callback"
    }
}

class SettingsRepository(private val context: Context) {
    private object K {
        val remindersEnabled = booleanPreferencesKey("reminders_enabled")
        val remindOnConnect = booleanPreferencesKey("remind_on_connect")
        val remindOnDisconnect = booleanPreferencesKey("remind_on_disconnect")
        val chargeDetection = booleanPreferencesKey("charge_detection_api")
        val navWatch = booleanPreferencesKey("navigation_watch")
        val incomplete = booleanPreferencesKey("incomplete_reminder")
        val btAddresses = stringSetPreferencesKey("bt_addresses")
        val btNames = stringSetPreferencesKey("bt_names")
        val detectBle = booleanPreferencesKey("detect_tesla_ble")
        val capacity = doublePreferencesKey("battery_capacity")
        val region = stringPreferencesKey("tesla_region")
        val clientId = stringPreferencesKey("tesla_client_id")
        val redirect = stringPreferencesKey("tesla_redirect_uri")
        val vehicleId = stringPreferencesKey("tesla_vehicle_id")
        val vehicleName = stringPreferencesKey("tesla_vehicle_name")
        val vin = stringPreferencesKey("tesla_vin")
        val partnerDomain = stringPreferencesKey("tesla_partner_domain")
        val carName = stringPreferencesKey("vehicle_name")
        val pro = booleanPreferencesKey("pro_purchased")
        val proDebug = booleanPreferencesKey("pro_debug_override")
        val campaignUrl = stringPreferencesKey("campaign_source_url")
        val campaignReminder = booleanPreferencesKey("campaign_monthly_reminder")
    }

    val settings: Flow<AppSettings> = context.dataStore.data.map { p -> p.toSettings() }

    suspend fun current(): AppSettings = settings.first()

    private fun Preferences.toSettings() = AppSettings(
        remindersEnabled = this[K.remindersEnabled] ?: true,
        remindOnConnect = this[K.remindOnConnect] ?: true,
        remindOnDisconnect = this[K.remindOnDisconnect] ?: true,
        chargeDetectionViaApi = this[K.chargeDetection] ?: true,
        navigationWatch = this[K.navWatch] ?: false,
        incompleteRecordReminder = this[K.incomplete] ?: true,
        bluetoothDeviceAddresses = this[K.btAddresses] ?: emptySet(),
        bluetoothDeviceNames = this[K.btNames] ?: emptySet(),
        detectTeslaBleNames = this[K.detectBle] ?: false,
        batteryCapacityKwh = this[K.capacity] ?: 75.0,
        teslaRegion = this[K.region]?.let { runCatching { TeslaRegion.valueOf(it) }.getOrNull() } ?: TeslaRegion.EU,
        teslaClientId = this[K.clientId] ?: "",
        teslaRedirectUri = this[K.redirect] ?: AppSettings.DEFAULT_REDIRECT_URI,
        teslaVehicleId = this[K.vehicleId] ?: "",
        teslaVehicleName = this[K.vehicleName] ?: "",
        teslaVin = this[K.vin] ?: "",
        teslaPartnerDomain = this[K.partnerDomain] ?: "",
        vehicleName = this[K.carName] ?: "Aracım",
        proPurchased = this[K.pro] ?: false,
        proDebugOverride = this[K.proDebug] ?: false,
        campaignSourceUrl = this[K.campaignUrl] ?: "",
        campaignMonthlyReminder = this[K.campaignReminder] ?: true,
    )

    suspend fun setCampaignSourceUrl(v: String) = context.dataStore.edit { it[K.campaignUrl] = v.trim() }
    suspend fun setCampaignMonthlyReminder(v: Boolean) = context.dataStore.edit { it[K.campaignReminder] = v }

    suspend fun setVehicleName(v: String) = context.dataStore.edit { it[K.carName] = v.trim().ifEmpty { "Aracım" }.take(40) }
    suspend fun setProPurchased(v: Boolean) = context.dataStore.edit { it[K.pro] = v }
    suspend fun setProDebugOverride(v: Boolean) = context.dataStore.edit { it[K.proDebug] = v }

    suspend fun setRemindersEnabled(v: Boolean) = context.dataStore.edit { it[K.remindersEnabled] = v }
    suspend fun setRemindOnConnect(v: Boolean) = context.dataStore.edit { it[K.remindOnConnect] = v }
    suspend fun setRemindOnDisconnect(v: Boolean) = context.dataStore.edit { it[K.remindOnDisconnect] = v }
    suspend fun setChargeDetection(v: Boolean) = context.dataStore.edit { it[K.chargeDetection] = v }
    suspend fun setNavigationWatch(v: Boolean) = context.dataStore.edit { it[K.navWatch] = v }
    suspend fun setIncompleteReminder(v: Boolean) = context.dataStore.edit { it[K.incomplete] = v }
    suspend fun setDetectTeslaBle(v: Boolean) = context.dataStore.edit { it[K.detectBle] = v }
    suspend fun setBatteryCapacity(v: Double) = context.dataStore.edit { it[K.capacity] = v }

    suspend fun toggleBluetoothDevice(address: String, name: String?, selected: Boolean) = context.dataStore.edit { p ->
        val addrs = (p[K.btAddresses] ?: emptySet()).toMutableSet()
        val names = (p[K.btNames] ?: emptySet()).toMutableSet()
        if (selected) { addrs += address; name?.let { names += it } } else { addrs -= address; name?.let { names -= it } }
        p[K.btAddresses] = addrs; p[K.btNames] = names
    }

    suspend fun setTeslaConfig(region: TeslaRegion, clientId: String, redirectUri: String) = context.dataStore.edit {
        it[K.region] = region.name; it[K.clientId] = clientId.trim(); it[K.redirect] = redirectUri.trim()
    }

    suspend fun setPartnerDomain(domain: String) = context.dataStore.edit { it[K.partnerDomain] = domain.trim() }

    suspend fun setVehicle(id: String, name: String, vin: String) = context.dataStore.edit {
        it[K.vehicleId] = id; it[K.vehicleName] = name; it[K.vin] = vin
    }

    suspend fun clearTesla() = context.dataStore.edit {
        it.remove(K.vehicleId); it.remove(K.vehicleName); it.remove(K.vin)
    }
}
