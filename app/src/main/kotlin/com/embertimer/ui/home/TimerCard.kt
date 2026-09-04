package com.embertimer.ui.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
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
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.embertimer.R
import com.embertimer.timer.DurationFormat
import com.embertimer.timer.EngineStatus
import com.embertimer.timer.Phase
import com.embertimer.ui.theme.rememberAnimationsEnabled

/** 播放⇄暂停 morph:图标随状态弹性交叉淡入+缩放(Compose 无路径插值,此为诚实近似)。animationsOn=false 时直切 */
@Composable
private fun MorphToggleIcon(
    running: Boolean,
    animationsOn: Boolean,
    modifier: Modifier = Modifier,
) {
    val pausedIcon = painterResource(R.drawable.ic_play)
    val runningIcon = painterResource(R.drawable.ic_pause)
    if (!animationsOn) {
        Icon(if (running) runningIcon else pausedIcon, contentDescription = if (running) "暂停" else "恢复", modifier = modifier)
        return
    }
    AnimatedContent(
        targetState = running,
        transitionSpec = {
            (fadeIn(tween(90)) + scaleIn(initialScale = 0.85f, animationSpec = spring(dampingRatio = 0.8f))) togetherWith
                (fadeOut(tween(90)) + scaleOut(targetScale = 0.85f, animationSpec = spring(dampingRatio = 0.8f)))
        },
        label = "playPauseMorph",
    ) { isRunning ->
        Icon(
            if (isRunning) runningIcon else pausedIcon,
            contentDescription = if (isRunning) "暂停" else "恢复",
            modifier = modifier,
        )
    }
}

/** D3:按压时图标微缩至 92%,释放弹簧回弹 */
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
                            MorphToggleIcon(
                                running = running,
                                animationsOn = animationsOn,
                                modifier = if (animationsOn) Modifier.pressScale(pressed) else Modifier,
                            )
                        }
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
