package com.sarjdefteri.app.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.sarjdefteri.core.ChargeRecord

enum class RecordSource { MANUAL, TESLA_API, NOTIFICATION }

@Entity(tableName = "charge_sessions", indices = [Index("started_at")])
data class ChargeSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "started_at") val startedAt: Long,
    @ColumnInfo(name = "ended_at") val endedAt: Long? = null,
    val station: String? = null,
    val location: String? = null,
    @ColumnInfo(name = "total_paid") val totalPaid: Double? = null,
    @ColumnInfo(name = "energy_kwh") val energyKwh: Double? = null,
    @ColumnInfo(name = "unit_price") val unitPrice: Double? = null,
    @ColumnInfo(name = "discount_rate") val discountRate: Double? = null,
    @ColumnInfo(name = "discount_amount") val discountAmount: Double? = null,
    val campaign: String? = null,
    val bank: String? = null,
    @ColumnInfo(name = "payment_method") val paymentMethod: String? = null,
    @ColumnInfo(name = "odometer_before") val odometerBefore: Double? = null,
    @ColumnInfo(name = "odometer_after") val odometerAfter: Double? = null,
    @ColumnInfo(name = "battery_before") val batteryBefore: Double? = null,
    @ColumnInfo(name = "battery_after") val batteryAfter: Double? = null,
    val notes: String? = null,
    @ColumnInfo(name = "exclude_from_consumption") val excludeFromConsumption: Boolean = false,
    @ColumnInfo(name = "distance_override_km") val distanceOverrideKm: Double? = null,
    val source: RecordSource = RecordSource.MANUAL,
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "updated_at") val updatedAt: Long = System.currentTimeMillis(),
) {
    fun toRecord() = ChargeRecord(
        id = id, startedAt = startedAt, station = station, bank = bank, totalPaid = totalPaid,
        energyKwh = energyKwh, unitPrice = unitPrice, discountRate = discountRate, discountAmount = discountAmount,
        odometerBefore = odometerBefore, odometerAfter = odometerAfter, batteryBefore = batteryBefore,
        batteryAfter = batteryAfter, excludeFromConsumption = excludeFromConsumption,
        distanceOverrideKm = distanceOverrideKm,
    )
}

enum class OdometerSource { MANUAL, BLUETOOTH_PROMPT, TESLA_API }

/** Şarjlar arasında alınan kilometre okumaları ("son şarjdan bu yana" hesabı için). */
@Entity(tableName = "odometer_readings", indices = [Index("recorded_at")])
data class OdometerReadingEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "recorded_at") val recordedAt: Long,
    @ColumnInfo(name = "odometer_km") val odometerKm: Double,
    @ColumnInfo(name = "battery_pct") val batteryPct: Double? = null,
    val source: OdometerSource = OdometerSource.MANUAL,
)

/** Kullanıcının girdiği banka/operatör kampanyası. Tarihler epochDay olarak saklanır. */
@Entity(tableName = "campaigns", indices = [Index("valid_to")])
data class CampaignEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val bank: String? = null,
    val operator: String? = null,
    @ColumnInfo(name = "discount_rate") val discountRate: Double,
    @ColumnInfo(name = "max_discount") val maxDiscount: Double? = null,
    @ColumnInfo(name = "min_spend") val minSpend: Double? = null,
    @ColumnInfo(name = "valid_from") val validFrom: Long? = null,
    @ColumnInfo(name = "valid_to") val validTo: Long? = null,
    val notes: String? = null,
    @ColumnInfo(name = "source_url") val sourceUrl: String? = null,
    val active: Boolean = true,
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis(),
) {
    fun toCampaign() = com.sarjdefteri.core.Campaign(
        id = id, title = title, bank = bank, operator = operator, discountRate = discountRate, maxDiscount = maxDiscount,
        minSpend = minSpend, validFrom = validFrom?.let(java.time.LocalDate::ofEpochDay),
        validTo = validTo?.let(java.time.LocalDate::ofEpochDay), active = active,
    )
}
