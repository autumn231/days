package com.example.countdowndays.widget

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.action.clickable
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ContentScale
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontFamily
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.example.countdowndays.CountdownApp
import com.example.countdowndays.MainActivity
import com.example.countdowndays.R
import com.example.countdowndays.data.EventEntity
import com.example.countdowndays.data.EventOccurrence
import com.example.countdowndays.data.occurrence
import com.example.countdowndays.ui.theme.eventColor
import com.example.countdowndays.util.Anniversary
import com.example.countdowndays.util.DateUtils
import com.example.countdowndays.util.SmartCrop
import java.io.File
import kotlin.math.abs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * 桌面小组件（列表款）：显示最近的三个日子。
 * 不让用户配置，默认展示"最近"的三个，避免第一次添加时一片空白。
 * v3.23 视觉升级：圆角卡面、每行强调色条、已过去事件带纪念日预告行。
 */
class DaysWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val app = context.applicationContext as CountdownApp
        val rows = runCatching {
            app.container.repository.getActiveEventsLight()
                .map { it to it.occurrence() }
                .sortedWith(
                    compareBy(
                        { if (it.first.isPinned) 0 else 1 },
                        { it.second.daysToNext ?: Long.MAX_VALUE }
                    )
                )
                .take(3)
        }.getOrDefault(emptyList())

        provideContent {
                Column(
                    modifier = GlanceModifier
                        .fillMaxSize()
                        .background(ColorProvider(R.color.widget_bg))
                        .cornerRadius(24.dp)
                        .padding(14.dp)
                        .clickable(actionStartActivity(Intent(context, MainActivity::class.java))),
                    verticalAlignment = Alignment.Top
                ) {
                    Text(
                        text = "倒数日",
                        style = TextStyle(
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = ColorProvider(R.color.widget_accent)
                        )
                    )
                    Spacer(GlanceModifier.height(8.dp))

                    if (rows.isEmpty()) {
                        Text(
                            text = "还没有记录的日子\n打开应用添加一个吧",
                            style = TextStyle(
                                fontSize = 13.sp,
                                color = ColorProvider(R.color.widget_text_dim)
                            )
                        )
                    } else {
                        rows.forEach { (event, occ) ->
                            Row(
                                modifier = GlanceModifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = GlanceModifier
                                        .width(4.dp)
                                        .height(22.dp)
                                        .background(ColorProvider(eventColor(event.colorIndex)))
                                        .cornerRadius(2.dp)
                                ) {}
                                Spacer(GlanceModifier.width(10.dp))
                                Column(modifier = GlanceModifier.defaultWeight()) {
                                    Text(
                                        text = event.name,
                                        maxLines = 1,
                                        style = TextStyle(
                                            fontSize = 14.sp,
                                            color = ColorProvider(R.color.widget_text_main)
                                        )
                                    )
                                    // 已过去的一次性事件：纪念日预告
                                    val teaser = if (!occ.isRepeating && (occ.daysToNext ?: 0L) < 0) {
                                        Anniversary.nextFor(event)
                                    } else null
                                    if (teaser != null) {
                                        Text(
                                            text = "距离${teaser.label}还有 ${teaser.daysUntil} 天",
                                            maxLines = 1,
                                            style = TextStyle(
                                                fontSize = 11.sp,
                                                color = ColorProvider(R.color.widget_text_dim)
                                            )
                                        )
                                    }
                                }
                                Spacer(GlanceModifier.width(8.dp))
                                Text(
                                    text = when {
                                        occ.ended -> "结束"
                                        occ.isToday -> "今天"
                                        else -> "${abs(occ.daysToNext ?: 0L)} 天"
                                    },
                                    style = TextStyle(
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = ColorProvider(eventColor(event.colorIndex))
                                    )
                                )
                            }
                        }
                    }
                }
        }
    }
}

class DaysWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = DaysWidget()
}

/**
 * 桌面小组件（主视觉款，v3.23 新增）：单个最值得看的日子整卡呈现，
 * 设计语言与 App 内展示页统一——事件配图作背景（按智能裁切焦点裁切）、
 * 底部压暗罩、白字、衬线大数字。无配图时用事件强调色纯色底。
 * 选事件口径：置顶优先，其次最近的将来事件，再其次最近的过去事件。
 */
class HeroDaysWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val app = context.applicationContext as CountdownApp
        val hero = runCatching {
            val events = app.container.repository.getActiveEventsLight()
            pickHero(events.map { it to it.occurrence() })
        }.getOrNull()

        val bitmap = if (hero != null) {
            heroBitmap(context, hero.first, HERO_ASPECT)
        } else null

        provideContent {
            if (hero == null) {
                Box(
                    modifier = GlanceModifier
                        .fillMaxSize()
                        .background(ColorProvider(R.color.widget_bg))
                        .cornerRadius(24.dp)
                        .padding(16.dp)
                        .clickable(actionStartActivity(Intent(context, MainActivity::class.java))),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "还没有记录的日子\n打开应用添加一个吧",
                        style = TextStyle(
                            fontSize = 13.sp,
                            color = ColorProvider(R.color.widget_text_dim)
                        )
                    )
                }
            } else {
                val (event, occ) = hero
                val openIntent = Intent(context, MainActivity::class.java)
                    .putExtra("open_event_id", event.id)
                Box(
                    modifier = GlanceModifier
                        .fillMaxSize()
                        .background(ColorProvider(eventColor(event.colorIndex)))
                        .cornerRadius(24.dp)
                        .clickable(actionStartActivity(openIntent))
                ) {
                    if (bitmap != null) {
                        Image(
                            provider = ImageProvider(bitmap),
                            contentDescription = event.name,
                            modifier = GlanceModifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    }
                    Column(
                        modifier = GlanceModifier
                            .fillMaxSize()
                            .padding(16.dp),
                        verticalAlignment = Alignment.Bottom
                    ) {
                        Text(
                            text = event.name,
                            maxLines = 1,
                            style = TextStyle(
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = ColorProvider(R.color.widget_hero_text)
                            )
                        )
                        Spacer(GlanceModifier.height(2.dp))
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(
                                text = when {
                                    occ.ended -> "结束"
                                    occ.isToday -> "今天"
                                    else -> abs(occ.daysToNext ?: 0L).toString()
                                },
                                style = TextStyle(
                                    fontSize = 36.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Serif,
                                    color = ColorProvider(R.color.widget_hero_text)
                                )
                            )
                            if (!occ.isToday && !occ.ended) {
                                Spacer(GlanceModifier.width(6.dp))
                                Text(
                                    text = if ((occ.daysToNext ?: 0L) > 0) "天后" else "天前",
                                    style = TextStyle(
                                        fontSize = 13.sp,
                                        color = ColorProvider(R.color.widget_hero_text)
                                    ),
                                    modifier = GlanceModifier.padding(bottom = 6.dp)
                                )
                            }
                        }
                        val dateText = occ.next?.let {
                            DateUtils.formatDateWithWeek(DateUtils.toMillis(it))
                        } ?: ""
                        if (dateText.isNotEmpty()) {
                            Text(
                                text = dateText,
                                maxLines = 1,
                                style = TextStyle(
                                    fontSize = 12.sp,
                                    color = ColorProvider(R.color.widget_hero_text_dim)
                                )
                            )
                        }
                        // 已过去的一次性事件：纪念日预告
                        if (!occ.isRepeating && (occ.daysToNext ?: 0L) < 0) {
                            Anniversary.nextFor(event)?.let { teaser ->
                                Text(
                                    text = "距离${teaser.label}还有 ${teaser.daysUntil} 天",
                                    maxLines = 1,
                                    style = TextStyle(
                                        fontSize = 12.sp,
                                        color = ColorProvider(R.color.widget_hero_text_dim)
                                    )
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    companion object {
        /** 目标格子 4x2，宽高比约 2:1 */
        private const val HERO_ASPECT = 2.0f

        internal fun pickHero(
            rows: List<Pair<EventEntity, EventOccurrence>>
        ): Pair<EventEntity, EventOccurrence>? =
            rows.filter { it.second.daysToNext != null }
                .sortedWith(
                    compareBy(
                        { if (it.first.isPinned) 0 else 1 },
                        { if ((it.second.daysToNext ?: 0L) >= 0) 0 else 1 },
                        { abs(it.second.daysToNext ?: 0L) }
                    )
                )
                .firstOrNull()
    }
}

class HeroDaysWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = HeroDaysWidget()
}

/**
 * 主视觉组件的背景图：解码（长边采样到 1280 内）→ 按智能裁切焦点裁到目标宽高比
 * → 底部压暗渐变（与展示页同款罩法，保证白字可读）。任何失败返回 null，组件退纯色底。
 */
private suspend fun heroBitmap(context: Context, event: EventEntity, aspect: Float): Bitmap? {
    val path = event.imagePath ?: return null
    val file = File(path)
    if (!file.exists()) return null
    return withContext(Dispatchers.IO) {
        runCatching {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(path, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@runCatching null
            var sample = 1
            while (bounds.outWidth / sample > 1280) sample *= 2
            val src = BitmapFactory.decodeFile(
                path,
                BitmapFactory.Options().apply { inSampleSize = sample }
            ) ?: return@runCatching null

            // 智能裁切焦点（带缓存）；超时或失败回落居中
            val focal = withTimeoutOrNull(4_000) {
                runCatching { SmartCrop.focalPoint(context, file, aspect) }.getOrNull()
            }
            val cropped = cropToAspect(
                src,
                aspect,
                focal?.alignment?.horizontalBias ?: 0f,
                focal?.alignment?.verticalBias ?: 0f
            )
            if (cropped !== src) src.recycle()
            addBottomScrim(cropped)
        }.getOrNull()
    }
}

/** 按对齐偏置把图裁到目标宽高比：bias -1..1 映射到可移动范围两端 */
private fun cropToAspect(src: Bitmap, aspect: Float, hBias: Float, vBias: Float): Bitmap {
    val srcAspect = src.width.toFloat() / src.height
    val cropW: Int
    val cropH: Int
    if (srcAspect > aspect) {
        cropH = src.height
        cropW = (src.height * aspect).toInt().coerceIn(1, src.width)
    } else {
        cropW = src.width
        cropH = (src.width / aspect).toInt().coerceIn(1, src.height)
    }
    if (cropW == src.width && cropH == src.height) return src
    val maxX = src.width - cropW
    val maxY = src.height - cropH
    val x = ((hBias + 1f) / 2f * maxX).toInt().coerceIn(0, maxX)
    val y = ((vBias + 1f) / 2f * maxY).toInt().coerceIn(0, maxY)
    return Bitmap.createBitmap(src, x, y, cropW, cropH)
}

/** 底部压暗渐变：上 35% 透明 → 底部约 70% 黑，与展示页压暗罩同思路 */
private fun addBottomScrim(src: Bitmap): Bitmap {
    val out = src.copy(Bitmap.Config.ARGB_8888, true)
    val canvas = Canvas(out)
    val gradient = LinearGradient(
        0f, out.height * 0.30f,
        0f, out.height.toFloat(),
        AndroidColor.TRANSPARENT,
        AndroidColor.argb(178, 0, 0, 0),
        Shader.TileMode.CLAMP
    )
    canvas.drawRect(
        0f, 0f, out.width.toFloat(), out.height.toFloat(),
        Paint().apply { shader = gradient }
    )
    return out
}
