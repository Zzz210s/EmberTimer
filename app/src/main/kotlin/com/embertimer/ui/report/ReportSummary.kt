package com.embertimer.ui.report

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.embertimer.R

/**
 * 健康风报表可视化(v1.7 重构):摘要 Hero 卡 + 指标格 + 时段分布条 + 通用「标签/条形/数值」行。
 * 参考健康与屏幕时间报表的多级信息层级(总览→关键指标→趋势→时段)。
 */
@Composable
fun ReportSummary(
    metrics: ReportMetrics,
    slots: List<SlotMinutes>,
    totalMillis: Long,
    isMonth: Boolean,
    showAvg: Boolean = true,
) {
    val card = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    Card(colors = card) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            // 摘要 Hero
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    localizedDur(totalMillis),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.weight(1f),
                )
                metrics.prevDeltaPercent?.let { d ->
                    val color = when {
                        d > 0 -> MaterialTheme.colorScheme.primary
                        d < 0 -> MaterialTheme.colorScheme.error
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    }
                    val sign = when { d > 0 -> "+"; d < 0 -> "-"; else -> "" }
                    Text(
                        stringResource(if (isMonth) R.string.vs_last_month else R.string.vs_last_week) +
                            " $sign${kotlin.math.abs(d)}%",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = color,
                    )
                }
            }
            // 指标行(专注天数/连续/日均/最佳)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MetricTile(caption = stringResource(R.string.m_focus_days), value = "${metrics.focusDays}", mod = Modifier.weight(1f))
                MetricTile(caption = stringResource(R.string.m_streak), value = "${metrics.streakDays}", mod = Modifier.weight(1f))
                if (showAvg) MetricTile(caption = stringResource(R.string.m_avg), value = localizedDur(metrics.avgMinutesPerDay * 60_000), mod = Modifier.weight(1f))
                MetricTile(
                    caption = stringResource(R.string.m_best),
                    value = metrics.bestDay?.let { localizedDur(metrics.bestMinutes * 60_000) } ?: "—",
                    sub = metrics.bestDay, mod = Modifier.weight(1f),
                )
            }
            // 时段分布
            if (slots.isNotEmpty()) {
                Text(
                    stringResource(R.string.ts_title),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                slots.take(3).forEach { s ->
                    FocusBarRow(
                        label = bucketLabel(s.bucket),
                        value = s.minutes,
                        max = slots.first().minutes.coerceAtLeast(1),
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
    }
}

/** 通用可视化行:标签 — 比例条 — 数值(用于每日/每周/各时钟) */
@Composable
fun FocusBarRow(label: String, value: Long, max: Long, color: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.primary) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1.2f),
            maxLines = 1,
        )
        Box(
            Modifier.weight(2f).height(12.dp)
                .background(MaterialTheme.colorScheme.surfaceContainerHighest, RoundedCornerShape(6.dp)),
        ) {
            val fraction = (if (max > 0) value.toFloat() / max else 0f).coerceIn(0f, 1f)
            Box(
                Modifier.fillMaxWidth(fraction).height(12.dp)
                    .background(color, RoundedCornerShape(6.dp)),
            )
        }
        Text(
            localizedDur(value * 60_000),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
            maxLines = 1,
        )
    }
}

/** 带标题的条形列表(每个条形按 max 比例) */
@Composable
fun BarsList(
    title: String,
    rows: List<Pair<String, Long>>,
    unitMinutes: Long = 1,
) {
    val max = (rows.maxOfOrNull { it.second } ?: 1).coerceAtLeast(1)
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall)
        rows.forEach { (label, minutes) ->
            FocusBarRow(label = label, value = minutes, max = max)
        }
    }
}

@Composable
private fun MetricTile(caption: String, value: String, mod: Modifier = Modifier, sub: String? = null) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = mod,
    ) {
        Column(Modifier.padding(horizontal = 8.dp, vertical = 8.dp)) {
            Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(
                if (sub != null) "$caption·$sub" else caption,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun bucketLabel(bucket: TimeBucket): String = stringResource(
    when (bucket) {
        TimeBucket.MORNING -> R.string.bucket_morning
        TimeBucket.AFTERNOON -> R.string.bucket_afternoon
        TimeBucket.EVENING -> R.string.bucket_evening
        TimeBucket.NIGHT -> R.string.bucket_night
    },
)

@Composable
internal fun localizedDur(millis: Long): String {
    val totalMinutes = (millis + 59_999) / 60_000
    val h = totalMinutes / 60
    val m = totalMinutes % 60
    return if (h == 0L) stringResource(R.string.duration_m, m)
    else stringResource(R.string.duration_hm, h, m)
}
