package com.example.countdowndays.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.os.Bundle
import android.util.Log
import android.util.TypedValue
import android.view.View
import android.widget.RemoteViews
import androidx.core.content.ContextCompat
import com.example.countdowndays.CountdownApp
import com.example.countdowndays.MainActivity
import com.example.countdowndays.R
import com.example.countdowndays.data.EventEntity
import com.example.countdowndays.data.EventOccurrence
import com.example.countdowndays.data.occurrence
import com.example.countdowndays.ui.theme.eventColor
import com.example.countdowndays.util.Anniversary
import com.example.countdowndays.util.DateUtils
import com.example.countdowndays.util.PrefsManager
import com.example.countdowndays.util.SmartCrop
import java.io.File
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.sqrt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * 主视觉组件的选事件解析与保存（自 v4.1 起的生产口径，v4.5.4 起与渲染层解耦）。
 *
 * 这里只管「显示哪个事件」：存储走 [PrefsManager] 的 per-id 键＋规范槽，
 * 解析走 [resolveConfiguredEventId] 单一入口（渲染与配置界面的 ✓ 同源）。
 * 渲染本身自 v4.5.4 起是经典 RemoteViews（[HeroWidgetRenderer]），本对象
 * 不再是 Glance 组件——历史上它是 GlanceAppWidget 的 companion，函数签名
 * 与口径原样保留，单测也仍调这同一批生产函数。
 *
 * 选事件口径回顾：添加组件时在配置界面选事件、按 appWidgetId 存偏好；渲染
 * 时先用配置的事件，没配置过、或所选事件已被删除/归档时回落最近的未来
 * 事件（今天算未来侧），一个未来事件都没有时再走 [pickHero]。
 * v4.5.1 换存法：单实例保存走 [saveHeroSelection]——选择覆盖写进与 id
 * 无关的规范槽、镜像写到实际放置 id 名下，保存后修剪残留键（根因与实证
 * 见 saveHeroSelection 注释）。
 */
object HeroDaysWidget {

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

    /**
     * 渲染侧的生产读取路径：经典渲染器 [HeroWidgetRenderer.renderAll] 对每个
     * 放置 id 调这个函数，单测也调这个函数，不许再出现「测试测的是另一条
     * 路径」。步骤：按 appWidgetId 读选事件配置（主键）→ 规范槽/单份配置
     * 回落（[resolveConfiguredEventId]）→ 取活跃事件列表 → [resolveHero]
     * 定下要显示的事件，并把这次渲染认到的选择记进回执状态。
     */
    internal suspend fun resolveHeroForInstance(
        context: Context,
        appWidgetId: Int
    ): Pair<EventEntity, EventOccurrence>? {
        val app = context.applicationContext as CountdownApp
        val prefs = app.container.prefs
        val primary = if (appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
            prefs.heroWidgetEventId(appWidgetId)
        } else {
            null
        }
        val configs = prefs.heroWidgetConfigs()
        val placedIds = HeroWidgetRenderer.placedIds(context)
        val configuredId = resolveConfiguredEventId(prefs, appWidgetId, placedIds)
        val events = app.container.repository.getActiveEventsLight()
        val hero = resolveHero(configuredId, events.map { it to it.occurrence() })
        val trace = "appWidgetId=$appWidgetId primary=$primary configs=$configs " +
            "canonical=${prefs.heroCanonicalSelection()} " +
            "placed=$placedIds configuredId=$configuredId " +
            "heroId=${hero?.first?.id} heroName=${hero?.first?.name}"
        Log.i("HeroWidgetRender", trace)
        // 回执自检状态：只记这次渲染认到的选择与时刻，供保存后 +7s 的回执
        // 检查比对（见 HeroReceiptCheck）；只记录，不影响渲染结果。
        runCatching { prefs.recordHeroLastRender(configuredId) }
        return hero
    }

