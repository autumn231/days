package com.example.countdowndays.ui.settings

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import com.example.countdowndays.data.EventRepository
import com.example.countdowndays.util.PrefsManager
import com.example.countdowndays.util.ThemeMode

class SettingsViewModel(
    private val repo: EventRepository,
    private val prefs: PrefsManager
) : ViewModel() {

    val themeMode = prefs.themeMode
    val dynamicColor = prefs.dynamicColor

    fun setThemeMode(mode: ThemeMode) {
        prefs.themeModeValue = mode
    }

    fun setDynamicColor(enabled: Boolean) {
        prefs.dynamicColorValue = enabled
    }

    suspend fun exportBackup(context: Context, uri: Uri): Boolean =
        BackupManager.export(context, repo, uri)

    suspend fun importBackup(context: Context, uri: Uri): Int =
        BackupManager.import(context, repo, uri)
}
