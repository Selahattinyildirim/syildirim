package com.sarjdefteri.core

import com.sarjdefteri.core.PriceCalculator.PriceFields
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PriceCalculatorTest {
    @Test fun totalFromEnergyAndUnit() {
        val r = PriceCalculator.complete(PriceFields(energyKwh = 40.0, unitPrice = 10.0))
        assertEquals(400.0, r.totalPaid!!, 0.001)
        assertNull(r.discountAmount)
    }

    @Test fun totalWithDiscountRate() {
        val r = PriceCalculator.complete(PriceFields(energyKwh = 40.0, unitPrice = 10.0, discountRate = 20.0))
        assertEquals(80.0, r.discountAmount!!, 0.001)
        assertEquals(320.0, r.totalPaid!!, 0.001)
    }

    @Test fun unitFromTotalAndEnergy() {
        val r = PriceCalculator.complete(PriceFields(totalPaid = 450.0, energyKwh = 50.0))
        assertEquals(9.0, r.unitPrice!!, 0.0001)
    }

    @Test fun unitFromTotalEnergyAndRate() {
        val r = PriceCalculator.complete(PriceFields(totalPaid = 360.0, energyKwh = 40.0, discountRate = 10.0))
        assertEquals(40.0, r.discountAmount!!, 0.01)
        assertEquals(10.0, r.unitPrice!!, 0.001)
    }

    @Test fun energyFromTotalAndUnit() {
        val r = PriceCalculator.complete(PriceFields(totalPaid = 300.0, unitPrice = 7.5))
        assertEquals(40.0, r.energyKwh!!, 0.001)
    }

    @Test fun inferDiscountFromGap() {
        val r = PriceCalculator.complete(PriceFields(totalPaid = 350.0, energyKwh = 40.0, unitPrice = 10.0))
        assertEquals(50.0, r.discountAmount!!, 0.001)
        assertEquals(12.5, r.discountRate!!, 0.001)
        assertTrue(PriceCalculator.isConsistent(r))
    }
}
