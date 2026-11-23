package com.example.countdowndays

import com.example.countdowndays.widget.HERO_MAX_BITMAP_BYTES
import com.example.countdowndays.widget.bitmapBytes
import com.example.countdowndays.widget.fitBitmapSize
import com.example.countdowndays.widget.heroCornerRadiusPx
import com.example.countdowndays.widget.heroDarkTextForLuminance
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 主视觉经典版（v4.5.4）的位图体积纪律与字色选取（纯函数）。
 *
 * 体积纪律来自诊断端 v1.4 的透明事故：880×440 的 ARGB 位图约 1.48MB，
 * 超 RemoteViews 约 1MB 的 Binder 事务上限被宿主整帧丢弃。主线推送的
 * 照片位图必须先按组件实际尺寸缩放、且字节数压在上限内。
 */
class HeroWidgetBitmapTest {

    @Test
    fun bitmapBytes_argb8888() {
        assertEquals(400, bitmapBytes(10, 10))
        // 诊断端事故尺寸：880×440 ≈ 1.48MB，远超上限——正是要防的
        assertTrue(bitmapBytes(880, 440) > HERO_MAX_BITMAP_BYTES)
    }

    @Test
    fun fitBitmapSize_withinLimits_unchanged() {
        assertEquals(400 to 200, fitBitmapSize(400, 200, 800, 400))
    }

    @Test
    fun fitBitmapSize_targetBox_scalesDown() {
        // 目标框 500×250：等比缩到框内
        assertEquals(500 to 250, fitBitmapSize(2000, 1000, 500, 250))
    }

    @Test
    fun fitBitmapSize_byteCap_scalesDown() {
        // 框够大但字节超限：按字节上限缩，结果必须达标
        val (w, h) = fitBitmapSize(4000, 2000, 4000, 2000)
        assertTrue(bitmapBytes(w, h) <= HERO_MAX_BITMAP_BYTES)
        // 等比：宽高比保持 2:1（允许取整误差）
        assertTrue(kotlin.math.abs(w - 2 * h) <= 2)
    }

    @Test
    fun fitBitmapSize_neverUpscales() {
        val (w, h) = fitBitmapSize(100, 50, 800, 800)
        assertEquals(100 to 50, w to h)
    }

    @Test
    fun fitBitmapSize_degenerateInput() {
        assertEquals(1 to 1, fitBitmapSize(0, 0, 100, 100))
    }

    @Test
    fun heroCornerRadiusPx_dpConversion() {
        // 22dp 口径：density=2 → 44px，空间充足时不封顶
        assertEquals(44f, heroCornerRadiusPx(800, 400, 2f), 0.01f)
        assertEquals(22f, heroCornerRadiusPx(800, 400, 1f), 0.01f)
    }

    @Test
    fun heroCornerRadiusPx_cappedAtHalfShortSide() {
        // 短边 60 → 半径封顶 30（即便 22dp×3=66 更大，圆角矩形也不退化）
        assertEquals(30f, heroCornerRadiusPx(100, 60, 3f), 0.01f)
    }

    @Test
    fun heroCornerRadiusPx_degenerate() {
        assertEquals(0f, heroCornerRadiusPx(0, 100, 2f), 0.01f)
        assertEquals(0f, heroCornerRadiusPx(100, 0, 2f), 0.01f)
        assertEquals(0f, heroCornerRadiusPx(100, 100, 0f), 0.01f)
    }

    @Test
    fun heroDarkTextForLuminance_crossover() {
        // 与展示页同一交叉点 0.19：亮底深字、暗底白字
        assertFalse(heroDarkTextForLuminance(0.10f))
        assertTrue(heroDarkTextForLuminance(0.50f))
        assertTrue(heroDarkTextForLuminance(0.19f))
        assertFalse(heroDarkTextForLuminance(0.18f))
    }
}
