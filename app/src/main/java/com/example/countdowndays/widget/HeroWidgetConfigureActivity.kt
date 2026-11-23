package com.example.countdowndays.widget

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.glance.appwidget.AppWidgetId
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.example.countdowndays.CountdownApp
import com.example.countdowndays.MainActivity
import com.example.countdowndays.data.EventEntity
import com.example.countdowndays.data.EventOccurrence
import com.example.countdowndays.data.occurrence
import com.example.countdowndays.ui.theme.CountdownTheme
import com.example.countdowndays.ui.theme.eventColor
import com.example.countdowndays.util.DateUtils
import com.example.countdowndays.util.ThemeMode
import kotlin.math.abs
import kotlinx.coroutines.launch

/**
 * 主视觉组件的配置界面：添加组件时由系统拉起（widget_info 里声明了 configure），
 * 支持 reconfigure 的启动器里长按组件也能再进来改选。
 *
 * 列表 = 全部未归档事件（名称 / 下一次日期 / 剩余天数），外加一行「自动」。
 * 点中即保存（按 appWidgetId 存偏好）→ 主动刷新该组件 → RESULT_OK 关闭；
 * 中途退出保持 RESULT_CANCELED，新加组件会被系统取消、改选则保持原配置。
 */
class HeroWidgetConfigureActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // 先按取消处理：没点选就退出，系统不会把组件放上桌面
        setResult(Activity.RESULT_CANCELED)

        val appWidgetId = intent?.extras?.getInt(
            AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }

        val container = (application as CountdownApp).container
        val prefs = container.prefs
        // v4.5.2 诊断留痕：记下本界面这次打开收到的 appWidgetId，诊断页
        // dump 时与渲染侧、系统枚举三方对照。只记录，不影响任何行为。
        runCatching { prefs.recordHeroConfigureOpen(appWidgetId) }
        val placedIds = placedHeroWidgetIds()
        // ✓ 判定与渲染同源（v4.5.1）：走 HeroDaysWidget.resolveConfiguredEventId
        // 同一解析入口，不再只读本界面 id 名下的键——旧口径下出现过「✓ 在 A
        // 事件、组件却显示 B 事件」的自相矛盾（用户真机截图实证）。
        val currentSelection =
            HeroDaysWidget.resolveConfiguredEventId(prefs, appWidgetId, placedIds)
        // 取证日志：配置界面收到的 id、解析出的当前选择、规范槽、全部已存
        // 配置与系统登记的放置实例，全量对照（v4.3 起）
        Log.i(
            "HeroWidgetCfg",
            "配置界面打开 appWidgetId=$appWidgetId 解析选择=$currentSelection " +
                "规范槽=${prefs.heroCanonicalSelection()} 已存配置=${prefs.heroWidgetConfigs()} " +
                "已放置实例=$placedIds"
        )

        // v4.5.2 诊断结论行的数据：用与渲染相同的入参口径再算一份结论——
        // 单实例时渲染的主键是系统登记的放置 id，不是本界面收到的 id。
        // ✓ 判定仍用上面的 currentSelection（配置侧 id 口径，行为不变）；
        // 结论行回答的是「组件实际会显示什么」，两个口径并存是有意的，
        // 对照着看才能分清断点在解析侧还是刷新侧。
        val renderPrimaryId = if (placedIds.size == 1) placedIds.single() else appWidgetId
        val widgetConclusionId =
            HeroDaysWidget.resolveConfiguredEventId(prefs, renderPrimaryId, placedIds)

        setContent {
            val mode by prefs.themeMode.collectAsStateWithLifecycle()
            val dynamic by prefs.dynamicColor.collectAsStateWithLifecycle()
            val systemDark = isSystemInDarkTheme()
            val darkTheme = when (mode) {
                ThemeMode.SYSTEM -> systemDark
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }
            CountdownTheme(darkTheme = darkTheme, dynamicColor = dynamic) {
                HeroWidgetConfigureScreen(
                    currentEventId = currentSelection,
                    conclusionEventId = widgetConclusionId,
                    canonicalEventId = prefs.heroCanonicalSelection(),
                    placedCount = placedIds.size,
                    loadEvents = { container.repository.getActiveEventsLight() },
                    onPick = { eventId -> confirmSelection(appWidgetId, eventId) },
                    onOpenApp = {
                        startActivity(Intent(this, MainActivity::class.java))
                        finish()
                    },
                    onClose = { finish() }
                )
            }
        }
    }

    /** 保存选择 → 刷新这个组件实例 → 回 RESULT_OK 给启动器 */
    private fun confirmSelection(appWidgetId: Int, eventId: Long?) {
        val container = (application as CountdownApp).container
        lifecycleScope.launch {
            // 保存走生产保存路径 saveHeroSelection（内部 commit() 同步落盘）：
            // 先把选择钉死，后面不管哪条刷新路径真正跑起来，provideGlance
            // 读到的都是新选择。v4.5.1 起单实例时还同步写规范槽＋镜像到实际
            // 放置 id，并修剪残留键——细节见 saveHeroSelection 的注释，这是
            // 「改选只跟随一次」换存法的主体，不许绕过它直接写 per-id 键。
            val placedIds = placedHeroWidgetIds()
            HeroDaysWidget.saveHeroSelection(container.prefs, appWidgetId, eventId, placedIds)
            Log.i(
                "HeroWidgetCfg",
                "保存选择 appWidgetId=$appWidgetId eventId=$eventId " +
                    "已放置实例=$placedIds 规范槽=${container.prefs.heroCanonicalSelection()} " +
                    "已存配置=${container.prefs.heroWidgetConfigs()}"
            )
            refreshHeroWidget()
            // 再排一次延迟兜底重渲染——首次添加时系统的初始渲染可能抢在配置
            // 落盘之前跑完、先按自动口径出一帧，几秒后按已落盘配置重出一帧
            // 把它纠正过来（机制见 HeroDelayedRefresh，v4.5 起走 AlarmManager
            // 精确闹钟，本 Activity 进程被杀也照样触发）
            HeroDelayedRefresh.schedule(
                applicationContext,
                HeroDelayedRefresh.Source.CONFIGURE_SAVE
            )
            setResult(
                Activity.RESULT_OK,
                Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            )
            finish()
        }
    }

    /**
     * 保存后主动刷新组件。系统在配置流程（添加/改选）结束后不会再调
     * onUpdate，这一次刷新只能靠应用自己，必须刷成功。
     *
     * v4.1 的教训：只靠 Glance 的 update() 一条路，还用 runCatching 把异常静默
     * 吞掉——真机上选了事件却不生效、界面一直停在旧事件，且没有任何日志痕迹。
     * v4.2 改双触发互为兜底；v4.3 再加 updateAll。
     * v4.5 寻址收口：此前三路里有两路（update 与广播）按**配置界面收到的 id**
     * 寻址，而 v4.3 已实测该 id 与系统登记的放置 id 值域对不上、必然静默打空。
     * 本版只留一条口径——按 AppWidgetManager 登记的实际放置 id 逐个
     * Glance update()，配置界面的 id 只用于存配置、永不用于刷新寻址。
     * 放置列表此刻为空（首次添加、系统还没登记完）时不硬刷：配置已同步
     * 落盘，配置流程结束后的系统 onUpdate 与已排的延迟兜底都会按新配置出帧。
     */
    private suspend fun refreshHeroWidget() {
        val appContext = applicationContext
        val placedIds = placedHeroWidgetIds()
        if (placedIds.isEmpty()) {
            Log.i("HeroWidgetCfg", "保存后刷新：放置列表为空，留待 onUpdate/延迟兜底出帧")
            return
        }
        placedIds.forEach { placedId ->
            runCatching {
                HeroDaysWidget().update(appContext, AppWidgetId(placedId))
            }.onFailure {
                Log.w("HeroWidgetCfg", "update() 刷新主视觉组件失败 placedId=$placedId", it)
            }
        }
    }

    /** 系统当前放置着的主视觉组件实例 id 列表（与配置界面收到的 id 对照取证用） */
    private fun placedHeroWidgetIds(): List<Int> = runCatching {
        AppWidgetManager.getInstance(this)
            .getAppWidgetIds(ComponentName(this, HeroDaysWidgetReceiver::class.java))
            .toList()
    }.getOrDefault(emptyList())
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HeroWidgetConfigureScreen(
    currentEventId: Long?,
    conclusionEventId: Long?,
    canonicalEventId: Long?,
    placedCount: Int,
    loadEvents: suspend () -> List<EventEntity>,
    onPick: (Long?) -> Unit,
    onOpenApp: () -> Unit,
    onClose: () -> Unit
) {
    var rows by remember { mutableStateOf<List<Pair<EventEntity, EventOccurrence>>?>(null) }
    LaunchedEffect(Unit) {
        rows = runCatching {
            loadEvents()
                .map { it to it.occurrence() }
                .sortedWith(
                    compareBy(
                        // 未来的在前按天数近→远；过去的随后按远近；已结束的垫底
                        { when {
                            it.second.ended -> 2
                            (it.second.daysToNext ?: 0L) >= 0L -> 0
                            else -> 1
                        } },
                        { abs(it.second.daysToNext ?: Long.MAX_VALUE) }
                    )
                )
        }.getOrDefault(emptyList())
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("选择要显示的日子", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.Filled.Close, contentDescription = "关闭")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        }
    ) { padding ->
        val list = rows
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            Box(Modifier.weight(1f)) {
                when {
                    list == null -> Box(Modifier.fillMaxSize())
                    list.isEmpty() -> Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                Text(
                    text = "还没有记录的日子",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "先去应用里添加一个，再回来选给组件显示。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(16.dp))
                Button(onClick = onOpenApp) { Text("打开倒数日") }
            }
                    else -> LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                item {
                    ConfigureRow(
                        title = "自动",
                        subtitle = "显示最近的日子（将来优先）",
                        daysText = "",
                        colorIndex = null,
                        selected = currentEventId == null,
                        onClick = { onPick(null) }
                    )
                }
                items(list, key = { it.first.id }) { (event, occ) ->
                    val dateText = occ.next?.let {
                        DateUtils.formatDateWithWeek(DateUtils.toMillis(it))
                    } ?: ""
                    ConfigureRow(
                        title = event.name,
                        subtitle = dateText,
                        daysText = when {
                            occ.ended -> "结束"
                            occ.isToday -> "今天"
                            (occ.daysToNext ?: 0L) > 0 -> "还有 ${occ.daysToNext} 天"
                            else -> "已 ${abs(occ.daysToNext ?: 0L)} 天"
                        },
                        colorIndex = event.colorIndex,
                        selected = currentEventId == event.id,
                        onClick = { onPick(event.id) }
                    )
                }
                    }
                }
            }
            // v4.5.2 诊断结论行：结论由 Activity 用与渲染相同的解析函数＋
            // 相同入参口径（放置 id 优先）算好后传进来，这里只负责展示。
            // 结论与组件实际显示不一致 → 断点在刷新送达侧；一致但与所选
            // 不同 → 断点在解析/存储侧。一张截图即可分清，全量证据见
            // 关于页的「组件诊断」
            val nameOf = { id: Long ->
                list?.firstOrNull { it.first.id == id }?.first?.name ?: "事件#$id"
            }
            Text(
                text = "组件将显示：" +
                    (conclusionEventId?.let { nameOf(it) } ?: "自动（最近的日子）") +
                    "（依据：规范槽＝" +
                    (canonicalEventId?.let { nameOf(it) } ?: "空") +
                    "，放置实例数＝$placedCount）",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp)
            )
        }
    }
}

@Composable
private fun ConfigureRow(
    title: String,
    subtitle: String,
    daysText: String,
    colorIndex: Int?,
    selected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = if (selected) {
            BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
        } else null
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (colorIndex != null) {
                Box(
                    modifier = Modifier
                        .width(4.dp)
                        .height(36.dp)
                        .background(eventColor(colorIndex), RoundedCornerShape(2.dp))
                )
                Spacer(Modifier.width(12.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1
                )
                if (subtitle.isNotEmpty()) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }
            }
            if (daysText.isNotEmpty()) {
                Text(
                    text = daysText,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            if (selected) {
                Spacer(Modifier.width(8.dp))
                Icon(
                    Icons.Filled.Check,
                    contentDescription = "当前选择",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}
