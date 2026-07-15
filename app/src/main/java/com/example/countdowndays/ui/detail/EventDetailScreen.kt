package com.example.countdowndays.ui.detail

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImagePainter
import coil.compose.rememberAsyncImagePainter
import com.example.countdowndays.data.TimelineNodeEntity
import com.example.countdowndays.util.DateUtils
import java.io.File
import java.util.Calendar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EventDetailScreen(
    eventId: Long,
    onBack: () -> Unit,
    onEdit: (Long) -> Unit,
    vm: EventDetailViewModel = com.example.countdowndays.ui.common.appViewModel {
        EventDetailViewModel(it.repository)
    }
) {
    LaunchedEffect(eventId) { vm.load(eventId) }

    val eventWithNodes by vm.event.collectAsStateWithLifecycle()
    val ew = eventWithNodes
    var showDelete by remember { mutableStateOf(false) }
    var fullImagePath by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(ew?.event?.name ?: "事件详情") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    if (ew != null) {
                        IconButton(onClick = { vm.togglePinned() }) {
                            Icon(
                                Icons.Filled.PushPin,
                                contentDescription = if (ew.event.isPinned) "取消置顶" else "置顶",
                                tint = if (ew.event.isPinned) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(onClick = { onEdit(ew.event.id) }) {
                            Icon(Icons.Filled.Edit, contentDescription = "编辑")
                        }
                        IconButton(onClick = { showDelete = true }) {
                            Icon(Icons.Filled.Delete, contentDescription = "删除")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        }
    ) { padding ->
        if (ew == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }
        val event = ew.event

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
        ) {
            HeaderCard(
                name = event.name,
                dateMillis = event.date,
                imagePath = event.imagePath,
                onImageClick = { fullImagePath = event.imagePath }
            )

            if (event.description.isNotBlank()) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                ) {
                    Text(
                        text = event.description,
                        modifier = Modifier.padding(16.dp),
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            }

            TimelineSection(
                nodes = ew.nodes.sortedBy { it.time },
                onAddNode = vm::addNode,
                onDelete = vm::deleteNode
            )
            Spacer(Modifier.height(24.dp))
        }
    }

    if (showDelete) {
        AlertDialog(
            onDismissRequest = { showDelete = false },
            title = { Text("删除事件") },
            text = { Text("确定删除该事件？此操作不可撤销。") },
            confirmButton = {
                TextButton(onClick = { showDelete = false; vm.deleteEvent(onBack) }) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDelete = false }) { Text("取消") }
            }
        )
    }

    // 全屏查看原图：点击空白处关闭，支持捏合/双击缩放
    fullImagePath?.let { path ->
        FullScreenImageViewer(
            imagePath = path,
            onDismiss = { fullImagePath = null }
        )
    }
}

/** 图片方向分类，用于自适应布局 */
private enum class ImageOrientation { NONE, LANDSCAPE, PORTRAIT, SQUARE }

private fun classifyOrientation(w: Int, h: Int): ImageOrientation {
    if (w <= 0 || h <= 0) return ImageOrientation.LANDSCAPE
    val ratio = w.toFloat() / h.toFloat()
    return when {
        ratio > 1.15f -> ImageOrientation.LANDSCAPE
        ratio < 0.87f -> ImageOrientation.PORTRAIT
        else -> ImageOrientation.SQUARE
    }
}

