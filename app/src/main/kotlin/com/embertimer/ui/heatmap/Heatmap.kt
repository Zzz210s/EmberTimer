package com.embertimer.ui.heatmap

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyHorizontalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.embertimer.timer.DurationFormat
import java.time.LocalDate

private val CELL = 20.dp
private val GAP = 2.dp
private val FUSED_CORNER = 2.dp
private val CORNER = 5.dp
private val LEVEL_ALPHA = listOf(0.35f, 0.55f, 0.75f, 1f)

@Composable
fun levelColor(level: HeatLevel): Color {
    val primary = MaterialTheme.colorScheme.primary
    return when (level) {
        HeatLevel.NONE -> MaterialTheme.colorScheme.surfaceVariant
        HeatLevel.L1 -> primary.copy(alpha = LEVEL_ALPHA[0])
        HeatLevel.L2 -> primary.copy(alpha = LEVEL_ALPHA[1])
        HeatLevel.L3 -> primary.copy(alpha = LEVEL_ALPHA[2])
        HeatLevel.L4 -> primary.copy(alpha = LEVEL_ALPHA[3])
    }
}

@Composable
fun Heatmap(model: HeatmapModel, selected: LocalDate?, onSelect: (LocalDate?) -> Unit) {
    // 默认落在最后一列(最新一周);数据有限,不用无限索引技巧
    val state = rememberLazyGridState(
        initialFirstVisibleItemIndex = ((model.columns.size - 1) * 7).coerceAtLeast(0),
    )
    Column {
        MonthLabels(model, state)
        Row {
            Column(verticalArrangement = Arrangement.spacedBy(GAP)) {
                model.weekLabels.forEach { label ->
                    Box(Modifier.size(CELL), contentAlignment = Alignment.Center) {
                        if (label.isNotEmpty()) Text(label, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
            LazyHorizontalGrid(
                state = state,
                rows = GridCells.Fixed(7),
                modifier = Modifier
                    .height(CELL * 7 + GAP * 6)
                    .clip(MaterialTheme.shapes.small),
                horizontalArrangement = Arrangement.spacedBy(GAP),
                verticalArrangement = Arrangement.spacedBy(GAP),
            ) {
                items(model.columns.flatMap { it.cells }, key = { it.date.toEpochDay() }) { cell ->
                    HeatmapCell(
                        cell = cell,
                        isSelected = cell.date == selected,
                        onClick = { onSelect(if (cell.date == selected) null else cell.date) },
                    )
                }
            }
        }
        Legend()
    }
}

@Composable
private fun HeatmapCell(cell: DayCell, isSelected: Boolean, onClick: () -> Unit) {
    val color by animateColorAsState(levelColor(cell.level), label = "cellColor")
    val shape = if (isSelected) CircleShape else RoundedCornerShape(
        topStart = if (cell.joinTop || cell.joinStart) FUSED_CORNER else CORNER,
        topEnd = if (cell.joinTop || cell.joinEnd) FUSED_CORNER else CORNER,
        bottomStart = if (cell.joinBottom || cell.joinStart) FUSED_CORNER else CORNER,
        bottomEnd = if (cell.joinBottom || cell.joinEnd) FUSED_CORNER else CORNER,
    )
    val desc = if (cell.millis > 0) "${cell.date}, ${DurationFormat.hm(cell.millis)}"
    else "${cell.date}, 无记录"
    Box(
        Modifier
            .size(CELL)
            .clip(shape)
            .background(color)
            .clickable(onClick = onClick, onClickLabel = desc)
            .let { m -> if (isSelected) m.border(1.dp, MaterialTheme.colorScheme.onSurface, CircleShape) else m }
            .semantics {
                contentDescription = desc
                this.selected = isSelected
            }
            .testTag("heatmap_cell_${cell.date.toEpochDay()}"),
    )
}

/** 月份标签行:读取网格 layoutInfo,把可见列首 item 的 x 偏移换算为标签位置(D1 英文缩写) */
@Composable
private fun MonthLabels(model: HeatmapModel, state: LazyGridState) {
    val density = LocalDensity.current
    Box(Modifier.fillMaxWidth().height(16.dp)) {
        state.layoutInfo.visibleItemsInfo.forEach { item ->
            if (item.index % 7 == 0) {
                val col = item.index / 7
                model.monthLabels[col]?.let { label ->
                    Text(
                        label,
                        style = MaterialTheme.typography.labelSmall,
                        // item.offset.x 相对网格视口;网格位于周标签列(宽 CELL)右侧,而本叠加层从父级左缘起算,需补 CELL
                        modifier = Modifier.offset(x = CELL + with(density) { item.offset.x.toDp() }),
                    )
                }
            }
        }
    }
}

@Composable
private fun Legend() {
    Row(
        Modifier.fillMaxWidth().padding(top = 4.dp),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("少", style = MaterialTheme.typography.labelSmall)
        Spacer(Modifier.width(4.dp))
        HeatLevel.entries.forEach { level ->
            Box(Modifier.size(12.dp).clip(RoundedCornerShape(3.dp)).background(levelColor(level)))
            Spacer(Modifier.width(3.dp))
        }
        Text("多", style = MaterialTheme.typography.labelSmall)
    }
}
