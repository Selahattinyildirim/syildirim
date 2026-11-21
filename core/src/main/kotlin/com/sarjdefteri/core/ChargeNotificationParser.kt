package com.sarjdefteri.core

/**
 * Şarj operatörü uygulamalarının bildirimlerinden olay ve değer çıkarır.
 * Operatörlerin metinleri standart değildir; bu yüzden anahtar kelime + sayı deseni yaklaşımı kullanılır ve
 * şarj bağlamı (şarj/charg/kWh/enerji) olmayan bildirimler yok sayılır.
 */
object ChargeNotificationParser {

    enum class EventType { STARTED, COMPLETED, PAYMENT, UNKNOWN }

    data class Event(
        val type: EventType,
        val energyKwh: Double? = null,
        val amount: Double? = null,
        val batteryPercent: Double? = null,
    )

    private val context = listOf("sarj", "charg", "kwh", "enerji", "energy")
    private val completed = listOf("tamamlandi", "bitti", "sona erdi", "durduruldu", "sonlandi", "sonlandirildi",
        "complete", "finished", "stopped", "ended")
    private val payment = listOf("odeme", "tahsil", "fatura", "makbuz", "payment", "receipt")
    private val started = listOf("basladi", "baslatildi", "sarj ediliyor", "started", "is charging", "charging now")

    private const val NUM = """\d{1,3}(?:[.\s]\d{3})+(?:,\d{1,2})?|\d+(?:[.,]\d{1,2})?"""
    private val amountAfter = Regex("""(?:₺|\bTL\b|\bTRY\b)\s*($NUM)""", RegexOption.IGNORE_CASE)
    private val amountBefore = Regex("""($NUM)\s*(?:₺|TL\b|TRY\b)""", RegexOption.IGNORE_CASE)
    private val energy = Regex("""(\d+(?:[.,]\d+)?)\s*kwh""", RegexOption.IGNORE_CASE)
    private val percent = Regex("""%\s*(\d{1,3})|(\d{1,3})\s*%""")

    fun parse(title: String?, text: String?): Event {
        val raw = listOfNotNull(title, text).joinToString(" ").trim()
        if (raw.isEmpty()) return Event(EventType.UNKNOWN)
        val n = TextNorm.normalize(raw)
        val amount = (amountBefore.find(raw) ?: amountAfter.find(raw))?.groupValues?.get(1)?.let(::parseMoney)
        val kwh = energy.find(raw)?.groupValues?.get(1)?.replace(',', '.')?.toDoubleOrNull()
        val pct = percent.find(raw)?.let { m -> (m.groupValues[1].ifEmpty { m.groupValues[2] }).toDoubleOrNull() }?.takeIf { it in 0.0..100.0 }

        val hasContext = context.any { n.contains(it) }
        val type = when {
            hasContext && completed.any { n.contains(it) } -> EventType.COMPLETED
            payment.any { n.contains(it) } && amount != null -> EventType.PAYMENT
            hasContext && started.any { n.contains(it) } -> EventType.STARTED
            else -> EventType.UNKNOWN
        }
        return Event(type, kwh, amount, pct)
    }

    /** "1.234,56" → 1234.56 · "1.500" → 1500 · "412.50" → 412.5 · "412,5" → 412.5 */
    fun parseMoney(s: String): Double? {
        val t = s.replace(" ", "")
        return when {
            t.contains(',') -> t.replace(".", "").replace(',', '.').toDoubleOrNull()
            Regex("""\d{1,3}(\.\d{3})+""").matches(t) -> t.replace(".", "").toDoubleOrNull()
            else -> t.toDoubleOrNull()
        }
    }
}
