package com.sarjdefteri.app.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ChargeSessionDao {
    @Query("SELECT * FROM charge_sessions ORDER BY started_at DESC")
    fun observeAll(): Flow<List<ChargeSessionEntity>>

    @Query("SELECT * FROM charge_sessions ORDER BY started_at DESC")
    suspend fun getAll(): List<ChargeSessionEntity>

    @Query("SELECT * FROM charge_sessions WHERE id = :id")
    fun observe(id: Long): Flow<ChargeSessionEntity?>

    @Query("SELECT * FROM charge_sessions WHERE id = :id")
    suspend fun get(id: Long): ChargeSessionEntity?

    @Query("SELECT * FROM charge_sessions ORDER BY started_at DESC LIMIT 1")
    suspend fun latest(): ChargeSessionEntity?

    @Query("SELECT * FROM charge_sessions WHERE started_at < :before ORDER BY started_at DESC LIMIT 1")
    suspend fun latestBefore(before: Long): ChargeSessionEntity?

    @Query("SELECT DISTINCT TRIM(station) FROM charge_sessions WHERE station IS NOT NULL AND TRIM(station) != '' GROUP BY TRIM(station) ORDER BY MAX(started_at) DESC")
    fun observeStations(): Flow<List<String>>

    @Query("SELECT DISTINCT TRIM(bank) FROM charge_sessions WHERE bank IS NOT NULL AND TRIM(bank) != '' GROUP BY TRIM(bank) ORDER BY MAX(started_at) DESC")
    fun observeBanks(): Flow<List<String>>

    @Query("SELECT DISTINCT TRIM(payment_method) FROM charge_sessions WHERE payment_method IS NOT NULL AND TRIM(payment_method) != '' GROUP BY TRIM(payment_method) ORDER BY MAX(started_at) DESC")
    fun observePaymentMethods(): Flow<List<String>>

    @Query("SELECT COUNT(*) FROM charge_sessions WHERE (energy_kwh IS NULL OR (total_paid IS NULL AND unit_price IS NULL) OR (odometer_before IS NULL AND odometer_after IS NULL)) AND started_at < :before")
    suspend fun countIncompleteBefore(before: Long): Int

    @Insert suspend fun insert(entity: ChargeSessionEntity): Long
    @Update suspend fun update(entity: ChargeSessionEntity)
    @Delete suspend fun delete(entity: ChargeSessionEntity)
}

@Dao
interface OdometerDao {
    @Query("SELECT * FROM odometer_readings ORDER BY recorded_at DESC")
    fun observeAll(): Flow<List<OdometerReadingEntity>>

    @Query("SELECT * FROM odometer_readings ORDER BY recorded_at DESC LIMIT 1")
    suspend fun latest(): OdometerReadingEntity?

    @Insert suspend fun insert(entity: OdometerReadingEntity): Long
    @Delete suspend fun delete(entity: OdometerReadingEntity)
}
