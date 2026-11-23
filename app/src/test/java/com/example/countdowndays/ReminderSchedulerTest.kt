package com.example.countdowndays

import com.example.countdowndays.data.EventEntity
import com.example.countdowndays.util.DateUtils
import com.example.countdowndays.util.ReminderScheduler
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

/**
 * 提醒触发时刻计算：一次性/每天/每周/每月/每年 × 提前 0/3/7/30 天。
 * 固定 today 与 now，避免测试结果随运行日期漂移。
 */
class ReminderSchedulerTest {

    private val today: LocalDate = LocalDate.of(2026, 11, 21)

    private fun nowAt(hour: Int): Long =
        today.atTime(hour, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

    private fun at(date: LocalDate, hour: Int): Long =
        date.atTime(hour, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

    private fun event(
        date: LocalDate,
        repeatType: String = "NONE",
        advance: Int = 0,
        hour: Int = 9,
        end: LocalDate? = null
    ) = EventEntity(
        name = "测试",
        date = DateUtils.toMillis(date),
        repeatType = repeatType,
        repeatEndDate = end?.let { DateUtils.toMillis(it) },
        reminderEnabled = true,
        reminderAdvanceDays = advance,
        reminderHour = hour,
        reminderMinute = 0
    )

    private fun trigger(e: EventEntity, nowHour: Int = 10): Long? =
        ReminderScheduler.nextTriggerMillis(e, today, nowAt(nowHour))

    // ---------- 一次性事件 ----------

    @Test
    fun oneTime_future() {
        val e = event(today.plusDays(5))
        assertEquals(at(today.plusDays(5), 9), trigger(e))
    }

    @Test
    fun oneTime_today_reminderLaterToday() {
        val e = event(today, hour = 11)
        assertEquals(at(today, 11), trigger(e))
    }

    @Test
    fun oneTime_today_reminderPassed() {
        val e = event(today, hour = 9)
        assertNull(trigger(e))
    }

    @Test
    fun oneTime_past() {
        val e = event(today.minusDays(3))
        assertNull(trigger(e))
    }

    @Test
    fun oneTime_advanceStillAhead() {
        val e = event(today.plusDays(5), advance = 3)
        assertEquals(at(today.plusDays(2), 9), trigger(e))
    }

    @Test
    fun oneTime_advanceMomentPassed_noTrigger() {
        // 事件还有 2 天，但「提前 3 天」的提醒时刻已经过了：一次性事件不补发
        val e = event(today.plusDays(2), advance = 3)
        assertNull(trigger(e))
    }

    // ---------- 每天 ----------

    @Test
    fun daily_todayPassed_rollsToTomorrow() {
        val e = event(today.minusDays(10), repeatType = "DAILY")
        assertEquals(at(today.plusDays(1), 9), trigger(e))
    }

    @Test
    fun daily_todayUpcoming() {
        val e = event(today.minusDays(10), repeatType = "DAILY", hour = 11)
        assertEquals(at(today, 11), trigger(e))
    }

    @Test
    fun daily_largeAdvance_notDropped() {
        // 回归：旧实现只试 2 次，提前 3 天时算不出触发时刻、返回 null，
        // 闹钟被取消后这个事件的提醒就整段没了
        val e = event(today.minusDays(10), repeatType = "DAILY", advance = 3)
        assertEquals(at(today.plusDays(1), 9), trigger(e))
    }

    @Test
    fun daily_largeAdvance_landsToday() {
        val e = event(today.minusDays(10), repeatType = "DAILY", advance = 3, hour = 11)
        assertEquals(at(today, 11), trigger(e))
    }

    @Test
    fun daily_notStartedYet() {
        val e = event(today.plusDays(10), repeatType = "DAILY")
        assertEquals(at(today.plusDays(10), 9), trigger(e))
    }

    // ---------- 每周 ----------

    @Test
    fun weekly_nextOccurrence() {
        // 起始日 11-11（周三），发生日为 11-11、11-18、11-25…，today 是 11-21
        val e = event(today.minusDays(10), repeatType = "WEEKLY")
        assertEquals(at(today.plusDays(4), 9), trigger(e))
    }

    @Test
    fun weekly_largeAdvance() {
        // 11-25 那次的提醒日是 11-18（已过），应顺延到 12-02 那次，提醒日 11-25
        val e = event(today.minusDays(10), repeatType = "WEEKLY", advance = 7)
        assertEquals(at(today.plusDays(4), 9), trigger(e))
    }

    @Test
    fun weekly_ended() {
        val e = event(
            today.minusDays(10),
            repeatType = "WEEKLY",
            end = today.minusDays(1)
        )
        assertNull(trigger(e))
    }

    // ---------- 每月 / 每年 ----------

    @Test
    fun monthly_nextOccurrence() {
        val e = event(LocalDate.of(2026, 8, 15), repeatType = "MONTHLY")
        assertEquals(at(LocalDate.of(2026, 12, 15), 9), trigger(e))
    }

    @Test
    fun yearly_nextOccurrence() {
        val e = event(LocalDate.of(2020, 12, 19), repeatType = "YEARLY")
        assertEquals(at(LocalDate.of(2026, 12, 19), 9), trigger(e))
    }

    @Test
    fun yearly_largeAdvance_rollsToNextYear() {
        // 今年 12-19 那次的提醒日是 11-19（已过），顺延到明年那次
        val e = event(LocalDate.of(2020, 12, 19), repeatType = "YEARLY", advance = 30)
        assertEquals(at(LocalDate.of(2027, 11, 19), 9), trigger(e))
    }

    // ---------- 多节点提醒（v3.23） ----------

    @Test
    fun multiParam_advanceAndTimeIndependent() {
        // 事件字段是提前 0 天 9 点，但按另一条提醒的参数（提前 3 天、20 点）算触发
        val e = event(today.plusDays(5), advance = 0, hour = 9)
        val ts = ReminderScheduler.nextTriggerMillis(e, 3, 20, 30, today, nowAt(10))
        assertEquals(
            today.plusDays(2).atTime(20, 30)
                .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(),
            ts
        )
    }

    @Test
    fun specs_rowsWinOverLegacyFields() {
        val e = event(today.plusDays(5)).copy(id = 42)
        val rows = listOf(
            com.example.countdowndays.data.ReminderEntity(
                id = 7, eventId = 42, advanceDays = 7, hour = 8, minute = 0
            ),
            com.example.countdowndays.data.ReminderEntity(
                id = 9, eventId = 42, advanceDays = 0, hour = 20, minute = 0
            )
        )
        val specs = ReminderScheduler.specsFor(e, rows)
        assertEquals(2, specs.size)
        // 调度键就是提醒行 id，两条互不冲突
        assertEquals(setOf(7L, 9L), specs.map { it.key }.toSet())
        assertEquals(7, specs.first { it.key == 7L }.advanceDays)
    }

    @Test
    fun specs_emptyRows_fallBackToLegacy() {
        val e = event(today.plusDays(5), advance = 3, hour = 8).copy(id = 42)
        val specs = ReminderScheduler.specsFor(e, emptyList())
        assertEquals(1, specs.size)
        assertEquals(-42L, specs.single().key)
        assertEquals(3, specs.single().advanceDays)
        assertEquals(8, specs.single().hour)
    }

    @Test
    fun specs_disabled_noSpecs() {
        val e = event(today.plusDays(5)).copy(reminderEnabled = false)
        assertEquals(0, ReminderScheduler.specsFor(e, emptyList()).size)
    }
}
