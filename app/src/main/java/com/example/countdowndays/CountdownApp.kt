package com.example.countdowndays

import android.app.Application
import android.content.Context
import com.example.countdowndays.data.AppDatabase
import com.example.countdowndays.data.EventRepository
import com.example.countdowndays.util.PrefsManager

class CountdownApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}

class AppContainer(context: Context) {
    val repository = EventRepository(AppDatabase.get(context).eventDao())
    val prefs = PrefsManager(context)
}
