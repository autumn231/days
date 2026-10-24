package com.example.countdowndays.util

import java.text.SimpleDateFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.Calendar
import java.util.Date
import java.util.Locale

object DateUtils {

    private val dateFmt = SimpleDateFormat("yyyy年MM月dd日", Locale.CHINA)
    private val weekFmt = SimpleDateFormat("yyyy年MM月dd日 EEEE", Locale.CHINA)
    private val dateTimeFmt = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA)

    /** 把时间戳对齐到当天 0 点 */
    fun startOfDay(millis: Long): Long {
        val c = Calendar.getInstance()
        c.timeInMillis = millis
        c.set(Calendar.HOUR_OF_DAY, 0)
        c.set(Calendar.MINUTE, 0)
        c.set(Calendar.SECOND, 0)
        c.set(Calendar.MILLISECOND, 0)
        return c.timeInMillis
    }

    fun todayMillis(): Long = startOfDay(System.currentTimeMillis())

    /**
     * 目标日期相对今天的天数差；未来为正，过去为负，今天为 0。
     *
     * 之前用 (startOfDay(目标) - today) / 86400000 计算，
     * 在有夏令时的时区"一天"可能是 23 或 25 小时，会被截断成少/多一天。
     * 这里按日历日相减，与时区偏移、夏令时无关。
     */
    fun dayDiff(targetMillis: Long): Long {
        val zone = ZoneId.systemDefault()
        val target = Instant.ofEpochMilli(targetMillis).atZone(zone).toLocalDate()
        return ChronoUnit.DAYS.between(LocalDate.now(zone), target)
    }

    fun isToday(targetMillis: Long): Boolean = dayDiff(targetMillis) == 0L

    fun isFuture(targetMillis: Long): Boolean = dayDiff(targetMillis) > 0

    /** 距离天数的绝对值 */
    fun countdownDays(targetMillis: Long): Long = kotlin.math.abs(dayDiff(targetMillis))

    fun countdownLabel(targetMillis: Long): String {
        val d = dayDiff(targetMillis)
        return when {
            d == 0L -> "就是今天"
            d > 0 -> "还有 $d 天"
            else -> "已过 ${-d} 天"
        }
    }

    fun formatDate(millis: Long): String = dateFmt.format(Date(millis))

    fun formatDateWithWeek(millis: Long): String = weekFmt.format(Date(millis))

    fun formatDateTime(millis: Long): String = dateTimeFmt.format(Date(millis))
}
