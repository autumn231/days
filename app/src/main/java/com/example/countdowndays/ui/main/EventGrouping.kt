package com.example.countdowndays.ui.main

import com.example.countdowndays.data.EventOccurrence
import com.example.countdowndays.data.EventWithNodes
import com.example.countdowndays.data.occurrence
import com.example.countdowndays.util.SortMode
import java.time.LocalDate

enum class EventBucket(val title: String) {
    PINNED("置顶"),
    TODAY("今天"),
    WEEK("本周"),
    MONTH("这个月"),
    LATER("以后"),
    PAST("已过去")
}

data class EventRow(
    val item: EventWithNodes,
    val occ: EventOccurrence
)

/** 把事件按时距分桶，置顶单独成组；同组内按用户选择的排序方式排列 */
fun groupEvents(
    items: List<EventWithNodes>,
    sortMode: SortMode,
    categoryId: Long?,
    today: LocalDate = LocalDate.now()
): List<Pair<EventBucket, List<EventRow>>> {
    val rows = items.asSequence()
        .filter { categoryId == null || it.event.categoryId == categoryId }
        .map { EventRow(it, it.event.occurrence(today)) }
        .toList()

    fun sort(list: List<EventRow>): List<EventRow> = when (sortMode) {
        SortMode.DATE -> list.sortedWith(
            compareBy(
                { it.occ.daysToNext ?: Long.MAX_VALUE },
                { it.item.event.name }
            )
        )
        SortMode.CREATED -> list.sortedByDescending { it.item.event.createdAt }
        SortMode.NAME -> list.sortedBy { it.item.event.name }
    }

    val pinned = sort(rows.filter { it.item.event.isPinned })
    val rest = rows.filter { !it.item.event.isPinned }

    val buckets = mutableListOf<Pair<EventBucket, List<EventRow>>>()
    if (pinned.isNotEmpty()) buckets += EventBucket.PINNED to pinned

    fun add(bucket: EventBucket, list: List<EventRow>) {
        if (list.isNotEmpty()) buckets += bucket to sort(list)
    }

    val active = rest.filter { !it.occ.ended }
    add(EventBucket.TODAY, active.filter { it.occ.isToday })
    add(
        EventBucket.WEEK,
        active.filter { !it.occ.isToday && (it.occ.daysToNext ?: 0) in 1..7 }
    )
    add(
        EventBucket.MONTH,
        active.filter { !it.occ.isToday && (it.occ.daysToNext ?: 0) in 8..31 }
    )
    add(EventBucket.LATER, active.filter { (it.occ.daysToNext ?: 0) > 31 })
    add(
        EventBucket.PAST,
        rest.filter { it.occ.ended || (!it.occ.isRepeating && (it.occ.daysToNext ?: 0) < 0) }
    )
    return buckets
}

/** 首页 Hero 用的「最值得关注」事件：优先最近的一个未来事件 */
fun pickHighlight(rows: List<EventRow>): EventRow? =
    rows.filter { (it.occ.daysToNext ?: 0) >= 0 }.minByOrNull { it.occ.daysToNext ?: Long.MAX_VALUE }
        ?: rows.minByOrNull { kotlin.math.abs(it.occ.daysToNext ?: Long.MAX_VALUE) }
