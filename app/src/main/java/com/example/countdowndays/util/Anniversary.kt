package com.example.countdowndays.util

import com.example.countdowndays.data.EventEntity
import com.example.countdowndays.data.RepeatType
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit

/**
 * 纪念日预告：给「已过去」的一次性事件推导下一个值得纪念的节点——
 * 整百天（第 100/200/300… 天）与每一周年，哪个先到算哪个（同一天优先周年）。
 * 重复事件本身会滚动到下一次发生，不在此列；纯本地推导，无副作用。
 */
data class AnniversaryInfo(
    /** 「第 1000 天」或「一周年」 */
    val label: String,
    /** 纪念日当天 */
    val date: LocalDate,
    /** 距纪念日还有多少天（>= 1；纪念日若在今天，调用方不会拿到——见 nextFor） */
    val daysUntil: Long,
    /** 到今天为止已经走过的天数 */
    val elapsedDays: Long,
    /** 纪念日当天的累计天数（整百天时 = 100 的倍数；周年时为实际天数） */
    val totalDays: Long
)

object Anniversary {

    private val yearNumerals = arrayOf("", "一", "二", "三", "四", "五", "六", "七", "八", "九", "十")

    fun nextFor(event: EventEntity, today: LocalDate = LocalDate.now()): AnniversaryInfo? {
        if (RepeatType.of(event.repeatType) != RepeatType.NONE) return null
        val start = DateUtils.toLocalDate(event.date)
        val elapsed = ChronoUnit.DAYS.between(start, today)
        if (elapsed <= 0) return null

        // 下一个整百天
        val hundredTotal = (elapsed / 100 + 1) * 100
        val hundredDate = start.plusDays(hundredTotal)

        // 下一个周年（2月29日平年落 2月28日，与 Recurrence.yearDayAt 同口径）
        var years = today.year - start.year
        var yearDate = yearDayAt(start, today.year)
        if (!yearDate.isAfter(today)) {
            yearDate = yearDayAt(start, today.year + 1)
            years += 1
        }

        // 周年更早或同日时优先周年，否则取整百天
        return if (!yearDate.isAfter(hundredDate)) {
            AnniversaryInfo(
                label = "${yearLabel(years)}周年",
                date = yearDate,
                daysUntil = ChronoUnit.DAYS.between(today, yearDate),
                elapsedDays = elapsed,
                totalDays = ChronoUnit.DAYS.between(start, yearDate)
            )
        } else {
            hundredInfo(start, hundredTotal, hundredDate, today, elapsed)
        }
    }

    private fun hundredInfo(
        start: LocalDate,
        total: Long,
        date: LocalDate,
        today: LocalDate,
        elapsed: Long
    ) = AnniversaryInfo(
        label = "第 $total 天",
        date = date,
        daysUntil = ChronoUnit.DAYS.between(today, date),
        elapsedDays = elapsed,
        totalDays = ChronoUnit.DAYS.between(start, date)
    )

    private fun yearLabel(years: Int): String =
        if (years in 1..10) yearNumerals[years] else years.toString()

    private fun yearDayAt(start: LocalDate, year: Int): LocalDate {
        val ym = YearMonth.of(year, start.month)
        return ym.atDay(start.dayOfMonth.coerceAtMost(ym.lengthOfMonth()))
    }
}
