package com.syildirim.teslacharge.core

/** Şarj geçmişi filtreleri. */
data class RecordFilter(
    val fromMillis: Long? = null,
    val toMillis: Long? = null,
    val stations: Set<String> = emptySet(),
    val banks: Set<String> = emptySet(),
    val minAmount: Double? = null,
    val maxAmount: Double? = null,
    val discount: DiscountFilter = DiscountFilter.ALL,
) {
    enum class DiscountFilter { ALL, DISCOUNTED, NOT_DISCOUNTED }

    val isActive: Boolean
        get() = fromMillis != null || toMillis != null || stations.isNotEmpty() || banks.isNotEmpty() ||
            minAmount != null || maxAmount != null || discount != DiscountFilter.ALL

    fun matches(r: ChargeRecord): Boolean {
        if (fromMillis != null && r.startedAt < fromMillis) return false
        if (toMillis != null && r.startedAt > toMillis) return false
        if (stations.isNotEmpty() && (r.station?.trim() !in stations)) return false
        if (banks.isNotEmpty() && (r.bank?.trim() !in banks)) return false
        val cost = r.effectiveCost
        if (minAmount != null && (cost == null || cost < minAmount)) return false
        if (maxAmount != null && (cost == null || cost > maxAmount)) return false
        when (discount) {
            DiscountFilter.DISCOUNTED -> if (!r.hasDiscount) return false
            DiscountFilter.NOT_DISCOUNTED -> if (r.hasDiscount) return false
            DiscountFilter.ALL -> Unit
        }
        return true
    }
}
