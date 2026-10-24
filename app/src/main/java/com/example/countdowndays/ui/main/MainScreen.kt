package com.example.countdowndays.ui.main

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.countdowndays.ui.common.EventCard
import com.example.countdowndays.ui.common.PillButton
import com.example.countdowndays.ui.common.SectionHeader
import com.example.countdowndays.ui.main.EventBucket
import com.example.countdowndays.ui.main.EventRow
import com.example.countdowndays.ui.main.MainViewModel
import com.example.countdowndays.ui.main.groupEvents
import com.example.countdowndays.ui.main.pickHighlight
import com.example.countdowndays.ui.theme.eventColor
import com.example.countdowndays.util.DateUtils
import com.example.countdowndays.util.LunarCalendar
import com.example.countdowndays.util.SortMode
import java.io.File
import kotlin.math.abs

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun MainScreen(
    onAddEvent: () -> Unit,
    onOpenEvent: (Long) -> Unit,
    onOpenShowcase: (Long) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenCalendar: () -> Unit,
    vm: MainViewModel = com.example.countdowndays.ui.common.appViewModel {
        MainViewModel(it.repository, it.prefs)
    }
) {
    val query by vm.query.collectAsStateWithLifecycle()
    val events by vm.events.collectAsStateWithLifecycle()
    val categories by vm.categories.collectAsStateWithLifecycle()
    val categoryId by vm.categoryId.collectAsStateWithLifecycle()
    val sortMode by vm.sortMode.collectAsStateWithLifecycle()
    val lastDeleted by vm.lastDeleted.collectAsStateWithLifecycle()

    var searchOpen by remember { mutableStateOf(false) }
    var sortMenuOpen by remember { mutableStateOf(false) }

    val snackbar = remember { SnackbarHostState() }
    val listState = rememberLazyListState()

    val groups = remember(events, sortMode, categoryId) {
        groupEvents(events, sortMode, categoryId)
    }
    val allRows = remember(groups) { groups.flatMap { it.second } }
    val highlight = remember(allRows) { pickHighlight(allRows) }

    LaunchedEffect(lastDeleted) {
        val item = lastDeleted ?: return@LaunchedEffect
        val result = snackbar.showSnackbar(
            message = "已删除「${item.event.name}」",
            actionLabel = "撤销",
            duration = SnackbarDuration.Short
        )
        if (result == SnackbarResult.ActionPerformed) vm.undoDelete() else vm.clearUndo()
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            Column(
                Modifier
                    .statusBarsPadding()
                    .padding(horizontal = 12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "倒数日",
                        style = MaterialTheme.typography.headlineLarge,
                        modifier = Modifier.padding(start = 8.dp)
                    )
                    Spacer(Modifier.weight(1f))
                    IconButton(onClick = onOpenCalendar) {
                        Icon(Icons.Outlined.CalendarMonth, contentDescription = "日历")
                    }
                    Box {
                        IconButton(onClick = { sortMenuOpen = true }) {
                            Icon(Icons.Filled.Sort, contentDescription = "排序")
                        }
                        DropdownMenu(expanded = sortMenuOpen, onDismissRequest = { sortMenuOpen = false }) {
                            SortMode.entries.forEach { mode ->
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = when (mode) {
                                                SortMode.DATE -> "按日期"
                                                SortMode.CREATED -> "按创建时间"
                                                SortMode.NAME -> "按名称"
                                            },
                                            fontWeight = if (mode == sortMode) FontWeight.Bold else FontWeight.Normal
                                        )
                                    },
                                    onClick = {
                                        vm.setSortMode(mode)
                                        sortMenuOpen = false
                                    }
                                )
                            }
                        }
                    }
                    IconButton(onClick = { searchOpen = !searchOpen }) {
                        Icon(Icons.Filled.Search, contentDescription = "搜索")
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Filled.Settings, contentDescription = "设置")
                    }
                }
                AnimatedVisibility(
                    visible = searchOpen,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    TextField(
                        value = query,
                        onValueChange = vm::setQuery,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp),
                        placeholder = { Text("搜索事件…") },
                        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                        singleLine = true,
                        shape = CircleShape,
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent
                        )
                    )
                }
            }
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onAddEvent,
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text("新建") },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = CircleShape
            )
        }
    ) { padding ->
        if (events.isEmpty() && query.isBlank()) {
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(padding),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                com.example.countdowndays.ui.common.EmptyState(
                    icon = Icons.Outlined.Event,
                    title = "还没有记录任何日子",
                    subtitle = "生日、纪念日、考试、旅行……\n先记下第一个重要的日子吧",
                    actionLabel = "添加第一个事件",
                    onAction = onAddEvent
                )
            }
            return@Scaffold
        }

        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // 焦点卡
            highlight?.let { hl ->
                item(key = "hero") {
                    HeroCard(
                        row = hl,
                        onClick = { onOpenEvent(hl.item.event.id) },
                        onShowcase = { onOpenShowcase(hl.item.event.id) }
                    )
                }
            }

            // 分类筛选
            if (categories.isNotEmpty()) {
                item(key = "cats") {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        item {
                            PillButton(
                                text = "全部",
                                onClick = { vm.setCategory(null) },
                                selected = categoryId == null
                            )
                        }
                        items(categories, key = { it.id }) { cat ->
                            PillButton(
                                text = cat.name,
                                onClick = { vm.setCategory(if (categoryId == cat.id) null else cat.id) },
                                selected = categoryId == cat.id,
                                color = if (categoryId == cat.id) eventColor(cat.colorIndex) else null
                            )
                        }
                    }
                }
            }

            if (groups.isEmpty()) {
                item(key = "empty") {
                    com.example.countdowndays.ui.common.EmptyState(
                        icon = Icons.Filled.Search,
                        title = "没有匹配的事件",
                        subtitle = "换个关键词或取消分类筛选试试"
                    )
                }
            }

            groups.forEach { (bucket, rows) ->
                stickyHeader(key = "header_${bucket.name}") {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.background.copy(alpha = 0.96f))
                    ) {
                        SectionHeader(
                            title = bucket.title,
                            trailing = "${rows.size}"
                        )
                    }
                }
                items(rows, key = { it.item.event.id }) { row ->
                    SwipeableEventRow(
                        row = row,
                        modifier = Modifier
                            .animateItem()
                            .padding(horizontal = 16.dp),
                        onClick = { onOpenEvent(row.item.event.id) },
                        onTogglePinned = { vm.togglePinned(row.item.event) },
                        onArchive = { vm.setArchived(row.item.event, true) },
                        onDelete = { vm.delete(row.item) }
                    )
                }
            }
        }
    }
}

