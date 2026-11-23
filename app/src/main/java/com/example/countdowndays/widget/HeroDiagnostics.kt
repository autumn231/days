package com.example.countdowndays.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.os.Build
import com.example.countdowndays.CountdownApp
import com.example.countdowndays.util.PrefsManager
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
* 主视觉组件诊断（v4.5.2 诊断版）：把「组件不跟随所选事件」定位需要的一
* 切证据拼成一页纯文本，用户在诊断页一键复制、粘贴回来即可。
*
* 分两层：[buildHeroDiagnosticDump] 是纯函数（快照进、文本出，可单测）；
* [collectHeroDiagnostics] 负责从偏好、系统枚举与事件库把快照采齐。
* 本文件只读不写，不参与组件的任何行为决策。
*/

/** 诊断快照：dump 组装的全部入参（纯数据） */
data class HeroDiagnosticsSnapshot(
/** 形如「4.5.2（versionCode 40）」 */
val versionLabel: String,
val generatedAtMillis: Long,
val configureOpen: PrefsManager.HeroConfigureOpenTrace?,
val saveTrace: PrefsManager.HeroSaveTrace?,
/** 全部 per-id 配置键：appWidgetId → eventId */
val configs: Map<Int, Long>,
val canonicalEventId: Long?,
val canonicalAtMillis: Long?,
/** 诊断页打开时现查的系统登记放置实例 */
val placedIds: List<Int>,
val renderTrace: PrefsManager.HeroRenderTrace?,
/** 事件 id → 名称（含已归档，查不到的 id 在 dump 里注明） */
val eventNames: Map<Long, String>
)

private fun fmtTime(millis: Long): String =
SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.CHINA).format(Date(millis))

/** 事件 id 的可读写法：带名字；名字查不到（已删除/归档且不在名册）时注明 */
private fun eventLabel(id: Long?, names: Map<Long, String>): String = when (id) {
null -> "（无）"
else -> names[id]?.let { "事件 $id（$it）"}
?: "事件 $id（名字查不到，可能已删除）"
}

/** 把快照拼成诊断全文（纯函数） */
fun buildHeroDiagnosticDump(s: HeroDiagnosticsSnapshot): String {
val sb = StringBuilder()
sb.appendLine("倒数日 · 主视觉组件诊断")
sb.appendLine("版本：v${s.versionLabel}")
sb.appendLine("生成时间：${fmtTime(s.generatedAtMillis)}")
sb.appendLine()

sb.appendLine("")
val open = s.configureOpen
if (open == null) {
sb.appendLine("最近打开配置页：暂无记录")
} else {
sb.appendLine(
"最近打开配置页：appWidgetId=${open.appWidgetId}，" +
"时间 ${fmtTime(open.atMillis)}"
)
}
val save = s.saveTrace
if (save == null) {
sb.appendLine("最近一次保存：暂无记录")
} else {
sb.appendLine("最近一次保存（saveHeroSelection 入参留痕）：")
sb.appendLine(" appWidgetId=${save.appWidgetId}")
sb.appendLine(
" 选择 eventId=" + if (save.eventId == null) {
"null（选了「自动」）"
} else {
eventLabel(save.eventId, s.eventNames)
}
)
sb.appendLine(" 保存瞬间放置实例 placedIds=${save.placedIds}")
sb.appendLine(
" 规范槽：" + if (save.canonicalWritten) {
"已覆盖写"
} else {
"未写（${save.canonicalNote}）"
}
)
sb.appendLine(
" 本次修剪残留键：" +
if (save.prunedIds.isEmpty()) "无" else save.prunedIds.sorted().toString()
)
sb.appendLine(" 时间：${fmtTime(save.atMillis)}")
}
sb.appendLine()

sb.appendLine("")
if (s.configs.isEmpty()) {
sb.appendLine("per-id 配置键 hero_widget_event_*：一条都没有")
} else {
sb.appendLine("per-id 配置键 hero_widget_event_*（共 ${s.configs.size} 条）：")
s.configs.toSortedMap().forEach { (id, eventId) ->
sb.appendLine(" hero_widget_event_$id → ${eventLabel(eventId, s.eventNames)}")
}
}
if (s.canonicalEventId == null) {
sb.appendLine("规范槽：（空）")
} else {
val at = s.canonicalAtMillis?.let { "，写入时间 ${fmtTime(it)}"}?: ""
sb.appendLine("规范槽：${eventLabel(s.canonicalEventId, s.eventNames)}$at")
}
sb.appendLine("残留键总数：${s.configs.size}（per-id 配置键条数）")
sb.appendLine()

sb.appendLine("")
sb.appendLine(
"当前实际放置实例（HeroDaysWidgetReceiver，打开诊断页时现查）：" +
"${s.placedIds}，共 ${s.placedIds.size} 个"
)
sb.appendLine()

sb.appendLine("")
val render = s.renderTrace
if (render == null) {
sb.appendLine("暂无渲染记录（组件还没渲染过，或留痕已被清）")
} else {
sb.appendLine("最近一次渲染（时间 ${fmtTime(render.atMillis)}）：")
sb.appendLine(render.text)
}
return sb.toString()
}

/** 采齐诊断快照：偏好留痕＋存储全量＋系统现查放置实例＋事件名册 */
suspend fun collectHeroDiagnostics(context: Context): HeroDiagnosticsSnapshot {
val app = context.applicationContext as CountdownApp
val prefs = app.container.prefs
val versionLabel = runCatching {
val info = context.packageManager.getPackageInfo(context.packageName, 0)
val code = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
info.longVersionCode
} else {
@Suppress("DEPRECATION") info.versionCode.toLong()
}
"${info.versionName}（versionCode $code）"
}.getOrDefault("未知")
val placedIds = runCatching {
AppWidgetManager.getInstance(context).getAppWidgetIds(
ComponentName(context, HeroDaysWidgetReceiver::class.java)
).toList()
}.getOrDefault(emptyList())
val names = runCatching {
app.container.repository.getAllEvents().associate { it.event.id to it.event.name}
}.getOrDefault(emptyMap())
return HeroDiagnosticsSnapshot(
versionLabel = versionLabel,
generatedAtMillis = System.currentTimeMillis(),
configureOpen = prefs.heroConfigureOpenTrace(),
saveTrace = prefs.heroSaveTrace(),
configs = prefs.heroWidgetConfigs(),
canonicalEventId = prefs.heroCanonicalSelection(),
canonicalAtMillis = prefs.heroCanonicalSelectionAt(),
placedIds = placedIds,
renderTrace = prefs.heroRenderTrace(),
eventNames = names
)
}
