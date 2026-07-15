package com.example.countdowndays.ui.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.countdowndays.data.EventEntity
import com.example.countdowndays.data.EventRepository
import com.example.countdowndays.data.EventWithNodes
import com.example.countdowndays.util.ImageStorage
import com.example.countdowndays.util.PrefsManager
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MainViewModel(
    private val repo: EventRepository,
    private val prefs: PrefsManager
) : ViewModel() {

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _background = MutableStateFlow<String?>(prefs.backgroundPath)
    val backgroundPath: StateFlow<String?> = _background.asStateFlow()

    @OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
    val events: StateFlow<List<EventWithNodes>> = _query
        .debounce(120)
        .flatMapLatest { q ->
            if (q.isBlank()) repo.observeAll() else repo.observeSearch(q.trim())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun setQuery(q: String) {
        _query.value = q
    }

    fun togglePinned(event: EventEntity) {
        viewModelScope.launch { repo.setPinned(event.id, !event.isPinned) }
    }

    fun delete(event: EventEntity) {
        viewModelScope.launch {
            ImageStorage.delete(event.imagePath)
            repo.deleteEvent(event)
        }
    }

    fun refreshBackground() {
        _background.value = prefs.backgroundPath
    }
}
