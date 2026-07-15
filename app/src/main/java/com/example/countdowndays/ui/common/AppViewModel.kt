package com.example.countdowndays.ui.common

import androidx.compose.runtime.Composable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.compose.ui.platform.LocalContext
import com.example.countdowndays.AppContainer
import com.example.countdowndays.CountdownApp

@Composable
inline fun <reified VM : ViewModel> appViewModel(
    crossinline create: (AppContainer) -> VM
): VM {
    val app = LocalContext.current.applicationContext as CountdownApp
    return viewModel(factory = viewModelFactory { initializer { create(app.container) } })
}
