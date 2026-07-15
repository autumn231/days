package com.example.countdowndays.ui.settings

import androidx.lifecycle.ViewModel
import com.example.countdowndays.util.PrefsManager
import com.example.countdowndays.util.ThemeMode

class SettingsViewModel(
    private val prefs: PrefsManager
) : ViewModel() {

    val themeMode = prefs.themeMode

    fun setThemeMode(mode: ThemeMode) {
        prefs.themeModeValue = mode
    }
}
