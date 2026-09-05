package com.embertimer.ui.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.embertimer.data.db.ProfileEntity
import com.embertimer.timer.EngineStatus
import com.embertimer.ui.morph.IconPaths
import com.embertimer.ui.morph.PathIcon
import com.embertimer.ui.report.ReportRange
import com.embertimer.ui.theme.MotionTokens
import com.embertimer.ui.theme.rememberAnimationsEnabled


/**
 * 主页顶栏(v1.2 #1):三件套 [齿轮 | 当前配置名(点击展开)| 汉堡] 之下挂**全屏宽展开面板**。
 * 面板在布局流内占位(AnimatedVisibility 高度动画),展开时把下方内容整体顺沿下移;
 * 收起回弹。两个面板互斥(开一个自动关另一个)。animationsOn=false 时面板直切无动画。
 * 面板行:配置面板 = 整宽行(仅配置名 + 当前勾选,计时中禁点,空态引导去设置);
 * 报表面板 = 周报/月报整宽行。菜单语义与 v1.1 下拉一致,仅形态改为推挤式全宽面板。
 */
@Composable
internal fun HomeTopBar(
    ui: HomeUiState,
    onSelectProfile: (ProfileEntity) -> Unit,
    onSettings: () -> Unit,
    onManageProfiles: () -> Unit,
    onOpenReport: (ReportRange) -> Unit,
) {
    val animationsOn = rememberAnimationsEnabled()
    var open by remember { mutableStateOf<HomePanel?>(null) }
    val running = ui.snap?.status == EngineStatus.RUNNING

    Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).statusBarsPadding()) {
        Row(
            Modifier.fillMaxWidth().height(56.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = { onSettings() }) {
                PathIcon(IconPaths.SETTINGS, size = 24.dp, contentDescription = "设置")
            }
            // 中央:当前配置名(或 未选择)+ 展开箭头;点击在 PROFILE 面板间切换。
            // 无配置时点击直达时钟管理(创建入口)
            Box(Modifier.weight(1f).fillMaxWidth().clickable {
                if (ui.profiles.isEmpty()) { onManageProfiles(); return@clickable }
                open = if (open == HomePanel.PROFILE) null else HomePanel.PROFILE
            }, contentAlignment = Alignment.Center) {
                val activeName = ui.profiles.firstOrNull { it.id == ui.activeProfileId }?.name ?: "未选择"
                val rotation by animateFloatAsState(if (open == HomePanel.PROFILE) 180f else 0f, label = "chevron")
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (animationsOn) {
                        AnimatedContent(
                            targetState = activeName,
                            transitionSpec = {
                                (fadeIn(tween(MotionTokens.TextSwapEnter.durationMillis)) +
                                    slideInVertically(tween(MotionTokens.TextSwapEnter.durationMillis)) { it / 3 })
                                    .togetherWith(
                                        fadeOut(tween(MotionTokens.TextSwapExit.durationMillis)) +
                                            slideOutVertically(tween(MotionTokens.TextSwapExit.durationMillis)) { -it / 3 },
                                    )
                            },
                            label = "profileNameSwap",
                        ) { name -> Text(name, style = MaterialTheme.typography.titleMedium) }
                    } else {
                        Text(activeName, style = MaterialTheme.typography.titleMedium)
                    }
                    Spacer(Modifier.width(4.dp))
                    PathIcon(
                        IconPaths.CHEVRON_DOWN, size = 20.dp, contentDescription = null,
                        modifier = Modifier.graphicsLayer { rotationZ = rotation },
                    )
                }
            }
            IconButton(onClick = { open = if (open == HomePanel.REPORT) null else HomePanel.REPORT }) {
                PathIcon(IconPaths.MENU, size = 24.dp, contentDescription = "菜单")
            }
        }
        // 面板区:布局流内占位 -> 高度动画推挤下方内容(顺沿下移/收起回弹)。
        // 开/关由外层 AnimatedVisibility 承担;面板间切换用 AnimatedContent 交叉淡入;
        // 关闭时保留最后面板内容随 shrink 一起收起(避免中途闪空)。
        if (animationsOn) {
            var last by remember { mutableStateOf<HomePanel?>(null) }
            if (open != null) last = open
            val shown = open ?: last
            AnimatedVisibility(
                visible = open != null,
                enter = expandVertically(tween(MotionTokens.TextSwapEnter.durationMillis)) + fadeIn(
                    tween(MotionTokens.TextSwapEnter.durationMillis),
                ),
                exit = shrinkVertically(tween(MotionTokens.TextSwapExit.durationMillis)) + fadeOut(
                    tween(MotionTokens.TextSwapExit.durationMillis),
                ),
            ) {
                AnimatedContent(
                    targetState = shown,
                    transitionSpec = {
                        (fadeIn(tween(MotionTokens.TextSwapEnter.durationMillis)) +
                            slideInVertically(tween(MotionTokens.TextSwapEnter.durationMillis)) { it / 8 })
                            .togetherWith(
                                fadeOut(tween(MotionTokens.TextSwapExit.durationMillis)) +
                                    slideOutVertically(tween(MotionTokens.TextSwapExit.durationMillis)) { -it / 8 },
                            )
                    },
                    label = "panelSwap",
                ) { p ->
                    if (p != null) {
                        PanelBody(p, ui, ui.profiles.isEmpty(), running, onSelectProfile, onManageProfiles, onOpenReport)
                    } else {
                        Box(Modifier.height(0.dp))
                    }
                }
            }
        } else if (open != null) {
            PanelBody(open!!, ui, ui.profiles.isEmpty(), running, onSelectProfile, onManageProfiles, onOpenReport)
        }
    }
}

/** 面板内容(整宽行列表);进入时行首自带轻微下滑位移,强化"展开"方向感 */
