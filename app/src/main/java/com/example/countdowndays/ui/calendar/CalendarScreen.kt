package com.example.countdowndays.ui.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.countdowndays.data.EventEntity
import com.example.countdowndays.data.EventWithNodes
import com.example.countdowndays.data.EventOccurrence
import com.example.countdowndays.data.RepeatType
import com.example.countdowndays.data.occurrence
import com.example.countdowndays.ui.common.MiniTag
import com.example.countdowndays.ui.main.MainViewModel
import com.example.countdowndays.ui.theme.eventColor
import com.example.countdowndays.util.DateUtils
import com.example.countdowndays.util.LunarCalendar
import java.time.LocalDate
import java.time.YearMonth

/**
 * 日历视图：按月展示每天有哪些事件（重复事件按规则展开）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarScreen(
    onBack: () -> Unit,
    onOpenEvent: (Long) -> Unit,
    vm: MainViewModel = com.example.countdowndays.ui.common.appViewModel {
        MainViewModel(it.repository, it.prefs)
    }
) {
    val events by vm.events.collectAsStateWithLifecycle()
    var month by remember { mutableStateOf(YearMonth.now()) }
    var selected by remember { mutableStateOf<LocalDate?>(LocalDate.now()) }

    val byDay = remember(events, month) {
        buildMap<LocalDate, MutableList<EventWithNodes>> {
            val start = month.atDay(1)
            val end = month.atEndOfMonth()
            var day = start
            while (!day.isAfter(end)) {
                events.forEach { item ->
                    if (item.event.occursOn(day)) {
                        getOrPut(day) { mutableListOf() }.add(item)
                    }
                }
                day = day.plusDays(1)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("日历") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(bottom = 24.dp)
        ) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { month = month.minusMonths(1) }) {
                        Icon(Icons.Filled.ChevronLeft, contentDescription = "上个月")
                    }
                    Text(
                        text = "${month.year} 年 ${month.monthValue} 月",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Center
                    )
                    IconButton(onClick = { month = month.plusMonths(1) }) {
                        Icon(Icons.Filled.ChevronRight, contentDescription = "下个月")
                    }
                }
            }

            item {
                MonthGrid(
                    month = month,
                    selected = selected,
                    byDay = byDay,
                    onSelect = { selected = it }
                )
            }

            val sel = selected
            if (sel != null) {
                item {
                    Text(
                        text = "${sel.monthValue} 月 ${sel.dayOfMonth} 日 · ${DateUtils.weekdayCn(sel)}",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(start = 20.dp, top = 18.dp, bottom = 6.dp)
                    )
                }
                val list = byDay[sel].orEmpty()
                if (list.isEmpty()) {
                    item {
                        Text(
                            text = "这一天没有安排",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
                        )
                    }
                } else {
                    items(list, key = { it.event.id }) { item ->
                        CalendarEventRow(item = item, date = sel) { onOpenEvent(item.event.id) }
                    }
                }
            }
        }
    }
}

@Composable
private fun MonthGrid(
    month: YearMonth,
    selected: LocalDate?,
    byDay: Map<LocalDate, List<EventWithNodes>>,
    onSelect: (LocalDate) -> Unit
) {
    val first = month.atDay(1)
    val offset = (first.dayOfWeek.value - 1)
    val days = month.lengthOfMonth()
    val rows = ((offset + days) + 6) / 7
    val weekLabels = listOf("一", "二", "三", "四", "五", "六", "日")

    Column(Modifier.padding(horizontal = 12.dp)) {
        Row(Modifier.fillMaxWidth()) {
            weekLabels.forEach { label ->
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f)
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        for (row in 0 until rows) {
            Row(Modifier.fillMaxWidth()) {
                for (col in 0..6) {
                    val index = row * 7 + col
                    val dayNumber = index - offset + 1
                    Box(Modifier.weight(1f).aspectRatio(1f)) {
                        if (dayNumber in 1..days) {
                            val date = month.atDay(dayNumber)
                            DayCell(
                                date = date,
                                isSelected = date == selected,
                                isToday = date == LocalDate.now(),
                                count = byDay[date]?.size ?: 0,
                                onClick = { onSelect(date) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DayCell(
    date: LocalDate,
    isSelected: Boolean,
    isToday: Boolean,
    count: Int,
    onClick: () -> Unit
) {
    val bg = when {
        isSelected -> MaterialTheme.colorScheme.primary
        isToday -> MaterialTheme.colorScheme.primaryContainer
        else -> androidx.compose.ui.graphics.Color.Transparent
    }
    val fg = when {
        isSelected -> MaterialTheme.colorScheme.onPrimary
        isToday -> MaterialTheme.colorScheme.onPrimaryContainer
        else -> MaterialTheme.colorScheme.onSurface
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(3.dp)
            .clip(CircleShape)
            .background(bg)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = date.dayOfMonth.toString(),
                style = MaterialTheme.typography.bodyMedium,
                color = fg,
                fontWeight = if (isSelected || isToday) FontWeight.Bold else FontWeight.Normal
            )
            if (count > 0) {
                Box(
                    Modifier
                        .size(4.dp)
                        .clip(CircleShape)
                        .background(if (isSelected) fg else MaterialTheme.colorScheme.primary)
                )
            }
        }
    }
}

@Composable
private fun CalendarEventRow(
    item: EventWithNodes,
    date: LocalDate,
    onClick: () -> Unit
) {
    val event = item.event
    val accent = eventColor(event.colorIndex)
    Surface(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant
        )
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(accent)
            )
            Spacer(Modifier.size(12.dp))
            Column(Modifier.weight(1f)) {
                Text(text = event.name, style = MaterialTheme.typography.titleSmall)
                Text(
                    text = buildString {
                        append(DateUtils.formatIso(date))
                        if (event.isLunar && event.lunarMonth > 0) {
                            append(" · ")
                            append(LunarCalendar.format(event.lunarMonth, event.lunarDay))
                        }
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (item.nodes.isNotEmpty()) MiniTag("${item.nodes.size} 条时间线", accent)
        }
    }
}

/** 某事件是否落在这一天（含重复规则） */
fun EventEntity.occursOn(day: LocalDate): Boolean {
    val start = DateUtils.toLocalDate(date)
    if (day.isBefore(start)) return false
    val end = repeatEndDate?.let { DateUtils.toLocalDate(it) }
    if (end != null && day.isAfter(end)) return false

    return when (RepeatType.of(repeatType)) {
        RepeatType.NONE -> day == start
        RepeatType.DAILY -> true
        RepeatType.WEEKLY -> java.time.temporal.ChronoUnit.DAYS.between(start, day) % 7 == 0L
        RepeatType.MONTHLY -> day.dayOfMonth == start.dayOfMonth.coerceAtMost(day.lengthOfMonth())
        RepeatType.YEARLY -> if (isLunar && lunarMonth > 0) {
            val lunar = LunarCalendar.fromSolar(day)
            lunar.month == lunarMonth && lunar.day == lunarDay
        } else {
            day.monthValue == start.monthValue &&
                day.dayOfMonth == start.dayOfMonth.coerceAtMost(day.lengthOfMonth())
        }
    }
}

private fun EventWithNodes.dayLabel(): EventOccurrence = event.occurrence()
