package com.syildirim.teslacharge.core

/**
 * Hesaplamalar için platformdan bağımsız şarj kaydı görünümü.
 * Tüm alanlar (zaman ve id hariç) eksik olabilir; kullanıcı sonradan tamamlayabilir.
 */
data class ChargeRecord(
    val id: Long,
    /** Şarj başlangıç zamanı (epoch millis). */
    val startedAt: Long,
    val station: String? = null,
    val bank: String? = null,
    /** Ödenen toplam tutar (TL, indirim düşülmüş). */
    val totalPaid: Double? = null,
    /** Alınan enerji (kWh). */
    val energyKwh: Double? = null,
    /** Liste birim fiyatı (TL/kWh, indirim öncesi). */
    val unitPrice: Double? = null,
    val discountRate: Double? = null,
    val discountAmount: Double? = null,
    val odometerBefore: Double? = null,
    val odometerAfter: Double? = null,
    val batteryBefore: Double? = null,
    val batteryAfter: Double? = null,
    /** Bu kaydı tüketim hesaplarından hariç tut (hatalı/eksik veri). */
    val excludeFromConsumption: Boolean = false,
    /** Önceki şarjdan bu yana yapılan mesafe için elle düzeltme (km). */
    val distanceOverrideKm: Double? = null,
) {
    /** Etkin maliyet: ödenen tutar, yoksa enerji × birim fiyat − indirim. */
    val effectiveCost: Double?
        get() = totalPaid ?: run {
            val e = energyKwh ?: return@run null
            val u = unitPrice ?: return@run null
            e * u - (discountAmount ?: 0.0)
        }

    val hasDiscount: Boolean
        get() = (discountAmount ?: 0.0) > 0.0 || (discountRate ?: 0.0) > 0.0

    /** Şarjdaki referans kilometre (sonrası, yoksa öncesi). */
    val odometerReference: Double?
        get() = odometerAfter ?: odometerBefore

    /** Kayıtta önemli alanlar eksik mi? */
    val isIncomplete: Boolean
        get() = energyKwh == null || effectiveCost == null || (odometerBefore == null && odometerAfter == null)
}
