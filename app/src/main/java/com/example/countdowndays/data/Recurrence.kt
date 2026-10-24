package com.example.countdowndays.data

import com.example.countdowndays.util.DateUtils
import com.example.countdowndays.util.LunarCalendar
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit

enum class RepeatType {
    NONE, DAILY, WEEKLY, MONTHLY, YEARLY;

    companion object {
        fun of(raw: String): RepeatType = entries.firstOrNull { it.name == raw } ?: NONE
    }
}

/**
 * 某条事件相对今天的展示状态。
 * 非重复事件：next 就是原日期，daysToNext 可能为负（已过去）。
 * 重复事件：next 是下一次发生日；超过结束日期后 ended = true。
 */
data class EventOccurrence(
    val start: LocalDate,
    val next: LocalDate?,
    val daysToNext: Long?,
    val daysSinceStart: Long,
    val isToday: Boolean,
    val isRepeating: Boolean,
    val ended: Boolean
) {
    val isFuture: Boolean get() = (daysToNext ?: 0) > 0
}

fun EventEntity.occurrence(today: LocalDate = LocalDate.now()): EventOccurrence {
    val start = DateUtils.toLocalDate(date)
    val type = RepeatType.of(repeatType)
    val repeating = type != RepeatType.NONE
    val sinceStart = ChronoUnit.DAYS.between(start, today)

    if (!repeating) {
        val diff = ChronoUnit.DAYS.between(today, start)
        return EventOccurrence(
            start = start,
            next = start,
            daysToNext = diff,
            daysSinceStart = sinceStart,
            isToday = diff == 0L,
            isRepeating = false,
            ended = false
        )
    }

    val end = repeatEndDate?.let { DateUtils.toLocalDate(it) }
    var next = nextRepeat(start, type, today)
    if (end != null && next != null && next.isAfter(end)) next = null

    return if (next == null) {
        EventOccurrence(start, null, null, sinceStart, false, true, true)
    } else {
        val diff = ChronoUnit.DAYS.between(today, next)
        EventOccurrence(start, next, diff, sinceStart, diff == 0L, true, false)
    }
}

private fun EventEntity.nextRepeat(start: LocalDate, type: RepeatType, today: LocalDate): LocalDate? {
    // 起始日还在未来时，下一次就是起始日本身
    if (start.isAfter(today)) return start
    return when (type) {
        RepeatType.NONE -> start

        RepeatType.DAILY -> today

        RepeatType.WEEKLY -> {
            val diff = ChronoUnit.DAYS.between(start, today)
            start.plusDays(((diff + 6) / 7) * 7)
        }

        RepeatType.MONTHLY -> {
            val startYm = YearMonth.from(start)
            var k = ChronoUnit.MONTHS.between(startYm, YearMonth.from(today)).toInt().coerceAtLeast(0)
            var candidate = monthDayAt(start, startYm.plusMonths(k.toLong()))
            if (candidate.isBefore(today)) {
                k += 1
                candidate = monthDayAt(start, startYm.plusMonths(k.toLong()))
            }
            candidate
        }

        RepeatType.YEARLY -> {
            if (isLunar && lunarMonth in 1..12 && lunarDay in 1..30) {
                LunarCalendar.nextOccurrence(lunarMonth, lunarDay, today)
            } else {
                var candidate = yearDayAt(start, today.year)
                if (candidate.isBefore(today)) candidate = yearDayAt(start, today.year + 1)
                candidate
            }
        }
}
}

/** 保持"日"不越界：1月31日 + 1个月 => 2月28/29日 */
private fun monthDayAt(start: LocalDate, ym: YearMonth): LocalDate =
    ym.atDay(start.dayOfMonth.coerceAtMost(ym.lengthOfMonth()))

/** 2月29日在平年落到2月28日 */
private fun yearDayAt(start: LocalDate, year: Int): LocalDate {
    val ym = YearMonth.of(year, start.month)
    return ym.atDay(start.dayOfMonth.coerceAtMost(ym.lengthOfMonth()))
}

fun EventEntity.repeatLabel(): String = when (RepeatType.of(repeatType)) {
    RepeatType.NONE -> ""
    RepeatType.DAILY -> "每天"
    RepeatType.WEEKLY -> "每周"
    RepeatType.MONTHLY -> "每月"
    RepeatType.YEARLY -> if (isLunar) "每年（农历）" else "每年"
}
