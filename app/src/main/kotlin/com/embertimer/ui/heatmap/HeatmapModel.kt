package com.embertimer.ui.heatmap

import java.time.DayOfWeek
import java.time.LocalDate

data class DayValue(val date: LocalDate, val millis: Long)

enum class HeatLevel { NONE, L1, L2, L3, L4 }

data class HeatmapModel(val columns: List<List<DayValue?>>, val monthStarts: Map<Int, String>)

object HeatmapLevels {
    fun of(millis: Long): HeatLevel = when {
        millis <= 0 -> HeatLevel.NONE
        millis < 30 * 60_000L -> HeatLevel.L1
        millis < 2 * 3_600_000L -> HeatLevel.L2
        millis < 4 * 3_600_000L -> HeatLevel.L3
        else -> HeatLevel.L4
    }
}

fun buildHeatmapModel(days: Map<LocalDate, Long>, today: LocalDate, weeks: Int = 53): HeatmapModel {
    val start = today.minusWeeks((weeks - 1).toLong()).with(DayOfWeek.MONDAY)
    val columns = mutableListOf<List<DayValue?>>()
    val monthStarts = mutableMapOf<Int, String>()
    var cursor = start
    var lastMonth = -1
    var col = 0
    while (!cursor.isAfter(today)) {
        val week = (0..6).map { dowIdx ->
            val d = cursor.plusDays(dowIdx.toLong())
            if (d.isAfter(today)) null else DayValue(d, days[d] ?: 0L)
        }
        columns += week
        val firstCell = week.firstOrNull { it != null }?.date
        if (firstCell != null && firstCell.monthValue != lastMonth) {
            if (col > 0) monthStarts[col] = firstCell.monthValue.toString() + "月"
            lastMonth = firstCell.monthValue
        }
        cursor = cursor.plusWeeks(1)
        col++
    }
    if (columns.isEmpty()) columns += (0..6).map { null }
    return HeatmapModel(columns, monthStarts)
}
