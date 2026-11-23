package com.example.countdowndays.widget

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
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
                    currentEventId = prefs.heroWidgetEventId(appWidgetId),
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
            container.prefs.setHeroWidgetEventId(appWidgetId, eventId)
            runCatching {
                HeroDaysWidget().update(
                    this@HeroWidgetConfigureActivity, AppWidgetId(appWidgetId)
                )
            }
            setResult(
                Activity.RESULT_OK,
                Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            )
            finish()
        }
    }
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
        when {
            list == null -> Box(Modifier.fillMaxSize().padding(padding))
            list.isEmpty() -> Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
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
                    .padding(padding)
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
