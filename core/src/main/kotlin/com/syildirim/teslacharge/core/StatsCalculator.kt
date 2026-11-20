package com.syildirim.teslacharge.core

import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId

/** Panel istatistikleri ve grafik serileri. */
object StatsCalculator {

    data class Totals(
        val totalCost: Double,
        val totalEnergyKwh: Double,
        val chargeCount: Int,
        /** Ödenen ortalama birim fiyat (Σ tutar / Σ kWh, ikisi de bilinen kayıtlardan). */
        val avgUnitCost: Double?,
        val totalDiscount: Double,
        val lastChargeAt: Long?,
    )

    data class MonthPoint(
        val month: YearMonth,
        val cost: Double,
        val energyKwh: Double,
        val distanceKm: Double,
        val avgUnitCost: Double?,
        val count: Int,
    )

    data class StationPoint(val station: String, val cost: Double, val energyKwh: Double, val count: Int) {
        val avgUnitCost: Double? get() = if (energyKwh > 0) cost / energyKwh else null
    }

    fun totals(records: List<ChargeRecord>): Totals {
        val priced = records.filter { it.effectiveCost != null && (it.energyKwh ?: 0.0) > 0 }
        val pricedEnergy = priced.sumOf { it.energyKwh!! }
        return Totals(
            totalCost = records.sumOf { it.effectiveCost ?: 0.0 },
            totalEnergyKwh = records.sumOf { it.energyKwh ?: 0.0 },
            chargeCount = records.size,
            avgUnitCost = if (pricedEnergy > 0) priced.sumOf { it.effectiveCost!! } / pricedEnergy else null,
            totalDiscount = records.sumOf { it.discountAmount ?: 0.0 },
            lastChargeAt = records.maxOfOrNull { it.startedAt },
        )
    }

    fun monthly(
        records: List<ChargeRecord>,
        segments: List<UsageCalculator.Segment>,
        zone: ZoneId = ZoneId.systemDefault(),
        lastMonths: Int = 12,
        now: YearMonth = YearMonth.now(zone),
    ): List<MonthPoint> {
        fun ym(millis: Long) = YearMonth.from(Instant.ofEpochMilli(millis).atZone(zone))
        val byMonth = records.groupBy { ym(it.startedAt) }
        val distByMonth = segments.filter { !it.odometerError && it.distanceKm != null }
            .groupBy { ym(it.endedAt) }
            .mapValues { (_, s) -> s.sumOf { it.distanceKm!! } }
        val first = (byMonth.keys + distByMonth.keys).minOrNull() ?: return emptyList()
        var start = now.minusMonths((lastMonths - 1).toLong())
        if (first.isAfter(start)) start = first
        val out = ArrayList<MonthPoint>()
        var m = start
        while (!m.isAfter(now)) {
            val recs = byMonth[m].orEmpty()
            val priced = recs.filter { it.effectiveCost != null && (it.energyKwh ?: 0.0) > 0 }
            val pe = priced.sumOf { it.energyKwh!! }
            out += MonthPoint(
                month = m,
                cost = recs.sumOf { it.effectiveCost ?: 0.0 },
                energyKwh = recs.sumOf { it.energyKwh ?: 0.0 },
                distanceKm = distByMonth[m] ?: 0.0,
                avgUnitCost = if (pe > 0) priced.sumOf { it.effectiveCost!! } / pe else null,
                count = recs.size,
            )
            m = m.plusMonths(1)
        }
        return out
    }

    fun byStation(records: List<ChargeRecord>): List<StationPoint> =
        records.groupBy { it.station?.trim()?.takeIf { s -> s.isNotEmpty() } ?: "Belirtilmemiş" }
            .map { (station, recs) ->
                val priced = recs.filter { it.effectiveCost != null }
                StationPoint(
                    station = station,
                    cost = priced.sumOf { it.effectiveCost!! },
                    energyKwh = priced.sumOf { it.energyKwh ?: 0.0 },
                    count = recs.size,
                )
            }
            .sortedByDescending { it.cost }
}
