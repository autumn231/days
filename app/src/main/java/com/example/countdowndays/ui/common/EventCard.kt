package com.example.countdowndays.ui.common

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.rememberAsyncImagePainter
import com.example.countdowndays.data.repeatLabel
import com.example.countdowndays.ui.main.EventRow
import com.example.countdowndays.ui.theme.eventColor
import com.example.countdowndays.util.DateUtils
import com.example.countdowndays.util.LunarCalendar
import java.io.File

/**
 * 首页列表卡片：左侧色条 + 名称/日期 + 右侧天数。
 * 天数用衬线体大字，切换排序时不会左右跳动。
 */
@Composable
fun EventCard(
    row: EventRow,
    onClick: () -> Unit,
    onTogglePinned: () -> Unit,
    onArchive: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val event = row.item.event
    val occ = row.occ
    val accent = eventColor(event.colorIndex)
    var menuOpen by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 0.dp,
        tonalElevation = 0.dp,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f)
        )
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // 左侧强调色条
            Box(
                Modifier
                    .width(4.dp)
                    .height(72.dp)
                    .background(accent)
            )

            Row(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 12.dp, top = 12.dp, bottom = 12.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (!event.imagePath.isNullOrEmpty()) {
                    Image(
                        painter = rememberAsyncImagePainter(File(event.imagePath)),
                        contentDescription = null,
                        modifier = Modifier
                            .size(52.dp)
                            .clip(RoundedCornerShape(14.dp)),
                        contentScale = ContentScale.Crop
                    )
                    Spacer(Modifier.width(12.dp))
                }

                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (event.isPinned) {
                            Icon(
                                Icons.Filled.PushPin,
                                contentDescription = "已置顶",
                                tint = accent,
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                        }
                        Text(
                            text = event.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Spacer(Modifier.height(3.dp))
                    Text(
                        text = buildString {
                            append(DateUtils.formatDate(event.date))
                            if (event.isLunar && event.lunarMonth > 0) {
                                append(" · ")
                                append(LunarCalendar.format(event.lunarMonth, event.lunarDay))
                            }
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    val repeat = event.repeatLabel()
                    if (repeat.isNotEmpty() || event.reminderEnabled) {
                        Spacer(Modifier.height(6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            if (repeat.isNotEmpty()) MiniTag(repeat, accent)
                            if (event.reminderEnabled) MiniTag("提醒", MaterialTheme.colorScheme.tertiary)
                        }
                    }
                }

                Spacer(Modifier.width(8.dp))

                DayCount(
                    days = occ.daysToNext,
                    isToday = occ.isToday,
                    ended = occ.ended,
                    accent = if (occ.isToday) MaterialTheme.colorScheme.tertiary else accent
                )

                IconButton(onClick = { menuOpen = true }, modifier = Modifier.size(36.dp)) {
                    Icon(
                        Icons.Filled.MoreVert,
                        contentDescription = "更多",
                        tint = MaterialTheme.colorScheme.outline
                    )
                }
            }
        }
    }

    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
        DropdownMenuItem(
            text = { Text(if (event.isPinned) "取消置顶" else "置顶") },
            onClick = { menuOpen = false; onTogglePinned() }
        )
        DropdownMenuItem(
            text = { Text("归档") },
            onClick = { menuOpen = false; onArchive() }
        )
        DropdownMenuItem(
            text = { Text("删除", color = MaterialTheme.colorScheme.error) },
            onClick = { menuOpen = false; confirmDelete = true }
        )
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("删除事件") },
            text = { Text("确定删除「${event.name}」？时间线会一并删除。") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    onDelete()
                }) { Text("删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("取消") }
            }
        )
    }
}

/** 右侧天数区：大字 + 小标签 */
@Composable
fun DayCount(
    days: Long?,
    isToday: Boolean,
    ended: Boolean,
    accent: Color,
    numberSize: Int = 26
) {
    Column(
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.Center
    ) {
        when {
            ended -> Text(
                text = "结束",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.outline
            )
            isToday -> Text(
                text = "今天",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = accent
            )
            else -> Text(
                text = kotlin.math.abs(days ?: 0L).toString(),
                fontSize = numberSize.sp,
                fontFamily = FontFamily.Serif,
                color = accent,
                fontWeight = FontWeight.Normal
            )
        }
        if (!isToday && !ended) {
            Text(
                text = if ((days ?: 0) > 0) "天后" else "天前",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
