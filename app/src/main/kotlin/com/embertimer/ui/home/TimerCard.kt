package com.embertimer.ui.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Card
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import com.embertimer.timer.DurationFormat
import com.embertimer.timer.Phase
import com.embertimer.ui.morph.IconPaths
import com.embertimer.ui.morph.PathIcon
import com.embertimer.ui.theme.MotionTokens
import com.embertimer.ui.theme.rememberAnimationsEnabled

/** 计时卡:阶段文案(D7 交叉交换)+ 大倒计时 + 循环徽标 + 动作区(重排见 TimerActions.kt) */
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
            // D7 状态文本交叉交换:新文案自下方 1/4 高度滑入(进 160ms),旧文案向上
            // 滑出淡出(出 100ms,快于进避免交叉发糊);时长全部取自 TextSwap* token。
            // animationsOn=false 直切纯文本
            if (animationsOn) {
                AnimatedContent(
                    targetState = phaseText,
                    contentAlignment = Alignment.Center,
                    transitionSpec = {
                        val enterMs = MotionTokens.TextSwapEnter.durationMillis
                        val exitMs = MotionTokens.TextSwapExit.durationMillis
                        (slideInVertically(tween(enterMs)) { it / 4 } + fadeIn(tween(enterMs)))
                            .togetherWith(
                                slideOutVertically(tween(exitMs)) { -it / 4 } + fadeOut(tween(exitMs)),
                            )
                            .using(SizeTransform(clip = false))
                    },
                    label = "phaseText",
                ) { text ->
                    Text(text, style = MaterialTheme.typography.titleMedium)
                }
            } else {
                Text(phaseText, style = MaterialTheme.typography.titleMedium)
            }
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
            ActionZone(
                status = snap?.status,
                startEnabled = ui.ready && ui.activeProfileId != -1L,
                animationsOn = animationsOn,
                onStart = { act(onStart) },
                onPause = { act(onPause) },
                onResume = { act(onResume) },
                onSkip = { act(onSkip) },
                onStop = { act(onStop) },
            )
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
