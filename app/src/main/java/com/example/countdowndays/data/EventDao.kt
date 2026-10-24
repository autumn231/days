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
}

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
