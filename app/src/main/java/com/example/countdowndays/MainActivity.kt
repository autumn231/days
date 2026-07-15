package com.example.countdowndays

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.countdowndays.ui.navigation.AppNav
import com.example.countdowndays.ui.theme.CountdownTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            CountdownTheme {
                AppNav()
            }
        }
    }
}
