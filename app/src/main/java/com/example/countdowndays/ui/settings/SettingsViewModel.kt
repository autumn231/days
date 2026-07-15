package com.example.countdowndays.ui.settings

import androidx.lifecycle.ViewModel
import com.example.countdowndays.util.ImageStorage
import com.example.countdowndays.util.PrefsManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class SettingsViewModel(
    private val prefs: PrefsManager
) : ViewModel() {

    private val _backgroundPath = MutableStateFlow(prefs.backgroundPath)
    val backgroundPath = _backgroundPath.asStateFlow()

    fun setBackground(path: String) {
        val old = _backgroundPath.value
        if (old != null && old != path) ImageStorage.delete(old)
        prefs.backgroundPath = path
        _backgroundPath.value = path
    }

    fun clearBackground() {
        _backgroundPath.value?.let { ImageStorage.delete(it) }
        prefs.backgroundPath = null
        _backgroundPath.value = null
    }
}
