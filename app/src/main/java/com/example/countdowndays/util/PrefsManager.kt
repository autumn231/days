package com.example.countdowndays.util

import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** 主题模式 */
enum class ThemeMode { SYSTEM, LIGHT, DARK }

/** 首页排序方式 */
enum class SortMode { DATE, CREATED, NAME }

/** 展示页卡片效果：素纸（原来的实色卡）/ 毛玻璃 / 液态玻璃 */
enum class CardStyle { PAPER, FROSTED, LIQUID }

/** 简单的偏好存储 */
class PrefsManager(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences("countdown_prefs", Context.MODE_PRIVATE)

    private val _themeMode = MutableStateFlow(loadThemeMode())
    val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()

    var themeModeValue: ThemeMode
        get() = _themeMode.value
        set(value) {
            prefs.edit { putString(KEY_THEME, value.name) }
            _themeMode.value = value
        }

    private val _dynamicColor = MutableStateFlow(prefs.getBoolean(KEY_DYNAMIC, false))
    val dynamicColor: StateFlow<Boolean> = _dynamicColor.asStateFlow()

    var dynamicColorValue: Boolean
        get() = _dynamicColor.value
        set(value) {
            prefs.edit { putBoolean(KEY_DYNAMIC, value) }
            _dynamicColor.value = value
        }

    private val _sortMode = MutableStateFlow(loadSortMode())
    val sortMode: StateFlow<SortMode> = _sortMode.asStateFlow()

    var sortModeValue: SortMode
        get() = _sortMode.value
        set(value) {
            prefs.edit { putString(KEY_SORT, value.name) }
            _sortMode.value = value
        }

    private val _cardStyle = MutableStateFlow(loadCardStyle())
    val cardStyle: StateFlow<CardStyle> = _cardStyle.asStateFlow()

    var cardStyleValue: CardStyle
        get() = _cardStyle.value
        set(value) {
            prefs.edit { putString(KEY_CARD_STYLE, value.name) }
            _cardStyle.value = value
        }

    // ---------- 主视觉组件的选事件配置（按 appWidgetId 分开存） ----------

    /** 某个主视觉组件实例选中的事件 id；没配置过（或选了「自动」）返回 null */
    fun heroWidgetEventId(appWidgetId: Int): Long? {
        val stored = prefs.getLong(heroWidgetKey(appWidgetId), -1L)
        return stored.takeIf { it > 0L }
    }

    /**
     * 保存主视觉组件的选事件；eventId 传 null 表示清掉配置、回落自动口径。
     * 用 commit() 同步落盘而不是 apply()：v4.1 用 apply() 时出过真机问题——配置界面
     * 选完就关、进程随即被系统杀掉，异步磁盘写没来得及刷完，选择永久丢失，组件
     * 每次渲染都只能读到空配置、永远回落默认事件。这里宁可阻塞几毫秒也要钉死。
     */
    fun setHeroWidgetEventId(appWidgetId: Int, eventId: Long?) {
        prefs.edit(commit = true) {
            if (eventId == null) remove(heroWidgetKey(appWidgetId))
            else putLong(heroWidgetKey(appWidgetId), eventId)
        }
    }

    /** 当前存着的全部主视觉选事件配置：appWidgetId → eventId（只含有效值） */
    fun heroWidgetConfigs(): Map<Int, Long> =
        prefs.all.entries.mapNotNull { (key, value) ->
            val id = key.removePrefix(HERO_KEY_PREFIX).toIntOrNull() ?: return@mapNotNull null
            val eventId = value as? Long ?: return@mapNotNull null
            if (eventId > 0L) id to eventId else null
        }.toMap()

    private fun heroWidgetKey(appWidgetId: Int) = "$HERO_KEY_PREFIX$appWidgetId"

    // ---------- 规范槽：单实例时的权威选择（v4.5.1） ----------
    //
    // 按 appWidgetId 分键存选择这条路在部分启动器（三星 One UI / HyperOS）上
    // 结构性不可靠：配置界面拿到的 id 与渲染侧 id 对不上、改选时给的 id 还会
    // 漂移，键越攒越多（删组件的 onDeleted 清的是渲染侧 id 的键，清不掉配置
    // 侧写下的键），而渲染侧的回落认领又要求「全局恰好一份配置」，第二份键
    // 一出现就永久哑火——真机表现为「改选只跟随一次、删旧重加也不行」。
    // 规范槽是与 id 无关的单一槽：桌面上只有一个主视觉组件时，每次保存都
    // 覆盖写这里，永远只有最新一份；渲染与配置界面的 ✓ 都读它（见
    // HeroDaysWidget.resolveConfiguredEventId），两边永不矛盾。

    /** 规范槽里存的选择（事件 id）；没写过或已清返回 null */
    fun heroCanonicalSelection(): Long? {
        val stored = prefs.getLong(KEY_HERO_CANONICAL_EVENT, -1L)
        return stored.takeIf { it > 0L }
    }

    /** 规范槽最近一次写入时间（毫秒）；没写过返回 null，只用于日志取证 */
    fun heroCanonicalSelectionAt(): Long? {
        val stored = prefs.getLong(KEY_HERO_CANONICAL_AT, -1L)
        return stored.takeIf { it > 0L }
    }

    /**
     * 覆盖写规范槽；eventId 传 null 表示清掉（用户选了「自动」或组件全删）。
     * 同样用 commit() 同步落盘，理由同 setHeroWidgetEventId。
     */
    fun setHeroCanonicalSelection(eventId: Long?) {
        prefs.edit(commit = true) {
            if (eventId == null) {
                remove(KEY_HERO_CANONICAL_EVENT)
                remove(KEY_HERO_CANONICAL_AT)
            } else {
                putLong(KEY_HERO_CANONICAL_EVENT, eventId)
                putLong(KEY_HERO_CANONICAL_AT, System.currentTimeMillis())
            }
        }
    }

    /**
     * 修剪残留的按 id 配置键：不在 [keepIds] 里的 hero_widget_event_* 键全删，
     * 返回删掉的 id 集合。保存选择后由 HeroDaysWidget.saveHeroSelection 调用，
     * keepIds = 系统登记的实际放置 id ＋本次保存用的 id（防放置列表在首次
     * 添加窗口期短暂滞后、把刚写下的键误删）。commit() 同步落盘。
     */
    fun pruneHeroWidgetConfigs(keepIds: Set<Int>): Set<Int> {
        val removed = heroWidgetConfigs().keys - keepIds
        if (removed.isNotEmpty()) {
            prefs.edit(commit = true) {
                removed.forEach { remove(heroWidgetKey(it)) }
            }
        }
        return removed
    }

    // ---------- 组件诊断留痕（v4.5.2 诊断版：只记录，不参与任何决策） ----------
    //
    // v4.5.1 的选事件逻辑在用户真机（HyperOS）上仍不跟随，云真机与单测都
    // 复现不出，靠猜已连错四版。本版只加仪表：配置界面收到的 id、最近一次
    // 保存的完整入参、最近一次渲染的解析全过程，各留一份痕在偏好里，诊断
    // 页（HeroDiagnostics）连同存储全量与系统枚举一起 dump 出来，用户复制
    // 粘贴即可取证。本节键全部 diag_ 前缀，不被任何生产决策读取；记录动作
    // 本身幂等无副作用，失败也不影响组件行为（关键调用点另有 runCatching）。

    /** 配置界面最近一次打开时，从 intent 里收到的 appWidgetId 留痕 */
    data class HeroConfigureOpenTrace(val appWidgetId: Int, val atMillis: Long)

    /**
     * 最近一次 saveHeroSelection 的完整入参留痕：保存用的 id、所选事件
     * （null＝选了「自动」）、保存瞬间枚举到的放置实例列表、规范槽到底写
     * 没写（没写时 [canonicalNote] 记下卡住的分支条件）、本次修剪掉的
     * 残留键 id 集合。
     */
    data class HeroSaveTrace(
        val appWidgetId: Int,
        val eventId: Long?,
        val placedIds: List<Int>,
        val canonicalWritten: Boolean,
        val canonicalNote: String,
        val prunedIds: Set<Int>,
        val atMillis: Long
    )

    /** 最近一次渲染的解析留痕：与 HeroWidgetRender 日志同字段的整行文本＋时间 */
    data class HeroRenderTrace(val text: String, val atMillis: Long)

    fun recordHeroConfigureOpen(appWidgetId: Int) {
        prefs.edit {
            putInt(KEY_DIAG_CFG_OPEN_ID, appWidgetId)
            putLong(KEY_DIAG_CFG_OPEN_AT, System.currentTimeMillis())
        }
    }

    fun heroConfigureOpenTrace(): HeroConfigureOpenTrace? {
        if (!prefs.contains(KEY_DIAG_CFG_OPEN_AT)) return null
        return HeroConfigureOpenTrace(
            appWidgetId = prefs.getInt(KEY_DIAG_CFG_OPEN_ID, -1),
            atMillis = prefs.getLong(KEY_DIAG_CFG_OPEN_AT, 0L)
        )
    }

    fun recordHeroSaveTrace(trace: HeroSaveTrace) {
        prefs.edit(commit = true) {
            putInt(KEY_DIAG_SAVE_ID, trace.appWidgetId)
            putLong(KEY_DIAG_SAVE_EVENT, trace.eventId ?: -1L)
            putString(KEY_DIAG_SAVE_PLACED, trace.placedIds.joinToString(","))
            putBoolean(KEY_DIAG_SAVE_CANONICAL_WRITTEN, trace.canonicalWritten)
            putString(KEY_DIAG_SAVE_CANONICAL_NOTE, trace.canonicalNote)
            putString(KEY_DIAG_SAVE_PRUNED, trace.prunedIds.joinToString(","))
            putLong(KEY_DIAG_SAVE_AT, trace.atMillis)
        }
    }

    fun heroSaveTrace(): HeroSaveTrace? {
        if (!prefs.contains(KEY_DIAG_SAVE_AT)) return null
        fun parseIds(raw: String?): List<Int> =
            raw?.split(",")?.mapNotNull { it.trim().toIntOrNull() } ?: emptyList()
        val rawEvent = prefs.getLong(KEY_DIAG_SAVE_EVENT, -1L)
        return HeroSaveTrace(
            appWidgetId = prefs.getInt(KEY_DIAG_SAVE_ID, -1),
            eventId = rawEvent.takeIf { it > 0L },
            placedIds = parseIds(prefs.getString(KEY_DIAG_SAVE_PLACED, "")),
            canonicalWritten = prefs.getBoolean(KEY_DIAG_SAVE_CANONICAL_WRITTEN, false),
            canonicalNote = prefs.getString(KEY_DIAG_SAVE_CANONICAL_NOTE, "") ?: "",
            prunedIds = parseIds(prefs.getString(KEY_DIAG_SAVE_PRUNED, "")).toSet(),
            atMillis = prefs.getLong(KEY_DIAG_SAVE_AT, 0L)
        )
    }

    fun recordHeroRenderTrace(text: String) {
        prefs.edit {
            putString(KEY_DIAG_RENDER_TEXT, text)
            putLong(KEY_DIAG_RENDER_AT, System.currentTimeMillis())
        }
    }

    fun heroRenderTrace(): HeroRenderTrace? {
        val text = prefs.getString(KEY_DIAG_RENDER_TEXT, null) ?: return null
        return HeroRenderTrace(text, prefs.getLong(KEY_DIAG_RENDER_AT, 0L))
    }

    private fun loadCardStyle(): CardStyle =
        runCatching {
            CardStyle.valueOf(prefs.getString(KEY_CARD_STYLE, CardStyle.PAPER.name)!!)
        }.getOrDefault(CardStyle.PAPER)

    private fun loadThemeMode(): ThemeMode =
        runCatching {
            ThemeMode.valueOf(prefs.getString(KEY_THEME, ThemeMode.SYSTEM.name)!!)
        }.getOrDefault(ThemeMode.SYSTEM)

    private fun loadSortMode(): SortMode =
        runCatching {
            SortMode.valueOf(prefs.getString(KEY_SORT, SortMode.DATE.name)!!)
        }.getOrDefault(SortMode.DATE)

    companion object {
        private const val HERO_KEY_PREFIX = "hero_widget_event_"
        private const val KEY_HERO_CANONICAL_EVENT = "hero_widget_canonical_event"
        private const val KEY_HERO_CANONICAL_AT = "hero_widget_canonical_at"
        private const val KEY_DIAG_CFG_OPEN_ID = "diag_cfg_open_id"
        private const val KEY_DIAG_CFG_OPEN_AT = "diag_cfg_open_at"
        private const val KEY_DIAG_SAVE_ID = "diag_save_id"
        private const val KEY_DIAG_SAVE_EVENT = "diag_save_event"
        private const val KEY_DIAG_SAVE_PLACED = "diag_save_placed"
        private const val KEY_DIAG_SAVE_CANONICAL_WRITTEN = "diag_save_canonical_written"
        private const val KEY_DIAG_SAVE_CANONICAL_NOTE = "diag_save_canonical_note"
        private const val KEY_DIAG_SAVE_PRUNED = "diag_save_pruned"
        private const val KEY_DIAG_SAVE_AT = "diag_save_at"
        private const val KEY_DIAG_RENDER_TEXT = "diag_render_text"
        private const val KEY_DIAG_RENDER_AT = "diag_render_at"
        private const val KEY_THEME = "theme_mode"
        private const val KEY_DYNAMIC = "dynamic_color"
        private const val KEY_SORT = "sort_mode"
        private const val KEY_CARD_STYLE = "card_style"
    }
}
