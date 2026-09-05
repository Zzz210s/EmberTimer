package com.embertimer.ui.heatmap

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.Month

data class DayCell(
    val date: LocalDate,
    val millis: Long,
    val level: HeatLevel,
)

data class WeekColumn(val weekStart: LocalDate, val cells: List<DayCell>)

data class HeatmapModel(
    val columns: List<WeekColumn>,
    val monthLabels: Map<Int, String>,
    val weekLabels: List<String> = listOf("1", "", "3", "", "5", "", ""),
)

enum class HeatLevel { NONE, L1, L2, L3, L4 }

object HeatmapLevels {
    fun of(millis: Long): HeatLevel = when {
        millis <= 0 -> HeatLevel.NONE
        millis < 30 * 60_000L -> HeatLevel.L1
        millis < 2 * 3_600_000L -> HeatLevel.L2
        millis < 4 * 3_600_000L -> HeatLevel.L3
        else -> HeatLevel.L4
    }
}

/** D4:月份标签固定英文缩写,与系统语言无关 */
private val MONTH_ABBREVIATIONS = mapOf(
    Month.JANUARY to "Jan", Month.FEBRUARY to "Feb", Month.MARCH to "Mar",
    Month.APRIL to "Apr", Month.MAY to "May", Month.JUNE to "Jun",
    Month.JULY to "Jul", Month.AUGUST to "Aug", Month.SEPTEMBER to "Sep",
    Month.OCTOBER to "Oct", Month.NOVEMBER to "Nov", Month.DECEMBER to "Dec",
)

/** 全历史构建:窗口从最早数据所在周的周一到 today 所在周;首记录前的日期不产出格子(纯空白) */
fun buildHeatmapModel(days: Map<LocalDate, Long>, today: LocalDate): HeatmapModel {
    val firstDataDate = days.keys.minOrNull() ?: today
    val first = firstDataDate.with(DayOfWeek.MONDAY)
    val weekStarts = generateSequence(first) { it.plusWeeks(1) }
        .takeWhile { !it.isAfter(today) }
        .toList()
        .ifEmpty { listOf(first) }

    // 未来日剔除;首记录前剔除(GitHub 式空白,非 NONE 格)
    fun cellOf(d: LocalDate): DayCell? = when {
        d.isAfter(today) -> null
        d.isBefore(firstDataDate) -> null
        else -> DayCell(d, days[d] ?: 0L, HeatmapLevels.of(days[d] ?: 0L))
    }

    val columns = weekStarts.map { ws ->
        WeekColumn(ws, (0..6).mapNotNull { dowIdx -> cellOf(ws.plusDays(dowIdx.toLong())) })
    }

    // D4(v1.1):GitHub 式月份标签——仅跨月列标注(与前一列 weekStart 不同月),首列不标
    val monthLabels = weekStarts.mapIndexedNotNull { i, ws ->
        if (i > 0 && ws.month != weekStarts[i - 1].month) {
            i to MONTH_ABBREVIATIONS.getValue(ws.month)
        } else null
    }.toMap()
    return HeatmapModel(columns, monthLabels)
}
