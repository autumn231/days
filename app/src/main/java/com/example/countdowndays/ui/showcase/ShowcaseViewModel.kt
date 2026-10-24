package com.example.countdowndays.ui.showcase

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.countdowndays.data.EventRepository
import com.example.countdowndays.data.EventWithNodes
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ShowcaseViewModel(private val repo: EventRepository) : ViewModel() {

    private val _event = MutableStateFlow<EventWithNodes?>(null)
    val event = _event.asStateFlow()

    private var loadedId: Long? = null

    fun load(id: Long) {
        if (loadedId == id) return
        loadedId = id
        viewModelScope.launch {
            repo.observeEvent(id).collect { _event.value = it }
        }
    }

    fun togglePin() {
        _event.value?.event?.let { e ->
            viewModelScope.launch { repo.setPinned(e.id, !e.isPinned) }
        }
    }

    fun setImage(path: String?) {
        val ew = _event.value ?: return
        viewModelScope.launch {
            repo.updateEvent(ew.event.copy(imagePath = path))
        }
    }
}
