package com.example.countdowndays

import android.app.Application
import android.content.Context
import com.example.countdowndays.data.AppDatabase
import com.example.countdowndays.data.EventRepository
import com.example.countdowndays.util.PrefsManager
import com.example.countdowndays.util.ReminderScheduler
import androidx.glance.appwidget.updateAll
import com.example.countdowndays.widget.DaysWidget
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class CountdownApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        container.bootstrap()
    }
}

class AppContainer(private val context: Context) {

    private val db = AppDatabase.get(context)

    val repository = EventRepository(db.eventDao(), db.categoryDao())
    val prefs = PrefsManager(context)

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** 启动时把提醒重排一遍，并挂上数据变更后的刷新钩子 */
    fun bootstrap() {
        repository.onEventsChanged = {
            appScope.launch {
                ReminderScheduler.rescheduleAll(context, repository)
                runCatching { DaysWidget().updateAll(context) }
            }
        }
        appScope.launch {
            // 清掉「删除又撤销」过程中可能留下的孤儿图片
            repository.cleanupOrphanImages(context)
            ReminderScheduler.rescheduleAll(context, repository)
        }
    }
}
