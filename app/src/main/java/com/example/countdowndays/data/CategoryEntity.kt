package com.example.countdowndays.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/** 分类 / 倒数本 */
@Entity(tableName = "categories")
data class CategoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val colorIndex: Int = 0,
    val sortOrder: Int = 0
)
