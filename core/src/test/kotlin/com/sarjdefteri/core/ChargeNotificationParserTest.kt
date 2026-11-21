package com.sarjdefteri.core

import com.sarjdefteri.core.ChargeNotificationParser.EventType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ChargeNotificationParserTest {
    private fun parse(title: String?, text: String?) = ChargeNotificationParser.parse(title, text)

    @Test fun completedTurkishWithEnergyAndAmount() {
        val e = parse("Şarj tamamlandı", "42,3 kWh enerji aktarıldı. Toplam tutar: 412,50 TL")
        assertEquals(EventType.COMPLETED, e.type)
        assertEquals(42.3, e.energyKwh!!, 0.0001)
        assertEquals(412.50, e.amount!!, 0.0001)
    }

    @Test fun amountFormats() {
        assertEquals(1234.56, parse("Ödeme alındı", "₺1.234,56 kartınızdan tahsil edildi").amount!!, 0.001)
        assertEquals(412.5, parse("Payment received", "412.50 TL charged").amount!!, 0.001)
        assertEquals(89.0, parse("Ödeme", "Tutar 89 ₺").amount!!, 0.001)
        assertEquals(1500.0, parse("Ödeme", "1.500 TL").amount!!, 0.001)
    }

    @Test fun eventTypes() {
        assertEquals(EventType.STARTED, parse("Şarj başladı", "Aracınız şarj ediliyor").type)
        assertEquals(EventType.STARTED, parse("Charging started", null).type)
        assertEquals(EventType.COMPLETED, parse("Charging complete", "Your vehicle finished charging").type)
        assertEquals(EventType.COMPLETED, parse("Şarj işlemi sona erdi", null).type)
        assertEquals(EventType.COMPLETED, parse(null, "Şarj durduruldu. 18,4 kWh").type)
        assertEquals(EventType.PAYMENT, parse("Ödemeniz alındı", "250,00 TL").type)
    }

    @Test fun unrelatedNotificationsAreIgnored() {
        assertEquals(EventType.UNKNOWN, parse("Kampanya!", "Bu hafta %20 indirim fırsatı").type)
        assertEquals(EventType.UNKNOWN, parse("Yeni istasyon", "Ankara'da yeni istasyonumuz açıldı").type)
        assertEquals(EventType.UNKNOWN, parse(null, null).type)
    }

    @Test fun energyVariants() {
        assertEquals(7.0, parse("Şarj tamamlandı", "7 kWh").energyKwh!!, 0.001)
        assertEquals(55.12, parse("Charging complete", "Energy: 55.12kWh").energyKwh!!, 0.001)
        assertNull(parse("Şarj tamamlandı", "Teşekkürler").energyKwh)
    }

    @Test fun percentIsNotAmount() {
        val e = parse("Şarj tamamlandı", "Batarya %80, 30 kWh")
        assertNull(e.amount)
        assertEquals(80.0, e.batteryPercent!!, 0.001)
    }
}