@Composable
private fun HeaderCard(
    name: String,
    dateMillis: Long,
    imagePath: String?,
    onImageClick: () -> Unit
) {
    val isToday = DateUtils.isToday(dateMillis)
    val isFuture = DateUtils.isFuture(dateMillis)
    val days = DateUtils.countdownDays(dateMillis)

    val hasImage = !imagePath.isNullOrEmpty()
    var orientation by remember(imagePath) {
        mutableStateOf(if (hasImage) ImageOrientation.LANDSCAPE else ImageOrientation.NONE)
    }

    val painter = rememberAsyncImagePainter(
        model = if (hasImage) File(imagePath) else null,
        onState = { state ->
            if (state is AsyncImagePainter.State.Success) {
                val d = state.result.drawable
                orientation = classifyOrientation(d.intrinsicWidth, d.intrinsicHeight)
            }
        }
    )

    val gradient = Brush.verticalGradient(
        0f to MaterialTheme.colorScheme.primary,
        1f to MaterialTheme.colorScheme.tertiary
    )
    val overlay = Brush.verticalGradient(
        0f to Color.Transparent,
        0.5f to Color.Black.copy(alpha = 0.15f),
        1f to Color.Black.copy(alpha = 0.65f)
    )

    when (orientation) {
        ImageOrientation.PORTRAIT -> {
            // 竖版：图片在左，信息在右
            Row(Modifier.fillMaxWidth().height(240.dp)) {
                Image(
                    painter = painter,
                    contentDescription = "查看原图",
                    modifier = Modifier
                        .width(150.dp)
                        .fillMaxHeight()
                        .clickable(onClick = onImageClick),
                    contentScale = ContentScale.Crop
                )
                Box(
                    Modifier.weight(1f).fillMaxHeight().background(gradient),
                    contentAlignment = Alignment.Center
                ) {
                    HeaderInfo(
                        name = name,
                        dateMillis = dateMillis,
                        isToday = isToday,
                        isFuture = isFuture,
                        days = days,
                        modifier = Modifier.padding(20.dp)
                    )
                }
            }
        }

        ImageOrientation.SQUARE -> {
            // 方框：顶部居中显示
            Column(Modifier.fillMaxWidth()) {
                Box(
                    modifier = Modifier.fillMaxWidth().height(200.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        painter = painter,
                        contentDescription = "查看原图",
                        modifier = Modifier
                            .size(200.dp)
                            .clickable(onClick = onImageClick),
                        contentScale = ContentScale.Crop
                    )
                }
                Box(Modifier.fillMaxWidth().background(gradient).padding(20.dp)) {
                    HeaderInfo(
                        name = name,
                        dateMillis = dateMillis,
                        isToday = isToday,
                        isFuture = isFuture,
                        days = days
                    )
                }
            }
        }

        else -> {
            // 横版或无图：图片铺满顶部
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(if (hasImage) 260.dp else 200.dp)
            ) {
                if (hasImage) {
                    Image(
                        painter = painter,
                        contentDescription = "查看原图",
                        modifier = Modifier
                            .fillMaxSize()
                            .clickable(onClick = onImageClick),
                        contentScale = ContentScale.Crop
                    )
                    Box(Modifier.fillMaxSize().background(overlay))
                } else {
                    Box(Modifier.fillMaxSize().background(gradient))
                }
                HeaderInfo(
                    name = name,
                    dateMillis = dateMillis,
                    isToday = isToday,
                    isFuture = isFuture,
                    days = days,
                    modifier = Modifier.align(Alignment.BottomStart).padding(24.dp)
                )
            }
        }
    }
}

