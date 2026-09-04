package com.embertimer.ui.home

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import com.embertimer.timer.DurationFormat
import com.embertimer.timer.EngineStatus
import com.embertimer.timer.Phase
import com.embertimer.ui.morph.IconPaths
import com.embertimer.ui.morph.MorphIcon
import com.embertimer.ui.morph.PathIcon
import com.embertimer.ui.theme.rememberAnimationsEnabled

/** D3:按压时图标微缩至 92%,释放弹簧回弹(曲线 = MotionTokens.PressSpring) */
private fun Modifier.pressScale(pressed: Boolean): Modifier = composed {
    val scale by animateFloatAsState(if (pressed) 0.92f else 1f, spring(dampingRatio = 0.6f), label = "pressScale")
    graphicsLayer { scaleX = scale; scaleY = scale }
}

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
    val animationsOn = rememberAnimationsEnabled()
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
            CycleBadge(count = snap?.cycleCount ?: 0, animationsOn = animationsOn)
            val haptic = LocalHapticFeedback.current
            fun act(perform: () -> Unit) {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                perform()
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                when (val status = snap?.status) {
                    null, EngineStatus.IDLE -> Button(
                        onClick = { act(onStart) },
                        enabled = ui.ready && ui.activeProfileId != -1L,
                    ) { Text("开始") } // 开始保留文字主按钮(空态唯一入口,图标歧义大)
                    // RUNNING/PAUSED 须共用一个组合槽:morph 靠同一 MorphToggleIcon 实例的
                    // targetState 翻转触发;若拆成两个 when 分支,状态切换会整枝替换、新图标直接落定,动效永不播
                    EngineStatus.RUNNING, EngineStatus.PAUSED -> {
                        val running = status == EngineStatus.RUNNING
                        val interaction = remember { MutableInteractionSource() }
                        val pressed by interaction.collectIsPressedAsState()
                        FilledIconToggleButton(
                            checked = running,
                            onCheckedChange = { if (running) act(onPause) else act(onResume) },
                            modifier = Modifier.size(72.dp),
                            interactionSource = interaction,
                        ) {
                            MorphIcon(
                                target = if (running) IconPaths.PAUSE else IconPaths.PLAY,
                                size = 24.dp,
                                animationsOn = animationsOn,
                                contentDescription = if (running) "暂停" else "恢复",
                                modifier = if (animationsOn) Modifier.pressScale(pressed) else Modifier,
                            )
                        }
                    }
                }
                if (snap != null && snap.status != EngineStatus.IDLE) {
                    FilledTonalIconButton(onClick = { act(onSkip) }, modifier = Modifier.size(56.dp)) {
                        PathIcon(d = IconPaths.SKIP, size = 24.dp, contentDescription = "跳过")
                    }
                    FilledTonalIconButton(onClick = { act(onStop) }, modifier = Modifier.size(56.dp)) {
                        PathIcon(d = IconPaths.STOP, size = 24.dp, contentDescription = "终止")
                    }
                }
            }
        }
    }
}

/** D4:循环徽标;count 递增时 repeat 图标弹跳一次(scale 1->1.25->1,约 220ms),animationsOn=false 时无动画 */
@Composable
internal fun CycleBadge(count: Int, animationsOn: Boolean, modifier: Modifier = Modifier) {
    val scale = remember { Animatable(1f) }
    var lastCount by remember { mutableIntStateOf(count) }
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        PathIcon(
            d = IconPaths.REPEAT,
            size = 16.dp,
            contentDescription = null, // 装饰性:数值紧随其后,语义由 Text 承载
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.graphicsLayer { scaleX = scale.value; scaleY = scale.value },
        )
        Spacer(Modifier.width(4.dp))
        Text("循环 $count", style = MaterialTheme.typography.bodyMedium)
    }
    LaunchedEffect(count) {
        if (count > lastCount) {
            if (animationsOn) {
                scale.snapTo(1.25f)
                scale.animateTo(1f, spring(dampingRatio = 0.5f))
            }
        }
        lastCount = count
    }
}
