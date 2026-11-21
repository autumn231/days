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

    /**
     * 已下发给 AlarmManager 的触发时刻快照（事件 id -> 触发毫秒，null 表示无闹钟）。
     * rescheduleAll 改为增量式：只动快照与期望不一致的事件，
     * 不再每次数据变更都把全部事件的闹钟取消重设一遍。
     * 进程重启后快照为空，首轮会全量下发一次，与旧行为一致。
     */
    private val snapshotLock = Any()
    private val scheduledTriggers = mutableMapOf<Long, Long?>()

    suspend fun rescheduleAll(context: Context, repo: EventRepository) {
        val alarms = context.getSystemService(AlarmManager::class.java) ?: return
        val now = System.currentTimeMillis()
        val desired = HashMap<Long, Long?>()
        repo.getActiveEventsLight().forEach { event ->
            desired[event.id] = if (event.reminderEnabled) {
                nextTriggerMillis(event)?.takeIf { it > now }
            } else {
                null
            }
        }
        synchronized(snapshotLock) {
            // 已被删除（或已归档）的事件：取消它的闹钟
            (scheduledTriggers.keys - desired.keys).toList().forEach { id ->
                alarms.cancel(pendingIntent(context, id))
                scheduledTriggers.remove(id)
            }
            desired.forEach { (id, triggerAt) ->
                if (id in scheduledTriggers && scheduledTriggers[id] == triggerAt) {
                    return@forEach // 期望与已下发的一致，不动
                }
                val pi = pendingIntent(context, id)
                if (triggerAt == null) {
                    alarms.cancel(pi)
                } else {
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
                scheduledTriggers[id] = triggerAt
            }
        }
    }

    /**
     * 下一次提醒触发的绝对时间；不会再触发则返回 null。
     * 若当前发生日的提醒时刻已经过去，就顺延到下一次发生日再算。
     */
    fun nextTriggerMillis(event: EventEntity, today: LocalDate = LocalDate.now()): Long? =
        nextTriggerMillis(event, today, System.currentTimeMillis())

    /**
     * 可测版本：nowMillis 显式传入。
     *
     * 逐个往后跳发生日找第一个还没到的提醒时刻。旧实现只试 2 次且基准日只 +1 天，
     * 「重复事件 + 提前天数较大」时提醒时刻还没轮到就退出返回 null，
     * rescheduleAll 会据此取消闹钟，后续提醒被整段丢掉。
     * 一次性事件的发生日恒定，提醒时刻一过就没有下一次，直接返回 null。
     */
    internal fun nextTriggerMillis(event: EventEntity, today: LocalDate, nowMillis: Long): Long? {
        var base = today
        val maxAttempts = event.reminderAdvanceDays.coerceAtLeast(0) + 400
        repeat(maxAttempts) {
            val occ = event.occurrence(base)
            val next = occ.next ?: return null
            val remindDate = next.minusDays(event.reminderAdvanceDays.toLong())
            val ts = remindDate
                .atTime(event.reminderHour.coerceIn(0, 23), event.reminderMinute.coerceIn(0, 59))
                .atZone(ZoneId.systemDefault())
                .toInstant()
                .toEpochMilli()
            if (ts > nowMillis) return ts
            if (!occ.isRepeating) return null
            base = next.plusDays(1)
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
