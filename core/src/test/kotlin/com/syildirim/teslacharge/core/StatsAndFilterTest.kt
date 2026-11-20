package com.syildirim.teslacharge.core

import java.time.LocalDateTime
import java.time.YearMonth
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StatsAndFilterTest {
    private fun t(y: Int, m: Int, d: Int) = LocalDateTime.of(y, m, d, 12, 0).toInstant(ZoneOffset.UTC).toEpochMilli()

    private val records = listOf(
        ChargeRecord(1, t(2026, 9, 3), station = "Tesla Supercharger Kadıköy", bank = "Akbank", totalPaid = 400.0, energyKwh = 40.0, odometerBefore = 1000.0),
        ChargeRecord(2, t(2026, 10, 5), station = "ZES Ataşehir", bank = "Garanti", totalPaid = 270.0, energyKwh = 30.0, discountAmount = 30.0, odometerBefore = 1300.0),
        ChargeRecord(3, t(2026, 10, 20), station = "Tesla Supercharger Kadıköy", bank = "Akbank", totalPaid = 500.0, energyKwh = 50.0, odometerBefore = 1700.0),
    )

    @Test fun totals() {
        val tt = StatsCalculator.totals(records)
        assertEquals(1170.0, tt.totalCost, 0.0)
        assertEquals(120.0, tt.totalEnergyKwh, 0.0)
        assertEquals(3, tt.chargeCount)
        assertEquals(9.75, tt.avgUnitCost!!, 0.0001)
    }

    @Test fun monthly() {
        val segs = UsageCalculator.segments(records)
        val m = StatsCalculator.monthly(records, segs, ZoneOffset.UTC, 12, YearMonth.of(2026, 11))
        assertEquals(listOf(YearMonth.of(2026, 9), YearMonth.of(2026, 10), YearMonth.of(2026, 11)), m.map { it.month })
        assertEquals(770.0, m[1].cost, 0.0)
        assertEquals(700.0, m[1].distanceKm, 0.0)
        assertEquals(0, m[2].count)
    }

    @Test fun byStation() {
        val s = StatsCalculator.byStation(records)
        assertEquals("Tesla Supercharger Kadıköy", s.first().station)
        assertEquals(900.0, s.first().cost, 0.0)
    }

    @Test fun filters() {
        assertEquals(1, records.count { RecordFilter(discount = RecordFilter.DiscountFilter.DISCOUNTED).matches(it) })
        assertEquals(2, records.count { RecordFilter(banks = setOf("Akbank")).matches(it) })
        assertEquals(1, records.count { RecordFilter(minAmount = 300.0, maxAmount = 450.0).matches(it) })
        val f = RecordFilter(fromMillis = t(2026, 10, 1))
        assertTrue(f.isActive)
        assertFalse(f.matches(records[0]))
    }

    @Test fun bleName() {
        assertTrue(TeslaBleName.looksLikeTesla(TeslaBleName.forVin("5YJ3E1EA7KF000000")))
        assertFalse(TeslaBleName.looksLikeTesla("Tesla Model Y"))
    }
}
