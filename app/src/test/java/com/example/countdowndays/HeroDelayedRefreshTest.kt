package com.example.countdowndays

import com.example.countdowndays.widget.HeroDelayedRefresh
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 主视觉组件延迟兜底重渲染的寻址决策（v4.5）。
 *
 * v4.4 的 renderTargets 收「配置侧 id＋放置列表」两个参数：id 在放置列表里
 * 就只刷它、不在就刷全部——寻址仍掺着配置侧 id。v4.5 底线：刷新寻址只认
 * 系统登记的实际放置 id，逐个全刷；配置侧 id 永不参与（两端值域对不上，
 * 按它刷必然静默打空）。这里只测这个纯函数；闹钟调度本身（AlarmManager）
 * 与提醒功能共用同一套已验证机制，不在单测范围。
 *
 * v4.4 的「已见过」seen 标记与 shouldSchedule/uniqueWorkName 随 WorkManager
 * 一并退役（闹钟共用一个 PendingIntent、后排顶先排，天然去重），对应测试
 * 已删除。
 */
class HeroDelayedRefreshTest {

    @Test
    fun renderTargets_refreshesEveryPlacedInstance() {
        assertEquals(
            listOf(5, 6),
            HeroDelayedRefresh.renderTargets(intArrayOf(5, 6))
        )
        assertEquals(
            listOf(5),
            HeroDelayedRefresh.renderTargets(intArrayOf(5))
        )
    }

    @Test
    fun renderTargets_nothingPlaced_refreshesNothing() {
        // 一个都没放置：按任何 id 刷都不可见，不做无用功
        assertEquals(
            emptyList<Int>(),
            HeroDelayedRefresh.renderTargets(intArrayOf())
        )
    }
}