    /**
     * 保存主视觉选择（生产保存路径，配置界面 confirmSelection 调它）：
     * 1) 照旧按配置界面给的 id 写一份 per-id 键（id 对得上的启动器走主键）；
     * 2) 实际放置实例恰好 1 个时：把同一选择镜像写到那个放置 id 名下
     *    （渲染主键直达，删组件时 onDeleted 也终于能清对键），并覆盖写
     *    规范槽——规范槽与 id 无关、永远只有最新一份，不存在攒键卡死；
     * 3) 放置列表非空时修剪残留键：只保留放置 id 与本次保存 id 名下的键，
     *    其余（已删组件的孤儿键、历次改选漂移出去的键）全删。放置列表为
     *    空（首次添加窗口期口径未定）时不修剪、不写规范槽，退回旧行为，
     *    等下一次保存/解析时还有单份配置回落兜底。
     * eventId 传 null（用户选了「自动」）时同样镜像清除＋清规范槽。
     */
    internal fun saveHeroSelection(
        prefs: PrefsManager,
        appWidgetId: Int,
        eventId: Long?,
        placedIds: List<Int>
    ) {
        prefs.setHeroWidgetEventId(appWidgetId, eventId)
        if (placedIds.size == 1) {
            val placedId = placedIds.single()
            if (placedId != appWidgetId) {
                prefs.setHeroWidgetEventId(placedId, eventId)
            }
            prefs.setHeroCanonicalSelection(eventId)
        }
        if (placedIds.isNotEmpty()) {
            prefs.pruneHeroWidgetConfigs(placedIds.toSet() + appWidgetId)
        }
    }

    /**
     * 「当前生效的选择」的唯一解析入口：渲染（resolveHeroForInstance）与
     * 配置界面的 ✓ 判定都调它，两边同源、永不互相矛盾。primaryAppWidgetId
     * 是调用方自己的 id（渲染侧或配置侧），placedIds 是系统登记的实际
     * 放置实例列表。
     */
    internal fun resolveConfiguredEventId(
        prefs: PrefsManager,
        primaryAppWidgetId: Int,
        placedIds: List<Int>
    ): Long? {
        val primary = if (primaryAppWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
            prefs.heroWidgetEventId(primaryAppWidgetId)
        } else {
            null
        }
        return pickConfiguredId(
            primary = primary,
            configs = prefs.heroWidgetConfigs(),
            canonical = prefs.heroCanonicalSelection(),
            placedCount = placedIds.size
        )
    }

    /**
     * 定这次解析认哪份选事件配置（纯函数，可单测）：
     * 1) 本实例主键命中 → 用主键（多实例各自键优先的现状语义）；
     * 2) 放置实例恰好 1 个且规范槽有值 → 用规范槽（v4.5.1 主路径）；
     * 3) 全局恰好只有一份 per-id 配置 → 用它（v4.5 兼容回落）；
     * 4) 其余 → null（走自动，不猜归属）。
     * 规范槽只在单实例时读：多实例时它无法代表某个具体实例，不猜。
     */
    internal fun pickConfiguredId(
        primary: Long?,
        configs: Map<Int, Long>,
        canonical: Long?,
        placedCount: Int
    ): Long? {
        if (primary != null) return primary
        if (placedCount == 1 && canonical != null) return canonical
        if (configs.size == 1) return configs.values.single()
        return null
    }

    /**
     * 主视觉最终选事件（纯函数，可单测）：
     * 1) 配置过且该事件还在活跃列表里（未删除、未归档）→ 用它；
     * 2) 否则回落最近的未来事件（daysToNext >= 0，今天在内，取最小）；
     * 3) 一个未来事件都没有 → pickHero（置顶优先、最近的过去事件）。
     */
    internal fun resolveHero(
        configuredId: Long?,
        rows: List<Pair<EventEntity, EventOccurrence>>
    ): Pair<EventEntity, EventOccurrence>? {
        if (configuredId != null) {
            rows.firstOrNull { it.first.id == configuredId }?.let { return it }
        }
        val nearestFuture = rows
            .filter { (it.second.daysToNext ?: Long.MIN_VALUE) >= 0L }
            .minByOrNull { it.second.daysToNext ?: Long.MAX_VALUE }
        if (nearestFuture != null) return nearestFuture
        return pickHero(rows)
    }
}

/** 主视觉文字区（左下）在成品图上的分数范围：名称/大数字/日期/预告四行都落在这里 */
private const val HERO_ZONE_LEFT = 0.03f
private const val HERO_ZONE_TOP = 0.45f
private const val HERO_ZONE_RIGHT = 0.88f
private const val HERO_ZONE_BOTTOM = 0.98f

/** 字色交叉点：文字区亮度低于它用浅字、高于它用深字，与展示页选字色同一口径 */
private const val HERO_TEXT_CROSSOVER = 0.19f