@Composable
private fun SwipeableEventRow(
    row: EventRow,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    onTogglePinned: () -> Unit,
    onArchive: () -> Unit,
    onDelete: () -> Unit
) {
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.EndToStart) {
                onDelete()
                true
            } else {
                false
            }
        }
    )
    SwipeToDismissBox(
        state = dismissState,
        modifier = modifier,
        enableDismissFromStartToEnd = false,
        backgroundContent = {
            Box(
                Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(20.dp))
                    .background(MaterialTheme.colorScheme.errorContainer),
                contentAlignment = Alignment.CenterEnd
            ) {
                Text(
                    text = "删除",
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(end = 24.dp)
                )
            }
        }
    ) {
        EventCard(
            row = row,
            onClick = onClick,
            onTogglePinned = onTogglePinned,
            onArchive = onArchive,
            onDelete = onDelete
        )
    }
}

/** 顶部焦点卡：最近的一个日子，大数字 + 渐变底 */
@Composable
private fun HeroCard(
    row: EventRow,
    onClick: () -> Unit,
    onShowcase: () -> Unit
) {
    val event = row.item.event
    val occ = row.occ
    val accent = eventColor(event.colorIndex)
    val dark = MaterialTheme.colorScheme.surface.luminanceIsDark()

    // 轻微呼吸感：数字所在区域缓慢缩放
    var breathing by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(300)
        breathing = true
    }
    val scale by animateFloatAsState(
        targetValue = if (breathing) 1.03f else 1f,
        animationSpec = tween(durationMillis = 2400),
        label = "heroBreath"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(
                Brush.linearGradient(
                    listOf(
                        accent.copy(alpha = if (dark) 0.55f else 0.92f),
                        accent.copy(alpha = if (dark) 0.30f else 0.62f)
                    )
                )
            )
            .clickable(onClick = onClick)
    ) {
        if (!event.imagePath.isNullOrEmpty()) {
            androidx.compose.foundation.Image(
                painter = coil.compose.rememberAsyncImagePainter(File(event.imagePath)),
                contentDescription = null,
                modifier = Modifier.matchParentSize(),
                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                alpha = 0.35f
            )
            Box(
                Modifier
                    .matchParentSize()
                    .background(Color.Black.copy(alpha = 0.18f))
            )
        }

        Column(Modifier.padding(22.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = event.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onShowcase, modifier = Modifier.size(32.dp)) {
                    Icon(
                        Icons.Filled.Fullscreen,
                        contentDescription = "全屏展示",
                        tint = Color.White.copy(alpha = 0.9f),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                if (occ.ended) {
                    Text(
                        text = "已结束",
                        style = MaterialTheme.typography.displaySmall,
                        color = Color.White
                    )
                } else if (occ.isToday) {
                    Text(
                        text = "今天",
                        style = MaterialTheme.typography.displayMedium,
                        color = Color.White
                    )
                } else {
                    Text(
                        text = abs(occ.daysToNext ?: 0L).toString(),
                        style = MaterialTheme.typography.displayLarge.copy(fontSize = 76.sp),
                        color = Color.White,
                        modifier = Modifier.graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                        }
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = if ((occ.daysToNext ?: 0) > 0) "天后" else "天前",
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White.copy(alpha = 0.9f),
                        modifier = Modifier.padding(bottom = 14.dp)
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = buildString {
                    append(DateUtils.formatDateWithWeek(event.date))
                    if (event.isLunar && event.lunarMonth > 0) {
                        append(" · ")
                        append(LunarCalendar.format(event.lunarMonth, event.lunarDay))
                    }
                },
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.85f)
            )
        }
    }
}

private fun Color.luminanceIsDark(): Boolean {
    val r = red * 0.299f
    val g = green * 0.587f
    val b = blue * 0.114f
    return (r + g + b) < 0.5f
}
