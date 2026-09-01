package com.embertimer.ui.heatmap

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class HeatmapModelTest {
    // 2026-08-31 是周一
    private val today = LocalDate.of(2026, 8, 31)

    @Test fun singleWeekStartsMonday() {
        val m = buildHeatmapModel(emptyMap(), today, weeks = 1)
        assertEquals(1, m.columns.size)
        assertEquals(7, m.columns[0].size)
        assertEquals(LocalDate.of(2026, 8, 31), m.columns[0][0]!!.date) // 周一
        assertNull(m.columns[0][6]) // 未来日期为空
    }

    @Test fun fullDataKeepsAllCells() {
        val m = buildHeatmapModel(emptyMap(), today, weeks = 5)
        val first = m.columns.first()[0]!!.date // 第一列周一
        assertEquals(today.minusWeeks(4), first)
        val count = m.columns.sumOf { c -> c.count { it != null } }
        assertEquals(29, count) // 8/3..8/31 共 29 天,过去日期即使无数据也有格子
        assertEquals(0L, m.columns.first()[0]!!.millis) // 过去日期无数据 -> millis 0(未来日期才是 null)
    }

    @Test fun valuesCarriedThrough() {
        val d = today.minusDays(1)
        val m = buildHeatmapModel(mapOf(d to 3_600_000L), today, weeks = 2)
        assertEquals(3_600_000L, m.columns[0][6]!!.millis)
    }

    @Test fun levels() {
        assertEquals(HeatLevel.NONE, HeatmapLevels.of(0))
        assertEquals(HeatLevel.L1, HeatmapLevels.of(29 * 60_000L))
        assertEquals(HeatLevel.L2, HeatmapLevels.of(30 * 60_000L))
        assertEquals(HeatLevel.L3, HeatmapLevels.of(2 * 3_600_000L))
        assertEquals(HeatLevel.L4, HeatmapLevels.of(4 * 3_600_000L))
    }

    @Test fun monthStartMarksNewMonth() {
        // 6 周窗口(2026-07-27 周一起)覆盖 7 月底 -> 8 月,8 月首列应标记
        val m = buildHeatmapModel(emptyMap(), today, weeks = 6)
        val marks = m.monthStarts
        org.junit.Assert.assertTrue(marks.isNotEmpty())
        marks.forEach { (col, label) ->
            val cell = m.columns[col].firstOrNull { it != null }!!.date
            assertEquals(cell.monthValue.toString() + "月", label)
        }
    }

    @Test fun monthStartsNeverMarksFirstColumn() {
        // weeks=6:col 0 是 7 月周(2026-07-27 起),col 1 是 8 月周(2026-08-03 起);首列永不标记
        val m = buildHeatmapModel(emptyMap(), today, weeks = 6)
        assertFalse(m.monthStarts.containsKey(0))
        assertEquals(setOf(1), m.monthStarts.keys)
    }

    @Test fun defaultWeeksIs53() {
        // Task 13 依赖默认 weeks=53(HomeScreen 调用不传 weeks)
        assertEquals(53, buildHeatmapModel(emptyMap(), today).columns.size)
    }
}
