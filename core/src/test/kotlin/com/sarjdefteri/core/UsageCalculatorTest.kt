package com.sarjdefteri.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UsageCalculatorTest {
    private fun rec(id: Long, t: Long, odoB: Double?, odoA: Double? = odoB, kwh: Double? = null, cost: Double? = null,
                    bB: Double? = null, bA: Double? = null, exclude: Boolean = false, override: Double? = null) =
        ChargeRecord(id = id, startedAt = t, energyKwh = kwh, totalPaid = cost, odometerBefore = odoB, odometerAfter = odoA,
            batteryBefore = bB, batteryAfter = bA, excludeFromConsumption = exclude, distanceOverrideKm = override)

    @Test fun distanceAndConsumption() {
        val records = listOf(
            rec(1, 1000, 10_000.0, kwh = 30.0, cost = 300.0, bB = 20.0, bA = 80.0),
            rec(2, 2000, 10_300.0, kwh = 48.0, cost = 480.0, bB = 30.0, bA = 90.0),
            rec(3, 3000, 10_500.0, kwh = 34.0, cost = 340.0, bB = 55.0, bA = 80.0),
        )
        val segs = UsageCalculator.segments(records)
        assertNull(segs[0].distanceKm)
        assertEquals(300.0, segs[1].distanceKm!!, 0.0)
        assertEquals(200.0, segs[2].distanceKm!!, 0.0)
        assertEquals(16.0, segs[1].consumptionPer100!!, 0.001)

        val s = UsageCalculator.summarize(segs, batteryCapacityKwh = 75.0)
        assertEquals(500.0, s.totalDistanceKm, 0.0)
        // (48 + 34) / 500 * 100
        assertEquals(16.4, s.avgConsumptionPer100!!, 0.001)
        assertEquals((480.0 + 340.0) / 500.0, s.costPerKm!!, 0.0001)
        // batarya: (80-30)+(90-55) = 85% of 75 kWh = 63.75 kWh / 500 km
        assertEquals(12.75, s.avgBatteryConsumptionPer100!!, 0.001)
    }

    @Test fun excludedAndOverride() {
        val records = listOf(
            rec(1, 1000, 10_000.0, kwh = 30.0),
            rec(2, 2000, null, kwh = 5.0, exclude = true),
            rec(3, 3000, 10_400.0, kwh = 60.0),
            rec(4, 4000, null, kwh = 20.0, override = 150.0),
        )
        val segs = UsageCalculator.segments(records)
        assertTrue(segs[1].excluded)
        assertNull(segs[2].distanceKm) // önceki kayıtta km yok
        assertEquals(150.0, segs[3].distanceKm!!, 0.0)
        val s = UsageCalculator.summarize(segs, 75.0)
        assertEquals(1, s.segmentsUsed)
        assertEquals(20.0 / 150.0 * 100, s.avgConsumptionPer100!!, 0.001)
    }

    @Test fun odometerGoingBackwardsIsFlagged() {
        val segs = UsageCalculator.segments(listOf(rec(1, 1, 500.0), rec(2, 2, 400.0, kwh = 10.0)))
        assertTrue(segs[1].odometerError)
        assertNull(segs[1].distanceKm)
    }

    @Test fun sinceLastCharge() {
        val records = listOf(rec(1, 1, 1000.0, 1001.0), rec(2, 2, 1200.0, 1202.0))
        assertEquals(98.0, UsageCalculator.distanceSinceLastCharge(records, 1300.0)!!, 0.0)
        assertNull(UsageCalculator.distanceSinceLastCharge(records, null))
    }
}
