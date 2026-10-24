package com.example.countdowndays

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.countdowndays.ui.navigation.AppNav
import com.example.countdowndays.ui.theme.CountdownTheme
import com.example.countdowndays.util.ThemeMode

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as CountdownApp).container
        val prefs = container.prefs
        val initialEventId = intent?.getLongExtra("open_event_id", 0L)?.takeIf { it > 0L }

        setContent {
            val mode by prefs.themeMode.collectAsStateWithLifecycle()
            val dynamic by prefs.dynamicColor.collectAsStateWithLifecycle()
            val systemDark = isSystemInDarkTheme()
            val darkTheme = when (mode) {
                ThemeMode.SYSTEM -> systemDark
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }
            var pendingEventId by remember { mutableStateOf(initialEventId) }

            CountdownTheme(darkTheme = darkTheme, dynamicColor = dynamic) {
                AppNav(
                    startEventId = pendingEventId,
                    onStartEventHandled = { pendingEventId = null }
                )
            }
        }
    }
}
