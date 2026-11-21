package com.example.countdowndays.data

import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.abs

/**
 * 卡片流（首页焦点卡 / 展示页）的统一顺序。
 *
 * 以「下一次发生日」为基准升序排列：左边是比今天早的日子，右边是比今天晚的日子。
 * 用下一次发生日而不是原始日期，是为了让每年重复的生日、纪念日排到正确的位置
 * ——否则 1990 年的生日会永远待在列表最左边。
 * 已经结束（不再发生）的事件用起始日排序，自然落在最左侧。
 *
 * 首页走轻量查询只有 EventEntity，展示页带时间线节点是 EventWithNodes，
 * 两种列表共用同一套排序实现（按事件抽取后走同一个核心函数）。
 */
private fun <T> List<T>.sortedByOccurrenceKey(
    today: LocalDate,
    eventOf: (T) -> EventEntity
): List<T> =
    // 先把排序键算好再排：compareBy 的选择器会在每次比较时重跑，
    // occurrence() 对农历事件还要走一遍 ICU 换算，不能放在比较器里反复算。
    map { item ->
        val occ = eventOf(item).occurrence(today)
        item to (occ.next ?: occ.start)
    }.sortedWith(compareBy({ it.second }, { eventOf(it.first).id }))
        .map { it.first }

fun List<EventWithNodes>.sortedTimeline(today: LocalDate = LocalDate.now()): List<EventWithNodes> =
    sortedByOccurrenceKey(today) { it.event }

@JvmName("sortedTimelineEntities")
fun List<EventEntity>.sortedTimeline(today: LocalDate = LocalDate.now()): List<EventEntity> =
    sortedByOccurrenceKey(today) { it }

/**
 * 离今天最近的一条的下标，作为两个卡片流的默认停留位置。
 *
 * 今天优先；距离相同时取未来的那条（倒数日更关心「还有几天」）。
 * 全部已结束时退化为最近结束的一条。
 */
private fun <T> List<T>.nearestTodayIndex(
    today: LocalDate,
    eventOf: (T) -> EventEntity
): Int {
    if (isEmpty()) return 0
    var bestIndex = 0
    var bestScore = Long.MAX_VALUE
    forEachIndexed { index, item ->
        val occ = eventOf(item).occurrence(today)
        val ref = occ.next ?: occ.start
        val distance = abs(ChronoUnit.DAYS.between(today, ref))
        // 同距离时未来优先：过去的时间多算 1
        val score = distance * 2 + if (ref.isBefore(today)) 1 else 0
        if (score < bestScore) {
            bestScore = score
            bestIndex = index
        }
    }
    return bestIndex
}

fun List<EventWithNodes>.indexNearestToday(today: LocalDate = LocalDate.now()): Int =
    nearestTodayIndex(today) { it.event }

@JvmName("indexNearestTodayEntities")
fun List<EventEntity>.indexNearestToday(today: LocalDate = LocalDate.now()): Int =
    nearestTodayIndex(today) { it }
