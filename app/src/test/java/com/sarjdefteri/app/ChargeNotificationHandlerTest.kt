package com.sarjdefteri.app

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.sarjdefteri.app.reminders.ChargeNotificationHandler
import com.sarjdefteri.app.reminders.ChargeNotificationHandler.Outcome
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class ChargeNotificationHandlerTest {
    private val app get() = ApplicationProvider.getApplicationContext<SarjDefteriApp>()
    private lateinit var handler: ChargeNotificationHandler
    private val zes = "com.example.zes"
    private val t0 = 1_763_000_000_000L

    @Before fun setUp() = runBlocking {
        app.container.settings.resetAll()
        app.container.settings.setProDebugOverride(true)
        app.container.settings.setWatchedApps(setOf(zes))
        handler = app.container.chargeNotificationHandler
    }

    @Test fun unwatchedAppIsIgnored() = runBlocking {
        assertEquals(Outcome.IgnoredApp, handler.handle("com.other", "Other", "Şarj tamamlandı", "10 kWh", t0))
    }

    @Test fun startThenCompleteBuildsPrefill() = runBlocking {
        assertEquals(Outcome.Started, handler.handle(zes, "ZES", "Şarj başladı", "Aracınız şarj ediliyor", t0))
        val out = handler.handle(zes, "ZES", "Şarj tamamlandı", "42,3 kWh · Toplam 412,50 TL", t0 + 40 * 60_000)
        assertTrue(out is Outcome.Completed)
        val p = (out as Outcome.Completed).prefill
        assertEquals(t0, p.startedAt)
        assertEquals(42.3, p.energyKwh!!, 0.001)
        assertEquals(412.5, p.totalPaid!!, 0.001)
        assertEquals("ZES", p.station)
    }

    @Test fun duplicateCompletionIsSuppressed() = runBlocking {
        val first = handler.handle(zes, "ZES", "Şarj tamamlandı", "20 kWh", t0)
        assertTrue(first is Outcome.Completed)
        assertEquals(Outcome.Duplicate, handler.handle(zes, "ZES", "Şarj tamamlandı", "20 kWh", t0 + 60_000))
        // Ödeme bildirimi aynı oturumun devamı: yeni bildirim yerine mevcut kayıt önerisini günceller
        val pay = handler.handle(zes, "ZES", "Ödemeniz alındı", "205,00 TL", t0 + 2 * 60_000)
        assertTrue(pay is Outcome.Completed)
        assertEquals(205.0, (pay as Outcome.Completed).prefill.totalPaid!!, 0.001)
        assertEquals(20.0, pay.prefill.energyKwh!!, 0.001)
    }

    @Test fun unrelatedTextIgnoredAndProRequired() = runBlocking {
        assertEquals(Outcome.NotChargeEvent, handler.handle(zes, "ZES", "Kampanya", "%20 indirim", t0))
        app.container.settings.setProDebugOverride(false)
        assertEquals(Outcome.Disabled, handler.handle(zes, "ZES", "Şarj tamamlandı", "10 kWh", t0))
    }
}
