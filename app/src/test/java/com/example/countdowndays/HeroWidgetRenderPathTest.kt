package com.example.countdowndays

import android.content.Context
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
 * 「按 id 读配置 → 查库 → resolveHero」这一整段——云真机实测这段在
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
        val hero = HeroDaysWidget.resolveHeroForInstance(app, 7)
        assertEquals(idB, hero?.first?.id)
    }

    // ---- v4.5 复现：真·首次添加的键集合（配置 id 与渲染 id 对不上） ----

    @Test
    fun renderFunction_mismatchedIds_singleConfig_adoptsIt() = runBlocking {
        // 云真机 v4.4 实测场景：全新安装、桌面上只有一个主视觉组件，配置界面
        // 拿到的 id（记作 7）与渲染侧解出的 id（记作 9）对不上——选择按 7 存
        // （重开配置界面 ✓ 还在，证明存住了），渲染按 9 读主键必然读不到。
        // 偏好里全局只有这一份配置，它必然属于桌面上这个组件，渲染必须认它。
        // Robolectric 下 getAppWidgetIds 返回空（placedCount=0），对应真机首次
        // 添加窗口期放置列表口径未定的状态；v4.4 的回落要求 placedCount==1，
        // 在此状态下整体失效、只能走自动口径（EventA）——与实测一致（先红）。
        container.prefs.setHeroWidgetEventId(7, idB)
        val hero = HeroDaysWidget.resolveHeroForInstance(app, 9)
        assertEquals(idB, hero?.first?.id)
    }

    @Test
    fun renderFunction_mismatchedIds_twoConfigs_staysAuto() = runBlocking {
        // 残留键污染：偏好里有两份配置（哪怕指向同一事件），渲染的这个实例
        // 主键又没命中——归属不明确就不猜，保持自动口径（行为钉死，防回退）
        container.prefs.setHeroWidgetEventId(7, idB)
        container.prefs.setHeroWidgetEventId(8, idB)
        val hero = HeroDaysWidget.resolveHeroForInstance(app, 9)
        assertEquals(idA, hero?.first?.id)
    }

    @Test
    fun renderFunction_withoutConfig_fallsBackToNearestFuture() = runBlocking {
        val hero = HeroDaysWidget.resolveHeroForInstance(app, 7)
        assertEquals(idA, hero?.first?.id)
    }

    @Test
    fun renderFunction_configForDeletedEvent_fallsBackToAuto() = runBlocking {
        container.prefs.setHeroWidgetEventId(7, 999_999L)
        val hero = HeroDaysWidget.resolveHeroForInstance(app, 7)
        assertEquals(idA, hero?.first?.id)
    }

    // ---- pickConfiguredId：回落的判定逻辑（生产函数的一部分） ----
    // v4.5.1 起签名带 canonical 与 placedCount：规范槽规则见
    // HeroWidgetCanonicalSlotTest.pickConfiguredId_canonicalRules；这里钉
    // 的是主键优先与「恰好一份配置」兼容回落（规范槽为空时）。

    @Test
    fun pickConfiguredId_primaryHitWins() {
        assertEquals(
            42L,
            HeroDaysWidget.pickConfiguredId(42L, mapOf(7 to 43L), null, 1)
        )
        // 主键命中也优先于规范槽
        assertEquals(
            42L,
            HeroDaysWidget.pickConfiguredId(42L, mapOf(7 to 43L), 44L, 1)
        )
    }

    @Test
    fun pickConfiguredId_singleConfig_usesItRegardlessOfPlacedCount() {
        // 配置界面的 id 与渲染侧 id 对不上时的兜底：全局只有一份配置就必属
        // 桌面上的主视觉组件，直接认——v4.5 起不再附带实例数条件（v4.4 的
        // placedCount==1 在首次添加窗口期不成立，是回落哑火的真因）；
        // v4.5.1 保留为规范槽为空时的兼容回落（升级前存下的单份选择）
        assertEquals(
            42L,
            HeroDaysWidget.pickConfiguredId(null, mapOf(7 to 42L), null, 0)
        )
        assertEquals(
            42L,
            HeroDaysWidget.pickConfiguredId(null, mapOf(7 to 42L), null, 2)
        )
    }

    @Test
    fun pickConfiguredId_ambiguousCases_stayAuto() {
        // 多份配置在场且规范槽为空：渲染的这个实例主键没命中，归属有歧义，不猜
        assertNull(
            HeroDaysWidget.pickConfiguredId(null, mapOf(7 to 42L, 8 to 43L), null, 1)
        )
        // 没有任何配置
        assertNull(HeroDaysWidget.pickConfiguredId(null, emptyMap(), null, 1))
    }
}
