package com.example.countdowndays.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/** 展示页 / 分享图的背景预设 */
data class BackgroundPreset(
    val name: String,
    /** 空列表表示「跟随事件卡片色」 */
    val colors: List<Color>
)

val BackgroundPresets: List<BackgroundPreset> = listOf(
    BackgroundPreset("跟随卡片", emptyList()),
    BackgroundPreset(
        "暖阳",
        listOf(Color(0xFFF2A65A), Color(0xFFE0612E), Color(0xFF8E3B1C))
    ),
    BackgroundPreset(
        "暮色",
        listOf(Color(0xFF8A7BB8), Color(0xFFB56576), Color(0xFF2A2438))
    ),
    BackgroundPreset(
        "森林",
        listOf(Color(0xFF4C8A6E), Color(0xFF6B9E5E), Color(0xFF16302A))
    ),
    BackgroundPreset(
        "海岸",
        listOf(Color(0xFF4A8CB4), Color(0xFF63B4C4), Color(0xFF123244))
    ),
    BackgroundPreset(
        "墨玉",
        listOf(Color(0xFF5A5A5A), Color(0xFF2C2C2C), Color(0xFF0D0D0D))
    ),
    BackgroundPreset(
        "樱粉",
        listOf(Color(0xFFE8A0B4), Color(0xFFC96A85), Color(0xFF4E2430))
    )
)

/** preset = 0 时跟随卡片主色 */
fun backgroundBrush(preset: Int, accent: Color): Brush {
    val colors = BackgroundPresets.getOrNull(preset)?.colors.orEmpty()
    return if (colors.isEmpty()) {
        Brush.verticalGradient(
            listOf(
                accent.copy(alpha = 0.92f),
                accent.copy(alpha = 0.62f),
                Color(0xFF1F1B16)
            )
        )
    } else {
        Brush.verticalGradient(colors)
    }
}
