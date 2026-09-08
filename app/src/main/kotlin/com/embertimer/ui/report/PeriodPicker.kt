package com.embertimer.ui.report

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.embertimer.R
import com.embertimer.ui.morph.IconPaths
import com.embertimer.ui.morph.PathIcon

/**
 * v1.9.12 周期选择器:自控 Popup + 卡片面板。
 *
 * 历史教训(v1.9.11):ExposedDropdownMenuBox 内部是 SubcomposeLayout,
 * ① 不支持 LazyColumn 的 intrinsic 测量(直接 IllegalStateException 崩溃);
 * ② 部分 OEM SystemUI 上锚点点击行为异常。
 * 故彻底弃用,改为 Popup(focusable 可捕获返回键关闭)+ Surface 卡片 +
 * Column+verticalScroll(候选仅 9 个,无需懒加载),完全自控无 intrinsic 问题。
 *
 * 性能:候选列表 remember(anchor/range) 缓存,输入 filter 仅轻量 contains。
 */
@Composable
internal fun PeriodPicker(
    range: ReportRange,
    anchor: java.time.LocalDate,
    canGoNext: Boolean,
    /** v1.9.13 #43:往期回顾下限(首次打开日);候选不早于此 */
    minDate: java.time.LocalDate?,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onJump: (java.time.LocalDate) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val label = periodLabel(range, anchor)

    // v1.9.13 #43:候选从首次打开日起(回顾起点),按周/月分段直到今日;可滚动。
    // anchor/range/minDate 变化才重算;无首次打开日时回退最近 9 期。
    val candidates = remember(range, anchor, minDate) {
        val today = java.time.LocalDate.now()
        if (minDate != null) {
            buildList {
                var d = when (range) {
                    ReportRange.WEEK -> minDate.minusDays((minDate.dayOfWeek.value - 1).toLong())
                    ReportRange.MONTH -> minDate.withDayOfMonth(1)
                    ReportRange.LIFETIME -> today
                }
                while (!d.isAfter(today)) {
                    add(d)
                    d = when (range) {
                        ReportRange.WEEK -> d.plusWeeks(1)
                        ReportRange.MONTH -> d.plusMonths(1)
                        ReportRange.LIFETIME -> today
                    }
                }
            }
        } else {
            buildList {
                for (i in -6..2) {
                    val d = if (range == ReportRange.WEEK) anchor.plusWeeks(i.toLong()) else anchor.plusMonths(i.toLong())
                    if (!d.isAfter(today)) add(d)
                }
            }
        }
    }

    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        TextButton(onClick = onPrev) { Text(stringResource(R.string.report_prev)) }
        // 锚点:标签行(点击弹出自控 Popup 面板)
        Box {
            Row(
                Modifier
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
            if (expanded) {
                Popup(
                    alignment = Alignment.TopCenter,
                    onDismissRequest = { expanded = false },
                    properties = PopupProperties(focusable = true, dismissOnClickOutside = true),
                ) {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        shadowElevation = 6.dp,
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        modifier = Modifier.widthIn(min = 300.dp, max = 460.dp),
                    ) {
                        Column(Modifier.padding(6.dp)) {
                            Column(
                                Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 280.dp)
                                    .verticalScroll(rememberScrollState()),
                            ) {
                                candidates.forEach { d ->
                                    Row(
                                        Modifier
                                            .fillMaxWidth()
                                            .clickable { onJump(d); expanded = false }
                                            .padding(horizontal = 14.dp, vertical = 13.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Text(periodLabel(range, d), style = MaterialTheme.typography.bodyMedium)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        TextButton(onClick = onNext, enabled = canGoNext) { Text(stringResource(R.string.report_next)) }
    }
}

/** 报表窗口标签:周显示 MM-dd ~ MM-dd;月显示 yyyy-MM */
internal fun periodLabel(range: ReportRange, anchor: java.time.LocalDate): String {
    val (from, to) = reportWindow(range, anchor)
    return when (range) {
        ReportRange.WEEK -> "${from.substring(5)} ~ ${to.substring(5)}"
        ReportRange.MONTH -> from.substring(0, 7)
        ReportRange.LIFETIME -> ""
    }
}
