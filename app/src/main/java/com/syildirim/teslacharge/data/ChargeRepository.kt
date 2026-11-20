package com.syildirim.teslacharge.data

import com.syildirim.teslacharge.core.PriceCalculator
import com.syildirim.teslacharge.data.db.AppDatabase
import com.syildirim.teslacharge.data.db.ChargeSessionEntity
import com.syildirim.teslacharge.data.db.OdometerReadingEntity
import com.syildirim.teslacharge.data.db.OdometerSource
import kotlinx.coroutines.flow.Flow

class ChargeRepository(private val db: AppDatabase) {
    private val sessions = db.chargeSessionDao()
    private val odometer = db.odometerDao()

    val allSessions: Flow<List<ChargeSessionEntity>> = sessions.observeAll()
    val odometerReadings: Flow<List<OdometerReadingEntity>> = odometer.observeAll()
    val stations: Flow<List<String>> = sessions.observeStations()
    val banks: Flow<List<String>> = sessions.observeBanks()
    val paymentMethods: Flow<List<String>> = sessions.observePaymentMethods()

    fun observeSession(id: Long) = sessions.observe(id)
    suspend fun getSession(id: Long) = sessions.get(id)
    suspend fun latestSession() = sessions.latest()
    suspend fun latestSessionBefore(t: Long) = sessions.latestBefore(t)
    suspend fun allSessionsOnce() = sessions.getAll()
    suspend fun countIncompleteBefore(t: Long) = sessions.countIncompleteBefore(t)

    /** Fiyat alanlarını tamamlayıp kaydeder. id == 0 ise yeni kayıt. */
    suspend fun save(entity: ChargeSessionEntity): Long {
        val p = PriceCalculator.complete(
            PriceCalculator.PriceFields(entity.totalPaid, entity.energyKwh, entity.unitPrice, entity.discountRate, entity.discountAmount)
        )
        val normalized = entity.copy(
            station = entity.station?.trim()?.ifEmpty { null },
            bank = entity.bank?.trim()?.ifEmpty { null },
            paymentMethod = entity.paymentMethod?.trim()?.ifEmpty { null },
            campaign = entity.campaign?.trim()?.ifEmpty { null },
            location = entity.location?.trim()?.ifEmpty { null },
            notes = entity.notes?.trim()?.ifEmpty { null },
            totalPaid = p.totalPaid, energyKwh = p.energyKwh, unitPrice = p.unitPrice,
            discountRate = p.discountRate, discountAmount = p.discountAmount,
            updatedAt = System.currentTimeMillis(),
        )
        return if (normalized.id == 0L) sessions.insert(normalized) else {
            sessions.update(normalized); normalized.id
        }
    }

    suspend fun delete(entity: ChargeSessionEntity) = sessions.delete(entity)

    suspend fun addOdometerReading(km: Double, battery: Double? = null, source: OdometerSource = OdometerSource.MANUAL,
                                   at: Long = System.currentTimeMillis()) =
        odometer.insert(OdometerReadingEntity(recordedAt = at, odometerKm = km, batteryPct = battery, source = source))

    suspend fun latestOdometerReading() = odometer.latest()

    suspend fun deleteOdometerReading(e: OdometerReadingEntity) = odometer.delete(e)

    /** Bilinen en güncel kilometre (okuma ya da şarj kaydı). */
    suspend fun latestKnownOdometer(): Double? {
        val r = odometer.latest()
        val s = sessions.latest()
        val sOdo = s?.odometerAfter ?: s?.odometerBefore
        return when {
            r == null -> sOdo
            s == null || sOdo == null -> r.odometerKm
            r.recordedAt >= s.startedAt -> maxOf(r.odometerKm, sOdo)
            else -> sOdo
        }
    }
}
