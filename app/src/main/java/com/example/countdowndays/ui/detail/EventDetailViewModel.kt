package com.example.countdowndays.ui.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.countdowndays.data.EventRepository
import com.example.countdowndays.data.EventWithNodes
import com.example.countdowndays.data.TimelineNodeEntity
import com.example.countdowndays.util.ImageStorage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class EventDetailViewModel(
    private val repo: EventRepository
) : ViewModel() {

    private val _event = MutableStateFlow<EventWithNodes?>(null)
    val event = _event.asStateFlow()

    private var loadedId: Long? = null

    fun load(eventId: Long) {
        if (loadedId == eventId) return
        loadedId = eventId
        viewModelScope.launch {
            repo.observeEvent(eventId).collect { _event.value = it }
        }
    }

    fun togglePinned() {
        _event.value?.event?.let { e ->
            viewModelScope.launch { repo.setPinned(e.id, !e.isPinned) }
        }
    }

    fun addNode(name: String, time: Long) {
        val id = _event.value?.event?.id ?: return
        if (name.isBlank()) return
        viewModelScope.launch {
            repo.addNode(TimelineNodeEntity(eventId = id, name = name.trim(), time = time))
        }
    }

    fun deleteNode(node: TimelineNodeEntity) {
        viewModelScope.launch { repo.deleteNode(node) }
    }

    fun deleteEvent(onDone: () -> Unit) {
        viewModelScope.launch {
            _event.value?.event?.let { e ->
                ImageStorage.delete(e.imagePath)
                repo.deleteEvent(e)
            }
            onDone()
        }
    }
}
