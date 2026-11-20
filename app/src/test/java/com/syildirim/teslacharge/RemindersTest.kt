package com.syildirim.teslacharge

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.syildirim.teslacharge.core.TeslaBleName
import com.syildirim.teslacharge.data.settings.AppSettings
import com.syildirim.teslacharge.reminders.ReminderEngine
import com.syildirim.teslacharge.reminders.ReminderState
import com.syildirim.teslacharge.tesla.TeslaRepository
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class RemindersTest {
    private val ctx = ApplicationProvider.getApplicationContext<Context>()
    private val app get() = ctx as TeslaChargeApp

    @Test fun cooldownPreventsDuplicateNotifications() {
        val st = ReminderState(ctx)
        val t0 = 1_763_000_000_000L
        assertTrue(st.tryAcquire("connect", 3_600_000, now = t0))
        assertFalse(st.tryAcquire("connect", 3_600_000, now = t0 + 1_800_000))
        assertTrue(st.tryAcquire("connect", 3_600_000, now = t0 + 3_600_001))
    }

    @Test fun connectionTrackingAcrossMultipleLinks() {
        val st = ReminderState(ctx)
        assertTrue(st.onConnected("AA"))     // ilk bağlantı → olay
        assertFalse(st.onConnected("BB"))    // ikinci link (ör. BLE) → yeni olay yok
        assertFalse(st.onDisconnected("AA")) // hâlâ BB bağlı
        assertTrue(st.onDisconnected("BB"))  // tamamen ayrıldı
    }

    @Test fun deviceMatching() {
        val engine = app.container.reminderEngine
        val none = AppSettings()
        assertTrue(engine.matches(none, "00:11", "Tesla Model Y"))
        assertFalse(engine.matches(none, "00:11", "JBL Flip"))
        val selected = AppSettings(bluetoothDeviceAddresses = setOf("00:11"))
        assertTrue(engine.matches(selected, "00:11", "Benim Arabam"))
        assertFalse(engine.matches(selected, "22:33", "Tesla Model Y"))
        val ble = selected.copy(detectTeslaBleNames = true)
        assertTrue(engine.matches(ble, "44:55", TeslaBleName.forVin("XP7YGCEK0RB000000")))
    }

    @Test fun chargerDestinationDetection() {
        assertTrue(ReminderEngine.looksLikeCharger("Tesla Supercharger Ankara"))
        assertTrue(ReminderEngine.looksLikeCharger("ZES Şarj İstasyonu"))
        assertFalse(ReminderEngine.looksLikeCharger("Ev"))
    }

    @Test fun parsesFleetApiVehicleData() {
        val json = JSONObject("""
            {"charge_state":{"battery_level":64,"charging_state":"Charging","charge_energy_added":18.42,"charger_power":110,
              "time_to_full_charge":0.5,"fast_charger_present":true,"fast_charger_brand":"Tesla"},
             "vehicle_state":{"odometer":10000.0},
             "drive_state":{"active_route_destination":"Supercharger Bolu","active_route_minutes_to_arrival":12.4,"shift_state":null}}
        """.trimIndent())
        val s = TeslaRepository.parseSnapshot(json)
        assertEquals(16093.4, s.odometerKm!!, 0.05)
        assertEquals(64.0, s.batteryLevel!!, 0.0)
        assertTrue(s.isCharging)
        assertEquals(30, s.minutesToFullCharge)
        assertEquals("Supercharger Bolu", s.activeRouteDestination)
        assertEquals(null, s.shiftState)
    }
}
