package com.example.countdowndays.util

import android.icu.util.ChineseCalendar
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Calendar

/**
 * 农历换算。直接使用 Android 平台内置的 ICU ChineseCalendar（API 24+），
 * 不自己维护 1900-2100 的农历表，避免手工表数据出错。
 */
object LunarCalendar {

    data class LunarDate(
        val year: Int,
        val month: Int,
        val day: Int,
        val isLeapMonth: Boolean
    )

    private const val YEAR_OFFSET = 2637

    private fun zone(): ZoneId = ZoneId.systemDefault()

    /** 公历 -> 农历 */
    fun fromSolar(date: LocalDate): LunarDate {
        val cal = ChineseCalendar()
        cal.timeInMillis = date.atStartOfDay(zone()).toInstant().toEpochMilli()
        val extendedYear = cal.get(ChineseCalendar.EXTENDED_YEAR)
        return LunarDate(
            year = extendedYear - YEAR_OFFSET,
            month = cal.get(Calendar.MONTH) + 1,
            day = cal.get(Calendar.DAY_OF_MONTH),
            isLeapMonth = cal.get(ChineseCalendar.IS_LEAP_MONTH) == 1
        )
    }

    /** 农历 -> 公历；闰月按平月处理（生日场景更符合直觉） */
    fun toSolar(lunarYear: Int, month: Int, day: Int): LocalDate {
        val cal = ChineseCalendar()
        cal.clear()
        cal.set(ChineseCalendar.EXTENDED_YEAR, lunarYear + YEAR_OFFSET)
        cal.set(ChineseCalendar.IS_LEAP_MONTH, 0)
        cal.set(Calendar.MONTH, (month - 1).coerceIn(0, 11))
        cal.set(Calendar.DAY_OF_MONTH, day.coerceIn(1, 30))
        val millis = cal.timeInMillis
        return Instant.ofEpochMilli(millis).atZone(zone()).toLocalDate()
    }

    /** 从 from 起（含当天）的下一个农历月日 */
    fun nextOccurrence(month: Int, day: Int, from: LocalDate): LocalDate {
        val startYear = fromSolar(from).year
        for (year in startYear..startYear + 2) {
            val candidate = toSolar(year, month, day)
            if (!candidate.isBefore(from)) return candidate
        }
        return toSolar(startYear + 3, month, day)
    }

    private val monthNames = arrayOf(
        "正月", "二月", "三月", "四月", "五月", "六月",
        "七月", "八月", "九月", "十月", "冬月", "腊月"
    )
    private val dayNames = arrayOf(
        "初一", "初二", "初三", "初四", "初五", "初六", "初七", "初八", "初九", "初十",
        "十一", "十二", "十三", "十四", "十五", "十六", "十七", "十八", "十九", "二十",
        "廿一", "廿二", "廿三", "廿四", "廿五", "廿六", "廿七", "廿八", "廿九", "三十"
    )

    fun format(month: Int, day: Int): String {
        val m = monthNames.getOrNull(month - 1) ?: "${month}月"
        val d = dayNames.getOrNull(day - 1) ?: "$day"
        return "农历$m$d"
    }
}
