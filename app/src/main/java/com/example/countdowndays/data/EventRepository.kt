package com.example.countdowndays.data

import kotlinx.coroutines.flow.Flow

class EventRepository(private val dao: EventDao) {

    fun observeAll(): Flow<List<EventWithNodes>> = dao.observeAllEventsWithNodes()

    fun observeSearch(query: String): Flow<List<EventWithNodes>> =
        dao.searchEventsWithNodes(query)

    fun observeEvent(id: Long): Flow<EventWithNodes?> = dao.observeEventWithNodes(id)

    suspend fun addEvent(event: EventEntity): Long = dao.insertEvent(event)

    suspend fun updateEvent(event: EventEntity) = dao.updateEvent(event)

    suspend fun deleteEvent(event: EventEntity) = dao.deleteEvent(event)

    suspend fun setPinned(id: Long, pinned: Boolean) = dao.setPinned(id, pinned)

    suspend fun addNode(node: TimelineNodeEntity): Long = dao.insertNode(node)

    suspend fun updateNode(node: TimelineNodeEntity) = dao.updateNode(node)

    suspend fun deleteNode(node: TimelineNodeEntity) = dao.deleteNode(node)
}
