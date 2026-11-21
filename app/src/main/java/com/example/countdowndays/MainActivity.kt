package com.example.countdowndays

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.countdowndays.ui.navigation.AppNav
import com.example.countdowndays.ui.theme.CountdownTheme
import com.example.countdowndays.util.ThemeMode

class MainActivity : ComponentActivity() {

    /** 通知点开要直达的事件 id；onCreate 与 onNewIntent 共用同一个状态入口 */
    private val pendingEventId = mutableStateOf<Long?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as CountdownApp).container
        val prefs = container.prefs
        pendingEventId.value = intent?.getLongExtra(EXTRA_OPEN_EVENT_ID, 0L)?.takeIf { it > 0L }

        setContent {
            val mode by prefs.themeMode.collectAsStateWithLifecycle()
            val dynamic by prefs.dynamicColor.collectAsStateWithLifecycle()
            val systemDark = isSystemInDarkTheme()
            val darkTheme = when (mode) {
                ThemeMode.SYSTEM -> systemDark
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }
            val startEventId by pendingEventId

            CountdownTheme(darkTheme = darkTheme, dynamicColor = dynamic) {
                AppNav(
                    startEventId = startEventId,
                    onStartEventHandled = { pendingEventId.value = null }
                )
            }
        }
    }

    /** singleTop 下 App 已在运行时点通知只会走这里，不接住的话跳转会丢 */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        pendingEventId.value = intent.getLongExtra(EXTRA_OPEN_EVENT_ID, 0L).takeIf { it > 0L }
    }

    companion object {
        private const val EXTRA_OPEN_EVENT_ID = "open_event_id"
    }
}
