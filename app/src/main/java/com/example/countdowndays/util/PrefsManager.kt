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

    private fun loadThemeMode(): ThemeMode =
        runCatching {
            ThemeMode.valueOf(prefs.getString(KEY_THEME, ThemeMode.SYSTEM.name)!!)
        }.getOrDefault(ThemeMode.SYSTEM)

    private fun loadSortMode(): SortMode =
        runCatching {
            SortMode.valueOf(prefs.getString(KEY_SORT, SortMode.DATE.name)!!)
        }.getOrDefault(SortMode.DATE)

    companion object {
        private const val KEY_THEME = "theme_mode"
        private const val KEY_DYNAMIC = "dynamic_color"
        private const val KEY_SORT = "sort_mode"
    }
}