/**
 * 推送位图字节上限（ARGB_8888 口径 w×h×4）：诊断端 v1.4 实证 880×440
 * （约 1.48MB）位图被宿主整帧丢弃、桌面全透明——RemoteViews 有约 1MB
 * 的 Binder 事务上限。主线把上限压到约 700KB 留足余量（含其余视图数据）。
 */
internal const val HERO_MAX_BITMAP_BYTES = 700 * 1024

/** ARGB_8888 位图字节数（纯函数，可单测） */
internal fun bitmapBytes(width: Int, height: Int): Int = width * height * 4

/**
 * 把 (width, height) 等比缩进 (maxWidth, maxHeight) 与字节上限内（纯函数，
 * 可单测）：只缩不放大；任一约束更紧时按更紧的算，返回至少 1×1。
 */
internal fun fitBitmapSize(
    width: Int,
    height: Int,
    maxWidth: Int,
    maxHeight: Int,
    maxBytes: Int = HERO_MAX_BITMAP_BYTES
): Pair<Int, Int> {
    if (width <= 0 || height <= 0) return 1 to 1
    var scale = 1f
    if (maxWidth > 0) scale = min(scale, maxWidth.toFloat() / width)
    if (maxHeight > 0) scale = min(scale, maxHeight.toFloat() / height)
    val maxPixels = (maxBytes / 4).coerceAtLeast(1)
    val pixels = width.toLong() * height
    if (pixels > maxPixels) {
        scale = min(scale, sqrt(maxPixels.toDouble() / pixels).toFloat())
    }
    val w = (width * scale).toInt().coerceAtLeast(1)
    val h = (height * scale).toInt().coerceAtLeast(1)
    return w to h
}

/** 纯色底字色选取（纯函数，可单测）：底色亮度 ≥ 交叉点用深字，否则白字 */
internal fun heroDarkTextForLuminance(luminance: Float): Boolean =
    luminance >= HERO_TEXT_CROSSOVER

/** 主视觉组件的成品背景：位图（已按最终罩法处理并缩到推送上限内）＋该背景上该用深字还是白字 */
private class HeroArtwork(val bitmap: Bitmap, val darkText: Boolean)

/**
 * 主视觉经典渲染器（v4.5.4）：逐放置 id 解析 → 组装 RemoteViews →
 * [AppWidgetManager.updateAppWidget] 同步推送 → 渲染记录已由解析函数落盘。
 *
 * 为什么换经典：诊断端五版实证，这台 HyperOS 上 Glance 的应用主动渲染
 * 会话从任何上下文都不执行（provideGlance 一次不来），而经典
 * updateAppWidget 从配置页、广播唤起的 onUpdate、后台闹钟 Receiver 三个
 * 上下文推送全部即时出帧、改选即时跟随。存储/解析/触发接线全是 v4.5.3
 * 现行口径，只换「渲染一帧」这一层。
 */
object HeroWidgetRenderer {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** 系统登记的实际放置 id（刷新寻址的唯一口径，永不用配置界面给的 id） */
    fun placedIds(context: Context): List<Int> = runCatching {
        AppWidgetManager.getInstance(context.applicationContext).getAppWidgetIds(
            ComponentName(
                context.applicationContext,
                HeroClassicWidgetProvider::class.java
            )
        ).toList()
    }.getOrDefault(emptyList())

    /** 非挂起入口：自起协程渲染全部实例（数据变更、Provider 回调等场景用） */
    fun renderAllAsync(context: Context, source: String) {
        val appContext = context.applicationContext
        scope.launch { renderAll(appContext, source) }
    }

    /**
     * 渲染并推送全部已放置实例。逐个走生产解析（[HeroDaysWidget
     * .resolveHeroForInstance]，它内部已落回执渲染记录）→ 组装 RemoteViews
     * → 同步推送。单个实例失败只记日志，不拖垮其余实例。
     */
    suspend fun renderAll(context: Context, source: String) {
        val appContext = context.applicationContext
        val ids = placedIds(appContext)
        if (ids.isEmpty()) {
            Log.i("HeroWidgetRender", "renderAll 跳过 来源=$source（未放置主视觉组件）")
            return
        }
        val manager = AppWidgetManager.getInstance(appContext)
        ids.forEach { id ->
            runCatching {
                val hero = HeroDaysWidget.resolveHeroForInstance(appContext, id)
                val views = buildViews(appContext, manager, id, hero)
                manager.updateAppWidget(id, views)
                Log.i(
                    "HeroWidgetRender",
                    "renderAll 已推送 id=$id 来源=$source 显示=${hero?.first?.name ?: "占位"}"
                )
            }.onFailure {
                Log.w("HeroWidgetRender", "renderAll 推送失败 id=$id 来源=$source", it)
            }
        }
    }

