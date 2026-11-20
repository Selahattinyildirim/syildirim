package com.sarjdefteri.core

/**
 * İki şarj arasındaki kullanım (segment) hesapları.
 *
 * Model: i. şarjda alınan enerji, (i−1). şarj ile i. şarj arasında harcanan enerjiyi yerine koyar.
 * Bu yüzden şebekeden ölçülen tüketim = Σ enerji(i) / Σ mesafe(i)  (i ≥ 1). Şarj kayıpları dahildir.
 * Batarya yüzdesi varsa ayrıca batarya bazlı (net) tüketim hesaplanır.
 */
object UsageCalculator {

    /** Segment başına tüketim gösterilmesi için gereken asgari mesafe. */
    const val MIN_SEGMENT_KM_FOR_RATE = 10.0

    data class Segment(
        val chargeId: Long,
        val previousChargeId: Long?,
        val endedAt: Long,
        /** Önceki şarjdan bu şarja kadar yapılan km (null: hesaplanamadı). */
        val distanceKm: Double?,
        val distanceIsManual: Boolean,
        val energyKwh: Double?,
        val cost: Double?,
        /** Önceki şarj sonrası % − bu şarj öncesi %. */
        val batteryUsedPct: Double?,
        val excluded: Boolean,
        /** Kilometre tutarsız (geri gitmiş) */
        val odometerError: Boolean,
    ) {
        val countsForConsumption: Boolean
            get() = !excluded && !odometerError && distanceKm != null && distanceKm >= 0 && energyKwh != null

        /** Bu segmentin şebeke bazlı tüketimi (kWh/100 km). */
        val consumptionPer100: Double?
            get() = if (countsForConsumption && distanceKm!! >= MIN_SEGMENT_KM_FOR_RATE) energyKwh!! / distanceKm * 100.0 else null

        val costPerKm: Double?
            get() = if (countsForConsumption && cost != null && distanceKm!! >= MIN_SEGMENT_KM_FOR_RATE) cost / distanceKm else null
    }

    data class Summary(
        val totalDistanceKm: Double,
        /** Şebekeden ölçülen ortalama tüketim, kWh/100 km. */
        val avgConsumptionPer100: Double?,
        /** Batarya yüzdesi bazlı ortalama tüketim, kWh/100 km (kapasite ayarına bağlı). */
        val avgBatteryConsumptionPer100: Double?,
        val costPerKm: Double?,
        val segmentsUsed: Int,
    )

    fun segments(records: List<ChargeRecord>): List<Segment> {
        val sorted = records.sortedBy { it.startedAt }
        return sorted.mapIndexed { index, cur ->
            val prev = sorted.getOrNull(index - 1)
            var odoError = false
            val distance: Double? = when {
                cur.distanceOverrideKm != null -> cur.distanceOverrideKm
                prev == null -> null
                else -> {
                    val start = prev.odometerReference
                    val end = cur.odometerBefore ?: cur.odometerAfter
                    if (start != null && end != null) {
                        val d = end - start
                        if (d < 0) { odoError = true; null } else d
                    } else null
                }
            }
            val batteryUsed = if (prev?.batteryAfter != null && cur.batteryBefore != null)
                prev.batteryAfter - cur.batteryBefore else null
            Segment(
                chargeId = cur.id,
                previousChargeId = prev?.id,
                endedAt = cur.startedAt,
                distanceKm = distance,
                distanceIsManual = cur.distanceOverrideKm != null,
                energyKwh = cur.energyKwh,
                cost = cur.effectiveCost,
                batteryUsedPct = batteryUsed,
                excluded = cur.excludeFromConsumption || prev == null && cur.distanceOverrideKm == null,
                odometerError = odoError,
            )
        }
    }

    fun summarize(segments: List<Segment>, batteryCapacityKwh: Double): Summary {
        val totalDistance = segments.filter { !it.odometerError }.sumOf { it.distanceKm ?: 0.0 }

        val usable = segments.filter { it.countsForConsumption }
        val dist = usable.sumOf { it.distanceKm!! }
        val energy = usable.sumOf { it.energyKwh!! }
        val consumption = if (dist >= MIN_SEGMENT_KM_FOR_RATE) energy / dist * 100.0 else null

        val withCost = usable.filter { it.cost != null }
        val costDist = withCost.sumOf { it.distanceKm!! }
        val costPerKm = if (costDist >= MIN_SEGMENT_KM_FOR_RATE) withCost.sumOf { it.cost!! } / costDist else null

        val battSegs = segments.filter {
            !it.excluded && !it.odometerError && it.batteryUsedPct != null && it.distanceKm != null && it.batteryUsedPct > 0
        }
        val battDist = battSegs.sumOf { it.distanceKm!! }
        val battKwh = battSegs.sumOf { it.batteryUsedPct!! } / 100.0 * batteryCapacityKwh
        val battConsumption = if (battDist >= MIN_SEGMENT_KM_FOR_RATE) battKwh / battDist * 100.0 else null

        return Summary(totalDistance, consumption, battConsumption, costPerKm, usable.size)
    }

    /**
     * Son şarjdan bu yana yapılan km.
     * @param latestOdometer son bilinen kilometre (manuel okuma, API ya da kayıt)
     */
    fun distanceSinceLastCharge(records: List<ChargeRecord>, latestOdometer: Double?): Double? {
        val last = records.maxByOrNull { it.startedAt } ?: return null
        val ref = last.odometerReference ?: return null
        val now = latestOdometer ?: return null
        return (now - ref).takeIf { it >= 0 }
    }
}
