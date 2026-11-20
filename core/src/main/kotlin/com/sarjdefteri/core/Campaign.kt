package com.sarjdefteri.core

import java.time.LocalDate

/**
 * Kart/operatör kampanyası (ör. "Akbank kartla ZES'te %20, en fazla 150 ₺").
 * Kullanıcı tarafından girilir; uygulama bilgi amaçlı eşleştirme ve hesaplama yapar.
 */
data class Campaign(
    val id: Long,
    val title: String,
    /** Boş: tüm bankalar. */
    val bank: String? = null,
    /** İstasyon/operatör adında aranacak ifade (ör. "ZES"). Boş: tüm istasyonlar. */
    val operator: String? = null,
    val discountRate: Double,
    /** Kampanya başına en fazla indirim (₺). */
    val maxDiscount: Double? = null,
    /** En az harcama (₺). */
    val minSpend: Double? = null,
    val validFrom: LocalDate? = null,
    val validTo: LocalDate? = null,
    val active: Boolean = true,
) {
    fun isValidOn(date: LocalDate): Boolean =
        active && (validFrom == null || !date.isBefore(validFrom)) && (validTo == null || !date.isAfter(validTo))

    fun matchesBank(b: String?): Boolean = bank.isNullOrBlank() || (b != null && CampaignMatcher.normalize(b).contains(CampaignMatcher.normalize(bank)))

    fun matchesStation(station: String?): Boolean =
        operator.isNullOrBlank() || (station != null && CampaignMatcher.normalize(station).contains(CampaignMatcher.normalize(operator)))

    /** Brüt tutara göre indirim (tavan ve alt limit uygulanmış). */
    fun discountFor(gross: Double): Double {
        if (minSpend != null && gross < minSpend) return 0.0
        val d = gross * discountRate / 100.0
        return if (maxDiscount != null) minOf(d, maxDiscount) else d
    }

    fun daysLeft(today: LocalDate): Long? = validTo?.let { java.time.temporal.ChronoUnit.DAYS.between(today, it) }
}

object CampaignMatcher {
    data class Suggestion(val campaign: Campaign, val discount: Double?)

    /** Türkçe karakter ve büyük/küçük harf duyarsız karşılaştırma. */
    fun normalize(s: String): String = s.trim().lowercase(java.util.Locale("tr", "TR"))
        .replace('ı', 'i').replace('ş', 's').replace('ğ', 'g').replace('ü', 'u').replace('ö', 'o').replace('ç', 'c')

    /**
     * Verilen tarih/istasyon/banka için uygun kampanyaları en yüksek indirimden başlayarak döndürür.
     * [bank] null ise banka şartı aranmaz (hangi kartla ödeyeceğini seçmeye yardımcı olur).
     */
    fun suggestions(campaigns: List<Campaign>, date: LocalDate, station: String?, bank: String?, gross: Double?): List<Suggestion> =
        campaigns.asSequence()
            .filter { it.isValidOn(date) && it.matchesStation(station) && (bank == null || it.matchesBank(bank)) }
            .map { Suggestion(it, gross?.let(it::discountFor)) }
            .sortedWith(compareByDescending<Suggestion> { it.discount ?: -1.0 }.thenByDescending { it.campaign.discountRate })
            .toList()

    fun activeOn(campaigns: List<Campaign>, date: LocalDate): List<Campaign> =
        campaigns.filter { it.isValidOn(date) }.sortedWith(compareBy(nullsLast()) { it.validTo })
}
