package com.example.countdowndays.ui.editevent

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.countdowndays.data.EventEntity
import com.example.countdowndays.data.EventRepository
import com.example.countdowndays.util.DateUtils
import com.example.countdowndays.util.ImageStorage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class AddEditEventViewModel(
    private val repo: EventRepository
) : ViewModel() {

    data class State(
        val name: String = "",
        val description: String = "",
        val dateMillis: Long = DateUtils.todayMillis(),
        val imagePath: String? = null,
        val loaded: Boolean = true
    )

    private val _state = MutableStateFlow(State())
    val state = _state.asStateFlow()

    private var editingId: Long? = null
    private var originalImagePath: String? = null

    fun load(eventId: Long?) {
        if (eventId == null) return
        editingId = eventId
        _state.value = _state.value.copy(loaded = false)
        viewModelScope.launch {
            repo.observeEvent(eventId).first()?.let { ew ->
                originalImagePath = ew.event.imagePath
                _state.value = State(
                    name = ew.event.name,
                    description = ew.event.description,
                    dateMillis = ew.event.date,
                    imagePath = ew.event.imagePath,
                    loaded = true
                )
            } ?: run { _state.value = _state.value.copy(loaded = true) }
        }
    }

    fun setName(v: String) { _state.value = _state.value.copy(name = v) }
    fun setDescription(v: String) { _state.value = _state.value.copy(description = v) }
    fun setDate(millis: Long) { _state.value = _state.value.copy(dateMillis = DateUtils.startOfDay(millis)) }
    fun setImage(path: String) { _state.value = _state.value.copy(imagePath = path) }
    fun clearImage() { _state.value = _state.value.copy(imagePath = null) }

    fun save(onDone: () -> Unit) {
        viewModelScope.launch {
            val s = _state.value
            if (s.name.isBlank()) return@launch
            if (editingId == null) {
                repo.addEvent(
                    EventEntity(
                        name = s.name.trim(),
                        description = s.description.trim(),
                        date = s.dateMillis,
                        imagePath = s.imagePath
                    )
                )
            } else {
                val orig = repo.observeEvent(editingId!!).first()?.event ?: return@launch
                if (originalImagePath != null && originalImagePath != s.imagePath) {
                    ImageStorage.delete(originalImagePath)
                }
                repo.updateEvent(
                    orig.copy(
                        name = s.name.trim(),
                        description = s.description.trim(),
                        date = s.dateMillis,
                        imagePath = s.imagePath
                    )
                )
            }
            onDone()
        }
    }
}
