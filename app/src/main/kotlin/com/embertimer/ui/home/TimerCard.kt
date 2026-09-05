package com.embertimer.ui.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.embertimer.data.db.ProfileMode
import com.embertimer.timer.DurationFormat
import com.embertimer.timer.Phase
import com.embertimer.ui.morph.IconPaths
import com.embertimer.ui.morph.PathIcon
import com.embertimer.ui.theme.MotionTokens
import com.embertimer.ui.theme.rememberAnimationsEnabled

/** 计时卡:阶段文案(D7 交叉交换)+ 大数字(倒计时剩余 / 正计时已走)+ 循环徽标 + 动作区(重排见 TimerActions.kt)
 *  countUp(运行快照或当前配置为正计时)时:无到期/循环概念 → 徽标隐藏;skip 引擎已 no-op → 键隐藏 */
@Composable
internal fun TimerCard(
    ui: HomeUiState,
    displayMillis: Long,
    onStart: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onSkip: () -> Unit,
    onStop: () -> Unit,
    onGoSettings: () -> Unit,
) {
    val snap = ui.snap
    val empty = ui.profiles.isEmpty()
    val countUpActive = snap?.countUp == true ||
        ui.profiles.firstOrNull { it.id == ui.activeProfileId }?.mode == ProfileMode.COUNTUP
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
            // #3 空态引导:无配置时倒计时数字位换文案(数字必为 00:00,无意义),
            // 开始键维持 disabled(activeProfileId == -1),另提供直达设置的按钮
            // v1.1 #7:空态 ↔ 计时内容交叉时淡入/展开(进 160ms 出 100ms);关闭动画直切
            AnimatedContent(
                targetState = empty,
                transitionSpec = {
                    val enterMs = MotionTokens.TextSwapEnter.durationMillis
                    val exitMs = MotionTokens.TextSwapExit.durationMillis
                    (fadeIn(tween(enterMs)) + expandVertically(tween(enterMs)))
                        .togetherWith(fadeOut(tween(exitMs)) + shrinkVertically(tween(exitMs)))
                        .using(SizeTransform(clip = false))
                },
                label = "emptyState",
            ) { isEmpty ->
                // AnimatedContent 的 content 只应产出单子布局:多个子组合会落在内部 Box
                // 上互相重叠(曾致循环图标叠在倒计时数字左上)。各分支包居中 Column。
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    if (isEmpty) {
                        Text("先新建一个计时时钟", style = MaterialTheme.typography.titleLarge)
                        TextButton(onClick = onGoSettings) { Text("去新建时钟") }
                    } else {
                        Text(
                            DurationFormat.ms(displayMillis),
                            style = MaterialTheme.typography.displayMedium,
                        )
                        if (!countUpActive) {
                            CycleBadge(count = snap?.cycleCount ?: 0, animationsOn = animationsOn)
                        }
                    }
                }
            }
            val haptic = LocalHapticFeedback.current
            fun act(perform: () -> Unit) {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                perform()
            }
            ActionZone(
                status = snap?.status,
                startEnabled = ui.ready && ui.activeProfileId != -1L,
                animationsOn = animationsOn,
                showSkip = !countUpActive,
                onStart = { act(onStart) },
                onPause = { act(onPause) },
                onResume = { act(onResume) },
                onSkip = { act(onSkip) },
                onStop = { act(onStop) },
            )
        }
    }
}

/** D4:循环徽标(仅图标);count 递增时 repeat 图标弹跳一次(scale 1->1.25->1,约 220ms),animationsOn=false 时无动画。
 * 数字由 PathIcon 的 contentDescription 承载(TalkBack 可读),不渲染可见文本。 */
@Composable
internal fun CycleBadge(count: Int, animationsOn: Boolean, modifier: Modifier = Modifier) {
    val scale = remember { Animatable(1f) }
    var lastCount by remember { mutableIntStateOf(count) }
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        PathIcon(
            d = IconPaths.REPEAT,
            size = 16.dp,
            contentDescription = "循环 $count",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.graphicsLayer { scaleX = scale.value; scaleY = scale.value },
        )
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
