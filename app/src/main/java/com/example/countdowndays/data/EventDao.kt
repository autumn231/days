package com.example.countdowndays.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface EventDao {

    @Transaction
    @Query("SELECT * FROM events WHERE archived = 0 ORDER BY is_pinned DESC, sortOrder ASC, date ASC, createdAt ASC")
    fun observeActiveEvents(): Flow<List<EventWithNodes>>

    @Transaction
    @Query("SELECT * FROM events ORDER BY is_pinned DESC, sortOrder ASC, date ASC, createdAt ASC")
    fun observeAllEventsWithNodes(): Flow<List<EventWithNodes>>

    @Transaction
    @Query(
        """
        SELECT * FROM events
        WHERE archived = 0 AND (
            name LIKE '%' || :query || '%'
            OR description LIKE '%' || :query || '%'
        )
        ORDER BY is_pinned DESC, date ASC, createdAt ASC
        """
    )
    fun searchEventsWithNodes(query: String): Flow<List<EventWithNodes>>

    @Transaction
    @Query("SELECT * FROM events WHERE id = :id")
    fun observeEventWithNodes(id: Long): Flow<EventWithNodes?>

    @Transaction
    @Query("SELECT * FROM events WHERE id = :id")
    suspend fun getEventWithNodes(id: Long): EventWithNodes?

    @Transaction
    @Query("SELECT * FROM events WHERE archived = 0")
    suspend fun getActiveEvents(): List<EventWithNodes>

    @Transaction
    @Query("SELECT * FROM events")
    suspend fun getAllEvents(): List<EventWithNodes>

    // ---------- 轻量查询（不带时间线节点） ----------
    // 首页、搜索、小组件、提醒调度都只用事件本身，原先走 @Relation 查询会把
    // 每个事件的全部时间线节点一起拉出来白白做 IO 和对象分配，这里拆开。

    @Query("SELECT * FROM events WHERE archived = 0 ORDER BY is_pinned DESC, sortOrder ASC, date ASC, createdAt ASC")
    fun observeActiveEventsLight(): Flow<List<EventEntity>>

    @Query(
        """
        SELECT * FROM events
        WHERE archived = 0 AND (
            name LIKE '%' || :query || '%'
            OR description LIKE '%' || :query || '%'
        )
        ORDER BY is_pinned DESC, date ASC, createdAt ASC
        """
    )
    fun searchEventsLight(query: String): Flow<List<EventEntity>>

    @Query("SELECT * FROM events WHERE archived = 0")
    suspend fun getActiveEventsLight(): List<EventEntity>

    @Query("SELECT * FROM events")
    suspend fun getAllEventsLight(): List<EventEntity>

    /** 每个事件的时间线节点数（日历页的「N 条时间线」标记用，只查计数不拉整行） */
    @Query("SELECT eventId, COUNT(*) AS cnt FROM timeline_nodes GROUP BY eventId")
    fun observeNodeCounts(): Flow<List<NodeCount>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEvent(event: EventEntity): Long

    @Update
    suspend fun updateEvent(event: EventEntity)

    @Delete
    suspend fun deleteEvent(event: EventEntity)

    @Query("UPDATE events SET is_pinned = :pinned WHERE id = :id")
    suspend fun setPinned(id: Long, pinned: Boolean)

    @Query("UPDATE events SET archived = :archived WHERE id = :id")
    suspend fun setArchived(id: Long, archived: Boolean)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNode(node: TimelineNodeEntity): Long

    @Update
    suspend fun updateNode(node: TimelineNodeEntity)

    @Delete
    suspend fun deleteNode(node: TimelineNodeEntity)

    // ---------- 提醒（多节点，v4） ----------

    @Query("SELECT * FROM reminders WHERE eventId = :eventId ORDER BY advanceDays DESC, hour ASC, minute ASC")
    fun observeReminders(eventId: Long): Flow<List<ReminderEntity>>

    @Query("SELECT * FROM reminders WHERE eventId = :eventId ORDER BY advanceDays DESC, hour ASC, minute ASC")
    suspend fun getReminders(eventId: Long): List<ReminderEntity>

    @Query("SELECT * FROM reminders")
    suspend fun getAllReminders(): List<ReminderEntity>

    @Insert
    suspend fun insertReminder(reminder: ReminderEntity): Long

    @Query("DELETE FROM reminders WHERE eventId = :eventId")
    suspend fun deleteRemindersForEvent(eventId: Long)
}

/** 时间线节点计数投影（配合 EventDao.observeNodeCounts） */
data class NodeCount(val eventId: Long, val cnt: Int)

@Dao
interface CategoryDao {

    @Query("SELECT * FROM categories ORDER BY sortOrder ASC, id ASC")
    fun observeAll(): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM categories ORDER BY sortOrder ASC, id ASC")
    suspend fun getAll(): List<CategoryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(category: CategoryEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(categories: List<CategoryEntity>)

    @Query("DELETE FROM categories WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("UPDATE events SET categoryId = NULL WHERE categoryId = :id")
    suspend fun detachEvents(id: Long)
}
