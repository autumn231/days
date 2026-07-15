package com.example.countdowndays.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "events")
data class EventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val description: String = "",
    /** 事件目标日期，当天 0 点的 epoch 毫秒 */
    val date: Long,
    /** 事件配图在内部存储的绝对路径，可为空 */
    val imagePath: String? = null,
    @ColumnInfo(name = "is_pinned") val isPinned: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)
