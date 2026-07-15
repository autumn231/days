package com.example.countdowndays.data

import androidx.room.Embedded
import androidx.room.Relation

data class EventWithNodes(
    @Embedded val event: EventEntity,
    @Relation(parentColumn = "id", entityColumn = "eventId")
    val nodes: List<TimelineNodeEntity>
)
