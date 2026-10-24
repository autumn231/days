package com.example.countdowndays

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.graphics.Paint
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.countdowndays.data.EventEntity
import com.example.countdowndays.ui.detail.EventDetailScreen
import com.example.countdowndays.ui.main.MainScreen
import com.example.countdowndays.ui.showcase.ShowcaseScreen
import com.example.countdowndays.ui.theme.CountdownTheme
import com.example.countdowndays.util.DateUtils
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.io.FileOutputStream
import java.time.LocalDate

/**
 * 在 JVM 上用 Robolectric 渲染真实界面并截图（不需要模拟器）。
 * 截图产物：app/build/screenshots/
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [33], qualifiers = "w411dp-h891dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class UiSmokeTest {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private lateinit var app: CountdownApp

    @Before
    fun setUp() {
        app = ApplicationProvider.getApplicationContext()
        // 同一 JVM 内数据库文件会复用，先清空避免用例间互相污染
        kotlinx.coroutines.runBlocking(kotlinx.coroutines.Dispatchers.IO) {
            com.example.countdowndays.data.AppDatabase.get(app).clearAllTables()
        }
        val repo = app.container.repository
        runBlocking {
            repo.addEvent(EventEntity(name = "今天", date = DateUtils.todayMillis(), colorIndex = 4))
            repo.addEvent(
                EventEntity(
                    name = "考研",
                    date = DateUtils.toMillis(LocalDate.of(2026, 12, 19)),
                    colorIndex = 0,
                    imagePath = makeSampleImage()
                )
            )
            repo.addEvent(
                EventEntity(
                    name = "哈哈",
                    date = DateUtils.toMillis(LocalDate.of(2025, 9, 1)),
                    colorIndex = 6
                )
            )
        }
    }

    @Test
    fun homeScreen_rendersHeroAndGroups() {
        rule.setContent {
            CountdownTheme(darkTheme = false) {
                MainScreen(
                    onAddEvent = {},
                    onOpenEvent = {},
                    onOpenShowcase = {},
                    onOpenSettings = {},
                    onOpenCalendar = {}
                )
            }
        }
        waitForText("哈哈")
        // 焦点卡按日期升序，最早的是「哈哈」
        assertTrue(hasText("哈哈"))
        assertTrue(hasText("倒数日"))
        snapshot("01_home")
    }

    @Test
    fun detailScreen_headerBackgroundBehindText() {
        val event = runBlocking {
            app.container.repository.getActiveEvents().first { it.event.name == "考研" }
        }
        rule.setContent {
            CountdownTheme(darkTheme = false) {
                EventDetailScreen(
                    eventId = event.event.id,
                    onBack = {},
                    onEdit = {},
                    onShowcase = {}
                )
            }
        }
        rule.waitForIdle()
        assertTrue(hasText("考研"))
        assertTrue(hasText("时间线"))
        snapshot("02_detail")
    }

    @Test
    fun showcaseScreen_swipeableCard() {
        val event = runBlocking {
            app.container.repository.getActiveEvents().first { it.event.name == "考研" }
        }
        rule.setContent {
            CountdownTheme(darkTheme = false) {
                ShowcaseScreen(
                    eventId = event.event.id,
                    onBack = {},
                    onEdit = {},
                    onAddNew = {},
                    captureEnabled = false
                )
            }
        }
        rule.waitForIdle()
        assertTrue(hasText("考研"))
        assertTrue(hasText("分享"))
        snapshot("03_showcase")
    }

    private fun waitForText(text: String, timeoutMs: Long = 5_000) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            rule.waitForIdle()
            if (hasText(text)) return
            Thread.sleep(50)
        }
    }

    private fun hasText(text: String): Boolean =
        rule.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty()

    /** 生成测试配图，避免依赖外部资源 */
    private fun makeSampleImage(): String {
        val file = File(app.filesDir, "images").apply { mkdirs() }
            .resolve("sample_test.jpg")
        if (!file.exists()) {
            val bitmap = Bitmap.createBitmap(800, 800, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            canvas.drawColor(AndroidColor.rgb(70, 110, 150))
            val paint = Paint().apply {
                color = AndroidColor.WHITE
                textSize = 110f
                isAntiAlias = true
            }
            canvas.drawText("SAMPLE", 150f, 430f, paint)
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
            }
        }
        return file.absolutePath
    }

    /** Robolectric 下用 decorView 直接绘制，避免 captureToImage 的 forceRedraw 卡住 */
    private fun snapshot(name: String) {
        rule.waitForIdle()
        val view = rule.activity.window.decorView
        val width = if (view.width > 0) view.width else 1080
        val height = if (view.height > 0) view.height else 2400
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bitmap))
        val dir = File("build/screenshots").apply { mkdirs() }
        FileOutputStream(File(dir, "$name.png")).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
    }
}
