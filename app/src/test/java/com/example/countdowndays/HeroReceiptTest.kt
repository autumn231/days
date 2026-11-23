package com.example.countdowndays

import com.example.countdowndays.util.PrefsManager
import com.example.countdowndays.widget.HeroReceiptVerdict
import com.example.countdowndays.widget.evaluateHeroReceipt
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 渲染回执比对（纯函数）用例（v4.5.3）。
 *
 * 回执是「保存 → 是否真的重渲染」这条链上的自检：保存时记期望
 * （时刻＋应解析出的选择），+7s 拿最近一次渲染的最小记录（认到的
 * 选择＋时刻）比对。这里钉死三种结论的边界：达标（含选「自动」时
 * 两边同为 null）、未达标（渲染还停在保存之前、或认到的选择不对）、
 * 无渲染记录。
 */
class HeroReceiptTest {

    @Test
    fun receipt_renderAfterSaveWithExpectedId_met() {
        val expectation = PrefsManager.HeroRefreshExpectation(
            savedAtMillis = 1_000L, expectedConfiguredId = 12L
        )
        val render = PrefsManager.HeroLastRender(configuredId = 12L, atMillis = 2_000L)
        assertEquals(HeroReceiptVerdict.MET, evaluateHeroReceipt(expectation, render))
    }

    @Test
    fun receipt_autoSelection_renderNull_met() {
        // 选了「自动」：期望 null，保存后的渲染认到的选择同为 null → 达标
        val expectation = PrefsManager.HeroRefreshExpectation(
            savedAtMillis = 1_000L, expectedConfiguredId = null
        )
        val render = PrefsManager.HeroLastRender(configuredId = null, atMillis = 2_000L)
        assertEquals(HeroReceiptVerdict.MET, evaluateHeroReceipt(expectation, render))
    }

    @Test
    fun receipt_renderBeforeSave_notMet() {
        // 最近一次渲染还停在保存之前（保存后压根没重渲染）→ 未达标
        val expectation = PrefsManager.HeroRefreshExpectation(
            savedAtMillis = 5_000L, expectedConfiguredId = 12L
        )
        val render = PrefsManager.HeroLastRender(configuredId = 12L, atMillis = 2_000L)
        assertEquals(HeroReceiptVerdict.NOT_MET, evaluateHeroReceipt(expectation, render))
    }

    @Test
    fun receipt_renderWithDifferentId_notMet() {
        // 保存后渲染过，但认到的选择不是期望的那个 → 未达标
        val expectation = PrefsManager.HeroRefreshExpectation(
            savedAtMillis = 1_000L, expectedConfiguredId = 12L
        )
        val render = PrefsManager.HeroLastRender(configuredId = 13L, atMillis = 2_000L)
        assertEquals(HeroReceiptVerdict.NOT_MET, evaluateHeroReceipt(expectation, render))
    }

    @Test
    fun receipt_noRenderRecord_noRecord() {
        val expectation = PrefsManager.HeroRefreshExpectation(
            savedAtMillis = 1_000L, expectedConfiguredId = 12L
        )
        assertEquals(
            HeroReceiptVerdict.NO_RENDER_RECORD,
            evaluateHeroReceipt(expectation, null)
        )
    }
}
