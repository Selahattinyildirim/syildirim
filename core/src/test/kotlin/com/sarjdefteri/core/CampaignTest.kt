package com.sarjdefteri.core

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CampaignTest {
    private val d = LocalDate.of(2026, 11, 15)
    private val akbankZes = Campaign(1, "Akbank ZES", bank = "Akbank", operator = "ZES", discountRate = 20.0, maxDiscount = 100.0,
        validFrom = LocalDate.of(2026, 11, 1), validTo = LocalDate.of(2026, 11, 30))
    private val garantiAll = Campaign(2, "Garanti her yerde", bank = "Garanti", discountRate = 10.0, minSpend = 200.0)
    private val expired = Campaign(3, "Eski", bank = "Akbank", discountRate = 50.0, validTo = LocalDate.of(2026, 10, 31))

    @Test fun matchingIsAccentAndCaseInsensitive() {
        assertTrue(akbankZes.matchesStation("zes ataşehir"))
        assertTrue(Campaign(9, "x", bank = "İş Bankası", discountRate = 5.0).matchesBank("is bankasi"))
        assertTrue(garantiAll.matchesBank("Garanti BBVA"))
        assertFalse(akbankZes.matchesStation("Trugo Bolu"))
    }

    @Test fun capAndMinimum() {
        assertEquals(80.0, akbankZes.discountFor(400.0), 0.001)
        assertEquals(100.0, akbankZes.discountFor(900.0), 0.001)
        assertEquals(0.0, garantiAll.discountFor(150.0), 0.001)
        assertEquals(30.0, garantiAll.discountFor(300.0), 0.001)
    }

    @Test fun bestSuggestionFirstAndExpiredIgnored() {
        val all = listOf(akbankZes, garantiAll, expired)
        val s = CampaignMatcher.suggestions(all, d, "ZES Kadıköy", bank = null, gross = 450.0)
        assertEquals(listOf(1L, 2L), s.map { it.campaign.id })
        assertEquals(90.0, s.first().discount!!, 0.001)
        assertEquals(listOf(2L), CampaignMatcher.suggestions(all, d, "ZES", bank = "Garanti BBVA", gross = 450.0).map { it.campaign.id })
        assertEquals(listOf(1L, 2L), CampaignMatcher.activeOn(all, d).map { it.id })
        assertEquals(15L, akbankZes.daysLeft(d))
    }
}
