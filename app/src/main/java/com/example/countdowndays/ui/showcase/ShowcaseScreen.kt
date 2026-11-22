package com.example.countdowndays.ui.showcase

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.rememberAsyncImagePainter
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.haze
import dev.chrisbanes.haze.hazeChild
import com.example.countdowndays.R
import com.example.countdowndays.data.EventWithNodes
import com.example.countdowndays.data.indexNearestToday
import com.example.countdowndays.data.occurrence
import com.example.countdowndays.ui.common.PillButton
import com.example.countdowndays.ui.theme.BackgroundPresets
import com.example.countdowndays.ui.theme.backgroundBrush
import com.example.countdowndays.ui.theme.eventColor
import com.example.countdowndays.util.CardStyle
import com.example.countdowndays.util.DateUtils
import com.example.countdowndays.util.ImageStorage
import com.example.countdowndays.util.LunarCalendar
import com.example.countdowndays.util.SmartCrop
import kotlinx.coroutines.launch
import java.io.File
import java.time.LocalDate
import kotlin.math.abs
import kotlin.math.absoluteValue

/** 分享图高度占屏幕的比例：上下各裁掉 20%，留出更修长的构图 */
private const val SHARE_HEIGHT_RATIO = 0.6f

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
    /** 关闭后不录制分享图层（JVM 测试的软件画布不支持 RenderNode） */
    captureEnabled: Boolean = true,
    vm: ShowcaseViewModel = com.example.countdowndays.ui.common.appViewModel {
        ShowcaseViewModel(it.repository, it.prefs)
    }
) {
    val events by vm.events.collectAsStateWithLifecycle()
    val cardStyle by (vm.cardStyle?.collectAsStateWithLifecycle()
        ?: remember { mutableStateOf(CardStyle.PAPER) })
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current

    val pagerState = rememberPagerState(initialPage = 0) { events.size }
    var initialApplied by rememberSaveable { mutableStateOf(false) }
    var showBackgroundSheet by remember { mutableStateOf(false) }
    // 当前卡片实际高度（px），用于保证裁切分享图时不切到卡片
    var cardHeightPx by remember { mutableIntStateOf(0) }
    // 底部操作栏入场动画
    var chromeIn by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { chromeIn = true }

    // 列表加载完成后定位到被点开的那一条；
    // 找不到（已删除等）就停在今天或离今天最近的一张
    LaunchedEffect(events) {
        if (!initialApplied && events.isNotEmpty()) {
            initialApplied = true
            val index = events.indexOfFirst { it.event.id == eventId }
                .takeIf { it >= 0 } ?: events.indexNearestToday()
            pagerState.scrollToPage(index)
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

    // 分享图：上下对称裁到屏幕高度的 3/5（不再铺满整屏）；
    // 卡片本身接近这个高度时，以「卡片 + 留白」为准，避免把内容切掉
    suspend fun captureForShare() = run {
        val raw = captureLayer.toImageBitmap()
        val padding = with(density) { 28.dp.roundToPx() }
        val target = maxOf(
            (raw.height * SHARE_HEIGHT_RATIO).toInt(),
            cardHeightPx + padding * 2
        )
        raw.cropCenterHeight(target)
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0xFF141210))
    ) {
        // 背景铺满整屏（含状态栏与底部操作区），分享时一起导出
        if (current != null) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
                pageSpacing = 0.dp
            ) { page ->
                // 翻页时相邻页轻微缩小淡出，滑动过程有层次
                val offset = ((pagerState.currentPage - page) +
                    pagerState.currentPageOffsetFraction).absoluteValue.coerceIn(0f, 1f)
                Box(
                    Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            val scale = 1f - 0.06f * offset
                            scaleX = scale
                            scaleY = scale
                            alpha = 1f - 0.40f * offset
                        }
                ) {
                    ShowcasePage(
                        item = events[page],
                        captureLayer = if (captureEnabled && page == pagerState.currentPage) captureLayer else null,
                        glassEnabled = captureEnabled,
                        cardStyle = cardStyle,
                        onCardHeight = { if (page == pagerState.currentPage) cardHeightPx = it }
                    )
                }
            }
        }
        // 顶栏：只承担顶部内容，不再参与底部布局
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
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
                AnimatedContent(
                    targetState = pagerState.currentPage,
                    transitionSpec = {
                        (slideInVertically(tween(200)) { it / 2 } + fadeIn(tween(200))) togetherWith
                            (slideOutVertically(tween(200)) { -it / 2 } + fadeOut(tween(140)))
                    },
                    label = "pageIndex",
                    modifier = Modifier.padding(end = 4.dp)
                ) { page ->
                    Text(
                        text = "${page + 1} / ${events.size}",
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White.copy(alpha = 0.75f)
                    )
                }
                IconButton(onClick = { onEdit(current.event.id) }) {
                    Icon(Icons.Filled.Edit, contentDescription = "编辑", tint = Color.White)
                }
            }
        }

        // 底部操作栏：锚在屏幕底部，切换卡片时位置不动
        if (current == null) {
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = 40.dp)
            ) {
                CircularProgressIndicator(color = Color.White)
            }
        } else {
            AnimatedVisibility(
                visible = chromeIn,
                modifier = Modifier.align(Alignment.BottomCenter),
                enter = fadeIn(tween(320, delayMillis = 100)) +
                    slideInVertically(tween(320, delayMillis = 100)) { it / 3 }
            ) {
                // 四个按钮固定均分一行：以前用 LazyRow 能左右滑动，用户要求钉死、放得下
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 16.dp)
                        .padding(bottom = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        PillButton(
                            text = "分享",
                            icon = Icons.Filled.Share,
                            filled = true,
                            color = accent,
                            compact = true,
                            onClick = {
                                scope.launch {
                                    runCatching { captureForShare() }
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
                    Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        PillButton(
                            text = "存为图片",
                            icon = Icons.Filled.SaveAlt,
                            color = accent,
                            compact = true,
                            onClick = {
                                scope.launch {
                                    runCatching { captureForShare() }
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
                    Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        PillButton(
                            text = "背景",
                            icon = Icons.Outlined.Image,
                            color = accent,
                            compact = true,
                            onClick = { showBackgroundSheet = true }
                        )
                    }
                    Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        PillButton(
                            text = if (current.event.isPinned) "已置顶" else "置顶",
                            icon = Icons.Filled.Flag,
                            color = accent,
                            compact = true,
                            onClick = { vm.togglePin(current.event.id, current.event.isPinned) }
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
                cardStyle = cardStyle,
                onSelectCardStyle = { vm.setCardStyle(it) },
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
private fun ShowcasePage(
    item: EventWithNodes,
    captureLayer: GraphicsLayer?,
    glassEnabled: Boolean = true,
    cardStyle: CardStyle = CardStyle.PAPER,
    onCardHeight: (Int) -> Unit = {}
) {
    val event = item.event
    val occ = event.occurrence()
    val accent = eventColor(event.colorIndex)
    var periodMode by remember(event.id) { mutableStateOf(false) }
    // 已经过去的日子：非重复且起始日在今天之前，或重复已到结束日
    val isPast = occ.ended || (!occ.isRepeating && occ.start.isBefore(LocalDate.now()))
    // 毛玻璃取样源：背景登记为 source，卡片用 hazeChild 采样背后的内容做模糊
    val hazeState = remember { HazeState() }
    // 液态玻璃取样源：背景另录一份进图层，卡片用 AGSL 折射着色器采样（API 33+）
    val liquidState = remember { LiquidGlassState() }
    val liquidBackdropLayer = rememberGraphicsLayer()
    liquidState.backdropLayer = liquidBackdropLayer
    // 智能裁切分析结果（裁切对齐 + 分区亮度）：背景分析时产出，卡片字色据此逐区自适应
    var cropResult by remember { mutableStateOf<SmartCrop.CropResult?>(null) }

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
        ShowcaseBackground(
            item,
            onCropResult = { cropResult = it },
            sourceModifier = if (glassEnabled) {
                Modifier.haze(hazeState).then(
                    if (cardStyle == CardStyle.LIQUID) Modifier.liquidSource(liquidState) else Modifier
                )
            } else {
                Modifier
            }
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 64.dp, bottom = 96.dp)
                .padding(horizontal = 26.dp),
            contentAlignment = Alignment.Center
        ) {
            ShowcaseCard(
                cardStyle = cardStyle,
                hazeState = if (glassEnabled) hazeState else null,
                liquidState = if (glassEnabled) liquidState else null,
                cropResult = cropResult,
                modifier = Modifier.onGloballyPositioned { onCardHeight(it.size.height) },
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
                // 「已经过了」放在累计时长上方，而不是日期行
                eyebrow = if (periodMode && isPast) "已经过了" else null,
                startLine = buildString {
                    append(if (occ.ended) "已经结束：" else "起始日：")
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
private fun ShowcaseBackground(
    item: EventWithNodes,
    sourceModifier: Modifier = Modifier,
    onCropResult: (SmartCrop.CropResult?) -> Unit = {}
) {
    val event = item.event
    val imagePath = event.imagePath
    val accent = eventColor(event.colorIndex)
    val context = LocalContext.current

    // 智能裁切焦点：背景仍铺满全屏（Crop），但裁切窗按 U2-NetP 主体掩膜
    // 挪到主体一侧，而不是永远居中裁（分析异步进行，未完成前先居中）；
    // 同一趟分析顺带给出卡片四个文字区的合成亮度，供卡片逐区自适应字色
    var containerSize by remember { mutableStateOf(IntSize.Zero) }
    var cropAlignment by remember(imagePath) { mutableStateOf<Alignment>(Alignment.Center) }
    // 裁切分析完成前不露图（先显示底色），完成后整张淡入——
    // 掩盖「先进来是居中裁切、模型算完后背景跳一下」的过程
    var cropReady by remember(imagePath) { mutableStateOf(false) }
    val photoAlpha by animateFloatAsState(
        targetValue = if (cropReady) 1f else 0f,
        animationSpec = tween(400),
        label = "bgFade"
    )
    LaunchedEffect(imagePath, containerSize) {
        if (!imagePath.isNullOrEmpty() && containerSize.width > 0 && containerSize.height > 0) {
            val result = SmartCrop.focalPoint(
                context,
                File(imagePath),
                containerSize.width.toFloat() / containerSize.height.toFloat()
            )
            if (result != null) {
                cropAlignment = result.alignment
                onCropResult(result)
            }
            cropReady = true
        } else if (imagePath.isNullOrEmpty()) {
            onCropResult(null)
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .then(sourceModifier)
            .onSizeChanged { containerSize = it }
    ) {
        // 底色永远先铺一层：即使配图丢失/解码失败，也不会出现整块黑
        Box(
            Modifier
                .fillMaxSize()
                .background(backgroundBrush(event.backgroundPreset, accent))
        )

        if (!imagePath.isNullOrEmpty()) {
            Image(
                painter = rememberAsyncImagePainter(remember(imagePath) { File(imagePath) }),
                contentDescription = null,
                modifier = Modifier
                    .fillMaxSize()
                    .alpha(photoAlpha),
                contentScale = ContentScale.Crop,
                alignment = cropAlignment
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
}

@Composable
private fun BackgroundSheet(
    current: EventWithNodes,
    cardStyle: CardStyle,
    onSelectCardStyle: (CardStyle) -> Unit,
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
        if (usingImage) {
            // 智能裁切诊断显形：跑的是模型还是回落算法、回落时失败原因是什么，一眼可见
            val report = remember(current.event.imagePath) {
                current.event.imagePath?.let { SmartCrop.reportFor(File(it)) }
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = when {
                    report == null -> "智能裁切：尚未分析"
                    report.engine == "ncnn" -> "智能裁切：U2-NetP 模型"
                    else -> "智能裁切：回落算法（模型未生效）"
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (report?.engine == "fallback" && !report.error.isNullOrEmpty()) {
                Text(
                    text = report.error,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
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

        Spacer(Modifier.height(22.dp))
        Text(
            text = "卡片效果",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            PillButton(
                text = "素纸",
                selected = cardStyle == CardStyle.PAPER,
                onClick = { onSelectCardStyle(CardStyle.PAPER) }
            )
            PillButton(
                text = "毛玻璃",
                selected = cardStyle == CardStyle.FROSTED,
                onClick = { onSelectCardStyle(CardStyle.FROSTED) }
            )
            PillButton(
                text = "液态玻璃",
                selected = cardStyle == CardStyle.LIQUID,
                onClick = { onSelectCardStyle(CardStyle.LIQUID) }
            )
        }
        Text(
            text = "素纸是原来的实色卡；毛玻璃是浅色模糊；液态玻璃是深色折射、白字。卡片效果对所有事件生效。",
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
    modifier: Modifier = Modifier,
    eyebrow: String? = null,
    cardStyle: CardStyle = CardStyle.PAPER,
    hazeState: HazeState? = null,
    liquidState: LiquidGlassState? = null,
    cropResult: SmartCrop.CropResult? = null,
    onClickNumber: () -> Unit
) {
    val cardShape = RoundedCornerShape(18.dp)
    // 字色逐区自适应（v3.16）：分析端已按合成链算出每个文字区背后的实际背景亮度，
    // 每区在「近黑 / 近白」两档极端色里按 WCAG 对比度择优——两档的交叉点在 L≈0.19，
    // 交叉点上两档都约有 4.5:1，所以任何背景下小字都达标；这也是 AndroidX Palette
    // 给图片配字色的同款算法。v3.15 的教训：①整卡一个平均值管不住分区明暗差；
    // ②「深灰」#5F5546 是中间调（实测对比度仅 1.8–2.9:1），深档必须用近黑。
    // 无照片（预置渐变背景）时：毛玻璃用深字、液态用白字，与之前一致。
    // 区序：0 数字（含小眉题）/ 1 小标题 / 2 起始日（含分隔线）/ 3 宣传栏
    val zoneLum = cropResult?.let {
        if (cardStyle == CardStyle.FROSTED) it.frostedZoneLum else it.liquidZoneLum
    }
    fun zoneLight(zone: Int): Boolean = when {
        cardStyle == CardStyle.PAPER -> false
        zoneLum != null -> zoneLum[zone] < 0.19f
        else -> cardStyle == CardStyle.LIQUID
    }
    val numberColor = when {
        cardStyle == CardStyle.PAPER -> Color(0xFF3A3530)
        zoneLight(0) -> Color.White
        else -> Color(0xFF14110D)
    }
    val captionColor = when {
        cardStyle == CardStyle.PAPER -> Color(0xFF9C9284)
        zoneLight(1) -> Color(0xFFF5F0E6)
        else -> Color(0xFF14110D)
    }
    val startColor = when {
        cardStyle == CardStyle.PAPER -> Color(0xFF8C8175)
        zoneLight(2) -> Color(0xFFEDE6D6)
        else -> Color(0xFF1A1611)
    }
    val promoNameColor = when {
        cardStyle == CardStyle.PAPER -> Color(0xFF5E564C)
        zoneLight(3) -> Color.White
        else -> Color(0xFF14110D)
    }
    val promoSloganColor = when {
        cardStyle == CardStyle.PAPER -> Color(0xFFA2988A)
        zoneLight(3) -> Color(0xFFDDD5C4)
        else -> Color(0xFF211C15)
    }
    val dividerColor = when {
        cardStyle == CardStyle.PAPER -> Color(0xFFEDE6DA)
        zoneLight(2) -> Color.White.copy(alpha = 0.16f)
        else -> Color(0xFF14110D).copy(alpha = 0.35f)
    }
    val stripBg = when {
        cardStyle == CardStyle.PAPER -> Color(0xFFF6F1E8)
        zoneLight(3) -> Color.Black.copy(alpha = 0.22f)
        else -> Color.White.copy(alpha = 0.30f)
    }
    val density = LocalDensity.current
    val liquidEffectLayer = rememberGraphicsLayer()
    val useLiquid = cardStyle == CardStyle.LIQUID && liquidGlassSupported && liquidState != null && hazeState != null
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(cardShape)
            .then(
                when {
                    // 毛玻璃：采样卡片背后的背景做模糊，叠奶白 tint（字色另行加深，不靠加厚 tint）
                    cardStyle == CardStyle.FROSTED && hazeState != null -> Modifier.hazeChild(
                        state = hazeState,
                        style = HazeStyle(
                            backgroundColor = Color(0xFFFDFBF7),
                            tint = HazeTint(Color(0xFFFDFBF7).copy(alpha = 0.32f)),
                            blurRadius = 24.dp,
                            noiseFactor = 0.03f
                        )
                    )
                    // 液态玻璃「深邃」（真折射，API 33+）：折射之上压一层深色 tint 配白字，
                    // 不再叠白色高光/描边（v3.13 的白边被用户否掉）
                    useLiquid -> Modifier
                        .background(Color(0xFF141416).copy(alpha = 0.72f))
                        .liquidGlass(
                            state = liquidState!!,
                            effectLayer = liquidEffectLayer,
                            shape = cardShape,
                            cornerRadiusPx = with(density) { 18.dp.toPx() },
                            tint = Color.Black.copy(alpha = 0.30f),
                            blurRadiusPx = with(density) { 8.dp.toPx() },
                            refractionHeightPx = with(density) { 30.dp.toPx() },
                            refractionAmountPx = with(density) { 22.dp.toPx() }
                        )
                    // 液态玻璃降级（API < 33）：haze 模糊 + 深色 tint 的近似
                    cardStyle == CardStyle.LIQUID && hazeState != null -> Modifier.hazeChild(
                        state = hazeState,
                        style = HazeStyle(
                            backgroundColor = Color(0xFF1B1B1F),
                            tint = HazeTint(Color.Black.copy(alpha = 0.28f)),
                            blurRadius = 18.dp,
                            noiseFactor = 0.02f
                        )
                    )
                    // 素纸：原来的实色卡
                    cardStyle == CardStyle.PAPER -> Modifier.background(Color(0xFFFDFBF7))
                    // 玻璃降级（单测等无 haze 场景）：半透奶白，不至于穿帮
                    else -> Modifier.background(Color(0xFFFDFBF7).copy(alpha = 0.85f))
                }
            )
    ) {
        // 顶部色带 + 事件名（玻璃下略透，让底色带一点背景色；液态更透）
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    when (cardStyle) {
                        CardStyle.PAPER -> accent
                        CardStyle.FROSTED -> accent.copy(alpha = 0.92f)
                        CardStyle.LIQUID -> accent.copy(alpha = 0.82f)
                    }
                )
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
                    if (eyebrow != null) {
                        Text(
                            text = eyebrow,
                            style = MaterialTheme.typography.labelMedium,
                            color = captionColor,
                            letterSpacing = 4.sp
                        )
                        Spacer(Modifier.height(8.dp))
                    }
                    BoxWithConstraints(
                        Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        // 按文本的估算 em 宽度反推字号：整行永远一行放下——
                        // 「还有 N 天」不再撑爆换行，三/四/五位数也自动缩小适配
                        val emWidth = text.sumOf { ch ->
                            when {
                                ch.isDigit() -> 0.56
                                ch == ' ' -> 0.30
                                ch.code > 0xFF -> 1.0
                                else -> 0.62
                            }
                        }.toFloat().coerceAtLeast(0.6f)
                        val fitSize = (maxWidth.value * 0.96f) / emWidth
                        val numberSize = fitSize.coerceIn(30f, 100f)
                        Text(
                            text = text,
                            fontFamily = FontFamily.Serif,
                            fontSize = numberSize.sp,
                            color = numberColor,
                            lineHeight = (numberSize * 1.15f).sp,
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                    Text(
                        text = captionText,
                        style = MaterialTheme.typography.labelMedium,
                        color = captionColor,
                        letterSpacing = 4.sp
                    )
                }
            }
        }

        CardDivider(color = dividerColor)

        // 起始日
        Text(
            text = startLine,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp, horizontal = 20.dp),
            style = MaterialTheme.typography.bodyMedium.copy(letterSpacing = 1.2.sp),
            color = startColor,
            textAlign = TextAlign.Center
        )

        CardDivider(color = dividerColor)

        // 宣传栏：图标 + 应用名（会一起进入分享图）；衬底色跟随字色深浅一起自适应
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(stripBg)
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
                color = promoNameColor,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = "记录每一个重要的日子",
                style = MaterialTheme.typography.labelSmall,
                color = promoSloganColor
            )
        }
    }
}

@Composable
private fun CardDivider(color: Color) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(color)
    )
}
