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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * 主视觉组件的配置界面：添加组件时由系统拉起（widget_info 里声明了 configure），
 * 支持 reconfigure 的启动器里长按组件也能再进来改选。
 *
 * 列表 = 全部未归档事件（名称 / 下一次日期 / 剩余天数），外加一行「自动」。
 * 点中即保存：同步落盘 → 排回执检查兜底 → 回 RESULT_OK 关闭，刷新随后
 * 由独立协程多通道发出（v4.5.3 解耦，见 confirmSelection）；
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

    /**
     * 保存选择（v4.5.3 解耦重排）：落盘 → 记回执期望 → 排回执检查 →
     * 回 RESULT_OK → 独立协程刷新。
     *
     * v4.5.2 及以前四步串行挤在一个协程里、刷新排在最前：update() 一旦
     * 挂起、或 Activity 被销毁导致协程取消，排闹钟与 setResult 被连带
     * 拖死（保存已 commit 落盘，所以 ✓ 是对的、组件却纹丝不动——真机
     * dump 实证）。本版把顺序倒过来：同步落盘在主线程直接执行、不进
     * 协程；兜底排程与结果返回其次；刷新最后单独用不依附 Activity
     * 生命周期的协程发——刷新再失败也连带不了前面任何一步。
     *
     * 刷新通道（见 HeroWidgetRefresher）：经典直推为主通道（v4.5.4，
     * RemoteViews 同步推送）、广播并行保险。+7s 还有回执检查兜底：
     * 渲染没跟上就自动升级再刷。
     */
    private fun confirmSelection(appWidgetId: Int, eventId: Long?) {
        val container = (application as CountdownApp).container
        val prefs = container.prefs
        // 1) 落盘：生产保存路径 saveHeroSelection（内部 commit() 同步落盘）：
        // 先把选择钉死，后面不管哪条刷新路径真正跑起来，渲染解析
        // 读到的都是新选择。v4.5.1 起单实例时还同步写规范槽＋镜像到实际
        // 放置 id，并修剪残留键——细节见 saveHeroSelection 的注释，这是
        // 「改选只跟随一次」换存法的主体，不许绕过它直接写 per-id 键。
        val placedIds = placedHeroWidgetIds()
        HeroDaysWidget.saveHeroSelection(prefs, appWidgetId, eventId, placedIds)
        Log.i(
            "HeroWidgetCfg",
            "保存选择 appWidgetId=$appWidgetId eventId=$eventId " +
                "已放置实例=$placedIds 规范槽=${prefs.heroCanonicalSelection()} " +
                "已存配置=${prefs.heroWidgetConfigs()}"
        )
        // 回执期望：记下保存时刻与「渲染应解析出的选择」（与底部结论行
        // 同一口径：单实例时主键取放置 id），+7s 回执检查拿它比对
        runCatching {
            val renderPrimaryId = if (placedIds.size == 1) placedIds.single() else appWidgetId
            val expected = HeroDaysWidget.resolveConfiguredEventId(prefs, renderPrimaryId, placedIds)
            prefs.recordHeroRefreshExpectation(System.currentTimeMillis(), expected)
        }
        // 2) 排兜底：回执检查闹钟（独立 requestCode，不与首次启用/更新共用）
        HeroReceiptCheck.schedule(applicationContext)
        // 3) 回 RESULT_OK 给启动器
        setResult(
            Activity.RESULT_OK,
            Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        )
        // 4) 刷新：单独协程，不依附 Activity 生命周期；经典直推（主，
        // 诊断端实证配置保存上下文即时出帧）＋广播通道并行保险
        val appContext = applicationContext
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            HeroWidgetRefresher.refreshAll(appContext, "配置保存")
            HeroWidgetRefresher.sendUpdateBroadcast(appContext)
        }
        finish()
    }

    /** 系统当前放置着的主视觉组件实例 id 列表（与配置界面收到的 id 对照取证用） */
    private fun placedHeroWidgetIds(): List<Int> = runCatching {
        AppWidgetManager.getInstance(this)
            .getAppWidgetIds(ComponentName(this, HeroClassicWidgetProvider::class.java))
            .toList()
    }.getOrDefault(emptyList())
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HeroWidgetConfigureScreen(
    currentEventId: Long?,
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
