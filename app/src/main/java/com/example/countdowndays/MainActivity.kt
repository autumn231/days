package com.example.countdowndays

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.countdowndays.ui.navigation.AppNav
import com.example.countdowndays.ui.theme.CountdownTheme
import com.example.countdowndays.util.ThemeMode

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val prefs = (application as CountdownApp).container.prefs
        setContent {
            // 订阅用户选择的主题模式，切换后实时生效
            val mode by prefs.themeMode.collectAsStateWithLifecycle()
            val systemDark = isSystemInDarkTheme()
            val darkTheme = when (mode) {
                ThemeMode.SYSTEM -> systemDark
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }
            CountdownTheme(darkTheme = darkTheme) {
                AppNav()
            }
        }
    }
}
