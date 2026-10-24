package com.example.countdowndays.ui.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.countdowndays.data.EventEntity
import com.example.countdowndays.data.EventRepository
import com.example.countdowndays.data.EventWithNodes
import com.example.countdowndays.util.PrefsManager
import com.example.countdowndays.util.SortMode
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
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

    private val _categoryId = MutableStateFlow<Long?>(null)
    val categoryId: StateFlow<Long?> = _categoryId.asStateFlow()

    val categories = repo.observeCategories()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val sortMode: StateFlow<SortMode> = prefs.sortMode

    @OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
    val events: StateFlow<List<EventWithNodes>> =
        combine(_query.debounce(150), _categoryId) { q, c -> q to c }
            .flatMapLatest { (q, _) ->
                if (q.isBlank()) repo.observeAll() else repo.observeSearch(q.trim())
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _lastDeleted = MutableStateFlow<EventWithNodes?>(null)
    val lastDeleted: StateFlow<EventWithNodes?> = _lastDeleted.asStateFlow()

    fun setQuery(q: String) {
        _query.value = q
    }

    fun setCategory(id: Long?) {
        _categoryId.value = id
    }

    fun setSortMode(mode: SortMode) {
        prefs.sortModeValue = mode
    }

    fun togglePinned(event: EventEntity) {
        viewModelScope.launch { repo.setPinned(event.id, !event.isPinned) }
    }

    fun setArchived(event: EventEntity, archived: Boolean) {
        viewModelScope.launch { repo.setArchived(event.id, archived) }
    }

    fun delete(item: EventWithNodes) {
        _lastDeleted.value = item
        viewModelScope.launch { repo.deleteEvent(item.event) }
    }

    fun undoDelete() {
        val item = _lastDeleted.value ?: return
        _lastDeleted.value = null
        viewModelScope.launch { repo.restoreEvent(item) }
    }

    fun clearUndo() {
        _lastDeleted.value = null
    }
}
