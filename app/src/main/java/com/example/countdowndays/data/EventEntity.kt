package com.example.countdowndays.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "events")
data class EventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val description: String = "",
    /** 事件起始/目标日期，当天 0 点的 epoch 毫秒 */
    val date: Long,
    /** 事件配图在内部存储的绝对路径，可为空 */
    val imagePath: String? = null,
    @ColumnInfo(name = "is_pinned") val isPinned: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),

    // ---------- v2 新增 ----------
    // 注意：这些 defaultValue 必须和 MIGRATION_1_2 里的 ALTER 语句完全一致，
    // 否则升级后的表结构与全新安装不一致，Room 的 schema 校验会直接抛异常。
    /** 重复规则，取值见 RepeatType.name */
    @ColumnInfo(defaultValue = "'NONE'") val repeatType: String = "NONE",
    /** 重复结束日期（epoch 毫秒，当天 0 点）；null 表示永不结束 */
    val repeatEndDate: Long? = null,
    /** 是否按农历记录（生日、祭日等） */
    @ColumnInfo(defaultValue = "0") val isLunar: Boolean = false,
    /** 农历月（1-12），isLunar 为 true 时有效 */
    @ColumnInfo(defaultValue = "0") val lunarMonth: Int = 0,
    /** 农历日（1-30） */
    @ColumnInfo(defaultValue = "0") val lunarDay: Int = 0,

    /** 所属分类，null 表示未分类 */
    val categoryId: Long? = null,
    /** 是否归档（不出现在主页列表） */
    @ColumnInfo(defaultValue = "0") val archived: Boolean = false,
    /** 卡片主题色索引，对应 EventPalette */
    @ColumnInfo(defaultValue = "0") val colorIndex: Int = 0,
    /** 手动排序权重，越小越靠前 */
    @ColumnInfo(defaultValue = "0") val sortOrder: Int = 0,
    /** 展示页背景预设：0 = 跟随卡片色，1..N 对应 BackgroundPresets */
    @ColumnInfo(defaultValue = "0") val backgroundPreset: Int = 0,

    /** 是否开启提醒 */
    @ColumnInfo(defaultValue = "0") val reminderEnabled: Boolean = false,
    /** 提前多少天提醒（0 = 当天） */
    @ColumnInfo(defaultValue = "0") val reminderAdvanceDays: Int = 0,
    /** 提醒时刻（24 小时制） */
    @ColumnInfo(defaultValue = "9") val reminderHour: Int = 9,
    @ColumnInfo(defaultValue = "0") val reminderMinute: Int = 0
)