    /**
     * 广播通道：显式 ACTION_APPWIDGET_UPDATE 直达 Provider 的 onUpdate，
     * 走宿主更新同款路径（v4.5.3 起的主通道之一，经典版下继续保留）。
     */
    fun sendUpdateBroadcast(context: Context) {
        val appContext = context.applicationContext
        val ids = placedIds(appContext)
        if (ids.isEmpty()) {
            Log.i("HeroWidgetRefresh", "广播 跳过（放置列表为空）")
            return
        }
        runCatching {
            val intent = Intent(AppWidgetManager.ACTION_APPWIDGET_UPDATE)
                .setComponent(
                    ComponentName(appContext, HeroClassicWidgetProvider::class.java)
                )
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids.toIntArray())
            appContext.sendBroadcast(intent)
            Log.i("HeroWidgetRefresh", "广播 已发送 placed=$ids")
        }.onFailure {
            Log.w("HeroWidgetRefresh", "广播 发送失败 placed=$ids", it)
        }
    }

    /** 组装一个实例的 RemoteViews：照片位图（或纯色底）＋左下文字组＋整卡点击 */
    private suspend fun buildViews(
        context: Context,
        manager: AppWidgetManager,
        appWidgetId: Int,
        hero: Pair<EventEntity, EventOccurrence>?
    ): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.hero_widget)
        val openIntent = if (hero != null) {
            Intent(context, MainActivity::class.java)
                .putExtra("open_event_id", hero.first.id)
        } else {
            Intent(context, MainActivity::class.java)
        }.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val pending = PendingIntent.getActivity(
            context, appWidgetId, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.hero_root, pending)

        if (hero == null) {
            views.setInt(
                R.id.hero_root, "setBackgroundColor",
                ContextCompat.getColor(context, R.color.widget_bg)
            )
            views.setViewVisibility(R.id.hero_bg, View.GONE)
            views.setTextViewText(R.id.hero_name, "还没有记录的日子\n打开应用添加一个吧")
            views.setTextViewTextSize(
                R.id.hero_name, TypedValue.COMPLEX_UNIT_SP, 13f
            )
            views.setTextColor(
                R.id.hero_name,
                ContextCompat.getColor(context, R.color.widget_text_dim)
            )
            views.setTextViewText(R.id.hero_days, "")
            views.setTextViewText(R.id.hero_days_unit, "")
            views.setTextViewText(R.id.hero_date, "")
            views.setTextViewText(R.id.hero_teaser, "")
            return views
        }

        val (event, occ) = hero
        val (targetW, targetH) = targetSizePx(context, manager, appWidgetId)
        val artwork = heroArtwork(context, event, targetW, targetH)
        // 字色按最终背景亮度选：配图用渲染时实测的结论，纯色底看底色本身
        val darkText = artwork?.darkText ?: run {
            val base = eventColor(event.colorIndex)
            heroDarkTextForLuminance(
                SmartCrop.wcagLuminanceOf(base.red, base.green, base.blue)
            )
        }
        val textRes =
            if (darkText) R.color.widget_hero_text_dark else R.color.widget_hero_text
        val textDimRes =
            if (darkText) R.color.widget_hero_text_dark_dim else R.color.widget_hero_text_dim
        val textColor = ContextCompat.getColor(context, textRes)
        val textDimColor = ContextCompat.getColor(context, textDimRes)

        if (artwork != null) {
            views.setViewVisibility(R.id.hero_bg, View.VISIBLE)
            views.setImageViewBitmap(R.id.hero_bg, artwork.bitmap)
            // 位图未铺满的边角露出事件强调色兜底（alpha 强制 0xFF 不透底）
            views.setInt(R.id.hero_root, "setBackgroundColor", eventArgb(event.colorIndex))
        } else {
            views.setViewVisibility(R.id.hero_bg, View.GONE)
            views.setInt(R.id.hero_root, "setBackgroundColor", eventArgb(event.colorIndex))
        }

        views.setTextViewText(R.id.hero_name, event.name)
        views.setTextColor(R.id.hero_name, textColor)
        views.setTextViewText(
            R.id.hero_days,
            when {
                occ.ended -> "结束"
                occ.isToday -> "今天"
                else -> abs(occ.daysToNext ?: 0L).toString()
            }
        )
        views.setTextColor(R.id.hero_days, textColor)
        if (!occ.isToday && !occ.ended) {
            views.setTextViewText(
                R.id.hero_days_unit,
                if ((occ.daysToNext ?: 0L) > 0) "天后" else "天前"
            )
        } else {
            views.setTextViewText(R.id.hero_days_unit, "")
        }
        views.setTextColor(R.id.hero_days_unit, textColor)
        val dateText = occ.next?.let {
            DateUtils.formatDateWithWeek(DateUtils.toMillis(it))
        } ?: ""
        views.setTextViewText(R.id.hero_date, dateText)
        views.setTextColor(R.id.hero_date, textDimColor)
        // 已过去的一次性事件：纪念日预告
        val teaser = if (!occ.isRepeating && (occ.daysToNext ?: 0L) < 0) {
            Anniversary.nextFor(event)?.let {
                "距离${it.label}还有 ${it.daysUntil} 天"
            }
        } else {
            null
        }
        views.setTextViewText(R.id.hero_teaser, teaser ?: "")
        views.setTextColor(R.id.hero_teaser, textDimColor)
        return views
    }

    /** 事件强调色 → 不透明 ARGB int（setBackgroundColor 收到半透明色会透出桌面） */
    private fun eventArgb(colorIndex: Int): Int {
        val c = eventColor(colorIndex)
        return AndroidColor.argb(
            255,
            (c.red * 255).toInt(),
            (c.green * 255).toInt(),
            (c.blue * 255).toInt()
        )
    }

    /**
     * 本实例的目标像素尺寸：取 AppWidgetOptions 的实际尺寸（dp×density）；
     * 读不到时回落 4×2 的典型值（250×110dp）。宽高比即裁切目标。
     */
    private fun targetSizePx(
        context: Context,
        manager: AppWidgetManager,
        appWidgetId: Int
    ): Pair<Int, Int> {
        val density = context.resources.displayMetrics.density
        val options = runCatching { manager.getAppWidgetOptions(appWidgetId) }.getOrNull()
        val widthDp = options?.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, 0)
            ?.takeIf { it > 0 }
            ?: options?.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 0)
                ?.takeIf { it > 0 }
            ?: 250
        val heightDp = options?.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 0)
            ?.takeIf { it > 0 }
            ?: options?.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 0)
                ?.takeIf { it > 0 }
            ?: 110
        return (widthDp * density).toInt().coerceAtLeast(1) to
            (heightDp * density).toInt().coerceAtLeast(1)
    }

    /**
     * 主视觉成品背景：解码（长边采样到 1280 内）→ 按智能裁切焦点裁到目标
     * 宽高比 → 选罩法与字色（底部压暗罩渲染后实测左下文字区亮度：低于交叉
     * 点用压暗罩＋白字；仍偏亮则改浅色提亮罩＋深色字；渐变直接烘进位图）
     * → 按目标尺寸与 [HERO_MAX_BITMAP_BYTES] 上限等比缩放。整体任何失败
     * 返回 null，组件退纯色底（字色在 buildViews 里同样按亮度选）。
     */
    private suspend fun heroArtwork(
        context: Context,
        event: EventEntity,
        targetW: Int,
        targetH: Int
    ): HeroArtwork? {
        val path = event.imagePath ?: return null
        val file = File(path)
        if (!file.exists()) return null
        val aspect = targetW.toFloat() / targetH.coerceAtLeast(1)
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
                // 罩法二选一：两个罩函数都返回新位图，cropped 用完回收
                val darkened = addBottomScrim(cropped)
                val zoneLum = SmartCrop.bitmapRegionLuminance(
                    darkened,
                    HERO_ZONE_LEFT, HERO_ZONE_TOP, HERO_ZONE_RIGHT, HERO_ZONE_BOTTOM
                )
                val finished = if (zoneLum == null || zoneLum < HERO_TEXT_CROSSOVER) {
                    cropped.recycle()
                    HeroArtwork(darkened, darkText = false)
                } else {
                    val lightened = addLightScrim(cropped)
                    darkened.recycle()
                    cropped.recycle()
                    HeroArtwork(lightened, darkText = true)
                }
                // 体积纪律：按目标尺寸＋字节上限等比缩放后才许推送
                val bmp = finished.bitmap
                val (fitW, fitH) = fitBitmapSize(bmp.width, bmp.height, targetW, targetH)
                val finalBmp = if (fitW == bmp.width && fitH == bmp.height) {
                    bmp
                } else {
                    val scaled = Bitmap.createScaledBitmap(bmp, fitW, fitH, true)
                    if (scaled !== bmp) bmp.recycle()
                    scaled
                }
                Log.i(
                    "HeroWidgetRender",
                    "hero 位图 ${finalBmp.width}x${finalBmp.height} " +
                        "bytes=${bitmapBytes(finalBmp.width, finalBmp.height)} " +
                        "上限=$HERO_MAX_BITMAP_BYTES darkText=${finished.darkText}"
                )
                HeroArtwork(finalBmp, finished.darkText)
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

    /** 底部提亮渐变：压暗罩的镜像，上 30% 透明 → 底部约 75% 白，托住深色字 */
    private fun addLightScrim(src: Bitmap): Bitmap {
        val out = src.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(out)
        val gradient = LinearGradient(
            0f, out.height * 0.30f,
            0f, out.height.toFloat(),
            AndroidColor.TRANSPARENT,
            AndroidColor.argb(190, 255, 255, 255),
            Shader.TileMode.CLAMP
        )
        canvas.drawRect(
            0f, 0f, out.width.toFloat(), out.height.toFloat(),
            Paint().apply { shader = gradient }
        )
        return out
    }
}

