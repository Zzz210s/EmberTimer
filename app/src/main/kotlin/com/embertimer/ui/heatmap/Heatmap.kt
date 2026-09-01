package com.embertimer.ui.heatmap

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.first
import java.time.LocalDate

private val CELL = 13.dp
private val GAP = 2.dp
private val COL_STEP = CELL + GAP

@Composable
fun levelColor(level: HeatLevel): androidx.compose.ui.graphics.Color {
    val p = MaterialTheme.colorScheme.primary
    return when (level) {
        HeatLevel.NONE -> MaterialTheme.colorScheme.surfaceVariant
        HeatLevel.L1 -> p.copy(alpha = 0.3f)
        HeatLevel.L2 -> p.copy(alpha = 0.5f)
        HeatLevel.L3 -> p.copy(alpha = 0.75f)
        HeatLevel.L4 -> p
    }
}

@Composable
fun Heatmap(model: HeatmapModel, selected: LocalDate?, onSelect: (LocalDate?) -> Unit) {
    val scroll = rememberScrollState()
    // 默认滚到最新。maxValue 在首次布局前为 0,需等布局后再滚动
    LaunchedEffect(Unit) {
        snapshotFlow { scroll.maxValue }.first { it > 0 }
        scroll.scrollTo(scroll.maxValue)
    }
    val labelStyle = MaterialTheme.typography.labelSmall
    Column {
        // 月份标签行
        Row(Modifier.horizontalScroll(scroll, enabled = false)) {
            model.columns.forEachIndexed { i, _ ->
                Box(Modifier.width(COL_STEP).height(14.dp)) {
                    model.monthStarts[i]?.let {
                        Text(it, style = labelStyle, modifier = Modifier.padding(start = 2.dp))
                    }
                }
            }
        }
        Row {
            // 周标签(固定)
            Column(
                Modifier.width(18.dp).padding(top = 2.dp),
                verticalArrangement = Arrangement.spacedBy(GAP),
            ) {
                listOf("", "一", "", "三", "", "五", "").forEach { l ->
                    Box(Modifier.size(CELL)) {
                        if (l.isNotEmpty()) Text(l, style = labelStyle)
                    }
                }
            }
            Row(Modifier.horizontalScroll(scroll).fillMaxWidth()) {
                model.columns.forEachIndexed { ci, week ->
                    Column(
                        Modifier.padding(end = GAP),
                        verticalArrangement = Arrangement.spacedBy(GAP),
                    ) {
                        week.forEach { cell ->
                            val sel = cell?.date == selected
                            Box(
                                Modifier
                                    .size(CELL)
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(levelColor(cell?.let { HeatmapLevels.of(it.millis) } ?: HeatLevel.NONE))
                                    .let { m ->
                                        if (cell == null) m
                                        else m.clickable { onSelect(if (sel) null else cell.date) }
                                    }
                                    .let { m -> if (sel) m.padding(2.dp) else m },
                            )
                        }
                    }
                }
            }
        }
    }
}
