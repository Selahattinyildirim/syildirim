package com.syildirim.teslacharge.core

import kotlin.math.abs

/**
 * Tutar / enerji / birim fiyat / indirim alanlarından eksik olanları tutarlı şekilde tamamlar.
 * Birim fiyat indirim öncesi liste fiyatıdır: toplam = enerji × birim − indirim.
 */
object PriceCalculator {

    data class PriceFields(
        val totalPaid: Double? = null,
        val energyKwh: Double? = null,
        val unitPrice: Double? = null,
        val discountRate: Double? = null,
        val discountAmount: Double? = null,
    )

    fun complete(input: PriceFields): PriceFields {
        var total = input.totalPaid?.takeIf { it >= 0 }
        var energy = input.energyKwh?.takeIf { it > 0 }
        var unit = input.unitPrice?.takeIf { it > 0 }
        var rate = input.discountRate?.takeIf { it in 0.0..100.0 }
        var amount = input.discountAmount?.takeIf { it >= 0 }

        fun gross(): Double? = if (energy != null && unit != null) energy!! * unit!! else null

        // İndirim oranı ↔ tutarı (brüt biliniyorsa)
        gross()?.let { g ->
            if (amount == null && rate != null) amount = round2(g * rate!! / 100.0)
            if (rate == null && amount != null && g > 0) rate = round2(amount!! / g * 100.0)
        }

        // Eksik ana alan
        when {
            total == null && energy != null && unit != null ->
                total = round2(energy!! * unit!! - (amount ?: 0.0))

            unit == null && total != null && energy != null -> {
                if (amount == null && rate != null && rate!! < 100.0) {
                    // toplam = brüt × (1 − oran) → brüt = toplam / (1 − oran)
                    val g = total!! / (1 - rate!! / 100.0)
                    amount = round2(g - total!!)
                }
                unit = round4((total!! + (amount ?: 0.0)) / energy!!)
            }

            energy == null && total != null && unit != null -> {
                if (amount == null && rate != null && rate!! < 100.0) {
                    val g = total!! / (1 - rate!! / 100.0)
                    amount = round2(g - total!!)
                }
                energy = round3((total!! + (amount ?: 0.0)) / unit!!)
            }
        }

        // Toplam brütten düşükse ve indirim girilmemişse farkı indirim olarak yorumla
        val g = gross()
        if (g != null && total != null && amount == null && rate == null && g - total!! > 0.01) {
            amount = round2(g - total!!)
            rate = round2(amount!! / g * 100.0)
        }
        if (g != null && amount != null && rate == null && g > 0) rate = round2(amount!! / g * 100.0)

        return PriceFields(total, energy, unit, rate, amount)
    }

    /** Etkin (ödenen) birim fiyat: toplam / kWh. */
    fun effectiveUnitPrice(total: Double?, energy: Double?): Double? =
        if (total != null && energy != null && energy > 0) total / energy else null

    fun isConsistent(f: PriceFields, tolerance: Double = 0.05): Boolean {
        val t = f.totalPaid ?: return true
        val e = f.energyKwh ?: return true
        val u = f.unitPrice ?: return true
        val expected = e * u - (f.discountAmount ?: 0.0)
        return abs(expected - t) <= maxOf(tolerance * t, 1.0)
    }

    private fun round2(v: Double) = Math.round(v * 100.0) / 100.0
    private fun round3(v: Double) = Math.round(v * 1000.0) / 1000.0
    private fun round4(v: Double) = Math.round(v * 10000.0) / 10000.0
}
