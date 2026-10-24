package com.example.countdowndays.util

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.countdowndays.CountdownApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** 定时器到点：查库 -> 发通知 -> 为重复事件重新排下一次 */
class ReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val eventId = intent.getLongExtra(ReminderScheduler.EXTRA_EVENT_ID, -1L)
        if (eventId <= 0L) return

        val pending = goAsync()
        val app = context.applicationContext as? CountdownApp ?: run { pending.finish(); return }

        CoroutineScope(Dispatchers.IO).launch {
            try {
                app.container.repository.getEvent(eventId)?.let { ew ->
                    ReminderScheduler.showNotification(context, ew.event)
                }
                ReminderScheduler.rescheduleAll(context, app.container.repository)
            } finally {
                pending.finish()
            }
        }
    }
}

/** 开机后重排所有提醒（闹钟在重启后会丢失） */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED &&
            intent.action != Intent.ACTION_MY_PACKAGE_REPLACED
        ) return

        val pending = goAsync()
        val app = context.applicationContext as? CountdownApp ?: run { pending.finish(); return }
        CoroutineScope(Dispatchers.IO).launch {
            try {
                ReminderScheduler.rescheduleAll(context, app.container.repository)
            } finally {
                pending.finish()
            }
        }
    }
}
