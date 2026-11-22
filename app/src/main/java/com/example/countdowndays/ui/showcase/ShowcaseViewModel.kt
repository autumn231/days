package com.example.countdowndays.ui.showcase

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.countdowndays.data.EventRepository
import com.example.countdowndays.data.sortedTimeline
import com.example.countdowndays.util.CardStyle
import com.example.countdowndays.util.PrefsManager
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * 展示页数据：以「下一次发生日」升序排列，形成「左边过去、右边将来」的卡片流。
 */
class ShowcaseViewModel(
    private val repo: EventRepository,
    private val prefs: PrefsManager? = null
) : ViewModel() {

    /** 卡片效果（全局偏好）：素纸 / 毛玻璃 / 液态玻璃 */
    val cardStyle: StateFlow<CardStyle>? = prefs?.cardStyle

    fun setCardStyle(style: CardStyle) {
        prefs?.cardStyleValue = style
    }

    val events = repo.observeAll()
        .map { it.sortedTimeline() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun togglePin(id: Long, currentlyPinned: Boolean) {
        viewModelScope.launch { repo.setPinned(id, !currentlyPinned) }
    }

    fun setPreset(id: Long, preset: Int) {
        viewModelScope.launch {
            // 选了预设就不再使用自定义照片
            repo.getEvent(id)?.let {
                repo.updateEvent(it.event.copy(backgroundPreset = preset, imagePath = null))
            }
        }
    }

    fun setImage(id: Long, path: String?) {
        viewModelScope.launch {
            repo.getEvent(id)?.let { repo.updateEvent(it.event.copy(imagePath = path)) }
        }
    }
}
