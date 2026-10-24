package com.example.countdowndays.ui.editevent

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.countdowndays.data.CategoryEntity
import com.example.countdowndays.data.EventEntity
import com.example.countdowndays.data.EventRepository
import com.example.countdowndays.data.RepeatType
import com.example.countdowndays.util.DateUtils
import com.example.countdowndays.util.ImageStorage
import com.example.countdowndays.util.LunarCalendar
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class AddEditEventViewModel(
    private val repo: EventRepository
) : ViewModel() {

    data class State(
        val name: String = "",
        val description: String = "",
        val dateMillis: Long = DateUtils.todayMillis(),
        val imagePath: String? = null,
        val repeatType: RepeatType = RepeatType.NONE,
        val isLunar: Boolean = false,
        val lunarMonth: Int = 1,
        val lunarDay: Int = 1,
        val categoryId: Long? = null,
        val colorIndex: Int = 0,
        val reminderEnabled: Boolean = false,
        val reminderAdvanceDays: Int = 0,
        val reminderHour: Int = 9,
        val reminderMinute: Int = 0,
        val loaded: Boolean = true
    )

    private val _state = MutableStateFlow(State())
    val state = _state.asStateFlow()

    val categories = repo.observeCategories()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private var editingId: Long? = null
    private var originalImagePath: String? = null

    fun load(eventId: Long?) {
        if (eventId == null) return
        editingId = eventId
        _state.value = _state.value.copy(loaded = false)
        viewModelScope.launch {
            repo.observeEvent(eventId).first()?.let { ew ->
                val e = ew.event
                originalImagePath = e.imagePath
                _state.value = State(
                    name = e.name,
                    description = e.description,
                    dateMillis = e.date,
                    imagePath = e.imagePath,
                    repeatType = RepeatType.of(e.repeatType),
                    isLunar = e.isLunar,
                    lunarMonth = e.lunarMonth.coerceAtLeast(1),
                    lunarDay = e.lunarDay.coerceAtLeast(1),
                    categoryId = e.categoryId,
                    colorIndex = e.colorIndex,
                    reminderEnabled = e.reminderEnabled,
                    reminderAdvanceDays = e.reminderAdvanceDays,
                    reminderHour = e.reminderHour,
                    reminderMinute = e.reminderMinute,
                    loaded = true
                )
            } ?: run { _state.value = _state.value.copy(loaded = true) }
        }
    }

    fun setName(v: String) { _state.value = _state.value.copy(name = v) }
    fun setDescription(v: String) { _state.value = _state.value.copy(description = v) }
    fun setColor(index: Int) { _state.value = _state.value.copy(colorIndex = index) }
    fun setCategory(id: Long?) { _state.value = _state.value.copy(categoryId = id) }
    fun setRepeat(type: RepeatType) { _state.value = _state.value.copy(repeatType = type) }

    fun setDate(millis: Long) {
        val s = _state.value
        if (s.isLunar) {
            val lunar = LunarCalendar.fromSolar(DateUtils.toLocalDate(millis))
            _state.value = s.copy(
                dateMillis = DateUtils.startOfDay(millis),
                lunarMonth = lunar.month,
                lunarDay = lunar.day
            )
        } else {
            _state.value = s.copy(dateMillis = DateUtils.startOfDay(millis))
        }
    }

    fun setLunar(enabled: Boolean) {
        val s = _state.value
        if (enabled) {
            val lunar = LunarCalendar.fromSolar(DateUtils.toLocalDate(s.dateMillis))
            _state.value = s.copy(isLunar = true, lunarMonth = lunar.month, lunarDay = lunar.day)
        } else {
            _state.value = s.copy(isLunar = false)
        }
    }

    fun setLunarDate(month: Int, day: Int) {
        val s = _state.value
        val solar = LunarCalendar.nextOccurrence(month, day, java.time.LocalDate.now())
        _state.value = s.copy(
            lunarMonth = month,
            lunarDay = day,
            dateMillis = DateUtils.toMillis(solar)
        )
    }

    fun setReminderEnabled(enabled: Boolean) {
        _state.value = _state.value.copy(reminderEnabled = enabled)
    }

    fun setReminderAdvance(days: Int) {
        _state.value = _state.value.copy(reminderAdvanceDays = days)
    }

    fun setReminderTime(hour: Int, minute: Int) {
        _state.value = _state.value.copy(reminderHour = hour, reminderMinute = minute)
    }

    fun setImage(path: String) { _state.value = _state.value.copy(imagePath = path) }
    fun clearImage() { _state.value = _state.value.copy(imagePath = null) }

    fun addCategory(name: String) {
        if (name.isBlank()) return
        viewModelScope.launch {
            val id = repo.saveCategory(CategoryEntity(name = name.trim()))
            _state.value = _state.value.copy(categoryId = id)
        }
    }

    fun save(onDone: () -> Unit) {
        viewModelScope.launch {
            val s = _state.value
            if (s.name.isBlank()) return@launch

            val base = EventEntity(
                name = s.name.trim(),
                description = s.description.trim(),
                date = s.dateMillis,
                imagePath = s.imagePath,
                repeatType = s.repeatType.name,
                isLunar = s.isLunar,
                lunarMonth = if (s.isLunar) s.lunarMonth else 0,
                lunarDay = if (s.isLunar) s.lunarDay else 0,
                categoryId = s.categoryId,
                colorIndex = s.colorIndex,
                reminderEnabled = s.reminderEnabled,
                reminderAdvanceDays = s.reminderAdvanceDays,
                reminderHour = s.reminderHour,
                reminderMinute = s.reminderMinute
            )

            if (editingId == null) {
                repo.addEvent(base)
            } else {
                val orig = repo.observeEvent(editingId!!).first()?.event ?: return@launch
                if (originalImagePath != null && originalImagePath != s.imagePath) {
                    ImageStorage.delete(originalImagePath)
                }
                repo.updateEvent(
                    base.copy(
                        id = orig.id,
                        isPinned = orig.isPinned,
                        createdAt = orig.createdAt,
                        archived = orig.archived,
                        sortOrder = orig.sortOrder
                    )
                )
            }
            onDone()
        }
    }
}
