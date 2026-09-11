package com.embertimer.ui.home

import com.embertimer.R
import androidx.compose.ui.res.stringResource
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.embertimer.data.DayPeriod
import com.embertimer.data.dayPeriodOf
import com.embertimer.data.groupByPeriod
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@Composable
fun DayDetailCard(detail: DayDetailUi?, modifier: Modifier = Modifier) {
    // 退出动画期间保留最后一份非空 detail:detail 变 null 的同帧内容会先重组为空,
    // shrink/fade 若作用于空布局则视觉上瞬间消失。写入必须与 null 过渡同一组合帧生效。
    var lastDetail by remember { mutableStateOf<DayDetailUi?>(null) }
    if (detail != null) lastDetail = detail
    AnimatedVisibility(
        visible = detail != null,
        enter = expandVertically() + fadeIn(),
        exit = shrinkVertically() + fadeOut(),
        modifier = modifier,
    ) {
        val d = lastDetail ?: return@AnimatedVisibility
        Column(
            Modifier.fillMaxWidth().padding(top = 12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                d.date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL)),
                style = MaterialTheme.typography.titleSmall,
            )
            Text(stringResource(R.string.focus_total, durLocalized(d.totalMillis)), style = MaterialTheme.typography.headlineSmall)
            if (d.rows.isEmpty()) {
                Text(stringResource(R.string.day_none), style = MaterialTheme.typography.bodyMedium)
            } else {
                d.rows.forEach { row -> ProfileSection(row) }
            }
        }
    }
}

@Composable
private fun ProfileSection(row: DayDetailRow) {
    Column(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(10.dp).clip(CircleShape).background(
                    MaterialTheme.colorScheme.primary.copy(alpha = (1f - row.index * 0.2f).coerceIn(0f, 1f)),
                ),
            )
            Spacer(Modifier.width(8.dp))
            Text(row.profileName, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            Text(durLocalized(row.millis), style = MaterialTheme.typography.bodyMedium)
        }
        // v1.10:各段 开始~结束(时:分)。左侧固定单列大时段标识(纯文字,无色块);
        // 标识右侧为双列时间段,单行不换行。
        if (row.sessions.isNotEmpty()) {
            val zone = ZoneId.systemDefault()
            Column(Modifier.fillMaxWidth().padding(start = 18.dp, top = 2.dp)) {
                groupByPeriod(row.sessions, zone).forEach { (period, spans) ->
                    spans.chunked(2).forEachIndexed { lineIdx, pair ->
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                if (lineIdx == 0) stringResource(periodRes(period)) else "",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                softWrap = false,
                                modifier = Modifier.width(PERIOD_COL_W),
                            )
                            SpanText(pair[0], zone, Modifier.weight(1f))
                            if (pair.size > 1) SpanText(pair[1], zone, Modifier.weight(1f))
                            else Spacer(Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}

/** 单条时间段文本:HH:mm ~ HH:mm(单行不换行) */
@Composable
private fun SpanText(seg: Pair<Long, Long>, zone: ZoneId, modifier: Modifier = Modifier) {
    Text(
        HHmm.format(Instant.ofEpochMilli(seg.first).atZone(zone)) + " ~ " +
            HHmm.format(Instant.ofEpochMilli(seg.second).atZone(zone)),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        softWrap = false,
        modifier = modifier,
    )
}

/** 左侧大时段标识列宽(固定,保证下方时间段对齐) */
private val PERIOD_COL_W = 32.dp

private fun periodRes(p: DayPeriod): Int = when (p) {
    DayPeriod.DAWN -> R.string.period_dawn
    DayPeriod.EARLY_MORNING -> R.string.period_early
    DayPeriod.MORNING -> R.string.period_morning
    DayPeriod.AFTERNOON -> R.string.period_afternoon
    DayPeriod.EVENING -> R.string.period_evening
}

private val HHmm = DateTimeFormatter.ofPattern("HH:mm")

@Composable
private fun durLocalized(millis: Long): String {
    val totalMinutes = (millis + 59_999) / 60_000
    val h = totalMinutes / 60
    val m = totalMinutes % 60
    return if (h == 0L) stringResource(R.string.duration_m, m)
    else stringResource(R.string.duration_hm, h, m)
}
