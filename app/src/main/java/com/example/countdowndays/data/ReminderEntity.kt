package com.example.countdowndays.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 一条提醒（v3.23 起支持多节点提醒）：一个事件可挂多条，
 * 每条 = 提前天数 + 时刻，各自独立调度（requestCode 用本行 id，天然不冲突）。
 *
 * events 表上的 reminderEnabled 仍是总开关；reminderAdvanceDays/Hour/Minute
 * 保留作第一条提醒的镜像（备份格式与旧版本读取兼容），事实来源以本表为准。
 */
@Entity(
    tableName = "reminders",
    foreignKeys = [
        ForeignKey(
            entity = EventEntity::class,
            parentColumns = ["id"],
            childColumns = ["eventId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("eventId")]
)
data class ReminderEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val eventId: Long,
    /** 提前多少天提醒（0 = 当天） */
    @ColumnInfo(defaultValue = "0") val advanceDays: Int = 0,
    @ColumnInfo(defaultValue = "9") val hour: Int = 9,
    @ColumnInfo(defaultValue = "0") val minute: Int = 0
)
