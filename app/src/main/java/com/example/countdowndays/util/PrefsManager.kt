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

    // ---------- 渲染回执自检状态（v4.5.3，功能状态） ----------
    //
    // 回执自检（HeroReceiptCheck）判断「保存后组件是否真的按新选择重渲染」
    // 必需的最小状态，只有两份：保存时期望（何时保存、渲染应解析出哪个
    // 选择）与最近一次渲染的解析结果（configuredId＋时刻）。字段只留回执
    // 判断要用的，不存全文文本。v4.5.2 诊断版的 diag_ 留痕（配置打开/
    // 保存入参/渲染全文）已按用户要求在 v4.5.3 整体删除。

    /**
     * 回执期望：最近一次保存的时刻＋那次保存后期望渲染解析出的选择
     * （expectedConfiguredId 为 null＝期望「自动」口径）。
     */
    data class HeroRefreshExpectation(
        val savedAtMillis: Long,
        val expectedConfiguredId: Long?
    )

    /** 最近一次渲染的解析结果：那次渲染认到的选择（null＝自动口径）＋时刻 */
    data class HeroLastRender(
        val configuredId: Long?,
        val atMillis: Long
    )

    fun recordHeroRefreshExpectation(savedAtMillis: Long, expectedConfiguredId: Long?) {
        prefs.edit(commit = true) {
            putLong(KEY_RECEIPT_EXPECT_AT, savedAtMillis)
            putLong(KEY_RECEIPT_EXPECT_ID, expectedConfiguredId ?: -1L)
        }
    }

    fun heroRefreshExpectation(): HeroRefreshExpectation? {
        if (!prefs.contains(KEY_RECEIPT_EXPECT_AT)) return null
        val raw = prefs.getLong(KEY_RECEIPT_EXPECT_ID, -1L)
        return HeroRefreshExpectation(
            savedAtMillis = prefs.getLong(KEY_RECEIPT_EXPECT_AT, 0L),
            expectedConfiguredId = raw.takeIf { it > 0L }
        )
    }

    /** 每次渲染解析完调用：只记认到的选择与时刻，供回执检查比对 */
    fun recordHeroLastRender(configuredId: Long?) {
        prefs.edit {
            putLong(KEY_LAST_RENDER_ID, configuredId ?: -1L)
            putLong(KEY_LAST_RENDER_AT, System.currentTimeMillis())
        }
    }

    fun heroLastRender(): HeroLastRender? {
        if (!prefs.contains(KEY_LAST_RENDER_AT)) return null
        val raw = prefs.getLong(KEY_LAST_RENDER_ID, -1L)
        return HeroLastRender(
            configuredId = raw.takeIf { it > 0L },
            atMillis = prefs.getLong(KEY_LAST_RENDER_AT, 0L)
        )
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
        private const val KEY_RECEIPT_EXPECT_AT = "hero_receipt_expect_at"
        private const val KEY_RECEIPT_EXPECT_ID = "hero_receipt_expect_id"
        private const val KEY_LAST_RENDER_ID = "hero_last_render_id"
        private const val KEY_LAST_RENDER_AT = "hero_last_render_at"
        private const val KEY_THEME = "theme_mode"
        private const val KEY_DYNAMIC = "dynamic_color"
        private const val KEY_SORT = "sort_mode"
        private const val KEY_CARD_STYLE = "card_style"
    }
}
