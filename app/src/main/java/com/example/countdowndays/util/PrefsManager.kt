package com.example.countdowndays.util

import android.content.Context
import androidx.core.content.edit

/** 简单的偏好存储：自定义背景图本地路径 */
class PrefsManager(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences("countdown_prefs", Context.MODE_PRIVATE)

    var backgroundPath: String?
        get() = prefs.getString(KEY_BG, null)
        set(value) = prefs.edit { putString(KEY_BG, value) }

    companion object {
        private const val KEY_BG = "background_path"
    }
}
