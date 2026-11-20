package com.syildirim.teslacharge.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.syildirim.teslacharge.core.ChargeRecord

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
