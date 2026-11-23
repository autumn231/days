package com.example.countdowndays

import com.example.countdowndays.util.PrefsManager
import com.example.countdowndays.widget.HeroDiagnosticsSnapshot
import com.example.countdowndays.widget.buildHeroDiagnosticDump
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 组件诊断 dump 组装（纯函数）用例（v4.5.2 诊断版）。
 *
 * 诊断页的价值全在「字段齐、口径对」：用户真机上跑一遍复制回来的全文，
 * 必须能直接对照出配置侧 id、保存入参、存储全量、系统放置实例与最近
 * 一次渲染解析这五方的数据。这里钉死每节的出现条件与占位文案，尤其是
 * 「没有记录时明示暂无」——含糊其辞的空节会让下一轮取证白跑。
 */
class HeroDiagnosticsDumpTest {

    private fun baseSnapshot() = HeroDiagnosticsSnapshot(
        versionLabel = "4.5.2（versionCode 40）",
        generatedAtMillis = 1_700_000_000_000L,
        configureOpen = PrefsManager.HeroConfigureOpenTrace(
            appWidgetId = 7, atMillis = 1_700_000_000_000L
        ),
        saveTrace = PrefsManager.HeroSaveTrace(
            appWidgetId = 7,
            eventId = 100L,
            placedIds = listOf(42),
            canonicalWritten = true,
            canonicalNote = "placedIds 恰好 1 个，规范槽已覆盖写",
            prunedIds = setOf(5, 6),
            atMillis = 1_700_000_000_000L
        ),
        configs = mapOf(7 to 100L, 42 to 100L),
        canonicalEventId = 100L,
        canonicalAtMillis = 1_700_000_000_000L,
        placedIds = listOf(42),
        renderTrace = PrefsManager.HeroRenderTrace(
            text = "appWidgetId=42 primary=100 configs={7=100, 42=100} " +
                "canonical=100 placed=[42] configuredId=100 heroId=100 heroName=看海",
            atMillis = 1_700_000_000_000L
        ),
        eventNames = mapOf(100L to "看海", 200L to "喝咖啡")
    )

    @Test
    fun dump_fullSnapshot_containsAllSectionsAndValues() {
        val dump = buildHeroDiagnosticDump(baseSnapshot())
        assertTrue(dump.contains("版本：v4.5.2（versionCode 40）"))
        // 配置侧：打开留痕＋保存入参全字段
        assertTrue(dump.contains("最近打开配置页：appWidgetId=7"))
        assertTrue(dump.contains("appWidgetId=7"))
        assertTrue(dump.contains("事件 100（看海）"))
        assertTrue(dump.contains("placedIds=[42]"))
        assertTrue(dump.contains("规范槽：已覆盖写"))
        assertTrue(dump.contains("本次修剪残留键：[5, 6]"))
        // 存储全量：每条键带事件名，规范槽与残留总数在
        assertTrue(dump.contains("hero_widget_event_7 → 事件 100（看海）"))
        assertTrue(dump.contains("hero_widget_event_42 → 事件 100（看海）"))
        assertTrue(dump.contains("残留键总数：2"))
        // 系统侧与渲染侧
        assertTrue(dump.contains("[42]，共 1 个"))
        assertTrue(dump.contains("configuredId=100"))
        assertTrue(dump.contains("heroName=看海"))
    }

    @Test
    fun dump_noRenderRecord_showsPlaceholder() {
        val dump = buildHeroDiagnosticDump(baseSnapshot().copy(renderTrace = null))
        assertTrue(dump.contains("暂无渲染记录"))
    }

    @Test
    fun dump_noOpenAndSaveRecords_showsPlaceholders() {
        val dump = buildHeroDiagnosticDump(
            baseSnapshot().copy(configureOpen = null, saveTrace = null)
        )
        assertTrue(dump.contains("最近打开配置页：暂无记录"))
        assertTrue(dump.contains("最近一次保存：暂无记录"))
    }

    @Test
    fun dump_canonicalNotWritten_showsReasonBranch() {
        val save = PrefsManager.HeroSaveTrace(
            appWidgetId = 9,
            eventId = 200L,
            placedIds = emptyList(),
            canonicalWritten = false,
            canonicalNote = "placedIds.size=0，规范槽要求放置实例恰好 1 个，未写",
            prunedIds = emptySet(),
            atMillis = 1_700_000_000_000L
        )
        val dump = buildHeroDiagnosticDump(
            baseSnapshot().copy(saveTrace = save, canonicalEventId = null)
        )
        assertTrue(dump.contains("规范槽：未写"))
        assertTrue(dump.contains("placedIds.size=0"))
        assertTrue(dump.contains("规范槽：（空）"))
        assertTrue(dump.contains("本次修剪残留键：无"))
    }

    @Test
    fun dump_saveAutoSelection_labelledAsAuto() {
        val save = PrefsManager.HeroSaveTrace(
            appWidgetId = 7,
            eventId = null,
            placedIds = listOf(42),
            canonicalWritten = true,
            canonicalNote = "placedIds 恰好 1 个，规范槽已覆盖写",
            prunedIds = emptySet(),
            atMillis = 1_700_000_000_000L
        )
        val dump = buildHeroDiagnosticDump(baseSnapshot().copy(saveTrace = save))
        assertTrue(dump.contains("null（选了「自动」）"))
    }

    @Test
    fun dump_noConfigs_showsEmptyAndZeroResidual() {
        val dump = buildHeroDiagnosticDump(
            baseSnapshot().copy(configs = emptyMap(), canonicalEventId = null)
        )
        assertTrue(dump.contains("一条都没有"))
        assertTrue(dump.contains("残留键总数：0"))
        assertFalse(dump.contains("hero_widget_event_7"))
    }

    @Test
    fun dump_unknownEventId_notedAsNameMissing() {
        val dump = buildHeroDiagnosticDump(
            baseSnapshot().copy(configs = mapOf(3 to 999L))
        )
        assertTrue(dump.contains("hero_widget_event_3 → 事件 999（名字查不到，可能已删除）"))
    }
}
