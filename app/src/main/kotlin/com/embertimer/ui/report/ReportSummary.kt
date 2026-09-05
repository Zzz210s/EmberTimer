package com.embertimer.ui.report

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.ui.unit.dp
import com.embertimer.R

/**
 * 健康风报表摘要(v1.5):总时长 Hero + 对比上期、四指标卡、时段分布小条。
 * 参考健康/屏幕时间周月报范式(总览→关键指标→趋势→时段)。
 */
@Composable
fun ReportSummary(
    metrics: ReportMetrics,
    slots: List<SlotMinutes>,
    totalMillis: Long,
    isMonth: Boolean,
    showAvg: Boolean = true,
) {
    val primary = MaterialTheme.colorScheme.primary
    val tileCard = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    Card {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            // Hero:总时长 + 较上期
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    localizedDur(totalMillis),
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                metrics.prevDeltaPercent?.let { d ->
                    val sign = if (d > 0) "+" else if (d < 0) "-" else ""
                    val abs = kotlin.math.abs(d)
                    val color = when {
                        d > 0 -> MaterialTheme.colorScheme.primary
                        d < 0 -> MaterialTheme.colorScheme.error
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    }
                    Text(
                        stringResource(if (isMonth) R.string.vs_last_month else R.string.vs_last_week) +
                            " $sign$abs%",
                        style = MaterialTheme.typography.labelMedium,
                        color = color,
                    )
                }
            }
            // 四指标(总时长页不计日均,避免长期跨度稀释误导)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MetricTile(tileCard, stringResource(R.string.m_focus_days), "${metrics.focusDays}", Modifier.weight(1f))
                MetricTile(tileCard, stringResource(R.string.m_streak), "${metrics.streakDays}", Modifier.weight(1f))
                if (showAvg) MetricTile(tileCard, stringResource(R.string.m_avg), localizedDur(metrics.avgMinutesPerDay * 60_000), Modifier.weight(1f))
                MetricTile(
                    tileCard, stringResource(R.string.m_best),
                    metrics.bestDay?.let { localizedDur(metrics.bestMinutes * 60_000) } ?: "—",
                    Modifier.weight(1f), sub = metrics.bestDay,
                )
            }
            // 时段分布(取前三)
            if (slots.isNotEmpty()) {
                Text(stringResource(R.string.ts_title), style = MaterialTheme.typography.labelLarge)
                slots.take(3).forEach { slot ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            bucketLabel(slot.bucket),
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.weight(1f),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Box(
                            Modifier
                                .weight(2f)
                                .height(8.dp)
                                .background(
                                    MaterialTheme.colorScheme.surfaceContainerHighest,
                                    RoundedCornerShape(4.dp),
                                ),
                        ) {
                            val max = slots.first().minutes
                            Box(
                                Modifier
                                    .fillMaxHeight()
                                    .fillMaxWidth(slot.minutes.toFloat() / max)
                                    .background(primary, RoundedCornerShape(4.dp)),
                            )
                        }
                        Text(
                            localizedDur(slot.minutes * 60_000),
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MetricTile(
    colors: androidx.compose.material3.CardColors,
    caption: String,
    value: String,
    modifier: Modifier,
    sub: String? = null,
) {
    Card(colors = colors, modifier = modifier) {
        Column(Modifier.padding(horizontal = 8.dp, vertical = 8.dp)) {
            Text(value, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
            Text(
                if (sub != null) "$caption·$sub" else caption,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
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
