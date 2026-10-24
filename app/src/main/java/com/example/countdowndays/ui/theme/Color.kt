package com.example.countdowndays.ui.theme

import androidx.compose.ui.graphics.Color

// ---------- 浅色：奶油底 + 暖橙主色 ----------
val WarmPrimary = Color(0xFFE0612E)
val WarmOnPrimary = Color(0xFFFFFFFF)
val WarmPrimaryContainer = Color(0xFFFFDBCB)
val WarmOnPrimaryContainer = Color(0xFF3A1200)

val WarmSecondary = Color(0xFF7A6A5C)
val WarmOnSecondary = Color(0xFFFFFFFF)
val WarmSecondaryContainer = Color(0xFFF0E4D8)
val WarmOnSecondaryContainer = Color(0xFF28180E)

val WarmTertiary = Color(0xFF2F6F62)
val WarmOnTertiary = Color(0xFFFFFFFF)
val WarmTertiaryContainer = Color(0xFFB8E8DC)
val WarmOnTertiaryContainer = Color(0xFF00201A)

val WarmBackground = Color(0xFFFBF8F4)
val WarmOnBackground = Color(0xFF1F1B16)
val WarmSurface = Color(0xFFFFFDFA)
val WarmOnSurface = Color(0xFF1F1B16)
val WarmSurfaceVariant = Color(0xFFF1EAE1)
val WarmOnSurfaceVariant = Color(0xFF544840)
val WarmOutline = Color(0xFF8C7B6B)
val WarmOutlineVariant = Color(0xFFE5DACE)
val WarmError = Color(0xFFB3261E)
val WarmOnError = Color(0xFFFFFFFF)
val WarmSurfaceContainer = Color(0xFFF5EFE7)
val WarmSurfaceContainerHigh = Color(0xFFEDE3D8)

// ---------- 深色：暖黑 ----------
val WarmPrimaryDark = Color(0xFFFFB596)
val WarmOnPrimaryDark = Color(0xFF562000)
val WarmPrimaryContainerDark = Color(0xFF7A3410)
val WarmOnPrimaryContainerDark = Color(0xFFFFDBCB)

val WarmSecondaryDark = Color(0xFFD8C6B6)
val WarmOnSecondaryDark = Color(0xFF3B2E22)
val WarmSecondaryContainerDark = Color(0xFF524437)
val WarmOnSecondaryContainerDark = Color(0xFFF0E4D8)

val WarmTertiaryDark = Color(0xFF9CD3C7)
val WarmOnTertiaryDark = Color(0xFF003731)
val WarmTertiaryContainerDark = Color(0xFF16544A)
val WarmOnTertiaryContainerDark = Color(0xFFB8E8DC)

val WarmBackgroundDark = Color(0xFF15120F)
val WarmOnBackgroundDark = Color(0xFFEDE4DA)
val WarmSurfaceDark = Color(0xFF1C1815)
val WarmOnSurfaceDark = Color(0xFFEDE4DA)
val WarmSurfaceVariantDark = Color(0xFF3A332C)
val WarmOnSurfaceVariantDark = Color(0xFFD3C5B7)
val WarmOutlineDark = Color(0xFF9C8B7B)
val WarmOutlineVariantDark = Color(0xFF4A4138)
val WarmErrorDark = Color(0xFFFFB4AB)
val WarmOnErrorDark = Color(0xFF690005)
val WarmSurfaceContainerDark = Color(0xFF231E19)
val WarmSurfaceContainerHighDark = Color(0xFF2E2822)

/** 事件卡片可选的强调色（索引持久化在 EventEntity.colorIndex） */
val EventPalette: List<Color> = listOf(
    Color(0xFFE0612E),
    Color(0xFFD14C67),
    Color(0xFF7357D6),
    Color(0xFF3B7DD8),
    Color(0xFF2C8F84),
    Color(0xFF5C8F45),
    Color(0xFFB9862B),
    Color(0xFF5A5A66)
)

fun eventColor(index: Int): Color = EventPalette[index.coerceIn(0, EventPalette.lastIndex)]

/** 卡片淡底色 */
fun eventTint(index: Int, dark: Boolean): Color =
    eventColor(index).copy(alpha = if (dark) 0.18f else 0.12f)
