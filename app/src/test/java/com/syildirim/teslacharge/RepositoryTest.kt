package com.syildirim.teslacharge

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.syildirim.teslacharge.core.UsageCalculator
import com.syildirim.teslacharge.data.ChargeRepository
import com.syildirim.teslacharge.data.db.AppDatabase
import com.syildirim.teslacharge.data.db.ChargeSessionEntity
import com.syildirim.teslacharge.export.Exporter
import java.io.ByteArrayOutputStream
import java.util.zip.ZipInputStream
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class RepositoryTest {
    private lateinit var db: AppDatabase
    private lateinit var repo: ChargeRepository

    @Before fun setUp() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(ctx, AppDatabase::class.java).allowMainThreadQueries().build()
        repo = ChargeRepository(db)
    }

    @After fun tearDown() = db.close()

    @Test fun saveCompletesPriceFieldsAndEditsLater() = runTest {
        val id = repo.save(ChargeSessionEntity(startedAt = 1_000, station = "  Tesla Supercharger Bostancı ", energyKwh = 40.0, unitPrice = 9.5, discountRate = 10.0))
        val saved = repo.getSession(id)!!
        assertEquals("Tesla Supercharger Bostancı", saved.station)
        assertEquals(38.0, saved.discountAmount!!, 0.001)
        assertEquals(342.0, saved.totalPaid!!, 0.001)

        // Eksik bilgi sonradan düzenlenir
        repo.save(saved.copy(odometerBefore = 12_345.0, bank = "Akbank"))
        val edited = repo.getSession(id)!!
        assertEquals(12_345.0, edited.odometerBefore!!, 0.0)
        assertEquals("Akbank", edited.bank)
        assertEquals(1, repo.allSessions.first().size)
    }

    @Test fun distinctStationsAndIncompleteCount() = runTest {
        repo.save(ChargeSessionEntity(startedAt = 1, station = "ZES", energyKwh = 10.0, totalPaid = 100.0, odometerBefore = 100.0))
        repo.save(ChargeSessionEntity(startedAt = 2, station = "ZES ", energyKwh = 10.0, totalPaid = 100.0))
        repo.save(ChargeSessionEntity(startedAt = 3, station = "Trugo", bank = "Garanti"))
        assertEquals(listOf("Trugo", "ZES"), repo.stations.first())
        assertEquals(listOf("Garanti"), repo.banks.first())
        assertEquals(2, repo.countIncompleteBefore(10))
    }

    @Test fun odometerReadingsAndConsumption() = runTest {
        repo.save(ChargeSessionEntity(startedAt = 1_000, energyKwh = 30.0, totalPaid = 300.0, odometerBefore = 10_000.0))
        repo.save(ChargeSessionEntity(startedAt = 2_000, energyKwh = 45.0, totalPaid = 450.0, odometerBefore = 10_300.0))
        repo.addOdometerReading(10_420.0, at = 3_000)
        assertEquals(10_420.0, repo.latestKnownOdometer()!!, 0.0)
        val recs = repo.allSessionsOnce().map { it.toRecord() }
        assertEquals(120.0, UsageCalculator.distanceSinceLastCharge(recs, repo.latestKnownOdometer())!!, 0.0)
        val summary = UsageCalculator.summarize(UsageCalculator.segments(recs), 75.0)
        assertEquals(15.0, summary.avgConsumptionPer100!!, 0.001)
        assertEquals(1.5, summary.costPerKm!!, 0.001)
    }

    @Test fun exportCsvAndXlsx() = runTest {
        repo.save(ChargeSessionEntity(startedAt = 1_700_000_000_000, station = "İstasyon; \"A\"", energyKwh = 40.5, totalPaid = 400.0, notes = "<not>"))
        val rows = repo.allSessionsOnce()
        val csv = ByteArrayOutputStream().also { Exporter().writeCsv(rows, it) }.toString("UTF-8")
        assertTrue(csv.startsWith("﻿Tarih;"))
        assertTrue(csv.contains("\"İstasyon; \"\"A\"\"\""))
        assertTrue(csv.contains("40,5"))

        val bytes = ByteArrayOutputStream().also { Exporter().writeXlsx(rows, it) }.toByteArray()
        val names = mutableListOf<String>()
        var sheet = ""
        ZipInputStream(bytes.inputStream()).use { z ->
            while (true) {
                val e = z.nextEntry ?: break
                names += e.name
                if (e.name == "xl/worksheets/sheet1.xml") sheet = z.readBytes().toString(Charsets.UTF_8)
            }
        }
        assertTrue(names.containsAll(listOf("[Content_Types].xml", "xl/workbook.xml", "xl/styles.xml", "xl/worksheets/sheet1.xml")))
        assertTrue(sheet.contains("&lt;not&gt;"))
        assertTrue(sheet.contains("<v>40.5</v>"))
    }
}
