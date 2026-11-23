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

    /** 编辑中的一条提醒（还没落库，不带 id） */
    data class ReminderDraft(
        val advanceDays: Int = 0,
        val hour: Int = 9,
        val minute: Int = 0
    )

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
        /** 多节点提醒（v3.23）；events 表上的单提醒字段保存时由第一条镜像生成 */
        val reminders: List<ReminderDraft> = listOf(ReminderDraft()),
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
                // 提醒以 reminders 表为准；表里没有（旧备份恢复等）时用事件上的旧字段兜底一条
                val rows = repo.getReminders(eventId)
                val drafts = when {
                    rows.isNotEmpty() -> rows.map {
                        ReminderDraft(it.advanceDays, it.hour, it.minute)
                    }
                    e.reminderEnabled -> listOf(
                        ReminderDraft(e.reminderAdvanceDays, e.reminderHour, e.reminderMinute)
                    )
                    else -> listOf(ReminderDraft())
                }
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
                    reminders = drafts,
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

    fun addReminder() {
        val s = _state.value
        if (s.reminders.size >= MAX_REMINDERS) return
        _state.value = s.copy(reminders = s.reminders + ReminderDraft(advanceDays = 7))
    }

    fun removeReminder(index: Int) {
        val s = _state.value
        if (s.reminders.size <= 1) return
        _state.value = s.copy(reminders = s.reminders.filterIndexed { i, _ -> i != index })
    }

    fun setReminderAdvance(index: Int, days: Int) {
        val s = _state.value
        _state.value = s.copy(
            reminders = s.reminders.mapIndexed { i, r ->
                if (i == index) r.copy(advanceDays = days) else r
            }
        )
    }

    fun setReminderTime(index: Int, hour: Int, minute: Int) {
        val s = _state.value
        _state.value = s.copy(
            reminders = s.reminders.mapIndexed { i, r ->
                if (i == index) r.copy(hour = hour, minute = minute) else r
            }
        )
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

            // events 表上的旧单提醒字段 = 第一条提醒的镜像（备份/旧版兼容），事实以 reminders 表为准
            val first = s.reminders.firstOrNull() ?: ReminderDraft()
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
                reminderAdvanceDays = first.advanceDays,
                reminderHour = first.hour,
                reminderMinute = first.minute
            )

            val savedId: Long
            if (editingId == null) {
                savedId = repo.addEvent(base)
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
                        sortOrder = orig.sortOrder,
                        // 编辑页不管这两项，必须从原记录保留，否则一保存就被清掉
                        repeatEndDate = orig.repeatEndDate,
                        backgroundPreset = orig.backgroundPreset
                    )
                )
                savedId = orig.id
            }
            // 提醒列表整体落库（关掉总开关时也保留列表，重开即恢复；调度层按开关放行）
            repo.setReminders(
                savedId,
                s.reminders.map {
                    com.example.countdowndays.data.ReminderEntity(
                        eventId = savedId,
                        advanceDays = it.advanceDays,
                        hour = it.hour,
                        minute = it.minute
                    )
                }
            )
            onDone()
        }
    }

    companion object {
        const val MAX_REMINDERS = 5
    }
}
