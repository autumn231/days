package com.example.countdowndays.util

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

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

    /** 目标日期相对今天的天数差；未来为正，过去为负，今天为 0 */
    fun dayDiff(targetMillis: Long): Long {
        val ms = startOfDay(targetMillis) - todayMillis()
        return TimeUnit.MILLISECONDS.toDays(ms)
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
