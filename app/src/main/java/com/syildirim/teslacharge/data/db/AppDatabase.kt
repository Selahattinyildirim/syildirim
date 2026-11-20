package com.syildirim.teslacharge.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters

class Converters {
    @TypeConverter fun fromSource(s: RecordSource): String = s.name
    @TypeConverter fun toSource(s: String): RecordSource = runCatching { RecordSource.valueOf(s) }.getOrDefault(RecordSource.MANUAL)
    @TypeConverter fun fromOdoSource(s: OdometerSource): String = s.name
    @TypeConverter fun toOdoSource(s: String): OdometerSource = runCatching { OdometerSource.valueOf(s) }.getOrDefault(OdometerSource.MANUAL)
}

@Database(
    entities = [ChargeSessionEntity::class, OdometerReadingEntity::class],
    version = 1,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun chargeSessionDao(): ChargeSessionDao
    abstract fun odometerDao(): OdometerDao

    companion object {
        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "tesla_charge.db")
                // Yeni sürümlerde Migration nesneleri eklenecek; veri asla sessizce silinmez.
                .build()
    }
}
