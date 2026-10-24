package com.example.countdowndays

import com.example.countdowndays.data.EventEntity
import com.example.countdowndays.data.EventWithNodes
import com.example.countdowndays.data.indexNearestToday
import com.example.countdowndays.data.sortedTimeline
import com.example.countdowndays.util.DateUtils
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

/**
 * 卡片流排序与默认定位：左边比今天早、右边比今天晚，默认停在今天或最近的一天。
 */
class EventOrderingTest {

    private fun item(
        id: Long,
        name: String,
        date: LocalDate,
        repeatType: String = "NONE"
    ) = EventWithNodes(
        EventEntity(
            id = id,
            name = name,
            date = DateUtils.toMillis(date),
            repeatType = repeatType,
            createdAt = id
        ),
        emptyList()
    )

    @Test
    fun sortedTimeline_pastLeftTodayMiddleFutureRight() {
        val today = LocalDate.of(2026, 10, 24)
        val sorted = listOf(
            item(1, "很久以后", today.plusDays(30)),
            item(2, "上个月", today.minusDays(10)),
            item(3, "今天", today),
            item(4, "三天后", today.plusDays(3))
        ).sortedTimeline(today)

        assertEquals(listOf("上个月", "今天", "三天后", "很久以后"), sorted.map { it.event.name })
    }

    @Test
    fun indexNearestToday_prefersToday() {
        val today = LocalDate.of(2026, 10, 24)
        val sorted = listOf(
            item(1, "两天前", today.minusDays(2)),
            item(2, "今天", today),
            item(3, "两天后", today.plusDays(2))
        ).sortedTimeline(today)

        assertEquals(1, sorted.indexNearestToday(today))
    }

    @Test
    fun indexNearestToday_withoutToday_prefersFutureOnTie() {
        val today = LocalDate.of(2026, 10, 24)
        val sorted = listOf(
            item(1, "十天后", today.plusDays(10)),
            item(2, "三天前", today.minusDays(3)),
            item(3, "三天后", today.plusDays(3))
        ).sortedTimeline(today)

        // 三天前与三天后距离相同，取未来那条
        assertEquals(1, sorted.indexNearestToday(today))
        assertEquals("三天后", sorted[1].event.name)
    }

    @Test
    fun yearlyRepeat_sortsByNextOccurrenceNotOriginalDate() {
        val today = LocalDate.of(2026, 10, 24)
        // 1995 年的生日，下一次发生在今年 12 月，应排在「一周后」的右边
        val birthday = item(1, "生日", LocalDate.of(1995, 12, 1), repeatType = "YEARLY")
        val nextWeek = item(2, "一周后", today.plusDays(7))

        val sorted = listOf(birthday, nextWeek).sortedTimeline(today)

        assertEquals(listOf("一周后", "生日"), sorted.map { it.event.name })
    }
}
