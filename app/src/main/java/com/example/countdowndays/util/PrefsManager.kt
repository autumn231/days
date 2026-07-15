package com.example.countdowndays.util

import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** 主题模式 */
enum class ThemeMode { SYSTEM, LIGHT, DARK }

/** 简单的偏好存储：主题模式 */
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

    private fun loadThemeMode(): ThemeMode =
        runCatching {
            ThemeMode.valueOf(prefs.getString(KEY_THEME, ThemeMode.SYSTEM.name)!!)
        }.getOrDefault(ThemeMode.SYSTEM)

    companion object {
        private const val KEY_THEME = "theme_mode"
    }
}
