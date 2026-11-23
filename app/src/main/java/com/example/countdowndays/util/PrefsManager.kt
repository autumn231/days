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
        private const val KEY_THEME = "theme_mode"
        private const val KEY_DYNAMIC = "dynamic_color"
        private const val KEY_SORT = "sort_mode"
        private const val KEY_CARD_STYLE = "card_style"
    }
}
