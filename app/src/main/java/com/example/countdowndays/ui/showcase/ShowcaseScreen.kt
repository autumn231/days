package com.example.countdowndays.ui.showcase

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.SaveAlt
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.rememberAsyncImagePainter
import com.example.countdowndays.R
import com.example.countdowndays.data.EventWithNodes
import com.example.countdowndays.data.occurrence
import com.example.countdowndays.ui.common.PillButton
import com.example.countdowndays.ui.theme.BackgroundPresets
import com.example.countdowndays.ui.theme.backgroundBrush
import com.example.countdowndays.ui.theme.eventColor
import com.example.countdowndays.util.DateUtils
import com.example.countdowndays.util.ImageStorage
import com.example.countdowndays.util.LunarCalendar
import kotlinx.coroutines.launch
import java.io.File
import kotlin.math.abs

/**
 * 全屏展示页：卡片流，按日期从早到晚排列（左过去、右将来）。
 * 左右滑动切换；分享/存图会把「背景 + 卡片」整体导出。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShowcaseScreen(
    eventId: Long,
    onBack: () -> Unit,
    onEdit: (Long) -> Unit,
    onAddNew: () -> Unit,
    vm: ShowcaseViewModel = com.example.countdowndays.ui.common.appViewModel {
        ShowcaseViewModel(it.repository)
    }
) {
    val events by vm.events.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val pagerState = rememberPagerState(initialPage = 0) { events.size }
    var initialApplied by rememberSaveable { mutableStateOf(false) }
    var showBackgroundSheet by remember { mutableStateOf(false) }

    // 列表加载完成后定位到被点开的那一条
    LaunchedEffect(events) {
        if (!initialApplied && events.isNotEmpty()) {
            val index = events.indexOfFirst { it.event.id == eventId }
            if (index >= 0) pagerState.scrollToPage(index)
            initialApplied = true
        }
    }

    val captureLayer = rememberGraphicsLayer()
    val current = events.getOrNull(pagerState.currentPage)

    val pickImage = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        val target = current
        if (uri != null && target != null) {
            ImageStorage.saveFromUri(context, uri, "event")?.let { path ->
                vm.setImage(target.event.id, path)
            }
        }
    }

    val accent by animateColorAsState(
        targetValue = current?.let { eventColor(it.event.colorIndex) } ?: Color(0xFFE0612E),
        animationSpec = tween(400),
        label = "accent"
    )

    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0xFF141210))
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
        ) {
            // 顶栏
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "返回",
                        tint = Color.White
                    )
                }
                Text(
                    text = "Days · 倒数日",
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.weight(1f))
                if (current != null) {
                    Text(
                        text = "${pagerState.currentPage + 1} / ${events.size}",
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White.copy(alpha = 0.75f),
                        modifier = Modifier.padding(end = 4.dp)
                    )
                    IconButton(onClick = { onEdit(current.event.id) }) {
                        Icon(Icons.Filled.Edit, contentDescription = "编辑", tint = Color.White)
                    }
                }
            }

            if (current == null) {
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Color.White)
                }
                return@Column
            }

            // 卡片流（背景包含在每一页里，分享时一起导出）
            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                pageSpacing = 0.dp
            ) { page ->
                ShowcasePage(
                    item = events[page],
                    captureLayer = if (page == pagerState.currentPage) captureLayer else null
                )
            }

            // 操作区：横向可滚动
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(vertical = 14.dp),
                contentPadding = PaddingValues(horizontal = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                item {
                    PillButton(
                        text = "分享",
                        icon = Icons.Filled.Share,
                        filled = true,
                        color = accent,
                        onClick = {
                            scope.launch {
                                runCatching { captureLayer.toImageBitmap() }
                                    .onSuccess { bitmap ->
                                        runCatching {
                                            ShareUtils.shareCard(context, bitmap, current.event.name)
                                        }.onFailure {
                                            Toast.makeText(context, "分享失败", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                            }
                        }
                    )
                }
                item {
                    PillButton(
                        text = "存为图片",
                        icon = Icons.Filled.SaveAlt,
                        color = accent,
                        onClick = {
                            scope.launch {
                                runCatching { captureLayer.toImageBitmap() }
                                    .onSuccess { bitmap ->
                                        val ok = ShareUtils.saveToGallery(
                                            context, bitmap, "days_${System.currentTimeMillis()}"
                                        )
                                        Toast.makeText(
                                            context,
                                            if (ok) "已保存到相册" else "保存失败",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                            }
                        }
                    )
                }
                item {
                    PillButton(
                        text = "背景",
                        icon = Icons.Outlined.Image,
                        color = accent,
                        onClick = { showBackgroundSheet = true }
                    )
                }
                item {
                    PillButton(
                        text = if (current.event.isPinned) "已置顶" else "置顶",
                        icon = Icons.Filled.Flag,
                        color = accent,
                        onClick = { vm.togglePin(current.event.id, current.event.isPinned) }
                    )
                }
                item {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(accent)
                            .clickable(onClick = onAddNew),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Filled.Add,
                            contentDescription = "新建事件",
                            tint = Color.White
                        )
                    }
                }
            }
        }
    }

    if (showBackgroundSheet && current != null) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { showBackgroundSheet = false },
            sheetState = sheetState
        ) {
            BackgroundSheet(
                current = current,
                onPickPreset = { index ->
                    vm.setPreset(current.event.id, index)
                    showBackgroundSheet = false
                },
                onPickImage = {
                    showBackgroundSheet = false
                    pickImage.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                },
                onRemoveImage = {
                    vm.setImage(current.event.id, null)
                    showBackgroundSheet = false
                }
            )
        }
    }
}

@Composable
private fun ShowcasePage(item: EventWithNodes, captureLayer: GraphicsLayer?) {
    val event = item.event
    val occ = event.occurrence()
    val accent = eventColor(event.colorIndex)
    var periodMode by remember(event.id) { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .then(
                if (captureLayer != null) {
                    Modifier.drawWithContent {
                        captureLayer.record { this@drawWithContent.drawContent() }
                        drawLayer(captureLayer)
                    }
                } else {
                    Modifier
                }
            )
    ) {
        ShowcaseBackground(item)

        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth()
                .padding(horizontal = 26.dp)
        ) {
            ShowcaseCard(
                name = event.name,
                accent = accent,
                numberText = if (periodMode) {
                    periodText(occ.isToday, occ.daysToNext, occ.start)
                } else {
                    if (occ.isToday) "今天" else abs(occ.daysToNext ?: 0L).toString()
                },
                captionText = when {
                    periodMode -> "累计"
                    occ.isToday -> "就是今天"
                    (occ.daysToNext ?: 0) < 0 -> "天前"
                    else -> "天后"
                },
                startLine = buildString {
                    append("起始日：")
                    append(DateUtils.formatIso(occ.start))
                    append("  ")
                    append(DateUtils.weekdayCn(occ.start))
                    if (event.isLunar && event.lunarMonth > 0) {
                        append("\n")
                        append(LunarCalendar.format(event.lunarMonth, event.lunarDay))
                    }
                },
                onClickNumber = { periodMode = !periodMode }
            )
        }
    }
}

@Composable
private fun ShowcaseBackground(item: EventWithNodes) {
    val event = item.event
    val imagePath = event.imagePath
    val accent = eventColor(event.colorIndex)

    if (!imagePath.isNullOrEmpty()) {
        Image(
            painter = rememberAsyncImagePainter(remember(imagePath) { File(imagePath) }),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color.Black.copy(alpha = 0.42f),
                            Color.Black.copy(alpha = 0.62f)
                        )
                    )
                )
        )
    } else {
        Box(
            Modifier
                .fillMaxSize()
                .background(backgroundBrush(event.backgroundPreset, accent))
        )
        // 轻微暗角，避免浅色背景上白色文字看不清
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color.Black.copy(alpha = 0.16f),
                            Color.Transparent,
                            Color.Black.copy(alpha = 0.22f)
                        )
                    )
                )
        )
    }
}

@Composable
private fun BackgroundSheet(
    current: EventWithNodes,
    onPickPreset: (Int) -> Unit,
    onPickImage: () -> Unit,
    onRemoveImage: () -> Unit
) {
    val accent = eventColor(current.event.colorIndex)
    val usingImage = !current.event.imagePath.isNullOrEmpty()

    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(bottom = 28.dp)
    ) {
        Text(
            text = "背景",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.height(14.dp))

        LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            items(BackgroundPresets.size) { index ->
                val preset = BackgroundPresets[index]
                val selected = !usingImage && current.event.backgroundPreset == index
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        modifier = Modifier
                            .size(60.dp)
                            .clip(CircleShape)
                            .background(
                                if (preset.colors.isEmpty()) {
                                    Brush.linearGradient(listOf(accent, accent.copy(alpha = 0.55f)))
                                } else {
                                    Brush.linearGradient(preset.colors)
                                }
                            )
                            .then(
                                if (selected) {
                                    Modifier.border(2.dp, MaterialTheme.colorScheme.primary, CircleShape)
                                } else {
                                    Modifier
                                }
                            )
                            .clickable { onPickPreset(index) },
                        contentAlignment = Alignment.Center
                    ) {
                        if (selected) {
                            Icon(
                                painter = painterResource(R.drawable.ic_notification),
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = preset.name,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Spacer(Modifier.height(20.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            PillButton(
                text = "从相册选择",
                icon = Icons.Outlined.Image,
                filled = true,
                onClick = onPickImage
            )
            if (usingImage) {
                PillButton(text = "移除配图", onClick = onRemoveImage)
            }
        }
        Text(
            text = "背景会一起进入分享图；选预设会清除已选的相片。",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.outline,
            modifier = Modifier.padding(top = 12.dp)
        )
    }
}

private fun periodText(isToday: Boolean, daysToNext: Long?, start: java.time.LocalDate): String {
    val today = java.time.LocalDate.now()
    return when {
        isToday -> DateUtils.periodText(start, today)
        (daysToNext ?: 0) > 0 -> "还有 ${daysToNext} 天"
        else -> DateUtils.periodText(start, today)
    }
}

@Composable
private fun ShowcaseCard(
    name: String,
    accent: Color,
    numberText: String,
    captionText: String,
    startLine: String,
    onClickNumber: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(Color(0xFFFDFBF7))
    ) {
        // 顶部色带 + 事件名
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(accent)
                .padding(vertical = 16.dp, horizontal = 20.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = name,
                style = MaterialTheme.typography.titleLarge.copy(letterSpacing = 3.sp),
                color = Color.White,
                textAlign = TextAlign.Center
            )
        }

        // 数字区
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(230.dp)
                .clickable(onClick = onClickNumber),
            contentAlignment = Alignment.Center
        ) {
            AnimatedContent(
                targetState = numberText,
                transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(160)) },
                label = "number"
            ) { text ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = text,
                        fontFamily = FontFamily.Serif,
                        fontSize = if (text.length > 6) 42.sp else 100.sp,
                        color = Color(0xFF3A3530),
                        lineHeight = if (text.length > 6) 56.sp else 112.sp,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = captionText,
                        style = MaterialTheme.typography.labelMedium,
                        color = Color(0xFF9C9284),
                        letterSpacing = 4.sp
                    )
                }
            }
        }

        CardDivider()

        // 起始日
        Text(
            text = startLine,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp, horizontal = 20.dp),
            style = MaterialTheme.typography.bodyMedium.copy(letterSpacing = 1.2.sp),
            color = Color(0xFF8C8175),
            textAlign = TextAlign.Center
        )

        CardDivider()

        // 宣传栏：图标 + 应用名（会一起进入分享图）
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFFF6F1E8))
                .padding(horizontal = 18.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(accent),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_notification),
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(14.dp)
                )
            }
            Spacer(Modifier.width(8.dp))
            Text(
                text = "倒数日",
                style = MaterialTheme.typography.labelLarge,
                color = Color(0xFF5E564C),
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = "记录每一个重要的日子",
                style = MaterialTheme.typography.labelSmall,
                color = Color(0xFFA2988A)
            )
        }
    }
}

@Composable
private fun CardDivider() {
    Box(
        Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(Color(0xFFEDE6DA))
    )
}
