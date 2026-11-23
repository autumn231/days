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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Cake
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.outlined.AddPhotoAlternate
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImagePainter
import coil.compose.rememberAsyncImagePainter
import com.example.countdowndays.data.TimelineNodeEntity
import com.example.countdowndays.ui.common.HolidayDatePickerDialog
import com.example.countdowndays.ui.common.WheelTimePickerDialog
import com.example.countdowndays.util.Anniversary
import com.example.countdowndays.util.DateUtils
import com.example.countdowndays.util.ImageStorage
import java.io.File
import java.util.Calendar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EventDetailScreen(
    eventId: Long,
    onBack: () -> Unit,
    onEdit: (Long) -> Unit,
    onShowcase: (Long) -> Unit,
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
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    if (ew != null) {
                        IconButton(onClick = { vm.togglePinned() }) {
                            Icon(
                                // 已置顶用实心图钉、未置顶用空心图钉，一眼可辨状态
                                if (ew.event.isPinned) Icons.Filled.PushPin
                                else Icons.Outlined.PushPin,
                                contentDescription = if (ew.event.isPinned) "取消置顶" else "置顶",
                                tint = if (ew.event.isPinned) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(onClick = { onShowcase(ew.event.id) }) {
                            Icon(Icons.Filled.Fullscreen, contentDescription = "全屏展示")
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

            // 纪念日预告：已过去的一次性事件，推导下一个整百天/周年
            Anniversary.nextFor(event)?.let { info ->
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Filled.Cake,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "距离${info.label}还有 ${info.daysUntil} 天",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = "${DateUtils.formatDate(DateUtils.toMillis(info.date))} · 已经走过 ${info.elapsedDays} 天",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

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
                onUpdateNode = vm::updateNode,
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

    val gradient = Brush.verticalGradient(
        0f to MaterialTheme.colorScheme.primary,
        1f to MaterialTheme.colorScheme.tertiary
    )
    // 压暗蒙版：保证白色文字在任何照片上都读得清
    val scrim = Brush.verticalGradient(
        0f to Color.Black.copy(alpha = 0.10f),
        0.45f to Color.Black.copy(alpha = 0.28f),
        1f to Color.Black.copy(alpha = 0.72f)
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 230.dp)
    ) {
        if (hasImage) {
            Image(
                painter = rememberAsyncImagePainter(remember(imagePath) { File(imagePath!!) }),
                contentDescription = "查看原图",
                modifier = Modifier
                    .matchParentSize()
                    .clickable(onClick = onImageClick),
                contentScale = ContentScale.Crop
            )
            Box(Modifier.matchParentSize().background(scrim))
        } else {
            Box(Modifier.matchParentSize().background(gradient))
        }

        HeaderInfo(
            name = name,
            dateMillis = dateMillis,
            isToday = isToday,
            isFuture = isFuture,
            days = days,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(24.dp)
        )
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
                style = MaterialTheme.typography.displayLarge.copy(
                    fontSize = 72.sp,
                    lineHeight = 84.sp
                ),
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

@Composable
private fun TimelineSection(
    nodes: List<TimelineNodeEntity>,
    onAddNode: (String, Long, String, String?) -> Unit,
    onUpdateNode: (TimelineNodeEntity) -> Unit,
    onDelete: (TimelineNodeEntity) -> Unit
) {
    var showAdd by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<TimelineNodeEntity?>(null) }

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
                        onEdit = { editing = node },
                        onDelete = { onDelete(node) }
                    )
                }
            }
        }
    }

    if (showAdd) {
        NodeEditorDialog(
            initial = null,
            onDismiss = { showAdd = false },
            onConfirm = { name, time, note, imagePath ->
                onAddNode(name, time, note, imagePath)
                showAdd = false
            },
            onDelete = null
        )
    }

    editing?.let { node ->
        NodeEditorDialog(
            initial = node,
            onDismiss = { editing = null },
            onConfirm = { name, time, note, imagePath ->
                onUpdateNode(
                    node.copy(name = name, time = time, note = note, imagePath = imagePath)
                )
                editing = null
            },
            onDelete = {
                onDelete(node)
                editing = null
            }
        )
    }
}

