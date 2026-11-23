package com.example.countdowndays.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.countdowndays.util.ChinaHolidays
import com.example.countdowndays.util.DateUtils
import java.time.LocalDate
import java.time.YearMonth

/**
 * 带节假日标注的日期选择器（v3.23）：Material3 的 DatePicker 不支持逐日装饰，
 * 这里自研月历网格——放假日标红「休」并显示节日名，调休上班日标「班」。
 * 数据来自 ChinaHolidays（国务院办公厅公布的 2026/2027 年安排），表外年份不标注。
 * 全程用 LocalDate 进出，没有 UTC 换算的坑。
 */
@Composable
fun HolidayDatePickerDialog(
    initialDate: LocalDate,
    onDismiss: () -> Unit,
    onConfirm: (LocalDate) -> Unit
) {
    var selected by remember { mutableStateOf(initialDate) }
    var month by remember { mutableStateOf(YearMonth.from(initialDate)) }
    val today = remember { LocalDate.now() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text("选择日期")
                Text(
                    text = DateUtils.formatDateWithWeek(DateUtils.toMillis(selected)) +
                        selectedHolidaySuffix(selected),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        text = {
            Column {
                // 月份导航
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { month = month.minusMonths(1) }) {
                        Icon(Icons.Filled.ChevronLeft, contentDescription = "上个月")
                    }
                    Text(
                        text = "${month.year}年${month.monthValue}月",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                    IconButton(onClick = { month = month.plusMonths(1) }) {
                        Icon(Icons.Filled.ChevronRight, contentDescription = "下个月")
                    }
                }

                // 星期头（周一起）
                Row(Modifier.fillMaxWidth()) {
                    listOf("一", "二", "三", "四", "五", "六", "日").forEach { w ->
                        Text(
                            text = w,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                Spacer(Modifier.height(4.dp))

                // 日期网格
                val firstDay = month.atDay(1)
                val leadBlanks = firstDay.dayOfWeek.value - 1 // 周一=0
                val daysInMonth = month.lengthOfMonth()
                val cells = leadBlanks + daysInMonth
                val rows = (cells + 6) / 7
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    for (r in 0 until rows) {
                        Row(Modifier.fillMaxWidth()) {
                            for (c in 0 until 7) {
                                val idx = r * 7 + c
                                val day = idx - leadBlanks + 1
                                if (day !in 1..daysInMonth) {
                                    Spacer(Modifier.weight(1f).height(46.dp))
                                } else {
                                    val date = month.atDay(day)
                                    DayCell(
                                        date = date,
                                        isSelected = date == selected,
                                        isToday = date == today,
                                        onClick = { selected = date },
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(10.dp))
                // 图例
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    LegendDot(color = MaterialTheme.colorScheme.error, text = "休 · 放假")
                    LegendDot(color = MaterialTheme.colorScheme.tertiary, text = "班 · 调休上班")
                    Text(
                        text = "节假日数据：2026–2027 年",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(selected) }) { Text("确定") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

private fun selectedHolidaySuffix(date: LocalDate): String {
    val name = ChinaHolidays.holidayName(date)
    return when {
        name != null -> " · $name（放假）"
        ChinaHolidays.isAdjustedWorkday(date) -> " · 调休上班"
        else -> ""
    }
}

@Composable
private fun LegendDot(color: Color, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(7.dp)
                .clip(CircleShape)
                .background(color)
        )
        Spacer(Modifier.size(4.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun DayCell(
    date: LocalDate,
    isSelected: Boolean,
    isToday: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val holiday = ChinaHolidays.holidayName(date) != null
    val workday = ChinaHolidays.isAdjustedWorkday(date)
    val isWeekend = date.dayOfWeek.value >= 6

    val numberColor = when {
        isSelected -> MaterialTheme.colorScheme.onPrimary
        holiday -> MaterialTheme.colorScheme.error
        isWeekend && !workday -> MaterialTheme.colorScheme.error.copy(alpha = 0.75f)
        else -> MaterialTheme.colorScheme.onSurface
    }

    Box(
        modifier = modifier
            .height(46.dp)
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (isSelected) {
            Box(
                Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary)
            )
        } else if (isToday) {
            Box(
                Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .border(1.dp, MaterialTheme.colorScheme.primary, CircleShape)
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = date.dayOfMonth.toString(),
                fontSize = 15.sp,
                fontWeight = if (isSelected || isToday) FontWeight.Bold else FontWeight.Normal,
                color = numberColor
            )
            val badge = when {
                holiday -> "休" to MaterialTheme.colorScheme.error
                workday -> "班" to MaterialTheme.colorScheme.tertiary
                else -> null
            }
            if (badge != null) {
                Text(
                    text = badge.first,
                    fontSize = 9.sp,
                    lineHeight = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isSelected) MaterialTheme.colorScheme.onPrimary else badge.second
                )
            } else {
                Spacer(Modifier.height(10.dp))
            }
        }
    }
}
