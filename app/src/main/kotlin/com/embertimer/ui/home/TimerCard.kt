package com.embertimer.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilledIconToggleButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.embertimer.R
import com.embertimer.timer.DurationFormat
import com.embertimer.timer.EngineStatus
import com.embertimer.timer.Phase

/** 计时卡:阶段文案 + 大倒计时 + 循环徽标 + 图标动作区(D7:48dp 触控、动作轻震) */
@Composable
internal fun TimerCard(
    ui: HomeUiState,
    remaining: Long,
    onStart: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onSkip: () -> Unit,
    onStop: () -> Unit,
) {
    val snap = ui.snap
    Card(Modifier.fillMaxWidth()) {
        Column(
            Modifier.padding(16.dp).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            val phaseText = when {
                snap == null -> "空闲"
                snap.phase == Phase.WORK -> "工作中"
                else -> "休息中"
            }
            Text(phaseText, style = MaterialTheme.typography.titleMedium)
            Text(
                DurationFormat.ms(remaining),
                style = MaterialTheme.typography.displayMedium,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    painterResource(R.drawable.ic_repeat),
                    contentDescription = null, // 装饰性:数值紧随其后,语义由 Text 承载
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.width(4.dp))
                Text("循环 ${snap?.cycleCount ?: 0}", style = MaterialTheme.typography.bodyMedium)
            }
            val haptic = LocalHapticFeedback.current
            fun act(perform: () -> Unit) {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                perform()
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                when (snap?.status) {
                    null, EngineStatus.IDLE -> Button(
                        onClick = { act(onStart) },
                        enabled = ui.ready && ui.activeProfileId != -1L,
                    ) { Text("开始") } // 开始保留文字主按钮(空态唯一入口,图标歧义大)
                    EngineStatus.RUNNING -> FilledIconToggleButton(
                        checked = true,
                        onCheckedChange = { act(onPause) },
                        modifier = Modifier.size(72.dp),
                    ) {
                        Icon(painterResource(R.drawable.ic_pause), contentDescription = "暂停")
                    }
                    EngineStatus.PAUSED -> FilledIconToggleButton(
                        checked = false,
                        onCheckedChange = { act(onResume) },
                        modifier = Modifier.size(72.dp),
                    ) {
                        Icon(painterResource(R.drawable.ic_play), contentDescription = "恢复")
                    }
                }
                if (snap != null && snap.status != EngineStatus.IDLE) {
                    FilledTonalIconButton(onClick = { act(onSkip) }, modifier = Modifier.size(56.dp)) {
                        Icon(painterResource(R.drawable.ic_skip_next), contentDescription = "跳过")
                    }
                    FilledTonalIconButton(onClick = { act(onStop) }, modifier = Modifier.size(56.dp)) {
                        Icon(painterResource(R.drawable.ic_stop), contentDescription = "终止")
                    }
                }
            }
        }
    }
}