/**
 * 主视觉经典 Provider（v4.5.4）：回调口径与原 Glance Receiver 对齐——
 * onEnabled 清规范槽＋排 FIRST_ENABLE 兜底；onUpdate 渲染推送＋排
 * FIRST_UPDATE 兜底；onDeleted 清本实例键、全删光时清规范槽；尺寸变化
 * 重推一帧。注意：Provider 类已换，桌面上旧的 Glance 版组件会失效，
 * 需删除重新添加（预期行为，见 CHANGELOG v4.5.4）。
 */
class HeroClassicWidgetProvider : AppWidgetProvider() {

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        // 新一代第一台组件不继承上一代的规范槽选择（v4.5.1 起的口径）：
        // 上一代删组件时 onDeleted 在部分启动器上送达不可靠，规范槽可能
        // 残留旧选择。清掉；本实例的选择随后由配置流程写回。
        runCatching {
            (context.applicationContext as CountdownApp).container.prefs
                .setHeroCanonicalSelection(null)
        }
        Log.i("HeroWidgetRender", "classic onEnabled 首次启用，排兜底重渲染")
        HeroDelayedRefresh.schedule(context, HeroDelayedRefresh.Source.FIRST_ENABLE)
    }

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        HeroWidgetRenderer.renderAllAsync(context, "onUpdate")
        // 每次 onUpdate 都顺手排一次延迟自检（v4.5）：防「本次更新发生在
        // 配置落盘之前」的最后一道保险；延迟重渲染不经 onUpdate，不会自激。
        HeroDelayedRefresh.schedule(context, HeroDelayedRefresh.Source.FIRST_UPDATE)
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        super.onDeleted(context, appWidgetIds)
        val prefs = (context.applicationContext as CountdownApp).container.prefs
        appWidgetIds.forEach {
            prefs.setHeroWidgetEventId(it, null)
        }
        // 主视觉组件全删光了，规范槽一并清掉，别让下一代新组件继承旧选择
        runCatching {
            val remaining = HeroWidgetRenderer.placedIds(context) - appWidgetIds.toSet()
            if (remaining.isEmpty()) {
                prefs.setHeroCanonicalSelection(null)
            }
        }
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle
    ) {
        super.onAppWidgetOptionsChanged(context, appWidgetManager, appWidgetId, newOptions)
        // 尺寸变了：位图目标尺寸随之变，重推一帧
        HeroWidgetRenderer.renderAllAsync(context, "尺寸变化")
    }
}
