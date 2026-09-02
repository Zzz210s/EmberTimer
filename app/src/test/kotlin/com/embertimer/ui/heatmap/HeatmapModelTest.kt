package com.embertimer.ui.heatmap

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 2026-09-07 是周一;2026-09-01 是周二 */
class HeatmapModelTest {
    private val today = LocalDate.of(2026, 9, 9) // 周三

    @Test fun emptyDataYieldsCurrentWeekUpToToday() {
        val m = buildHeatmapModel(emptyMap(), today)
        assertEquals(1, m.columns.size)
        assertEquals(3, m.columns[0].cells.size) // 周一..周三
        assertEquals(listOf("1", "", "3", "", "5", "", ""), m.weekLabels)
    }

    @Test fun unusedDaysAreCellsWithNoneNotMissing() {
        val m = buildHeatmapModel(mapOf(today to 3_600_000L), today)
        val cells = m.columns[0].cells
        assertEquals(3, cells.size)
        assertEquals(HeatLevel.NONE, cells[0].level) // 周一无数据,仍渲染
        assertEquals(0L, cells[0].millis)
        assertEquals(HeatLevel.L2, cells[2].level)
    }

    @Test fun levelsThresholdsUnchanged() {
        assertEquals(HeatLevel.NONE, HeatmapLevels.of(0))
        assertEquals(HeatLevel.L1, HeatmapLevels.of(29 * 60_000L))
        assertEquals(HeatLevel.L2, HeatmapLevels.of(30 * 60_000L))
        assertEquals(HeatLevel.L3, HeatmapLevels.of(2 * 3_600_000L))
        assertEquals(HeatLevel.L4, HeatmapLevels.of(4 * 3_600_000L))
    }

    @Test fun monthLabelsAreEnglishAbbreviations() {
        val d = LocalDate.of(2026, 8, 15) // 有数据的最早日,所在周周一是 8/10
        val m = buildHeatmapModel(mapOf(d to 60_000L), today)
        val idx = m.columns.indexOfFirst { it.weekStart == LocalDate.of(2026, 8, 31) }
        assertTrue(idx > 0)
        assertEquals(mapOf(idx to "Sep"), m.monthLabels) // 首列(8月)不标;跨月首列标 Sep
    }

    @Test fun monthLabelCrossesYearBoundary() {
        val m = buildHeatmapModel(
            mapOf(LocalDate.of(2026, 12, 20) to 60_000L),
            LocalDate.of(2027, 1, 6), // 周三
        )
        assertTrue(m.monthLabels.values.contains("Jan"))
        // weekStarts: 12/14, 12/21, 12/28, 1/4 -> 1/1 落在第 3 列(col 2);首列不标,Dec 全程不标
        assertEquals(mapOf(2 to "Jan"), m.monthLabels)
    }

    @Test fun joinFlagsFollowSameLevelContiguity() {
        // 周二 9/1、周三 9/2、下周二 9/8 同为 1h(L2);周四 9/3 无数据
        val days = mapOf(
            LocalDate.of(2026, 9, 1) to 3_600_000L,
            LocalDate.of(2026, 9, 2) to 3_600_000L,
            LocalDate.of(2026, 9, 8) to 3_600_000L,
        )
        val m = buildHeatmapModel(days, today)
        fun cell(d: LocalDate) = m.columns.flatMap { it.cells }.first { it.date == d }
        val tue = cell(LocalDate.of(2026, 9, 1))
        assertEquals(HeatLevel.L2, tue.level)
        assertEquals(false, tue.joinTop)   // 周一 NONE
        assertEquals(true, tue.joinBottom) // 周三 L2
        assertEquals(false, tue.joinStart) // 上周二 8/25 早于首列(8/31)且无数据 NONE -> 不融合
        val wed = cell(LocalDate.of(2026, 9, 2))
        assertEquals(false, wed.joinBottom) // 周四 NONE
        val nextTue = cell(LocalDate.of(2026, 9, 8))
        assertEquals(true, nextTue.joinStart) // 9/1 同级
    }

    @Test fun firstHistoryColumnNeverJoinsStart() {
        val m = buildHeatmapModel(mapOf(LocalDate.of(2026, 8, 31) to 0L, today to 0L), today)
        // 8/31 是数据最早周的周一(实际首列为 8/31 所在周):其左侧越界不得融合
        val first = m.columns.first().cells.first()
        assertEquals(false, first.joinStart)
    }
}