@Composable
private fun HeaderInfo(
    name: String,
    dateMillis: Long,
    isToday: Boolean,
    isFuture: Boolean,
    days: Long,
    modifier: Modifier = Modifier
) {
    Column(modifier) {
        Text(
            text = name,
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = DateUtils.formatDateWithWeek(dateMillis),
            style = MaterialTheme.typography.bodyLarge,
            color = Color.White.copy(alpha = 0.92f)
        )
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = if (isToday) "今" else days.toString(),
                style = MaterialTheme.typography.displayLarge,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Spacer(Modifier.width(10.dp))
            Text(
                text = when {
                    isToday -> "就是今天"
                    isFuture -> "天后"
                    else -> "天前"
                },
                style = MaterialTheme.typography.titleMedium,
                color = Color.White.copy(alpha = 0.92f),
                modifier = Modifier.padding(bottom = 16.dp)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimelineSection(
    nodes: List<TimelineNodeEntity>,
    onAddNode: (String, Long) -> Unit,
    onDelete: (TimelineNodeEntity) -> Unit
) {
    var showAdd by remember { mutableStateOf(false) }

    Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "时间线",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.weight(1f)
            )
            FilledTonalButton(onClick = { showAdd = true }) {
                Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text("添加")
            }
        }
        Spacer(Modifier.height(16.dp))

        if (nodes.isEmpty()) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceContainer
            ) {
                Text(
                    "暂无时间线节点，添加一个记录重要时刻吧",
                    modifier = Modifier.padding(20.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        } else {
            // 用竖线把各节点串联起来
            Column(Modifier.fillMaxWidth()) {
                nodes.forEachIndexed { index, node ->
                    TimelineRow(
                        node = node,
                        isFirst = index == 0,
                        isLast = index == nodes.lastIndex,
                        onDelete = { onDelete(node) }
                    )
                }
            }
        }
    }

    if (showAdd) {
        AddNodeDialog(
            onDismiss = { showAdd = false },
            onConfirm = { name, time ->
                onAddNode(name, time)
                showAdd = false
            }
        )
    }
}

@Composable
private fun TimelineRow(
    node: TimelineNodeEntity,
    isFirst: Boolean,
    isLast: Boolean,
    onDelete: () -> Unit
) {
    val lineColor = MaterialTheme.colorScheme.outlineVariant
    val dotColor = MaterialTheme.colorScheme.primary

    Row(
        modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min)
    ) {
        // 左侧轨道：竖线 + 圆点
        Box(
            modifier = Modifier.width(32.dp).fillMaxHeight(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier.fillMaxHeight(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // 上半段竖线（首个节点不画）
                Box(
                    Modifier
                        .width(2.dp)
                        .weight(1f)
                        .background(if (isFirst) Color.Transparent else lineColor)
                )
                // 节点圆点（带外环，更精致）
                Box(
                    Modifier.size(14.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        Modifier
                            .size(14.dp)
                            .clip(CircleShape)
                            .background(dotColor.copy(alpha = 0.25f))
                    )
                    Box(
                        Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(dotColor)
                    )
                }
                // 下半段竖线（末个节点不画）
                Box(
                    Modifier
                        .width(2.dp)
                        .weight(1f)
                        .background(if (isLast) Color.Transparent else lineColor)
                )
            }
        }

        // 右侧内容卡片
        Surface(
            modifier = Modifier
                .weight(1f)
                .padding(start = 4.dp, bottom = 12.dp),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceContainer,
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Filled.Schedule,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        node.name,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        DateUtils.formatDateTime(node.time),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = onDelete) {
                    Icon(
                        Icons.Filled.Delete,
                        contentDescription = "删除节点",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddNodeDialog(
    onDismiss: () -> Unit,
    onConfirm: (String, Long) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var dateMillis by remember { mutableLongStateOf(DateUtils.todayMillis()) }
    var hour by remember { mutableStateOf(Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) }
    var minute by remember { mutableStateOf(Calendar.getInstance().get(Calendar.MINUTE)) }
    var showDate by remember { mutableStateOf(false) }
    var showTime by remember { mutableStateOf(false) }

    val datePickerState = rememberDatePickerState(initialSelectedDateMillis = dateMillis)
    val timePickerState = rememberTimePickerState(initialHour = hour, initialMinute = minute)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("添加时间线节点") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("名称") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp)
                )
                FilledTonalButton(
                    onClick = { showDate = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Filled.CalendarMonth, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(DateUtils.formatDate(dateMillis))
                }
                FilledTonalButton(
                    onClick = { showTime = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Filled.Schedule, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("%02d:%02d".format(hour, minute))
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val combined = DateUtils.startOfDay(dateMillis) +
                        (hour * 3600L + minute * 60L) * 1000L
                    onConfirm(name, combined)
                },
                enabled = name.isNotBlank()
            ) { Text("添加") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )

    if (showDate) {
        DatePickerDialog(
            onDismissRequest = { showDate = false },
            confirmButton = {
                TextButton(onClick = {
                    showDate = false
                    datePickerState.selectedDateMillis?.let { dateMillis = DateUtils.startOfDay(it) }
                }) { Text("确定") }
            },
            dismissButton = { TextButton(onClick = { showDate = false }) { Text("取消") } }
        ) { DatePicker(state = datePickerState) }
    }

    if (showTime) {
        AlertDialog(
            onDismissRequest = { showTime = false },
            confirmButton = {
                TextButton(onClick = {
                    showTime = false
                    hour = timePickerState.hour
                    minute = timePickerState.minute
                }) { Text("确定") }
            },
            dismissButton = { TextButton(onClick = { showTime = false }) { Text("取消") } },
            text = { TimePicker(state = timePickerState) }
        )
    }
}

/**
 * 全屏查看原图：黑色背景，支持捏合缩放和拖动，单击关闭。
 */
@Composable
private fun FullScreenImageViewer(
    imagePath: String,
    onDismiss: () -> Unit
) {
    var scale by remember { mutableStateOf(1f) }
    var offsetX by remember { mutableStateOf(0f) }
    var offsetY by remember { mutableStateOf(0f) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .clickable(
                    // 单击关闭（仅在未缩放或复位时直接关闭，避免误触）
                    onClick = {
                        if (scale > 1.05f) {
                            scale = 1f
                            offsetX = 0f
                            offsetY = 0f
                        } else {
                            onDismiss()
                        }
                    }
                )
                .pointerInput(Unit) {
                    // 双指捏合缩放 + 单指拖动
                    detectTransformGestures { _, pan, zoom, _ ->
                        scale = (scale * zoom).coerceIn(1f, 5f)
                        if (scale > 1f) {
                            offsetX += pan.x
                            offsetY += pan.y
                        } else {
                            offsetX = 0f
                            offsetY = 0f
                        }
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = rememberAsyncImagePainter(File(imagePath)),
                contentDescription = "原图",
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer(
                        scaleX = scale,
                        scaleY = scale,
                        translationX = offsetX,
                        translationY = offsetY
                    ),
                contentScale = ContentScale.Fit
            )
        }
    }
}
