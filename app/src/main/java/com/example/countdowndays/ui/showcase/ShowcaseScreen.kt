package com.example.countdowndays.ui.showcase

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.SaveAlt
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.rememberAsyncImagePainter
import com.example.countdowndays.data.occurrence
import com.example.countdowndays.ui.common.PillButton
import com.example.countdowndays.ui.theme.eventColor
import com.example.countdowndays.util.DateUtils
import com.example.countdowndays.util.ImageStorage
import com.example.countdowndays.util.LunarCalendar
import kotlinx.coroutines.launch
import java.io.File
import kotlin.math.abs

/**
 * 全屏展示页：大卡片 + 超大天数 + 分享/存图/换背景/置顶。
 * 点数字可在「天数」与「X年X个月X天」之间切换。
 */
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
    LaunchedEffect(eventId) { vm.load(eventId) }
    val ew by vm.event.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var periodMode by rememberSaveable { mutableStateOf(false) }

    val graphicsLayer = rememberGraphicsLayer()

    val pickImage = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            ImageStorage.saveFromUri(context, uri, "event")?.let { vm.setImage(it) }
        }
    }

    val event = ew?.event
    if (event == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    val occ = event.occurrence()
    val accent = eventColor(event.colorIndex)
    val hasImage = !event.imagePath.isNullOrEmpty()

    Box(Modifier.fillMaxSize()) {
        // 背景：配图 / 渐变
        if (hasImage) {
            Image(
                painter = rememberAsyncImagePainter(File(event.imagePath!!)),
                contentDescription = null,
                modifier = Modifier.matchParentSize(),
                contentScale = ContentScale.Crop
            )
            Box(
                Modifier
                    .matchParentSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                Color.Black.copy(alpha = 0.45f),
                                Color.Black.copy(alpha = 0.65f)
                            )
                        )
                    )
            )
        } else {
            Box(
                Modifier
                    .matchParentSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                accent.copy(alpha = 0.90f),
                                accent.copy(alpha = 0.62f),
                                Color(0xFF1F1B16)
                            )
                        )
                    )
            )
        }

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
                IconButton(onClick = { onEdit(event.id) }) {
                    Icon(Icons.Filled.Edit, contentDescription = "编辑", tint = Color.White)
                }
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(Modifier.height(24.dp))

                // 需要被截图保存的卡片
                Box(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                        .drawWithContent {
                            graphicsLayer.record { this@drawWithContent.drawContent() }
                            drawLayer(graphicsLayer)
                        }
                ) {
                    ShowcaseCard(
                        name = event.name,
                        accent = accent,
                        numberText = if (periodMode) {
                            periodText(occ.isToday, occ.daysToNext, occ.start)
                        } else {
                            if (occ.isToday) "今天" else abs(occ.daysToNext ?: 0L).toString()
                        },
                        captionText = if (periodMode) "累计" else if ((occ.daysToNext ?: 0) < 0) "天前" else "天后",
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

                Spacer(Modifier.height(28.dp))

                // 操作区
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally)
                ) {
                    PillButton(
                        text = "分享",
                        icon = Icons.Filled.Share,
                        filled = true,
                        color = accent,
                        onClick = {
                            scope.launch {
                                runCatching { graphicsLayer.toImageBitmap() }
                                    .onSuccess { bitmap ->
                                        runCatching {
                                            ShareUtils.shareCard(context, bitmap, event.name)
                                        }.onFailure { Toast.makeText(context, "分享失败", Toast.LENGTH_SHORT).show() }
                                    }
                            }
                        }
                    )
                    PillButton(
                        text = "存为图片",
                        icon = Icons.Filled.SaveAlt,
                        color = accent,
                        onClick = {
                            scope.launch {
                                runCatching { graphicsLayer.toImageBitmap() }
                                    .onSuccess { bitmap ->
                                        val ok = ShareUtils.saveToGallery(
                                            context,
                                            bitmap,
                                            "days_${System.currentTimeMillis()}"
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
                    PillButton(
                        text = "背景",
                        icon = Icons.Outlined.Image,
                        color = accent,
                        onClick = {
                            pickImage.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        }
                    )
                    PillButton(
                        text = if (event.isPinned) "已置顶" else "置顶",
                        icon = Icons.Filled.Flag,
                        color = accent,
                        onClick = { vm.togglePin() }
                    )
                    PillButton(
                        text = "",
                        icon = Icons.Filled.Add,
                        color = accent,
                        onClick = onAddNew
                    )
                }

                Spacer(Modifier.height(32.dp))
            }
        }
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
                        fontSize = if (text.length > 6) 44.sp else 104.sp,
                        color = Color(0xFF3A3530),
                        lineHeight = 112.sp,
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

        Box(
            Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(Color(0xFFEDE6DA))
        )

        // 起始日
        Text(
            text = startLine,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 18.dp, horizontal = 20.dp),
            style = MaterialTheme.typography.bodyMedium.copy(letterSpacing = 1.5.sp),
            color = Color(0xFF8C8175),
            textAlign = TextAlign.Center
        )
    }
}
