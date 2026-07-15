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
    @Query(
        "SELECT * FROM events ORDER BY is_pinned DESC, date ASC, createdAt ASC"
    )
    fun observeAllEventsWithNodes(): Flow<List<EventWithNodes>>

    @Transaction
    @Query(
        """
        SELECT * FROM events
        WHERE name LIKE '%' || :query || '%' OR description LIKE '%' || :query || '%'
        ORDER BY is_pinned DESC, date ASC, createdAt ASC
        """
    )
    fun searchEventsWithNodes(query: String): Flow<List<EventWithNodes>>

    @Transaction
    @Query("SELECT * FROM events WHERE id = :id")
    fun observeEventWithNodes(id: Long): Flow<EventWithNodes?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEvent(event: EventEntity): Long

    @Update
    suspend fun updateEvent(event: EventEntity)

    @Delete
    suspend fun deleteEvent(event: EventEntity)

    @Query("UPDATE events SET is_pinned = :pinned WHERE id = :id")
    suspend fun setPinned(id: Long, pinned: Boolean)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNode(node: TimelineNodeEntity): Long

    @Update
    suspend fun updateNode(node: TimelineNodeEntity)

    @Delete
    suspend fun deleteNode(node: TimelineNodeEntity)
}
