package com.embertimer.ui.report

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.embertimer.R
import com.embertimer.ui.morph.IconPaths
import com.embertimer.ui.morph.PathIcon
import androidx.compose.ui.res.stringResource

/** v1.9.11 周期选择器:M3 ExposedDropdownMenuBox + 卡片面板。候选 remember 缓存、输入轻量 filter
 * (不再每次重组重算 9 个周期对象),候选 LazyColumn 限高,消除卡顿与溢出;风格统一卡片/主题色。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PeriodPicker(
    range: ReportRange,
    anchor: java.time.LocalDate,
    canGoNext: Boolean,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onJump: (java.time.LocalDate) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    val label = periodLabel(range, anchor)

    // 候选缓存:anchor/range 变化才重算;输入 filter 只做轻量 contains(不重建周期对象)
    val allCandidates = remember(range, anchor) {
        val today = java.time.LocalDate.now()
        buildList {
            for (i in -6..2) {
                val d = if (range == ReportRange.WEEK) anchor.plusWeeks(i.toLong()) else anchor.plusMonths(i.toLong())
                if (!d.isAfter(today)) add(d)
            }
        }
    }
    val candidates = remember(allCandidates, query) {
        if (query.isBlank()) allCandidates
        else allCandidates.filter { periodLabel(range, it).contains(query) || it.toString().contains(query) }
    }

    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        TextButton(onClick = onPrev) { Text(stringResource(R.string.report_prev)) }
        ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
            // 锚点:标签(菜单挂靠点),menuAnchor 使面板锚定于此
            Row(
                Modifier
                    .menuAnchor()
                    .clickable { expanded = true }
                    .padding(horizontal = 10.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                PathIcon(
                    d = IconPaths.CHEVRON_DOWN,
                    size = 14.dp,
                    contentDescription = stringResource(R.string.report_pick_period),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 4.dp),
                )
            }
            // 下拉面板(scope 成员函数,不需全限定名)
            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
            ) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                ) {
                    androidx.compose.foundation.layout.Column(Modifier.padding(8.dp)) {
                        TextField(
                            value = query,
                            onValueChange = { query = it },
                            singleLine = true,
                            placeholder = { Text(stringResource(R.string.report_search_hint), style = MaterialTheme.typography.bodySmall) },
                            colors = TextFieldDefaults.colors(),
                            modifier = Modifier.fillMaxWidth(),
                        )
                        LazyColumn(Modifier.fillMaxWidth().heightIn(max = 220.dp)) {
                            items(candidates, key = { it.toEpochDay() }) { d ->
                                DropdownMenuItem(
                                    text = { Text(periodLabel(range, d), style = MaterialTheme.typography.bodyMedium) },
                                    onClick = { onJump(d); expanded = false },
                                )
                            }
                        }
                        if (candidates.isEmpty()) {
                            Text(
                                stringResource(R.string.report_search_empty),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(12.dp),
                            )
                        }
                    }
                }
            }
        }
        TextButton(onClick = onNext, enabled = canGoNext) { Text(stringResource(R.string.report_next)) }
    }
}

/** 报表窗口标签:v1.9.1 —— 周显示 MM-dd ~ MM-dd;月显示 yyyy-MM */
internal fun periodLabel(range: ReportRange, anchor: java.time.LocalDate): String {
    val (from, to) = reportWindow(range, anchor)
    return when (range) {
        ReportRange.WEEK -> "${from.substring(5)} ~ ${to.substring(5)}"
        ReportRange.MONTH -> from.substring(0, 7)
        ReportRange.LIFETIME -> ""
    }
}
