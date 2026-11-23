package com.example.countdowndays

import com.example.countdowndays.data.EventEntity
import com.example.countdowndays.data.EventOccurrence
import com.example.countdowndays.widget.HeroDaysWidget
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 主视觉组件选事件口径（v4.1）：配置优先 → 回落最近的未来事件 → 再回落 pickHero。
 * rows 只含未归档事件，所以「所选被删/被归档」都表现为 id 在 rows 里找不到。
 */
class HeroWidgetResolveTest {

    private fun event(id: Long, pinned: Boolean = false) =
        EventEntity(id = id, name = "事件$id", date = 0L, isPinned = pinned)

    private fun occ(daysToNext: Long?, ended: Boolean = false) = EventOccurrence(
        start = LocalDate.of(2026, 1, 1),
        next = daysToNext?.let { LocalDate.of(2026, 6, 1).plusDays(it) },
        daysToNext = daysToNext,
        daysSinceStart = 0L,
        isToday = daysToNext == 0L,
        isRepeating = false,
        ended = ended
    )

    private fun row(id: Long, daysToNext: Long?, pinned: Boolean = false, ended: Boolean = false) =
        event(id, pinned) to occ(daysToNext, ended)

    @Test
    fun configuredEventPresent_winsOverNearerFuture() {
        val rows = listOf(row(1L, 3L), row(2L, 30L))
        val picked = HeroDaysWidget.resolveHero(configuredId = 2L, rows = rows)
        assertEquals(2L, picked?.first?.id)
    }

    @Test
    fun configuredEventGone_fallsBackToNearestFuture() {
        // 配置的 99 号已删除/归档，不在活跃列表里
        val rows = listOf(row(1L, 10L), row(2L, 4L), row(3L, -2L))
        val picked = HeroDaysWidget.resolveHero(configuredId = 99L, rows = rows)
        assertEquals(2L, picked?.first?.id)
    }

    @Test
    fun noConfig_picksNearestFuture_todayCounts() {
        val rows = listOf(row(1L, 5L), row(2L, 0L), row(3L, -1L))
        val picked = HeroDaysWidget.resolveHero(configuredId = null, rows = rows)
        assertEquals(2L, picked?.first?.id)
    }

    @Test
    fun noFuture_pinnedPastWins() {
        val rows = listOf(row(1L, -5L), row(2L, -10L, pinned = true))
        val picked = HeroDaysWidget.resolveHero(configuredId = null, rows = rows)
        assertEquals(2L, picked?.first?.id)
    }

    @Test
    fun noFuture_noPin_picksNearestPast() {
        val rows = listOf(row(1L, -9L), row(2L, -3L))
        val picked = HeroDaysWidget.resolveHero(configuredId = null, rows = rows)
        assertEquals(2L, picked?.first?.id)
    }

    @Test
    fun endedEventsAreSkippedInFallback() {
        val rows = listOf(row(1L, null, ended = true), row(2L, 7L))
        val picked = HeroDaysWidget.resolveHero(configuredId = null, rows = rows)
        assertEquals(2L, picked?.first?.id)
    }

    @Test
    fun emptyRows_returnsNull() {
        assertNull(HeroDaysWidget.resolveHero(configuredId = 1L, rows = emptyList()))
        assertNull(HeroDaysWidget.resolveHero(configuredId = null, rows = emptyList()))
    }
}
