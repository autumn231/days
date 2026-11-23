package com.example.countdowndays

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import androidx.glance.appwidget.AppWidgetId
import androidx.test.core.app.ApplicationProvider
import com.example.countdowndays.data.EventEntity
import com.example.countdowndays.util.DateUtils
import com.example.countdowndays.widget.HeroDaysWidget
import com.example.countdowndays.widget.HeroDaysWidgetReceiver
import java.time.LocalDate
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config

/**
 * 主视觉组件「改选只跟随一次」复现＋v4.5.1 换存法验证（用户真机实证，
 * 2026-11-23，v4.5）。
 *
 * 现场：桌面只摆 1 个主视觉组件。更新后改选跟随过一次，之后再切换永远不
 * 跟随；删旧重加一个全新组件也一样无法切换。配置页能选、✓ 也在新事件上，
 * 组件却一直显示自动口径。
 *
 * 根因（已用 v4.5 代码实证跑红）：配置界面每次改选拿到的 appWidgetId 会
 * 漂移，选择被分散存到多个键名下越攒越多（删组件的 onDeleted 清的是渲染
 * 侧 id 的键，清不掉配置侧写下的键），而 v4.5 的渲染回落要求「全局恰好
 * 一份配置」——第二份键一出现就永久哑火。本文件两个现场用例在 v4.5 的
 * 存法（直接调 setHeroWidgetEventId）下实测为红。
 *
 * v4.5.1 换存法：保存走生产保存路径 HeroDaysWidget.saveHeroSelection——
 * 单实例时选择覆盖写进与 id 无关的规范槽＋镜像到实际放置 id，保存后修剪
 * 残留键；解析统一走 resolveConfiguredEventId（渲染与配置页 ✓ 同源）。
 * 放置实例在 Robolectric 里用 ShadowAppWidgetManager.bindAppWidgetId 登记
 * id=42，渲染走 provideGlance 的生产函数 resolveHeroForInstance 全链路。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class HeroWidgetCanonicalSlotTest {

    private val app: CountdownApp = ApplicationProvider.getApplicationContext()
    private val container get() = app.container

    private var idA: Long = -1L
    private var idB: Long = -1L
    private var idC: Long = -1L

    /** 渲染侧（系统登记）的放置 id：桌面上唯一的主视觉组件 */
    private val placedId = 42

    @Before
    fun setUp() = runBlocking {
        app.getSharedPreferences("countdown_prefs", Context.MODE_PRIVATE)
            .edit().clear().commit()
        container.repository.getAllEvents().forEach {
            container.repository.deleteEvent(it.event)
        }
        // 登记一个已放置的主视觉实例（id=42），让生产渲染路径里的
        // getAppWidgetIds 能看到它、placedCount==1 成立
        Shadows.shadowOf(AppWidgetManager.getInstance(app))
            .bindAppWidgetId(
                placedId,
                ComponentName(app, HeroDaysWidgetReceiver::class.java)
            )
        val today = LocalDate.now()
        // A 是自动口径必然选中的最近事件；B、C 是用户先后改选的目标
        idA = container.repository.addEvent(
            EventEntity(name = "EventA Near3d", date = DateUtils.toMillis(today.plusDays(3)))
        )
        idB = container.repository.addEvent(
            EventEntity(name = "EventB Far100d", date = DateUtils.toMillis(today.plusDays(100)))
        )
        idC = container.repository.addEvent(
            EventEntity(name = "EventC Mid30d", date = DateUtils.toMillis(today.plusDays(30)))
        )
    }

    // ---- 现场复现（v4.5 存法下先红，v4.5.1 生产保存路径下转绿） ----

    @Test
    fun resaveWithDriftedIds_renderFollowsLatestSelection() = runBlocking {
        val prefs = container.prefs
        // 第一次改选：配置界面拿到 id=7，选了 EventB
        HeroDaysWidget.saveHeroSelection(prefs, 7, idB, listOf(placedId))
        assertEquals(
            idB,
            HeroDaysWidget.resolveHeroForInstance(app, AppWidgetId(placedId))?.first?.id
        )
        // 第二次改选：配置界面的 id 漂移成 8，选了 EventC。渲染必须跟随
        // 最后一次选择——v4.5 时偏好里攒下两份配置、回落哑火、退回自动
        // 口径 EventA（红）；v4.5.1 规范槽覆盖写＋修剪后跟随 EventC（绿）
        HeroDaysWidget.saveHeroSelection(prefs, 8, idC, listOf(placedId))
        assertEquals(
            idC,
            HeroDaysWidget.resolveHeroForInstance(app, AppWidgetId(placedId))?.first?.id
        )
    }

    @Test
    fun freshAddWithResidualKeys_renderFollowsNewSelection() = runBlocking {
        val prefs = container.prefs
        // 跨版本/删旧组件攒下的残留键：旧组件早已删掉，但键还躺在偏好里
        prefs.setHeroWidgetEventId(5, idA)
        prefs.setHeroWidgetEventId(6, idB)
        // 删旧重加一个全新组件，配置界面拿到 id=7，选了 EventC。保存后
        // 残留键必须被修剪掉、规范槽写上 EventC，渲染跟随 EventC
        HeroDaysWidget.saveHeroSelection(prefs, 7, idC, listOf(placedId))
        assertEquals(idC, prefs.heroCanonicalSelection())
        assertEquals(setOf(7, placedId), prefs.heroWidgetConfigs().keys)
        assertEquals(
            idC,
            HeroDaysWidget.resolveHeroForInstance(app, AppWidgetId(placedId))?.first?.id
        )
    }

    // ---- 配置页 ✓ 与渲染同源 ----

    @Test
    fun configPageAndRender_agreeAfterDriftedSaves() {
        val prefs = container.prefs
        HeroDaysWidget.saveHeroSelection(prefs, 7, idB, listOf(placedId))
        HeroDaysWidget.saveHeroSelection(prefs, 8, idC, listOf(placedId))
        // 渲染侧解析（自己的 id=42）
        assertEquals(
            idC,
            HeroDaysWidget.resolveConfiguredEventId(prefs, placedId, listOf(placedId))
        )
        // 配置页重开（id 还是 8，或又漂移成 11）：✓ 必须与渲染一致，
        // 不许再出现「✓ 在 EventC、组件显示别的」式自相矛盾
        assertEquals(
            idC,
            HeroDaysWidget.resolveConfiguredEventId(prefs, 8, listOf(placedId))
        )
        assertEquals(
            idC,
            HeroDaysWidget.resolveConfiguredEventId(prefs, 11, listOf(placedId))
        )
    }

    @Test
    fun pickAutoAfterSelection_clearsCanonicalAndBothSidesAgreeOnAuto() = runBlocking {
        val prefs = container.prefs
        HeroDaysWidget.saveHeroSelection(prefs, 7, idB, listOf(placedId))
        // 改选回「自动」：规范槽与镜像键都清掉，渲染回自动口径、配置页
        // 解析为 null（＝「自动」行打勾），两边一致
        HeroDaysWidget.saveHeroSelection(prefs, 8, null, listOf(placedId))
        assertNull(prefs.heroCanonicalSelection())
        assertNull(prefs.heroWidgetEventId(placedId))
        assertNull(HeroDaysWidget.resolveConfiguredEventId(prefs, 8, listOf(placedId)))
        assertEquals(
            idA,
            HeroDaysWidget.resolveHeroForInstance(app, AppWidgetId(placedId))?.first?.id
        )
    }

    // ---- 规范槽与修剪（PrefsManager 层） ----

    @Test
    fun canonicalSlot_overwriteClearAndTimestamp() {
        val prefs = container.prefs
        assertNull(prefs.heroCanonicalSelection())
        prefs.setHeroCanonicalSelection(idB)
        assertEquals(idB, prefs.heroCanonicalSelection())
        assertTrue(prefs.heroCanonicalSelectionAt() != null)
        // 覆盖写：永远只有最新一份
        prefs.setHeroCanonicalSelection(idC)
        assertEquals(idC, prefs.heroCanonicalSelection())
        prefs.setHeroCanonicalSelection(null)
        assertNull(prefs.heroCanonicalSelection())
        assertNull(prefs.heroCanonicalSelectionAt())
    }

    @Test
    fun saveWithNoPlacedInfo_fallsBackToLegacyBehavior() {
        val prefs = container.prefs
        prefs.setHeroWidgetEventId(5, idA)
        // 放置列表为空（首次添加窗口期口径未定）：只写 per-id 键，不写
        // 规范槽、不修剪——残留键原样保留，等口径明确后的下一次保存处理
        HeroDaysWidget.saveHeroSelection(prefs, 7, idC, emptyList())
        assertNull(prefs.heroCanonicalSelection())
        assertEquals(mapOf(5 to idA, 7 to idC), prefs.heroWidgetConfigs())
    }

    // ---- pickConfiguredId：规范槽的生效条件（纯函数） ----

    @Test
    fun pickConfiguredId_canonicalRules() {
        // 单实例＋规范槽有值：哪怕残留键攒了多份，也认规范槽（v4.5 死点）
        assertEquals(
            99L,
            HeroDaysWidget.pickConfiguredId(
                null, mapOf(7 to 42L, 8 to 43L), canonical = 99L, placedCount = 1
            )
        )
        // 多实例：规范槽不代表某个具体实例，不猜 → 两份配置在场时回自动
        assertNull(
            HeroDaysWidget.pickConfiguredId(
                null, mapOf(7 to 42L, 8 to 43L), canonical = 99L, placedCount = 2
            )
        )
        // 放置口径未定（0）：同样不用规范槽
        assertNull(
            HeroDaysWidget.pickConfiguredId(
                null, mapOf(7 to 42L, 8 to 43L), canonical = 99L, placedCount = 0
            )
        )
        // 本实例主键命中永远优先于规范槽（多实例各自键语义的底线）
        assertEquals(
            42L,
            HeroDaysWidget.pickConfiguredId(
                42L, mapOf(7 to 43L), canonical = 99L, placedCount = 1
            )
        )
    }
}
