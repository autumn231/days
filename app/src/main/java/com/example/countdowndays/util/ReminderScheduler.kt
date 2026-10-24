package com.example.countdowndays.util

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.countdowndays.MainActivity
import com.example.countdowndays.R
import com.example.countdowndays.data.EventEntity
import com.example.countdowndays.data.EventRepository
import com.example.countdowndays.data.occurrence
import java.time.LocalDate
import java.time.ZoneId

/**
 * 提醒调度：每个事件一个定时器，触发后由 ReminderReceiver 发通知。
 * 重复事件在触发后不会被取消，数据变更/启动时会整体重排。
 */
object ReminderScheduler {

    const val CHANNEL_ID = "event_reminders"
    const val EXTRA_EVENT_ID = "extra_event_id"

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "重要日子提醒",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "事件到期前的提醒通知"
            enableVibration(true)
        }
        manager.createNotificationChannel(channel)
    }

    suspend fun rescheduleAll(context: Context, repo: EventRepository) {
        val alarms = context.getSystemService(AlarmManager::class.java) ?: return
        repo.getActiveEvents().map { it.event }.forEach { event ->
            val pi = pendingIntent(context, event.id)
            if (!event.reminderEnabled) {
                alarms.cancel(pi)
                return@forEach
            }
            val triggerAt = nextTriggerMillis(event)
            if (triggerAt == null || triggerAt <= System.currentTimeMillis()) {
                alarms.cancel(pi)
                return@forEach
            }
            try {
                if (canScheduleExact(alarms)) {
                    alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
                } else {
                    alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
                }
            } catch (e: SecurityException) {
                // 没有精确闹钟权限时退化为普通闹钟
                runCatching { alarms.set(AlarmManager.RTC_WAKEUP, triggerAt, pi) }
            }
        }
    }

    /**
     * 下一次提醒触发的绝对时间；不会再触发则返回 null。
     * 若今天的提醒时刻已经过去，就顺延到下一次发生日再算。
     */
    fun nextTriggerMillis(event: EventEntity, today: LocalDate = LocalDate.now()): Long? {
        val now = System.currentTimeMillis()
        var base = today
        repeat(2) {
            val next = event.occurrence(base).next ?: return null
            val remindDate = next.minusDays(event.reminderAdvanceDays.toLong())
            val ts = remindDate
                .atTime(event.reminderHour.coerceIn(0, 23), event.reminderMinute.coerceIn(0, 59))
                .atZone(ZoneId.systemDefault())
                .toInstant()
                .toEpochMilli()
            if (ts > now) return ts
            base = base.plusDays(1)
        }
        return null
    }

    private fun canScheduleExact(alarms: AlarmManager): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) alarms.canScheduleExactAlarms() else true

    private fun pendingIntent(context: Context, eventId: Long): PendingIntent {
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            putExtra(EXTRA_EVENT_ID, eventId)
        }
        return PendingIntent.getBroadcast(
            context,
            eventId.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    /** 发一条提醒通知 */
    fun showNotification(context: Context, event: EventEntity) {
        ensureChannel(context)
        val occ = event.occurrence()
        val days = occ.daysToNext ?: return

        val title = event.name
        val body = when {
            days == 0L -> "就是今天"
            days > 0 -> "还有 $days 天"
            else -> "已过去 ${-days} 天"
        }

        val contentIntent = PendingIntent.getActivity(
            context,
            event.id.toInt(),
            Intent(context, MainActivity::class.java)
                .putExtra("open_event_id", event.id)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(contentIntent)
            .build()

        runCatching {
            NotificationManagerCompat.from(context).notify(event.id.toInt(), notification)
        }
    }
}
