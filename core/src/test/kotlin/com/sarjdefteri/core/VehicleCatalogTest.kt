package com.sarjdefteri.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VehicleCatalogTest {
    @Test fun searchIsAccentAndCaseInsensitive() {
        val togg = VehicleCatalog.search("togg")
        assertTrue(togg.isNotEmpty())
        assertTrue(togg.all { it.brand == "Togg" })
        assertTrue(VehicleCatalog.search("MODEL y").any { it.brand == "Tesla" })
        assertTrue(VehicleCatalog.search("megane").any { it.brand == "Renault" })
        assertEquals(VehicleCatalog.all.size, VehicleCatalog.search("").size)
    }

    @Test fun idsAreUniqueAndCapacitiesPlausible() {
        assertEquals(VehicleCatalog.all.size, VehicleCatalog.all.map { it.id }.toSet().size)
        assertTrue(VehicleCatalog.all.all { it.usableKwh in 20.0..130.0 })
    }

    @Test fun lookupById() {
        val first = VehicleCatalog.all.first()
        assertEquals(first, VehicleCatalog.byId(first.id))
        assertNull(VehicleCatalog.byId("yok"))
        assertNotNull(VehicleCatalog.brands.firstOrNull { it == "Togg" })
    }

    @Test fun displayName() {
        val p = VehiclePreset("x", "BYD", "Atto 3", "Comfort", 60.5)
        assertEquals("BYD Atto 3 Comfort", p.displayName)
    }
}
