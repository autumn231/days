package com.example.countdowndays

import android.content.Context
import androidx.glance.appwidget.AppWidgetId
import androidx.test.core.app.ApplicationProvider
import com.example.countdowndays.data.EventEntity
import com.example.countdowndays.util.DateUtils
import com.example.countdowndays.widget.HeroDaysWidget
import java.time.LocalDate
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 主视觉组件渲染侧生产路径（v4.3 补测）。
 *
 * v4.1/v4.2 的教训：测试测的不是生产路径。HeroWidgetConfigChainTest 存/读两端
 * 都用 PrefsManager、再手动喂 resolveHero，而生产渲染走的是 provideGlance 里
 * 「GlanceId 解 id → 读配置 → 查库 → resolveHero」这一整段——云真机实测这段在
 * 真机上读不到已保存的配置，旧测试却全绿。本测试直接调 provideGlance 本身
 * 调用的那个生产函数 resolveHeroForInstance（真实 SharedPreferences＋真实
 * Room 库），杜绝两条路径再次分叉。
 *
 * 场景与云真机复现一致：EventA 3 天后（自动口径必选它）、EventB 100 天后，
 * 配置选了 EventB，渲染必须返回 EventB。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class HeroWidgetRenderPathTest {

    private val app: CountdownApp = ApplicationProvider.getApplicationContext()
    private val container get() = app.container

    private var idA: Long = -1L
    private var idB: Long = -1L

    @Before
    fun setUp() = runBlocking {
        app.getSharedPreferences("countdown_prefs", Context.MODE_PRIVATE)
            .edit().clear().commit()
        container.repository.getAllEvents().forEach {
            container.repository.deleteEvent(it.event)
        }
        val today = LocalDate.now()
        idA = container.repository.addEvent(
            EventEntity(name = "EventA Near3d", date = DateUtils.toMillis(today.plusDays(3)))
        )
        idB = container.repository.addEvent(
            EventEntity(name = "EventB Far100d", date = DateUtils.toMillis(today.plusDays(100)))
        )
    }

    @Test
    fun renderFunction_returnsConfiguredEvent_notAutoPick() = runBlocking {
        // 存：与配置界面完全同一调用——按 appWidgetId 存 EventB
        container.prefs.setHeroWidgetEventId(7, idB)
        // 渲染：provideGlance 调用的生产函数，必须命中 EventB 而不是自动口径的 EventA
        val hero = HeroDaysWidget.resolveHeroForInstance(app, AppWidgetId(7))
        assertEquals(idB, hero?.first?.id)
    }

    @Test
    fun renderFunction_withoutConfig_fallsBackToNearestFuture() = runBlocking {
        val hero = HeroDaysWidget.resolveHeroForInstance(app, AppWidgetId(7))
        assertEquals(idA, hero?.first?.id)
    }

    @Test
    fun renderFunction_configForDeletedEvent_fallsBackToAuto() = runBlocking {
        container.prefs.setHeroWidgetEventId(7, 999_999L)
        val hero = HeroDaysWidget.resolveHeroForInstance(app, AppWidgetId(7))
        assertEquals(idA, hero?.first?.id)
    }

    // ---- pickConfiguredId：单实例回落的判定逻辑（生产函数的一部分） ----

    @Test
    fun pickConfiguredId_primaryHitWins() {
        assertEquals(42L, HeroDaysWidget.pickConfiguredId(42L, mapOf(7 to 43L), placedCount = 2))
    }

    @Test
    fun pickConfiguredId_singleConfigSinglePlaced_usesTheOnlyConfig() {
        // 配置界面的 id 与渲染侧 id 对不上时的兜底：只有一份配置、一个实例，必属它
        assertEquals(42L, HeroDaysWidget.pickConfiguredId(null, mapOf(7 to 42L), placedCount = 1))
    }

    @Test
    fun pickConfiguredId_ambiguousCases_stayAuto() {
        // 多个实例在场：一份配置不知属于谁，不猜
        assertNull(HeroDaysWidget.pickConfiguredId(null, mapOf(7 to 42L), placedCount = 2))
        // 多份配置在场：渲染的这个实例没配置过，不抢别人的
        assertNull(HeroDaysWidget.pickConfiguredId(null, mapOf(7 to 42L, 8 to 43L), placedCount = 1))
        // 没有任何配置
        assertNull(HeroDaysWidget.pickConfiguredId(null, emptyMap(), placedCount = 1))
        // 实例数取不到（0）：不启用回落
        assertNull(HeroDaysWidget.pickConfiguredId(null, mapOf(7 to 42L), placedCount = 0))
    }
}
