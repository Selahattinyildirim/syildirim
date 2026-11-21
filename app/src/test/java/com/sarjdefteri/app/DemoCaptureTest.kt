package com.sarjdefteri.app

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.core.content.ContextCompat
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Demo görselleri: ilk açılış akışı ve uygulama ikonu. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-night-xxhdpi")
class DemoCaptureTest {
    @get:Rule val compose = createEmptyComposeRule()
    private lateinit var scenario: ActivityScenario<MainActivity>
    private val dir = File((System.getProperty("screenshotDir") ?: "build/screenshots") + "/demo").apply { mkdirs() }

    private fun shot(name: String) {
        compose.waitForIdle()
        scenario.onActivity { a ->
            val v = a.window.decorView
            val bmp = Bitmap.createBitmap(v.width, v.height, Bitmap.Config.ARGB_8888)
            v.draw(Canvas(bmp))
            File(dir, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }

    private fun waitFor(text: String) =
        compose.waitUntil(5_000) { compose.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty() }

    @Test fun firstLaunchFlow() {
        kotlinx.coroutines.runBlocking { ApplicationProvider.getApplicationContext<SarjDefteriApp>().container.settings.resetAll() }
        val ctx = ApplicationProvider.getApplicationContext<SarjDefteriApp>()
        val icon = ContextCompat.getDrawable(ctx, R.mipmap.ic_launcher)!!
        val ib = Bitmap.createBitmap(432, 432, Bitmap.Config.ARGB_8888)
        icon.setBounds(0, 0, 432, 432); icon.draw(Canvas(ib))
        File(dir, "icon.png").outputStream().use { ib.compress(Bitmap.CompressFormat.PNG, 100, it) }

        scenario = ActivityScenario.launch(MainActivity::class.java)
        waitFor("Aracını seç")
        shot("a0-arac-secimi")
        compose.onNode(hasText("Marka veya model ara") and hasSetTextAction()).performTextInput("togg")
        compose.waitForIdle()
        shot("a0b-arac-arama")
        compose.onNodeWithText("Togg T10X Uzun Menzil").performClick()
        waitFor("İlk şarjı ekle")
        compose.onNodeWithText("Togg T10X Uzun Menzil").assertExists() // panel başlığında araç adı
        shot("a1-ilk-acilis")
        compose.onNodeWithText("İlk şarjı ekle").performClick()
        waitFor("Yeni şarj")
        shot("a2-bos-form")
        compose.onNode(hasText("Şarj istasyonu") and hasSetTextAction()).performTextInput("Tesla Supercharger Kadıköy")
        compose.onNode(hasText("Enerji") and hasSetTextAction()).performTextInput("52,4")
        compose.onNode(hasText("Ödenen") and hasSetTextAction()).performTextInput("471,60")
        compose.onNodeWithText("Akbank").performClick()
        compose.onNode(hasText("Kilometre") and hasSetTextAction()).performTextInput("1250")
        compose.onNode(hasText("Önce") and hasSetTextAction()).performTextInput("18")
        compose.onNode(hasText("Sonra") and hasSetTextAction()).performTextInput("80")
        shot("a3-dolu-form")
        compose.onNodeWithText("Kaydet").performClick()
        waitFor("Şarj detayı")
        shot("a4-detay")
        compose.onNodeWithContentDescription("Geri").performClick()
        waitFor("Son şarjdan bu yana")
        shot("a5-panel")
        compose.onNodeWithText("Km gir").performClick()
        waitFor("Kilometre kaydı")
        compose.onNode(hasText("Güncel km") and hasSetTextAction()).performTextInput("1418")
        shot("a6-km-girisi")
        compose.onNodeWithText("Kaydet").performClick()
        waitFor("168 km")
        shot("a7-panel-km")
        scenario.close()
    }
}
