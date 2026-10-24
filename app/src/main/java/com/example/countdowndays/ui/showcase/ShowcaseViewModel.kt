package com.example.countdowndays.ui.showcase

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.countdowndays.data.EventRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * 展示页数据：按日期升序排列，形成「左边过去、右边将来」的卡片流。
 */
class ShowcaseViewModel(private val repo: EventRepository) : ViewModel() {

    val events = repo.observeAll()
        .map { list ->
            list.sortedWith(compareBy({ it.event.date }, { it.event.id }))
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun togglePin(id: Long, currentlyPinned: Boolean) {
        viewModelScope.launch { repo.setPinned(id, !currentlyPinned) }
    }

    fun setImage(id: Long, path: String?) {
        viewModelScope.launch {
            repo.getEvent(id)?.let { repo.updateEvent(it.event.copy(imagePath = path)) }
        }
    }
}
