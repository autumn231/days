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
import com.example.countdowndays.data.ReminderEntity
import com.example.countdowndays.data.occurrence
import java.time.LocalDate
import java.time.ZoneId

/**
 * 提醒调度（v3.23 起多节点）：每个事件可挂多条提醒，每条提醒一个独立定时器
 * （requestCode = 提醒行 id，天然互不冲突），触发后由 ReminderReceiver 发通知。
 * 重复事件在触发后不会被取消，数据变更/启动时会整体重排。
 *
 * 兼容：reminders 表为空的事件（极少数异常路径）回退到 events 上的旧单提醒字段，
 * 调度键用 -eventId，与行 id（恒为正）错开。
 */
object ReminderScheduler {

    const val CHANNEL_ID = "event_reminders"
    const val EXTRA_EVENT_ID = "extra_event_id"
    const val EXTRA_REMINDER_KEY = "extra_reminder_key"

    /** 一条待调度的提醒：key 是调度键（行 id 或 -eventId），其余是触发参数 */
    data class ReminderSpec(
        val key: Long,
        val eventId: Long,
        val advanceDays: Int,
        val hour: Int,
        val minute: Int
    )

    /** 某事件实际生效的提醒列表：优先 reminders 表，为空时回退旧字段 */
    fun specsFor(event: EventEntity, rows: List<ReminderEntity>): List<ReminderSpec> {
        if (!event.reminderEnabled) return emptyList()
        if (rows.isNotEmpty()) {
            return rows.map {
                ReminderSpec(it.id, event.id, it.advanceDays, it.hour, it.minute)
            }
        }
        return listOf(
            ReminderSpec(
                key = -event.id,
                eventId = event.id,
                advanceDays = event.reminderAdvanceDays,
                hour = event.reminderHour,
                minute = event.reminderMinute
            )
        )
    }

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
     * 已下发给 AlarmManager 的触发时刻快照（提醒调度键 -> 触发毫秒，null 表示无闹钟）。
     * rescheduleAll 是增量式：只动快照与期望不一致的提醒，
     * 不再每次数据变更都把全部闹钟取消重设一遍。
     * 进程重启后快照为空，首轮会全量下发一次，与旧行为一致。
     */
    private val snapshotLock = Any()
    private val scheduledTriggers = mutableMapOf<Long, Long?>()

    suspend fun rescheduleAll(context: Context, repo: EventRepository) {
        val alarms = context.getSystemService(AlarmManager::class.java) ?: return
        val now = System.currentTimeMillis()
        val rowsByEvent = repo.getAllReminders().groupBy { it.eventId }
        val desired = HashMap<Long, Long?>()
        val specsByKey = HashMap<Long, ReminderSpec>()
        repo.getActiveEventsLight().forEach { event ->
            specsFor(event, rowsByEvent[event.id].orEmpty()).forEach { spec ->
                specsByKey[spec.key] = spec
                desired[spec.key] = nextTriggerMillis(
                    event, spec.advanceDays, spec.hour, spec.minute
                )?.takeIf { it > now }
            }
        }
        synchronized(snapshotLock) {
            // 事件被删/归档、提醒被删或总开关关掉：取消对应闹钟
            (scheduledTriggers.keys - desired.keys).toList().forEach { key ->
                alarms.cancel(pendingIntent(context, key, key))
                scheduledTriggers.remove(key)
            }
            desired.forEach { (key, triggerAt) ->
                if (key in scheduledTriggers && scheduledTriggers[key] == triggerAt) {
                    return@forEach // 期望与已下发的一致，不动
                }
                val spec = specsByKey.getValue(key)
                val pi = pendingIntent(context, key, spec.eventId)
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
                scheduledTriggers[key] = triggerAt
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
     * 可测版本：nowMillis 显式传入。参数取事件上的旧单提醒字段（兼容旧调用与测试）。
     */
    internal fun nextTriggerMillis(event: EventEntity, today: LocalDate, nowMillis: Long): Long? =
        nextTriggerMillis(
            event,
            event.reminderAdvanceDays, event.reminderHour, event.reminderMinute,
            today, nowMillis
        )

    /**
     * 逐个往后跳发生日找第一个还没到的提醒时刻。旧实现只试 2 次且基准日只 +1 天，
     * 「重复事件 + 提前天数较大」时提醒时刻还没轮到就退出返回 null，
     * rescheduleAll 会据此取消闹钟，后续提醒被整段丢掉。
     * 一次性事件的发生日恒定，提醒时刻一过就没有下一次，直接返回 null。
     */
    internal fun nextTriggerMillis(
        event: EventEntity,
        advanceDays: Int,
        hour: Int,
        minute: Int,
        today: LocalDate,
        nowMillis: Long
    ): Long? {
        var base = today
        val maxAttempts = advanceDays.coerceAtLeast(0) + 400
        repeat(maxAttempts) {
            val occ = event.occurrence(base)
            val next = occ.next ?: return null
            val remindDate = next.minusDays(advanceDays.toLong())
            val ts = remindDate
                .atTime(hour.coerceIn(0, 23), minute.coerceIn(0, 59))
                .atZone(ZoneId.systemDefault())
                .toInstant()
                .toEpochMilli()
            if (ts > nowMillis) return ts
            if (!occ.isRepeating) return null
            base = next.plusDays(1)
        }
        return null
    }

    /** 按任意提醒参数算下一次触发（调度用，today/now 取当前） */
    fun nextTriggerMillis(event: EventEntity, advanceDays: Int, hour: Int, minute: Int): Long? =
        nextTriggerMillis(
            event, advanceDays, hour, minute,
            LocalDate.now(), System.currentTimeMillis()
        )

    private fun canScheduleExact(alarms: AlarmManager): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) alarms.canScheduleExactAlarms() else true

    /**
     * requestCode = 调度键（提醒行 id 或 -eventId），保证每条提醒一个独立 PendingIntent。
     * 注意 PendingIntent 的匹配不看 extras，取消时只要 requestCode 与目标组件一致即可。
     */
    private fun pendingIntent(context: Context, key: Long, eventId: Long): PendingIntent {
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            putExtra(EXTRA_EVENT_ID, eventId)
            putExtra(EXTRA_REMINDER_KEY, key)
        }
        return PendingIntent.getBroadcast(
            context,
            key.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    /**
     * 发一条提醒通知。notificationKey 用调度键，保证同一事件的多条提醒
     * 各自成一条通知、不互相覆盖；缺省（旧闹钟）时退回事件 id。
     */
    fun showNotification(context: Context, event: EventEntity, notificationKey: Long? = null) {
        ensureChannel(context)
        val occ = event.occurrence()
        val days = occ.daysToNext ?: return

        val title = event.name
        val body = when {
            days == 0L -> "就是今天"
            days > 0 -> "还有 $days 天"
            else -> "已过去 ${-days} 天"
        }

        val notifyId = (notificationKey ?: event.id).toInt()
        val contentIntent = PendingIntent.getActivity(
            context,
            notifyId,
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
            NotificationManagerCompat.from(context).notify(notifyId, notification)
        }
    }
}
