package com.embertimer.ui.home

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
import androidx.compose.ui.unit.dp
import com.embertimer.timer.DurationFormat
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
            Text("专注 " + DurationFormat.hm(d.totalMillis), style = MaterialTheme.typography.headlineSmall)
            if (d.rows.isEmpty()) {
                Text("当日无专注记录", style = MaterialTheme.typography.bodyMedium)
            } else {
                d.rows.forEach { row ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier.size(10.dp).clip(CircleShape).background(
                                MaterialTheme.colorScheme.primary.copy(alpha = (1f - row.index * 0.2f).coerceIn(0f, 1f)),
                            ),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(row.profileName, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                        Text(DurationFormat.hm(row.millis), style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }
}