@Composable
private fun TimelineRow(
    node: TimelineNodeEntity,
    isFirst: Boolean,
    isLast: Boolean,
    onEdit: () -> Unit,
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

        // 右侧内容卡片：点开可编辑（名称/文案/配图/时间）
        Surface(
            onClick = onEdit,
            modifier = Modifier
                .weight(1f)
                .padding(start = 4.dp, bottom = 12.dp),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceContainer,
        ) {
            Column(Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
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
                            // 只记日期的节点（时间为 0 点整）不显示时间
                            if (DateUtils.hasTimeOfDay(node.time)) {
                                DateUtils.formatDateTime(node.time)
                            } else {
                                DateUtils.formatDate(node.time)
                            },
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

                // 配图：整宽展示，圆角裁切
                if (!node.imagePath.isNullOrEmpty()) {
                    Spacer(Modifier.height(12.dp))
                    Image(
                        painter = rememberAsyncImagePainter(remember(node.imagePath) { File(node.imagePath!!) }),
                        contentDescription = "节点配图",
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(150.dp)
                            .clip(RoundedCornerShape(12.dp)),
                        contentScale = ContentScale.Crop
                    )
                }

                // 文案
                if (node.note.isNotBlank()) {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = node.note,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}

/**
 * 时间线节点编辑器（新增与编辑共用，v3.23）：
 * 名称 + 文案（多行）+ 配图（相册选图走 ImageStorage 既有管线）+ 日期/时间。
 * 时间默认只记日期，打开「具体时间」开关后用滚轮选择——
 * 不再用 Material3 的时间输入框（数字会被裁切，用户截图实证）。
 */
@Composable
private fun NodeEditorDialog(
    initial: TimelineNodeEntity?,
    onDismiss: () -> Unit,
    onConfirm: (name: String, time: Long, note: String, imagePath: String?) -> Unit,
    onDelete: (() -> Unit)?
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var note by remember { mutableStateOf(initial?.note ?: "") }
    var imagePath by remember { mutableStateOf(initial?.imagePath) }
    var dateMillis by remember {
        mutableLongStateOf(initial?.time?.let { DateUtils.startOfDay(it) } ?: DateUtils.todayMillis())
    }
    val initialHasTime = initial != null && DateUtils.hasTimeOfDay(initial.time)
    var hasTime by remember { mutableStateOf(initialHasTime) }
    var hour by remember {
        mutableStateOf(
            initial?.let { DateUtils.hourOf(it.time) }
                ?: Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        )
    }
    var minute by remember {
        mutableStateOf(
            initial?.let { DateUtils.minuteOf(it.time) }
                ?: Calendar.getInstance().get(Calendar.MINUTE)
        )
    }
    var showDate by remember { mutableStateOf(false) }
    var showTime by remember { mutableStateOf(false) }

    // 选图：复用事件配图同一条管线（拷入私有目录、长边 1920 压缩）
    val imagePicker = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            ImageStorage.saveFromUri(context, uri, "node")
                ?.let { imagePath = it }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "添加时间线节点" else "编辑时间线节点") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("名称") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp)
                )
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("文案（可选）") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    minLines = 2,
                    maxLines = 4
                )

                // 配图
                if (!imagePath.isNullOrEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(140.dp)
                            .clip(RoundedCornerShape(12.dp))
                    ) {
                        Image(
                            painter = rememberAsyncImagePainter(remember(imagePath) { File(imagePath!!) }),
                            contentDescription = "节点配图",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                        IconButton(
                            onClick = { imagePath = null },
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(6.dp)
                                .size(30.dp)
                                .clip(CircleShape)
                                .background(Color.Black.copy(alpha = 0.45f))
                        ) {
                            Icon(
                                Icons.Filled.Close,
                                contentDescription = "移除配图",
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                } else {
                    FilledTonalButton(
                        onClick = {
                            imagePicker.launch(
                                androidx.activity.result.PickVisualMediaRequest(
                                    androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia.ImageOnly
                                )
                            )
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            Icons.Outlined.AddPhotoAlternate,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("添加配图（可选）")
                    }
                }

                FilledTonalButton(
                    onClick = { showDate = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Filled.CalendarMonth, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(DateUtils.formatDate(dateMillis))
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "具体时间",
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.weight(1f)
                    )
                    androidx.compose.material3.Switch(
                        checked = hasTime,
                        onCheckedChange = { hasTime = it }
                    )
                }
                if (hasTime) {
                    FilledTonalButton(
                        onClick = { showTime = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Filled.Schedule, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("%02d:%02d".format(hour, minute))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val combined = DateUtils.startOfDay(dateMillis) +
                        if (hasTime) (hour * 3600L + minute * 60L) * 1000L else 0L
                    onConfirm(name.trim(), combined, note, imagePath)
                },
                enabled = name.isNotBlank()
            ) { Text(if (initial == null) "添加" else "保存") }
        },
        dismissButton = {
            Row {
                if (onDelete != null) {
                    TextButton(onClick = onDelete) {
                        Text("删除", color = MaterialTheme.colorScheme.error)
                    }
                }
                TextButton(onClick = onDismiss) { Text("取消") }
            }
        }
    )

    if (showDate) {
        HolidayDatePickerDialog(
            initialDate = DateUtils.toLocalDate(dateMillis),
            onDismiss = { showDate = false },
            onConfirm = {
                dateMillis = DateUtils.toMillis(it)
                showDate = false
            }
        )
    }

    if (showTime) {
        WheelTimePickerDialog(
            initialHour = hour,
            initialMinute = minute,
            onDismiss = { showTime = false },
            onConfirm = { h, m ->
                hour = h
                minute = m
                showTime = false
            }
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
    // File 实例复用，避免每次重组都重新加载图片
    val imageModel = remember(imagePath) { File(imagePath) }

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
                painter = rememberAsyncImagePainter(imageModel),
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
