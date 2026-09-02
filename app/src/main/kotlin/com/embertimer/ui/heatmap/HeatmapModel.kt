package com.embertimer.ui.heatmap

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.Month

data class DayCell(
    val date: LocalDate,
    val millis: Long,
    val level: HeatLevel,
    val joinTop: Boolean,
    val joinBottom: Boolean,
    val joinStart: Boolean,
    val joinEnd: Boolean,
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

/** D1:月份标签固定英文缩写,与系统语言无关 */
private val MONTH_ABBREVIATIONS = mapOf(
    Month.JANUARY to "Jan", Month.FEBRUARY to "Feb", Month.MARCH to "Mar",
    Month.APRIL to "Apr", Month.MAY to "May", Month.JUNE to "Jun",
    Month.JULY to "Jul", Month.AUGUST to "Aug", Month.SEPTEMBER to "Sep",
    Month.OCTOBER to "Oct", Month.NOVEMBER to "Nov", Month.DECEMBER to "Dec",
)

/** 全历史构建:窗口从最早数据所在周的周一到 today 所在周;未使用日为 0 值格子(不消失) */
fun buildHeatmapModel(days: Map<LocalDate, Long>, today: LocalDate): HeatmapModel {
    val first = (days.keys.minOrNull() ?: today).with(DayOfWeek.MONDAY)
    val weekStarts = generateSequence(first) { it.plusWeeks(1) }
        .takeWhile { !it.isAfter(today) }
        .toList()
        .ifEmpty { listOf(first) }

    fun levelOf(d: LocalDate): HeatLevel? =
        if (d.isAfter(today)) null else HeatmapLevels.of(days[d] ?: 0L)

    val columns = weekStarts.map { ws ->
        WeekColumn(ws, (0..6).mapNotNull { dowIdx ->
            val d = ws.plusDays(dowIdx.toLong())
            val level = levelOf(d) ?: return@mapNotNull null
            val joinTop = dowIdx > 0 && levelOf(d.minusDays(1)) == level
            val joinBottom = dowIdx < 6 && levelOf(d.plusDays(1)) == level
            // 左侧越界(早于首列周一)视为不融合
            val joinStart = !d.minusWeeks(1).isBefore(first) && levelOf(d.minusWeeks(1)) == level
            val joinEnd = levelOf(d.plusWeeks(1)) == level
            DayCell(d, days[d] ?: 0L, level, joinTop, joinBottom, joinStart, joinEnd)
        })
    }

    // 标签落在首个包含新月日期的列(跨月周,如 8/31 周含 9/1 -> 标 Sep),首列不标;
    // 同一周最多跨两个月,逐日扫描即可
    val monthLabels = mutableMapOf<Int, String>()
    var lastMonth: Month? = null
    weekStarts.forEachIndexed { i, ws ->
        (0..6).forEach { dowIdx ->
            val d = ws.plusDays(dowIdx.toLong())
            if (!d.isAfter(today) && d.month != lastMonth) {
                if (i > 0) monthLabels[i] = MONTH_ABBREVIATIONS.getValue(d.month)
                lastMonth = d.month
            }
        }
    }
    return HeatmapModel(columns, monthLabels)
}
