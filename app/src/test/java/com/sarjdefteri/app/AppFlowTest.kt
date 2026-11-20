package com.sarjdefteri.app

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.sarjdefteri.app.data.db.ChargeSessionEntity
import java.io.File
import java.time.LocalDateTime
import java.time.ZoneId
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-night-xxhdpi")
class AppFlowTest {
    @get:Rule val compose = createEmptyComposeRule()

    private val app get() = ApplicationProvider.getApplicationContext<SarjDefteriApp>()

    private var scenario: ActivityScenario<MainActivity>? = null

    /** Pencereyi yazılım kanvasına çizerek ekran görüntüsü alır (Robolectric native grafik). */
    private fun shot(name: String) {
        compose.waitForIdle()
        val dir = File(System.getProperty("screenshotDir") ?: "build/screenshots").apply { mkdirs() }
        scenario!!.onActivity { a ->
            val v = a.window.decorView
            val bmp = Bitmap.createBitmap(v.width, v.height, Bitmap.Config.ARGB_8888)
            v.draw(Canvas(bmp))
            File(dir, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }

    private fun waitForText(text: String, substring: Boolean = false) =
        compose.waitUntil(5_000) { compose.onAllNodesWithText(text, substring = substring).fetchSemanticsNodes().isNotEmpty() }

    private fun scrollTo(text: String) {
        compose.onAllNodes(hasScrollToNodeAction()).onFirst().performScrollToNode(hasText(text))
        compose.waitForIdle()
    }

    private fun launch(): ActivityScenario<MainActivity> = ActivityScenario.launch(MainActivity::class.java).also { scenario = it }

    private fun seed() = runBlocking {
        app.container.settings.setProDebugOverride(true)
        app.container.settings.setVehicleName("Model Y")
        val repo = app.container.charges
        val zone = ZoneId.systemDefault()
        val now = LocalDateTime.now()
        data class S(val monthsAgo: Long, val day: Int, val station: String, val bank: String, val kwh: Double, val unit: Double, val disc: Double?, val odo: Double, val bb: Double, val ba: Double)
        listOf(
            S(5, 3, "Tesla Supercharger Kadıköy", "Akbank", 42.0, 8.9, null, 1_200.0, 18.0, 80.0),
            S(5, 21, "ZES Ataşehir", "Garanti BBVA", 35.5, 9.4, 15.0, 1_520.0, 30.0, 80.0),
            S(4, 9, "Tesla Supercharger Kadıköy", "Akbank", 51.2, 8.9, null, 1_905.0, 12.0, 85.0),
            S(3, 2, "Trugo Bolu", "İş Bankası", 48.0, 10.2, 10.0, 2_330.0, 15.0, 80.0),
            S(3, 18, "Tesla Supercharger Kadıköy", "Akbank", 38.4, 9.1, null, 2_640.0, 28.0, 80.0),
            S(2, 6, "Eşarj Maltepe", "Garanti BBVA", 30.1, 9.8, 20.0, 2_915.0, 40.0, 82.0),
            S(1, 11, "Tesla Supercharger Kadıköy", "Akbank", 55.0, 9.3, null, 3_320.0, 10.0, 85.0),
            S(0, 1, "ZES Ataşehir", "Garanti BBVA", 41.7, 9.6, 15.0, 3_710.0, 22.0, 80.0),
        ).forEach { s ->
            val t = now.minusMonths(s.monthsAgo).withDayOfMonth(s.day).withHour(19).withMinute(30).atZone(zone).toInstant().toEpochMilli()
            repo.save(ChargeSessionEntity(startedAt = t, station = s.station, bank = s.bank, energyKwh = s.kwh, unitPrice = s.unit,
                discountRate = s.disc, odometerBefore = s.odo, batteryBefore = s.bb, batteryAfter = s.ba, paymentMethod = "Kredi kartı"))
        }
        repo.addOdometerReading(3_862.0)
        val today = java.time.LocalDate.now()
        repo.saveCampaign(com.sarjdefteri.app.data.db.CampaignEntity(title = "Garanti · Tesla · %20", bank = "Garanti", operator = "Tesla",
            discountRate = 20.0, maxDiscount = 150.0, validFrom = today.withDayOfMonth(1).toEpochDay(),
            validTo = today.withDayOfMonth(today.lengthOfMonth()).toEpochDay()))
        repo.saveCampaign(com.sarjdefteri.app.data.db.CampaignEntity(title = "Akbank · ZES · %15", bank = "Akbank", operator = "ZES",
            discountRate = 15.0, maxDiscount = 75.0, validFrom = today.withDayOfMonth(1).toEpochDay(),
            validTo = today.withDayOfMonth(today.lengthOfMonth()).toEpochDay()))
    }

    @Test fun addChargeEndToEnd() {
        seed()
        launch().use {
            waitForText("152 km")
            compose.onNodeWithText("Toplam şarj harcaması").assertIsDisplayed()
            compose.onNodeWithText("152 km").assertIsDisplayed() // son şarjdan bu yana: 3862 - 3710
            shot("01-dashboard")

            compose.onNodeWithContentDescription("Şarj ekle").performClick()
            compose.waitForIdle()
            compose.onNodeWithText("Yeni şarj").assertIsDisplayed()
            compose.onAllNodesWithText("Tesla Supercharger Kadıköy").onFirst().performClick() // son birim fiyat otomatik gelir
            compose.onNode(hasText("Enerji") and hasSetTextAction()).performTextInput("44,6")
            compose.onNode(hasText("Kilometre") and hasSetTextAction()).performTextInput("3950")
            compose.onNode(hasText("Önce") and hasSetTextAction()).performTextInput("21")
            compose.onNode(hasText("Sonra") and hasSetTextAction()).performTextInput("80")
            compose.waitForIdle()
            waitForText("Uygun kampanya")
            shot("02-quick-entry")
            compose.onNodeWithText("Uygula").performClick()
            compose.waitForIdle()
            compose.onNodeWithText("Kaydet").performClick()
            waitForText("240 km") // 3950 - 3710
            compose.onNodeWithText("Şarj detayı").assertIsDisplayed()
            compose.onNodeWithText("240 km").performScrollTo().assertIsDisplayed()
            shot("03-detail")
        }
        val saved = runBlocking { app.container.charges.allSessions.first() }.maxBy { it.startedAt }
        assertEquals(44.6, saved.energyKwh!!, 0.001)
        assertEquals(9.3, saved.unitPrice!!, 0.001)               // istasyondan hatırlandı
        assertEquals(82.96, saved.discountAmount!!, 0.01)         // %20 kampanya
        assertEquals(331.82, saved.totalPaid!!, 0.01)             // 44,6 × 9,3 − 82,96
        assertEquals("Garanti · Tesla · %20", saved.campaign)
        assertEquals("Garanti BBVA", saved.bank)                  // son kayıttan
    }

    @Test fun historyFiltersAndSettings() {
        seed()
        launch().use {
            waitForText("152 km")
            compose.onNodeWithText("Geçmiş").performClick()
            waitForText("Trugo Bolu")
            compose.onNodeWithText("Şarj geçmişi").assertIsDisplayed()
            shot("04-history")
            compose.onNodeWithContentDescription("Filtrele").performClick()
            compose.waitForIdle()
            compose.onNodeWithText("İndirimli").performClick()
            compose.onNodeWithText("Uygula").performClick()
            waitForText("4 kayıt", substring = true)
            shot("05-history-filtered")

            compose.onNodeWithText("Kampanya").performClick()
            waitForText("Akbank · ZES · %15")
            shot("10-campaigns")
            compose.onNodeWithText("Ayarlar").performClick()
            compose.waitForIdle()
            compose.onNodeWithText("Hatırlatmalar").assertIsDisplayed()
            shot("06-settings")
            compose.onNode(hasClickAction() and hasAnyDescendant(hasText("Otomatik km, batarya ve şarj verisi")), useUnmergedTree = true)
                .performScrollTo().performSemanticsAction(SemanticsActions.OnClick)
            waitForText("Nasıl çalışır?")
            shot("07-tesla")
        }
    }

    @Test fun scrolledDashboardCharts() {
        seed()
        launch().use {
            waitForText("152 km")
            scrollTo("Aylık kilometre")
            shot("08-dashboard-charts")
            scrollTo("İstasyonlara göre maliyet")
            shot("09-dashboard-stations")
        }
    }
}
