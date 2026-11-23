package com.example.countdowndays.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.util.Log
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
import androidx.glance.appwidget.GlanceAppWidgetManager
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
import com.example.countdowndays.util.PrefsManager
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
 * 衬线大数字。罩法与字色按背景实测亮度自适应（v4.0）：深底用底部压暗罩＋白字，
 * 浅底改浅色提亮罩＋深色字；无配图时用事件强调色纯色底，字色同样按底色亮度选。
 * 选事件口径（v4.1 起可配置）：添加组件时在配置界面选事件、按 appWidgetId 存偏好，
 * 长按组件（启动器支持时）可重新选。渲染时先用配置的事件；没配置过、或所选事件
 * 已被删除/归档（活跃列表里找不到）时回落到最近的未来事件（今天算未来侧），
 * 一个未来事件都没有时再走 pickHero（置顶优先、最近的过去事件）。
 * v4.3：渲染读取抽成 resolveHeroForInstance 并全程打日志；另加单实例回落——
 * 部分启动器给配置界面的 appWidgetId 与渲染侧解出的 id 对不上（v4.2 云真机实测
 * 配置已存住、渲染却永远读不到），桌面上只有一个主视觉组件且偏好里只有一份
 * 配置时直接认这份配置，见 pickConfiguredId。
 * v4.5：回落去掉「桌面上只有一个实例」这个条件——v4.4 云真机实测它在真·首次
 * 添加时不成立（系统登记的放置实例数口径不受应用控制，窗口期可能为 0 或多于
 * 1），回落整体哑火、错帧一直挂到周期更新。渲染必须自洽：本实例主键读不到
 * 配置时，只要全局恰好存着一份配置就认它，不限首次、不限实例数。
 * v4.5.1：用户真机实证 v4.5 仍不行——改选只跟随过一次、之后再也不跟随，删旧
 * 重加全新组件也一样。根因是按 appWidgetId 分键存配置这条路本身：改选时配置
 * 界面拿到的 id 会漂移、键越攒越多（onDeleted 清的是渲染侧 id 的键，清不掉
 * 配置侧写下的），「恰好一份配置」在真实设备状态下基本永远不成立。本版换存
 * 法：单实例时保存走 saveHeroSelection——选择覆盖写进与 id 无关的规范槽、并
 * 镜像写到实际放置 id 名下，保存后修剪不在放置列表里的残留键；解析统一走
 * resolveConfiguredEventId，配置界面的 ✓ 与渲染同源、永不互相矛盾。多实例
 * 维持各自键优先的现状语义，规范槽只在单实例时生效。
 * v4.5.2（诊断版）：行为逻辑与 v4.5.1 完全一致，只加仪表——配置界面打开、
 * 每次保存、每次渲染三处留痕进偏好（见 PrefsManager 的 diag_ 键），新增
 * 「组件诊断」页一键 dump＋复制（见 HeroDiagnostics），配置界面底部加一行
 * 与渲染同口径的「组件将显示」结论行。证据回来再定下一版怎么修。
 */
class HeroDaysWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val hero = runCatching {
            resolveHeroForInstance(context, id)
        }.getOrNull()

        val artwork = if (hero != null) {
            heroArtwork(context, hero.first, HERO_ASPECT)
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
                // 字色按最终背景亮度选：配图用渲染时实测的结论，纯色底看底色本身，
                // 交叉点与展示页选字色同一口径（L<0.19 用浅字）
                val darkText = artwork?.darkText ?: run {
                    val base = eventColor(event.colorIndex)
                    SmartCrop.wcagLuminanceOf(base.red, base.green, base.blue) >= HERO_TEXT_CROSSOVER
                }
                val heroTextRes =
                    if (darkText) R.color.widget_hero_text_dark else R.color.widget_hero_text
                val heroTextDimRes =
                    if (darkText) R.color.widget_hero_text_dark_dim else R.color.widget_hero_text_dim
                val openIntent = Intent(context, MainActivity::class.java)
                    .putExtra("open_event_id", event.id)
                Box(
                    modifier = GlanceModifier
                        .fillMaxSize()
                        .background(ColorProvider(eventColor(event.colorIndex)))
                        .cornerRadius(24.dp)
                        .clickable(actionStartActivity(openIntent))
                ) {
                    if (artwork != null) {
                        Image(
                            provider = ImageProvider(artwork.bitmap),
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
                                color = ColorProvider(heroTextRes)
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
                                    color = ColorProvider(heroTextRes)
                                )
                            )
                            if (!occ.isToday && !occ.ended) {
                                Spacer(GlanceModifier.width(6.dp))
                                Text(
                                    text = if ((occ.daysToNext ?: 0L) > 0) "天后" else "天前",
                                    style = TextStyle(
                                        fontSize = 13.sp,
                                        color = ColorProvider(heroTextRes)
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
                                    color = ColorProvider(heroTextDimRes)
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
                                        color = ColorProvider(heroTextDimRes)
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

        /**
         * 渲染侧的生产读取路径：provideGlance 本身就调这个函数，单测也调这个函数，
         * 不许再出现「测试测的是另一条路径」。
         *
         * 步骤：从 GlanceId 解出 appWidgetId → 按它读选事件配置（主键）→ 取活跃
         * 事件列表 → resolveHero 定下要显示的事件。全过程打一条日志（tag
         * HeroWidgetRender），把渲染时实际看到的 id、配置全集、已放置实例和最终
         * 选择都留痕——v4.1/v4.2 两版都是渲染侧静默读不到配置却无从取证。
         *
         * 主键读不到时走 pickConfiguredId 的回落：实测三星/HyperOS 上出现过
         * 「配置界面里选择保存成功（重开界面 ✓ 还在），渲染却永远走自动分支」，
         * 即配置界面拿到的 appWidgetId 与渲染侧解出的 id 对不上。v4.5 的回落
         * 是「全局恰好一份配置就认领」，真机上键越攒越多、这条件基本永远不
         * 成立（见类头 v4.5.1 注释）；v4.5.1 起单实例改走规范槽（见
         * resolveConfiguredEventId），「恰好一份」只作旧状态的兼容回落保留。
         */
        internal suspend fun resolveHeroForInstance(
            context: Context,
            glanceId: GlanceId
        ): Pair<EventEntity, EventOccurrence>? {
            val app = context.applicationContext as CountdownApp
            val prefs = app.container.prefs
            val appWidgetId = runCatching {
                GlanceAppWidgetManager(context).getAppWidgetId(glanceId)
            }.getOrDefault(AppWidgetManager.INVALID_APPWIDGET_ID)
            val primary = if (appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
                prefs.heroWidgetEventId(appWidgetId)
            } else {
                null
            }
            val configs = prefs.heroWidgetConfigs()
            val placedIds = runCatching {
                AppWidgetManager.getInstance(context).getAppWidgetIds(
                    ComponentName(context, HeroDaysWidgetReceiver::class.java)
                )
            }.getOrDefault(IntArray(0))
            val configuredId = resolveConfiguredEventId(prefs, appWidgetId, placedIds.toList())
            val events = app.container.repository.getActiveEventsLight()
            val hero = resolveHero(configuredId, events.map { it to it.occurrence() })
            val trace = "appWidgetId=$appWidgetId primary=$primary configs=$configs " +
                "canonical=${prefs.heroCanonicalSelection()} " +
                "placed=${placedIds.toList()} configuredId=$configuredId " +
                "heroId=${hero?.first?.id} heroName=${hero?.first?.name}"
            Log.i("HeroWidgetRender", trace)
            // v4.5.2 诊断留痕：最近一次渲染的解析全过程存进偏好，诊断页
            // 读出来展示。用户在真机上跑一遍就能把这行证据带回来，不再
            // 靠 logcat（真机上抓不到）盲猜。只记录，不影响渲染结果。
            runCatching { prefs.recordHeroRenderTrace(trace) }
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
            var pruned: Set<Int> = emptySet()
            if (placedIds.isNotEmpty()) {
                pruned = prefs.pruneHeroWidgetConfigs(placedIds.toSet() + appWidgetId)
            }
            // v4.5.2 诊断留痕：把这次保存的完整入参记下来——保存用的 id、
            // 所选事件、保存瞬间的放置实例、规范槽写没写（没写就记下卡住的
            // 分支条件）、修剪掉哪些键。只记录，上面的保存行为一字未改；
            // 记录失败也不许影响保存本身。
            runCatching {
                val canonicalWritten = placedIds.size == 1
                prefs.recordHeroSaveTrace(
                    PrefsManager.HeroSaveTrace(
                        appWidgetId = appWidgetId,
                        eventId = eventId,
                        placedIds = placedIds,
                        canonicalWritten = canonicalWritten,
                        canonicalNote = if (canonicalWritten) {
                            "placedIds 恰好 1 个，规范槽已覆盖写"
                        } else {
                            "placedIds.size=${placedIds.size}，" +
                                "规范槽要求放置实例恰好 1 个，未写"
                        },
                        prunedIds = pruned,
                        atMillis = System.currentTimeMillis()
                    )
                )
            }
        }

        /**
         * 「当前生效的选择」的唯一解析入口：渲染（resolveHeroForInstance）与
         * 配置界面的 ✓ 判定都调它，两边同源、永不互相矛盾（v4.5 及以前配置
         * 页只读自己 id 的键、渲染走另一套回落，截图实证出现过 ✓ 在 A 事件、
         * 组件却显示 B 事件）。primaryAppWidgetId 是调用方自己的 id（渲染侧
         * 或配置侧），placedIds 是系统登记的实际放置实例列表。
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
         * 2) 放置实例恰好 1 个且规范槽有值 → 用规范槽（v4.5.1 主路径：规范
         *    槽与 id 无关、每次保存覆盖写，多少份残留键在场都不受影响）；
         * 3) 全局恰好只有一份 per-id 配置 → 用它（v4.5 兼容回落，覆盖升级
         *    前存下的单份选择与放置列表口径未定的窗口期）；
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
         * rows 只含未归档事件，所以「所选被归档」与「被删除」都自然落到第 2 步。
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
}

class HeroDaysWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = HeroDaysWidget()

    /**
     * 首个主视觉组件被放上桌面：排一次延迟自检重渲染。首次添加时系统的
     * 初始渲染可能抢在配置流程之前跑完，几秒后按已落盘配置重出一帧纠正
     * （机制见 HeroDelayedRefresh，v4.5 起走 AlarmManager）。延迟重渲染
     * 到点按系统登记的实际放置 id 全刷一遍。
     */
    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        // v4.5.1：新一代第一台组件不继承上一代的规范槽选择——上一代删组件
        // 时 onDeleted 在部分启动器上送达不可靠，规范槽可能残留旧选择，新
        // 组件在用户配置前会先读到它。清掉规范槽；本实例的选择随后由配置
        // 流程写回，已有实例的 per-id 主键不受此影响（onEnabled 只在从无到
        // 有第一台时触发）。
        runCatching {
            (context.applicationContext as CountdownApp).container.prefs
                .setHeroCanonicalSelection(null)
        }
        Log.i("HeroWidgetRender", "delayed onEnabled 首次启用，排兜底重渲染")
        HeroDelayedRefresh.schedule(context, HeroDelayedRefresh.Source.FIRST_ENABLE)
    }

    /**
     * 每次 onUpdate 都顺手排一次延迟自检（v4.5）：渲染决策本身已自洽，这次
     * onUpdate 出的帧就该是对的；延迟重渲染只是防「本次更新发生在配置落盘
     * 之前」的最后一道保险。闹钟全应用共用一个，后排顶掉先排、天然去重，
     * 不再需要 v4.4 的「已见过」标记（它有个隐患：先置标记再排程，排程失败
     * 一次该实例就永久不再排）。延迟重渲染只走 Glance update()、不经
     * onUpdate，不会自激循环。
     */
    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        HeroDelayedRefresh.schedule(context, HeroDelayedRefresh.Source.FIRST_UPDATE)
    }

    /** 组件被删时清掉它那份选事件配置，别在偏好里留孤儿记录 */
    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        super.onDeleted(context, appWidgetIds)
        val prefs = (context.applicationContext as CountdownApp).container.prefs
        appWidgetIds.forEach {
            prefs.setHeroWidgetEventId(it, null)
        }
        // v4.5.1：主视觉组件全删光了，规范槽一并清掉，别让下一代新组件
        // 继承旧选择（onEnabled 那边还有一道同样的清理，双保险）
        runCatching {
            val remaining = AppWidgetManager.getInstance(context).getAppWidgetIds(
                ComponentName(context, HeroDaysWidgetReceiver::class.java)
            ).toList() - appWidgetIds.toSet()
            if (remaining.isEmpty()) {
                prefs.setHeroCanonicalSelection(null)
            }
        }
    }
}

/** 主视觉文字区（左下）在成品图上的分数范围：名称/大数字/日期/预告四行都落在这里 */
private const val HERO_ZONE_LEFT = 0.03f
private const val HERO_ZONE_TOP = 0.45f
private const val HERO_ZONE_RIGHT = 0.88f
private const val HERO_ZONE_BOTTOM = 0.98f

/** 字色交叉点：文字区亮度低于它用浅字、高于它用深字，与展示页选字色同一口径 */
private const val HERO_TEXT_CROSSOVER = 0.19f

/** 主视觉组件的成品背景：位图（已按最终罩法处理）＋该背景上该用深字还是白字 */
private class HeroArtwork(val bitmap: Bitmap, val darkText: Boolean)

/**
 * 主视觉组件的背景图：解码（长边采样到 1280 内）→ 按智能裁切焦点裁到目标宽高比
 * → 选罩法与字色：先按底部压暗罩渲染，实测左下文字区亮度——低于交叉点（0.19，
 * 与展示页分区亮度选字色同一口径）就用压暗罩＋白字；压暗后文字区仍偏亮（浅色
 * 照片，白字会被洗没）则改浅色提亮罩＋深色字。实测失败回落压暗罩＋白字（旧行为）。
 * 整体任何失败返回 null，组件退纯色底（字色在 provideGlance 里同样按亮度选）。
 */
private suspend fun heroArtwork(context: Context, event: EventEntity, aspect: Float): HeroArtwork? {
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
            // 罩法二选一：两个罩函数都返回新位图，cropped 用完回收
            val darkened = addBottomScrim(cropped)
            val zoneLum = SmartCrop.bitmapRegionLuminance(
                darkened,
                HERO_ZONE_LEFT, HERO_ZONE_TOP, HERO_ZONE_RIGHT, HERO_ZONE_BOTTOM
            )
            if (zoneLum == null || zoneLum < HERO_TEXT_CROSSOVER) {
                cropped.recycle()
                HeroArtwork(darkened, darkText = false)
            } else {
                val lightened = addLightScrim(cropped)
                darkened.recycle()
                cropped.recycle()
                HeroArtwork(lightened, darkText = true)
            }
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
