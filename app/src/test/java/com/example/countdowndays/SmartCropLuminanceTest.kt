package com.example.countdowndays

import com.example.countdowndays.util.SmartCrop
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * WCAG 相对亮度口径：展示页分区选字色与桌面组件（v4.0）共用同一实现，
 * 这里钉住极值、中间灰与字色交叉点（L=0.19）两侧的数值，防口径漂移。
 */
class SmartCropLuminanceTest {

    @Test
    fun blackIsZeroWhiteIsOne() {
        assertEquals(0f, SmartCrop.wcagLuminanceOf(0f, 0f, 0f), 1e-6f)
        assertEquals(1f, SmartCrop.wcagLuminanceOf(1f, 1f, 1f), 1e-6f)
    }

    @Test
    fun midGrayMatchesWcagFormula() {
        // sRGB 0.5 线性化 ≈ 0.2140
        assertEquals(0.2140f, SmartCrop.wcagLuminanceOf(0.5f, 0.5f, 0.5f), 0.001f)
    }

    @Test
    fun crossoverPointSplitsAsExpected() {
        // 交叉点 0.19：0.46 灰在其下（用浅字），0.5 灰在其上（用深字）
        assertTrue(SmartCrop.wcagLuminanceOf(0.46f, 0.46f, 0.46f) < 0.19f)
        assertTrue(SmartCrop.wcagLuminanceOf(0.5f, 0.5f, 0.5f) > 0.19f)
    }
}
