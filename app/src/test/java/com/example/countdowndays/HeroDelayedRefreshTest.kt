package com.example.countdowndays

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.countdowndays.util.PrefsManager
import com.example.countdowndays.widget.HeroDelayedRefresh
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 主视觉组件延迟兜底重渲染的调度决策（v4.4）。
 *
 * 只测纯函数：要不要排（shouldSchedule）、到点刷哪些实例（renderTargets）、
 * 唯一任务名（uniqueWorkName）。WorkManager 本身不在这里测——它只是持久化
 * 调度管道，决策对了它照着执行即可。
 */
class HeroDelayedRefreshTest {

    private val configure = HeroDelayedRefresh.Source.CONFIGURE_SAVE
    private val firstEnable = HeroDelayedRefresh.Source.FIRST_ENABLE
    private val firstUpdate = HeroDelayedRefresh.Source.FIRST_UPDATE

    @Test
    fun configureSave_alwaysSchedules() {
        assertTrue(HeroDelayedRefresh.shouldSchedule(configure, alreadySeen = false))
        assertTrue(HeroDelayedRefresh.shouldSchedule(configure, alreadySeen = true))
    }

    @Test
    fun firstEnable_alwaysSchedules() {
        assertTrue(HeroDelayedRefresh.shouldSchedule(firstEnable, alreadySeen = false))
        assertTrue(HeroDelayedRefresh.shouldSchedule(firstEnable, alreadySeen = true))
    }

    @Test
    fun firstUpdate_onlyWhenUnseen() {
        assertTrue(HeroDelayedRefresh.shouldSchedule(firstUpdate, alreadySeen = false))
        // 周期更新每 30 分钟都来，见过的实例不能每次都跟着排
        assertFalse(HeroDelayedRefresh.shouldSchedule(firstUpdate, alreadySeen = true))
    }

    @Test
    fun renderTargets_idPlaced_refreshesOnlyIt() {
        assertEquals(
            listOf(5),
            HeroDelayedRefresh.renderTargets(5, intArrayOf(5, 6))
        )
    }

    @Test
    fun renderTargets_idNotPlaced_refreshesAllPlaced() {
        // 配置界面的 id 与放置 id 对不上（v4.3 病根）：按 id 刷必然刷空，刷全部
        assertEquals(
            listOf(5, 6),
            HeroDelayedRefresh.renderTargets(9, intArrayOf(5, 6))
        )
    }

    @Test
    fun renderTargets_nullId_refreshesAllPlaced() {
        assertEquals(
            listOf(5, 6),
            HeroDelayedRefresh.renderTargets(null, intArrayOf(5, 6))
        )
    }

    @Test
    fun renderTargets_nothingPlacedYet_stillTriesKnownId() {
        // 刚添加、系统登记列表还没刷新到的窗口期：仍试刷这个 id
        assertEquals(
            listOf(9),
            HeroDelayedRefresh.renderTargets(9, intArrayOf())
        )
    }

    @Test
    fun renderTargets_nothingKnown_refreshesNothing() {
        assertEquals(
            emptyList<Int>(),
            HeroDelayedRefresh.renderTargets(null, intArrayOf())
        )
    }

    @Test
    fun uniqueWorkName_stableAndDistinct() {
        assertEquals(
            HeroDelayedRefresh.uniqueWorkName(configure, 5),
            HeroDelayedRefresh.uniqueWorkName(configure, 5)
        )
        assertNotEquals(
            HeroDelayedRefresh.uniqueWorkName(configure, 5),
            HeroDelayedRefresh.uniqueWorkName(configure, 6)
        )
        assertNotEquals(
            HeroDelayedRefresh.uniqueWorkName(configure, 5),
            HeroDelayedRefresh.uniqueWorkName(firstUpdate, 5)
        )
    }
}

/**
 * 「已见过」标记的偏好往返（v4.4）：首次 onUpdate 去重靠它，必须真实落盘、
 * 换实例读得回、清除后归零，且不能污染选事件配置的枚举（heroWidgetConfigs）。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class HeroWidgetSeenFlagTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Before
    fun clearPrefs() {
        context.getSharedPreferences("countdown_prefs", Context.MODE_PRIVATE)
            .edit().clear().commit()
    }

    @Test
    fun seenFlag_defaultsFalse_setAndClearRoundTrip() {
        val prefs = PrefsManager(context)
        assertFalse(prefs.heroWidgetSeen(7))
        prefs.setHeroWidgetSeen(7, true)
        // 换新实例读：模拟 Receiver 在另一时机读标记
        assertTrue(PrefsManager(context).heroWidgetSeen(7))
        assertFalse(PrefsManager(context).heroWidgetSeen(8))
        PrefsManager(context).setHeroWidgetSeen(7, false)
        assertFalse(PrefsManager(context).heroWidgetSeen(7))
    }

    @Test
    fun seenFlag_doesNotPolluteEventConfigs() {
        val prefs = PrefsManager(context)
        prefs.setHeroWidgetSeen(7, true)
        prefs.setHeroWidgetEventId(7, 42L)
        // 选事件配置枚举只认 hero_widget_event_ 前缀，不能把 seen 标记算进去
        assertEquals(mapOf(7 to 42L), PrefsManager(context).heroWidgetConfigs())
    }
}
