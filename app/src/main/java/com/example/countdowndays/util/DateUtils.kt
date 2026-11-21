package com.example.countdowndays.util

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.Period
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

object DateUtils {

    // DateTimeFormatter 不可变且线程安全；旧实现用共享 SimpleDateFormat，
    // 主线程与小组件/后台线程并发调用时可能打出错误日期。
    private val dateFmt = DateTimeFormatter.ofPattern("yyyy年MM月dd日", Locale.CHINA)
    private val weekFmt = DateTimeFormatter.ofPattern("yyyy年MM月dd日 EEEE", Locale.CHINA)
    private val dateTimeFmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm", Locale.CHINA)
    private val isoFmt = DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.CHINA)

    private val weekNames = arrayOf("星期一", "星期二", "星期三", "星期四", "星期五", "星期六", "星期日")

    /** 把时间戳对齐到当天 0 点 */
    fun startOfDay(millis: Long): Long = toMillis(toLocalDate(millis))

    fun todayMillis(): Long = startOfDay(System.currentTimeMillis())

    fun toLocalDate(millis: Long): LocalDate =
        Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate()

    fun toMillis(date: LocalDate): Long =
        date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

    /**
     * 目标日期相对今天的天数差；未来为正，过去为负，今天为 0。
     * 按日历日相减，不受夏令时（23/25 小时的一天）影响。
     */
    fun dayDiff(targetMillis: Long): Long =
        ChronoUnit.DAYS.between(LocalDate.now(), toLocalDate(targetMillis))

    fun daysBetween(from: LocalDate, to: LocalDate): Long = ChronoUnit.DAYS.between(from, to)

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

    private fun toLocalDateTime(millis: Long): LocalDateTime =
        LocalDateTime.ofInstant(Instant.ofEpochMilli(millis), ZoneId.systemDefault())

    fun formatDate(millis: Long): String = dateFmt.format(toLocalDateTime(millis))

    fun formatDateWithWeek(millis: Long): String = weekFmt.format(toLocalDateTime(millis))

    fun formatDateTime(millis: Long): String = dateTimeFmt.format(toLocalDateTime(millis))

    fun formatIso(localDate: LocalDate): String = localDate.format(isoFmt)

    fun weekdayCn(localDate: LocalDate): String =
        weekNames[(localDate.dayOfWeek.value - 1).coerceIn(0, 6)]

    /** 「3年9个月16天」，不足一年时省略年 */
    fun periodText(from: LocalDate, to: LocalDate): String {
        if (to.isBefore(from)) return "0天"
        val p = Period.between(from, to)
        val parts = mutableListOf<String>()
        if (p.years > 0) parts += "${p.years}年"
        if (p.months > 0) parts += "${p.months}个月"
        if (p.days > 0 || parts.isEmpty()) parts += "${p.days}天"
        return parts.joinToString("")
    }
}
