package com.embertimer.ui.heatmap

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 2026-09-07 是周一;2026-09-01 是周二;2026-09-09 是周三 */
class HeatmapModelTest {
    private val today = LocalDate.of(2026, 9, 9)

    @Test fun emptyDataYieldsCurrentWeekUpToToday() {
        val m = buildHeatmapModel(emptyMap(), today)
        assertEquals(1, m.columns.size)
        assertEquals(3, m.columns[0].cells.size) // 周一..周三
    }

    @Test fun unusedDaysAreCellsWithNoneNotMissing() {
        val m = buildHeatmapModel(mapOf(today to 3_600_000L), today)
        val cells = m.columns[0].cells
        assertEquals(3, cells.size)
        assertEquals(HeatLevel.NONE, cells[0].level)
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

    @Test fun dayCellHasNoJoinFlags() {
        val d = LocalDate.of(2026, 9, 1)
        val m = buildHeatmapModel(mapOf(d to 3_600_000L), today)
        val c = m.columns.flatMap { it.cells }.first { it.date == d }
        assertEquals(HeatLevel.L2, c.level)
        // join 字段已随直角渲染移除(编译期保证:DayCell 仅 date/millis/level)
        assertEquals(d, c.date)
        assertEquals(3_600_000L, c.millis)
    }

    @Test fun everyColumnLabeledWithItsWeekStartMonth() {
        // 跨月数据:9/1 所在周周一是 8/31(8 月),9/8 所在周周一是 9/7(Sep)
        // 列标签 = 列 weekStart 的月份缩写,故首列为 Aug、次列为 Sep
        val d1 = LocalDate.of(2026, 9, 1)
        val d2 = LocalDate.of(2026, 9, 8)
        val m = buildHeatmapModel(mapOf(d1 to 60_000L, d2 to 60_000L), today)
        assertTrue(m.columns.size >= 2)
        assertEquals("Aug", m.monthLabels[0])
        assertEquals("Sep", m.monthLabels[1])
    }

    @Test fun monthLabelCrossesYearBoundaryEveryColumn() {
        val m = buildHeatmapModel(
            mapOf(LocalDate.of(2026, 12, 20) to 60_000L),
            LocalDate.of(2027, 1, 6),
        )
        // weekStarts: 12/14(Dec), 12/21(Dec), 12/28(Dec), 1/4(Jan) -> 每列都标
        assertEquals("Dec", m.monthLabels[0])
        assertEquals("Jan", m.monthLabels[m.columns.lastIndex])
    }
}
