package com.example.countdowndays

import com.example.countdowndays.data.EventEntity
import com.example.countdowndays.util.Anniversary
import com.example.countdowndays.util.DateUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

/** 纪念日预告推导：整百天与周年哪个先到，固定 today 避免随日期漂移 */
class AnniversaryTest {

    private val today: LocalDate = LocalDate.of(2026, 11, 23)

    private fun event(date: LocalDate, repeatType: String = "NONE") = EventEntity(
        name = "测试",
        date = DateUtils.toMillis(date),
        repeatType = repeatType
    )

    @Test
    fun hundredDays_next() {
        // 2026-08-15 起，今天正好第 100 天 → 下一个节点是第 200 天
        val info = Anniversary.nextFor(event(LocalDate.of(2026, 8, 15)), today)!!
        assertEquals("第 200 天", info.label)
        assertEquals(LocalDate.of(2027, 3, 3), info.date)
        assertEquals(100L, info.daysUntil)
        assertEquals(100L, info.elapsedDays)
        assertEquals(200L, info.totalDays)
    }

    @Test
    fun anniversary_beatsHundred() {
        // 2025-11-30 起，一周年（2026-11-30）在第 400 天（2027-01-04）之前
        val info = Anniversary.nextFor(event(LocalDate.of(2025, 11, 30)), today)!!
        assertEquals("一周年", info.label)
        assertEquals(LocalDate.of(2026, 11, 30), info.date)
        assertEquals(7L, info.daysUntil)
    }

    @Test
    fun leapDay_hundredFirst() {
        // 2024-02-29 起，今天第 998 天 → 第 1000 天（2026-11-25）先于三周年（2027-02-28）
        val info = Anniversary.nextFor(event(LocalDate.of(2024, 2, 29)), today)!!
        assertEquals("第 1000 天", info.label)
        assertEquals(LocalDate.of(2026, 11, 25), info.date)
        assertEquals(2L, info.daysUntil)
    }

    @Test
    fun multiYear_numeral() {
        // 2020-12-01 起：六周年 2026-12-01；第 2200 天在 2026-12-11，周年先到
        val info = Anniversary.nextFor(event(LocalDate.of(2020, 12, 1)), today)!!
        assertEquals("六周年", info.label)
        assertEquals(LocalDate.of(2026, 12, 1), info.date)
    }

    @Test
    fun futureEvent_null() {
        assertNull(Anniversary.nextFor(event(today.plusDays(3)), today))
    }

    @Test
    fun todayEvent_null() {
        assertNull(Anniversary.nextFor(event(today), today))
    }

    @Test
    fun repeatingEvent_null() {
        assertNull(Anniversary.nextFor(event(today.minusDays(500), "YEARLY"), today))
    }
}
