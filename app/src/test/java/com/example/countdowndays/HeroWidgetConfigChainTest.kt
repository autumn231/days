package com.example.countdowndays

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.countdowndays.data.EventEntity
import com.example.countdowndays.data.EventOccurrence
import com.example.countdowndays.util.PrefsManager
import com.example.countdowndays.widget.HeroDaysWidget
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 主视觉组件「选事件」存读链路（v4.2 补测）。
 *
 * v4.1 的 HeroWidgetResolveTest 只覆盖了 resolveHero 纯函数本身，没测「配置界面
 * 存入 → 组件渲染时读回」这一段——真机上正是这一段断了：配置界面能选能存，
 * 组件却一直显示默认事件。这里用真实 SharedPreferences 往返验证整条链：
 * 一个 PrefsManager 实例存（模拟配置界面），换一个新实例读（模拟 provideGlance
 * 在另一时机读配置），读回的 id 再喂给 resolveHero，必须命中所选事件。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class HeroWidgetConfigChainTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Before
    fun clearPrefs() {
        context.getSharedPreferences("countdown_prefs", Context.MODE_PRIVATE)
            .edit().clear().commit()
    }

    @Test
    fun savedId_readBackByFreshInstance() {
        PrefsManager(context).setHeroWidgetEventId(7, 42L)
        // 换新实例读：模拟组件渲染侧（不同对象、可能不同进程时机）的读取
        assertEquals(42L, PrefsManager(context).heroWidgetEventId(7))
    }

    @Test
    fun idsAreIsolatedPerWidgetInstance() {
        val writer = PrefsManager(context)
        writer.setHeroWidgetEventId(7, 42L)
        writer.setHeroWidgetEventId(8, 43L)
        val reader = PrefsManager(context)
        assertEquals(42L, reader.heroWidgetEventId(7))
        assertEquals(43L, reader.heroWidgetEventId(8))
        assertNull(reader.heroWidgetEventId(9))
    }

    @Test
    fun clearedId_readsBackNull() {
        val prefs = PrefsManager(context)
        prefs.setHeroWidgetEventId(7, 42L)
        prefs.setHeroWidgetEventId(7, null)
        assertNull(PrefsManager(context).heroWidgetEventId(7))
    }

    @Test
    fun savedId_drivesResolveHeroToConfiguredEvent() {
        // 存：widget 7 选了事件 2（比事件 1 更远的未来事件，默认口径绝不会选它）
        PrefsManager(context).setHeroWidgetEventId(7, 2L)
        // 读回 + 选事件：必须命中事件 2，而不是回落口径的事件 1
        val readBack = PrefsManager(context).heroWidgetEventId(7)
        val rows = listOf(row(1L, 3L), row(2L, 30L))
        val picked = HeroDaysWidget.resolveHero(configuredId = readBack, rows = rows)
        assertEquals(2L, picked?.first?.id)
    }

    private fun event(id: Long) =
        EventEntity(id = id, name = "事件$id", date = 0L)

    private fun occ(daysToNext: Long) = EventOccurrence(
        start = LocalDate.of(2026, 1, 1),
        next = LocalDate.of(2026, 6, 1).plusDays(daysToNext),
        daysToNext = daysToNext,
        daysSinceStart = 0L,
        isToday = daysToNext == 0L,
        isRepeating = false,
        ended = false
    )

    private fun row(id: Long, daysToNext: Long) = event(id) to occ(daysToNext)
}
