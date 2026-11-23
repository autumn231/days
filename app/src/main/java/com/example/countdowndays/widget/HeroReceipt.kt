package com.example.countdowndays.widget

import com.example.countdowndays.util.PrefsManager

/** 回执比对结论（v4.5.3） */
enum class HeroReceiptVerdict { MET, NOT_MET, NO_RENDER_RECORD }

/**
 * 回执比对（纯函数，可单测）：保存时记下的期望是「[PrefsManager
 * .HeroRefreshExpectation.savedAtMillis] 之后出现过一次渲染、且那次
 * 渲染认到的选择等于期望值」。比对只用回执判断必需的最小状态
 * （期望的选择与时刻、最近渲染的选择与时刻），不依赖任何文本。
 *
 * - 没有任何渲染记录 → [HeroReceiptVerdict.NO_RENDER_RECORD]；
 * - 最近一次渲染早于保存时刻（保存后还没渲染过）、或它认到的选择
 *   与期望不符 → NOT_MET；
 * - 期望本身缺失（没保存过却跑来检查）→ NOT_MET：无法证明达标就按
 *   未达标处理，调用方会升级走广播通道再刷一次，宁多刷不错过。
 *
 * 期望为 null（选了「自动」）时，最近渲染认到的选择同为 null 才算达标。
 */
fun evaluateHeroReceipt(
    expectation: PrefsManager.HeroRefreshExpectation?,
    render: PrefsManager.HeroLastRender?
): HeroReceiptVerdict {
    if (render == null) return HeroReceiptVerdict.NO_RENDER_RECORD
    if (expectation == null) return HeroReceiptVerdict.NOT_MET
    if (render.atMillis < expectation.savedAtMillis) return HeroReceiptVerdict.NOT_MET
    return if (render.configuredId == expectation.expectedConfiguredId) {
        HeroReceiptVerdict.MET
    } else {
        HeroReceiptVerdict.NOT_MET
    }
}
