package com.syildirim.teslacharge.tesla

data class TeslaTokens(
    val accessToken: String,
    val refreshToken: String?,
    /** epoch millis */
    val expiresAt: Long,
)

data class TeslaVehicle(
    val id: String,
    val vin: String,
    val displayName: String,
    /** online / asleep / offline */
    val state: String,
)

/** Fleet API'den okunan, şarj takibi için gerekli alanlar. */
data class VehicleSnapshot(
    val fetchedAt: Long,
    val odometerKm: Double?,
    val batteryLevel: Double?,
    /** Charging, Complete, Disconnected, Stopped, Starting, NoPower */
    val chargingState: String?,
    val chargeEnergyAddedKwh: Double?,
    val chargerPowerKw: Double?,
    val minutesToFullCharge: Int?,
    val fastChargerPresent: Boolean?,
    val fastChargerBrand: String?,
    val latitude: Double?,
    val longitude: Double?,
    val activeRouteDestination: String?,
    val activeRouteMinutesToArrival: Double?,
    val shiftState: String?,
) {
    val isCharging: Boolean get() = chargingState == "Charging" || chargingState == "Starting"
    val isChargeFinished: Boolean get() = chargingState == "Complete" || chargingState == "Stopped"
    val isPluggedIn: Boolean get() = chargingState != null && chargingState != "Disconnected"
}

sealed class TeslaResult<out T> {
    data class Ok<T>(val value: T) : TeslaResult<T>()
    /** Araç uykuda/çevrimdışı — bilinçli olarak uyandırılmaz. */
    data object VehicleAsleep : TeslaResult<Nothing>()
    data object NotConfigured : TeslaResult<Nothing>()
    data class Error(val message: String, val httpCode: Int? = null) : TeslaResult<Nothing>()
}
