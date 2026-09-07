package com.embertimer.ui.report

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.embertimer.R
import com.embertimer.ui.morph.IconPaths
import com.embertimer.ui.morph.PathIcon

/** v1.9.8 周期选择器:中间标签点击弹 DropdownMenu —— 搜索栏(yyyy-MM-dd / yyyy-MM)+ 候选周期列表 */
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
    Row(Modifier.fillMaxWidth(), horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        TextButton(onClick = onPrev) { Text(stringResource(R.string.report_prev)) }
        Box {
            Row(
                Modifier
                    .clip(MaterialTheme.shapes.small)
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
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    placeholder = { Text(stringResource(R.string.report_search_hint), style = MaterialTheme.typography.bodySmall) },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp).widthIn(min = 220.dp),
                )
                // 候选:以 anchor 为中心,前 6 ~ 后 2 个周期
                val today = java.time.LocalDate.now()
                val candidates = buildList {
                    for (i in -6..2) {
                        val d = if (range == ReportRange.WEEK) anchor.plusWeeks(i.toLong()) else anchor.plusMonths(i.toLong())
                        if (!d.isAfter(today)) add(d)
                    }
                }.filter { d ->
                    query.isBlank() || periodLabel(range, d).contains(query) || d.toString().contains(query)
                }
                candidates.forEach { d ->
                    DropdownMenuItem(
                        text = { Text(periodLabel(range, d), style = MaterialTheme.typography.bodyMedium) },
                        onClick = { onJump(d); expanded = false },
                    )
                }
                if (candidates.isEmpty()) {
                    Text(
                        stringResource(R.string.report_search_empty),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(12.dp),
                    )
                }
                HorizontalDivider(Modifier.padding(vertical = 4.dp))
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.report_next), style = MaterialTheme.typography.bodyMedium) },
                    enabled = canGoNext,
                    onClick = { onNext(); expanded = false },
                )
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
