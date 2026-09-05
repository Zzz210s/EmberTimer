package com.embertimer.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.embertimer.data.db.ProfileEntity
import com.embertimer.timer.EngineStatus
import com.embertimer.ui.morph.IconPaths
import com.embertimer.ui.morph.PathIcon
import com.embertimer.ui.report.ReportRange
import com.embertimer.ui.theme.MotionTokens
import com.embertimer.ui.theme.rememberAnimationsEnabled

/** 顶栏可展开的面板类型:配置选择 / 报表入口 */
internal enum class HomePanel { PROFILE, REPORT }


@Composable
internal fun PanelBody(
    panel: HomePanel,
    ui: HomeUiState,
    running: Boolean,
    onSelectProfile: (ProfileEntity) -> Unit,
    onManageProfiles: () -> Unit,
    onOpenReport: (ReportRange) -> Unit,
) {
    val surface = MaterialTheme.colorScheme.surface
    Column(Modifier.fillMaxWidth().background(surface)) {
        HorizontalDivider()
        when (panel) {
            HomePanel.PROFILE -> {
                // v1.3:置顶「时钟管理」管理入口(固定行,位于配置列表上方)
                Row(
                    Modifier.fillMaxWidth().clickable(onClick = onManageProfiles)
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("时钟管理", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    PathIcon(IconPaths.BACK, size = 18.dp, contentDescription = null)
                }
                HorizontalDivider()
                // 空态不可达:无时钟时 HomeTopBar 中央点击直接进时钟管理(永不展开本面板)
                ui.profiles.forEach { p ->
                    val selected = p.id == ui.activeProfileId
                    Row(
                        Modifier.fillMaxWidth().clickable(enabled = !running) { onSelectProfile(p) }
                            .padding(horizontal = 20.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            p.name,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.weight(1f),
                            color = if (running) MaterialTheme.colorScheme.onSurfaceVariant
                            else MaterialTheme.colorScheme.onSurface,
                        )
                        if (selected) {
                            PathIcon(IconPaths.CHECK, size = 20.dp, contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                    HorizontalDivider()
                }
            }
            HomePanel.REPORT -> {
                listOf(ReportRange.WEEK to "周报", ReportRange.MONTH to "月报").forEach { (r, label) ->
                    Row(
                        Modifier.fillMaxWidth().clickable { onOpenReport(r) }
                            .padding(horizontal = 20.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    }
                    HorizontalDivider()
                }
            }
        }
    }
}
